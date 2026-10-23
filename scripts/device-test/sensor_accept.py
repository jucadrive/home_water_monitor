"""Physical ADS1115/SEN0244 acceptance; no simulated values or raw MQTT bodies in evidence."""
import argparse
from decimal import Decimal, ROUND_HALF_UP
import hashlib
import json
import re
import sys
import uuid
from bench import CACHE, sql
from control_accept import ControlAcceptance, AcceptanceFailure, http, require
from fault_proxy import FaultProxy
from server import start, stop

CALIBRATION = "sen0244-uncalibrated-v1"
SAMPLE = re.compile(r"HWM_SENSOR_SAMPLE seq=(\d+) tds_hundredths=(\d+) voltage_hundredths=(\d+)")


def sensor_values(body):
    require(body.get("calibrationVersion") == CALIBRATION and body.get("temperatureAssumedC") == 25,
            "real sensor source and assumed temperature")
    samples = body["measurements"]
    require(len(samples) <= 60, "sensor maximum samples")
    previous = 0
    for i, m in enumerate(samples, 1):
        require(m["seq"] == i and m["isWarmup"] == (i <= 10)
                and 1000 <= m["elapsedMs"] - previous <= 1250, "sensor sequence timing warmup")
        previous = m["elapsedMs"]
        mv = Decimal(str(m["rawVoltageMv"]))
        ppm = Decimal(str(m["tdsPpm"]))
        require(mv.is_finite() and ppm.is_finite() and 0 <= mv <= 2300 and 0 <= ppm <= 1000, "sensor ranges")
        # 0.01mV wire rounding still uniquely identifies a 0.125mV ADS1115 count.
        n = (mv / Decimal("0.125")).quantize(Decimal(1), rounding=ROUND_HALF_UP)
        exact_mv = n * Decimal("0.125")
        require(mv == exact_mv.quantize(Decimal(".01"), rounding=ROUND_HALF_UP), "ADC voltage quantization")
        v = exact_mv / 1000
        expected = ((Decimal("133.42") * v ** 3 - Decimal("255.86") * v ** 2
                     + Decimal("857.39") * v) / 2).quantize(Decimal(".01"), rounding=ROUND_HALF_UP)
        require(ppm == expected, "independent sensor polynomial")
    return samples


