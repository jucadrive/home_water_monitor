"""Physical AP loss/EN reset acceptance; waits for human actions without changing the public device protocol."""
import argparse
import json
import sys
import time
import uuid
from accept import Acceptance, AcceptanceFailure, require
from bench import CACHE, Observer, sql
from server import start, stop
from uart import Uart


def run(case):
    start()
    observer, uart = Observer(), None
    test = None
    try:
        uart = Uart()
        deadline = time.monotonic() + 40
        while time.monotonic() < deadline:
            status = uart.refresh()
            require(status.get("state") == "IDLE", "A retained session exists; refusing manual acceptance START")
            if status.get("rx_ready") == "1": break
            time.sleep(0.5)
        require(status.get("rx_ready") == "1", "Device receiver did not become ready before manual acceptance")
        test = Acceptance(uart, observer, 60, str(uuid.uuid4()))
        stop()
        uart.send("START " + test.session_id)
        test.wait(lambda: bool(test.results), 120, "First physical publication")
        test.retained()
        if case == "wifi-loss":
            test.wait(lambda: uart.status.get("state") == "MANUAL_WAIT", 260, "Manual wait before AP loss")
            test.retained_guards()
            print("MANUAL_READY action=hotspot_off_20_seconds_then_on", flush=True)
            test.wait(lambda: uart.status.get("wifi_up") == "0", 240, "Physical Wi-Fi disconnection")
            outage_status = dict(uart.status)
            require(outage_status.get("session") == test.session_id and outage_status.get("samples") == "60"
                    and outage_status.get("finalized") == "1" and not test.released, "Wi-Fi outage did not retain the final buffer")
            observer.close()
            observer = None
            test.wait(lambda: uart.status.get("wifi_up") == "1" and uart.status.get("rx_ready") == "1", 240, "Physical Wi-Fi recovery")
            test.retained_guards()
            observer = Observer()
            test.observer = observer
            previous_results = len(test.results)
            uart.send("RETRY " + test.session_id)
            test.wait(lambda: len(test.results) > previous_results, 120, "Same-session retransmission after AP recovery")
            test.retained()
            start()
            test.database()
            test.completed_guards()
            evidence = {"case": case, "physicalDevice": True, "wifiDownObserved": True, "wifiRecovered": True,
                        "outageStatus": outage_status,
                        "resultPublications": len(test.results), "uniquePayloadHashes": len({r["sha256"] for r in test.results}),
                        "deviceReleased": test.released, "status": uart.status}
        else:
            before_reset_status = dict(uart.status)
            print("MANUAL_READY action=press_EN_once_without_BOOT", flush=True)
            marker = time.monotonic()
            test.wait(lambda: any(t >= marker and line.startswith("HWM_BOOT ") for t, line in uart.history), 240, "Physical EN reboot")
            test.wait(lambda: uart.status.get("state") == "IDLE" and uart.status.get("samples") == "0"
                      and uart.status.get("finalized") == "0" and uart.status.get("rx_ready") == "1", 60, "Idle state after physical reboot")
            before = sum(line.startswith("HWM_CONFIRM_REJECTED") for _, line in uart.history)
            start()
            test.wait(lambda: bool(test.confirmations) and sum(line.startswith("HWM_CONFIRM_REJECTED") for _, line in uart.history) > before,
                      40, "Rejection of pre-reboot preservation confirmation")
            deadline = time.monotonic() + 10
            while time.monotonic() < deadline:
                test.pump()
                require(not test.released and uart.status.get("state") == "IDLE" and uart.status.get("samples") == "0",
                        "Reboot unexpectedly started measurement or reported confirmation release")
            require(sql("SELECT preserved_sample_count FROM tds_session WHERE session_id='" + test.session_id + "'") == "60",
                    "Pre-reboot result was not preserved by the isolated server")
            originals = sql("SELECT json_agg(json_build_object('seq',seq,'elapsedMs',elapsed_ms,'tdsPpm',tds_ppm,'isWarmup',is_warmup,'rawVoltageMv',raw_voltage_mv) ORDER BY seq) FROM tds_measurement WHERE session_id='" + test.session_id + "'")
            from decimal import Decimal
            require(json.loads(originals, parse_float=Decimal) == test.results[0]["body"]["measurements"], "Pre-reboot DB original content mismatch")
            evidence = {"case": case, "physicalDevice": True, "bootObserved": True, "ramLossAllowedByMvp": True,
                        "beforeResetStatus": before_reset_status,
                        "noAutoStart": True, "oldConfirmationRejected": True, "status": uart.status}
        evidence["sessionId"] = test.session_id
        CACHE.mkdir(parents=True, exist_ok=True)
        (CACHE / ("accept-" + case + "-60.json")).write_text(json.dumps(evidence, indent=2), encoding="utf-8")
        print("PHYSICAL ACCEPTANCE PASS case=" + case, flush=True)
    except Exception:
        if uart:
            CACHE.mkdir(parents=True, exist_ok=True)
            (CACHE / ("failed-" + case + ".json")).write_text(json.dumps({"status": uart.status, "uart": list(uart.history)}, indent=2), encoding="utf-8")
        raise
    finally:
        if uart: uart.close()
        if observer: observer.close()
        start()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--case", choices=["wifi-loss", "reboot"], required=True)
    args = parser.parse_args()
    try:
        run(args.case)
    except AcceptanceFailure as failure:
        print("PHYSICAL ACCEPTANCE NOT VERIFIED/FAILED: " + str(failure), file=sys.stderr)
        sys.exit(1)
    except Exception as failure:
        print("PHYSICAL ACCEPTANCE NOT VERIFIED/FAILED category=" + type(failure).__name__, file=sys.stderr)
        sys.exit(1)