class SensorAcceptance(ControlAcceptance):
    def inspect_result(self, session_id):
        wires = [w for w, b in self.wires if b.get("eventType") == "SESSION_RESULT" and b.get("sessionId") == session_id]
        require(bool(wires), "physical sensor result observed")
        require(len({hashlib.sha256(w["payload"]).hexdigest() for w in wires}) == 1, "sensor payload frozen")
        body = json.loads(wires[0]["payload"], parse_float=Decimal)
        samples = sensor_values(body)
        uart_samples = {}
        for _, line in self.uart.history:
            match = SAMPLE.fullmatch(line)
            if match:
                seq, ppm, mv = map(int, match.groups())
                require(seq not in uart_samples, "device did not resample retained values")
                uart_samples[seq] = (Decimal(ppm) / 100, Decimal(mv) / 100)
        require(len(uart_samples) == len(samples) and all(uart_samples[m["seq"]] == (m["tdsPpm"], m["rawVoltageMv"])
                for m in samples), "device sampled values match wire")
        rows = json.loads(sql("SELECT COALESCE(json_agg(json_build_object('seq',seq,'elapsedMs',elapsed_ms,'tdsPpm',tds_ppm,"
            "'isWarmup',is_warmup,'rawVoltageMv',raw_voltage_mv) ORDER BY seq),'[]'::json) FROM tds_measurement WHERE session_id='"
            + session_id + "'"), parse_float=Decimal)
        require(rows == samples, "sensor wire equals every DB field")
        require(sql("SELECT calibration_version FROM tds_session WHERE session_id='" + session_id + "'") == CALIBRATION,
                "DB real calibration metadata")
        self.evidence["sensor"] = {"samples": len(samples), "resultPublications": len(wires), "uniquePayloadHashes": 1,
                                   "uartWireDatabaseEqual": True, "polynomialVerified": True, "calibrationVerified": False}

    def fault_blocks_start(self):
        snapshot = self.probe()
        require(snapshot["state"] == "IDLE" and snapshot["fault"] == "CONTROL_UNAVAILABLE"
                and not snapshot["controlReady"] and snapshot["controlWindow"] is None, "sensor fault blocks execution window")
        code, c = http("/api/devices/esp32-bench/measurement-commands", {"type": "START"}, str(uuid.uuid4()))
        require(code == 202, "fault command tracked by REST")
        query = "/api/measurement-commands/" + c["commandId"]
        self.wait(lambda: http(query)[1]["commandState"] == "NOT_DISPATCHED", 15, "sensor START not dispatched")
        require(sql("SELECT count(*) FROM tds_session WHERE session_id='" + c["sessionId"] + "'") == "0",
                "no fabricated measurement after init fault")
        require(not any(b.get("eventType") == "START" and b.get("commandId") == c["commandId"] for _, b in self.wires),
                "no MQTT START while faulted")
        self.evidence["faultBlocksStart"] = True

    def interrupted(self, after):
        start(control=True)
        require(self.uart.refresh().get("state") == "IDLE", "sensor initial idle")
        self.wait(lambda: http("/api/devices/esp32-bench/status")[1].get("liveness") == "FRESH", 35, "sensor fresh")
        code, c = http("/api/devices/esp32-bench/measurement-commands", {"type": "START"}, str(uuid.uuid4()))
        require(code == 202, "sensor START")
        query = "/api/measurement-commands/" + c["commandId"]
        self.wait(lambda: int(self.uart.status.get("samples", "0")) >= after, 90, "sensor before manual SDA removal")
        print("MANUAL_READY action=remove_ADC_SDA_signal_only_keep_power_and_USB", flush=True)
        self.wait(lambda: http(query)[1].get("deviceBuffer") == "RELEASE_CONFIRMED", 110, "incomplete preservation release")
        view = http(query)[1]
        require(view["serverPreservation"] == "INCOMPLETE" and view["commandState"] == "ACCEPTED", "incomplete axes")
        require(any(line.startswith("HWM_SENSOR_FAULT category=acquisition") for _, line in self.uart.history), "actual acquisition failure")
        self.inspect_result(c["sessionId"])
        require(self.evidence["sensor"]["samples"] < 60, "failure did not fabricate remaining samples")
        require(sql("SELECT stats_sample_count=0 AND min_tds_ppm IS NULL AND max_tds_ppm IS NULL AND avg_tds_ppm IS NULL"
                    " AND median_tds_ppm IS NULL FROM tds_session WHERE session_id='" + c["sessionId"] + "'") == "t",
                "incomplete excluded from statistics")
        self.fault_blocks_start()
        self.evidence["interrupted"] = {"sessionId": c["sessionId"], "preserved": "INCOMPLETE", "released": True}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", default="COM5")
    parser.add_argument("--case", choices=["normal", "init-fault", "interrupted", "retry"], default="normal")
    parser.add_argument("--after", type=int, choices=range(1, 31), default=10)
    args = parser.parse_args()
    test = SensorAcceptance(args.port)
    try:
        if args.case == "normal":
            test.normal()
            test.inspect_result(test.evidence["normal"]["sessionId"])
        elif args.case == "init-fault":
            start(control=True)
            test.fault_blocks_start()
        elif args.case == "interrupted":
            test.interrupted(args.after)
        else:
            stop()
            with FaultProxy(tx_id="bench-server-control", rx_id="bench-server-status") as proxy:
                proxy.set_mode("drop-results")
                try:
                    test.retry(proxy)
                    test.inspect_result(test.evidence["retry"]["sessionId"])
                finally:
                    test.evidence["retryProxy"] = {"resultsDropped": proxy.results_dropped, "errors": proxy.errors}
                    stop()
            start(control=True)
        test.evidence["physicalDevice"] = True
        test.evidence["finalStatus"] = test.uart.refresh()
        require(test.evidence["finalStatus"].get("state") == "IDLE" and test.evidence["finalStatus"].get("samples") == "0",
                "sensor final IDLE and empty buffer")
        CACHE.mkdir(parents=True, exist_ok=True)
        (CACHE / ("sensor-" + args.case + ".json")).write_text(json.dumps(test.evidence, indent=2), encoding="utf-8")
        print("PHYSICAL SENSOR ACCEPTANCE PASS case=" + args.case)
    finally:
        CACHE.mkdir(parents=True, exist_ok=True)
        # Retain diagnostic counts/hashes on failure without storing MQTT bodies or credentials.
        test.evidence["resultObservations"] = [
            {"sessionId": b.get("sessionId"), "bytes": len(w["payload"]),
             "sha256": hashlib.sha256(w["payload"]).hexdigest()}
            for w, b in test.wires if isinstance(b, dict) and b.get("eventType") == "SESSION_RESULT"][-100:]
        (CACHE / "sensor-last-diagnostic.json").write_text(json.dumps({"evidence": test.evidence, "status": test.uart.status,
            "uart": list(test.uart.history)}, indent=2), encoding="utf-8")
        test.close()


if __name__ == "__main__":
    try:
        main()
    except AcceptanceFailure as error:
        print("PHYSICAL SENSOR ACCEPTANCE FAILED stage=" + str(error), file=sys.stderr)
        sys.exit(1)
    except Exception as error:
        print("PHYSICAL SENSOR ACCEPTANCE FAILED category=" + type(error).__name__ + "; raw/secret output withheld", file=sys.stderr)
        sys.exit(1)
