"""Physical ESP32 acceptance. Requires a reachable LAN and correctly configured/flashed image.
Never substitutes PC smoke/proxy unit results for device evidence.
"""
import argparse
from contextlib import nullcontext
from decimal import Decimal
import hashlib
import json
import sys
import time
import uuid
from bench import CACHE, Observer, compose, credentials, port_ready, publish, sql
from fault_proxy import FaultProxy
from server import start, stop
from uart import Uart


class AcceptanceFailure(RuntimeError):
    """Only constant, secret-free acceptance labels may be reported to the terminal."""


def require(condition, reason):
    if not condition: raise AcceptanceFailure(reason)


def valid_wire_metadata(wire):
    return (wire["qos"] == 1 and not wire["retained"] and wire["pfi"]
            and wire["contentType"] == "application/json" and len(wire["payload"]) <= 16384)


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result: raise ValueError("Duplicate JSON property")
        result[key] = value
    return result


def valid_confirmation(body, count):
    def whole(value):
        return isinstance(value, (int, Decimal)) and not isinstance(value, bool) and 0 <= value <= 60 and value == int(value)
    if set(body) != {"schemaVersion", "eventType", "deviceId", "sessionId", "preservationResult", "preservedSampleCount", "comparison"}:
        return False
    if not whole(body["schemaVersion"]) or body["schemaVersion"] != 1 or body["eventType"] != "SESSION_PRESERVED" or body["deviceId"] != "esp32-bench":
        return False
    if not whole(body["preservedSampleCount"]) or body["preservedSampleCount"] != count or body["preservationResult"] != ("NORMAL" if count == 60 else "INCOMPLETE"):
        return False
    comparison = body["comparison"]
    return comparison is None or (isinstance(comparison, dict) and set(comparison) == {"identicalCount", "conflictCount"}
        and all(whole(value) for value in comparison.values()) and sum(comparison.values()) == count)


class Acceptance:
    def __init__(self, uart, observer, samples, session_id):
        self.uart, self.observer, self.samples, self.session_id = uart, observer, samples, session_id
        self.results, self.confirmations = [], []
        self.released = False
        self.last_status = 0
        self.last_progress = time.monotonic()

    def pump(self):
        for line in self.uart.receive():
            if line.startswith("HWM_BUFFER_RELEASED session=" + self.session_id + " "):
                self.released = True
        wire = self.observer.receive(timeout=0.02)
        if wire:
            result_topic = wire["topic"].endswith("/events")
            try:
                body = json.loads(wire["payload"], object_pairs_hook=unique_object, parse_float=Decimal)
            except ValueError:
                if result_topic: raise
                body = {}
            if isinstance(body, dict) and body.get("sessionId") == self.session_id:
                valid_metadata = valid_wire_metadata(wire)
                if result_topic:
                    require(valid_metadata and body.get("eventType") == "SESSION_RESULT", "MQTT result metadata mismatch")
                    self.validate_result(body)
                    self.results.append({"body": body, "sha256": hashlib.sha256(wire["payload"]).hexdigest()})
                elif valid_metadata and valid_confirmation(body, self.samples):
                    self.confirmations.append(wire["payload"])
        if time.monotonic() - self.last_status >= 2:
            self.uart.send("STATUS")
            self.last_status = time.monotonic()
        if time.monotonic() - self.last_progress >= 30:
            print("PHYSICAL PROGRESS state=" + self.uart.status.get("state", "unknown")
                  + " samples=" + self.uart.status.get("samples", "unknown")
                  + " attempt=" + self.uart.status.get("attempt", "unknown")
                  + " results=" + str(len(self.results)), flush=True)
            self.last_progress = time.monotonic()

    def wait(self, predicate, seconds, label):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            self.pump()
            if predicate(): return
        raise AcceptanceFailure(label + " timed out")

    def validate_result(self, body):
        require(set(body) == {"schemaVersion", "eventType", "deviceId", "sessionId", "startedAt", "expectedSamples", "samplingIntervalMs", "temperatureAssumedC", "calibrationVersion", "measurements"}, "Result fields mismatch")
        require(body["schemaVersion"] == 1 and body["deviceId"] == "esp32-bench" and body["startedAt"] is None and body["expectedSamples"] == 60
                and body["samplingIntervalMs"] == 1000 and body["temperatureAssumedC"] == 25 and body["calibrationVersion"] == "simulated-v1", "Result metadata mismatch")
        require(len(body["measurements"]) == self.samples, "Flashed sample target differs from acceptance target")
        for index, measurement in enumerate(body["measurements"]):
            require(set(measurement) == {"seq", "elapsedMs", "tdsPpm", "isWarmup", "rawVoltageMv"}, "Measurement fields mismatch")
            require(type(measurement["seq"]) is int and measurement["seq"] == index + 1 and type(measurement["elapsedMs"]) is int
                    and (index + 1) * 1000 <= measurement["elapsedMs"] <= 2147483647
                    and measurement["tdsPpm"] == Decimal(200) + Decimal(index) / 100
                    and type(measurement["isWarmup"]) is bool and measurement["isWarmup"] == (index < 10)
                    and measurement["rawVoltageMv"] == Decimal(1000) + Decimal(index) / 100, "Simulated source mismatch")

    def retained(self):
        self.uart.refresh()
        require(self.uart.status.get("session") == self.session_id and self.uart.status.get("samples") == str(self.samples)
                and self.uart.status.get("finalized") == "1" and not self.released, "Device did not retain its final buffer")

    def rejected_command(self, command):
        prefix = "HWM_COMMAND_REJECTED type=" + command.split()[0]
        before = sum(line == prefix for _, line in self.uart.history)
        self.uart.send(command)
        self.wait(lambda: sum(line == prefix for _, line in self.uart.history) > before, 5, "Device command rejection")

    def retained_guards(self):
        self.rejected_command("START " + str(uuid.uuid4()))
        self.rejected_command("RETRY " + str(uuid.uuid4()))
        self.retained()

    def completed_guards(self):
        self.rejected_command("START " + self.session_id)
        before = sum(line.startswith("HWM_CONFIRM_REJECTED") for _, line in self.uart.history)
        require(publish(self.confirmations[-1]), "Duplicate confirmation publication failed")
        self.wait(lambda: sum(line.startswith("HWM_CONFIRM_REJECTED") for _, line in self.uart.history) > before, 40, "Duplicate confirmation rejection")
        self.uart.refresh()
        require(self.uart.status.get("state") == "IDLE" and self.uart.status.get("samples") == "0"
                and sum(line.startswith("HWM_BUFFER_RELEASED session=" + self.session_id + " ") for _, line in self.uart.history) == 1,
                "Completed session was restarted or released twice")

    def database(self):
        self.wait(lambda: self.released and bool(self.results) and bool(self.confirmations), 40, "Device release evidence")
        self.uart.refresh()
        require(self.uart.status.get("state") == "IDLE" and self.uart.status.get("samples") == "0" and self.uart.status.get("finalized") == "0", "Device buffer is not empty")
        expected = "NORMAL" if self.samples == 60 else "INCOMPLETE"
        require(sql("SELECT preserved_sample_count || ':' || preservation_result FROM tds_session WHERE session_id='" + self.session_id + "'") == str(self.samples) + ":" + expected, "DB preservation mismatch")
        require(sql("SELECT count(*) FROM tds_measurement WHERE session_id='" + self.session_id + "'") == str(self.samples), "DB original row count mismatch")
        originals = sql("SELECT COALESCE(json_agg(json_build_object('seq',seq,'elapsedMs',elapsed_ms,'tdsPpm',tds_ppm,'isWarmup',is_warmup,'rawVoltageMv',raw_voltage_mv) ORDER BY seq),'[]'::json) FROM tds_measurement WHERE session_id='" + self.session_id + "'")
        require(json.loads(originals, parse_float=Decimal) == self.results[0]["body"]["measurements"], "DB original content differs from frozen device list")
        require(len({item["sha256"] for item in self.results}) == 1, "Retransmission changed its fixed payload")


def negative_confirmations(test):
    valid = json.loads(test.confirmations[-1])
    variants = []
    for name, value in (("sessionId", str(uuid.uuid4())), ("deviceId", "foreign"), ("eventType", "SESSION_RESULT"), ("preservedSampleCount", 59 if test.samples == 60 else test.samples + 1)):
        variants.append((dict(valid, **{name: value}), {}))
    variants.extend([(dict(valid, extra=1), {}), (dict(valid, comparison={"identicalCount": 60, "conflictCount": 60}), {}),
                     (valid, {"pfi": "false"}), (valid, {"pfi": "missing"}), (valid, {"content_type": "missing"}),
                     (valid, {"qos": 0}), (valid, {"retained": True})])
    encoded = json.dumps(valid, separators=(",", ":")).encode()
    variants.append((encoded[:-1] + b',"schemaVersion":1}', {}))
    variants.append((encoded + b" " * (16385 - len(encoded)), {}))
    try:
        for payload, options in variants:
            rejected = sum(line.startswith("HWM_CONFIRM_REJECTED") for _, line in test.uart.history)
            require(publish(payload, **options), "Negative confirmation was not published")
            test.wait(lambda: sum(line.startswith("HWM_CONFIRM_REJECTED") for _, line in test.uart.history) > rejected, 10, "Device invalid confirmation rejection")
            test.retained()
    finally:
        require(publish(b"", retained=True), "Retained test confirmation cleanup failed")
    require(publish(encoded + b" " * (16384 - len(encoded))), "Maximum-size valid confirmation failed")


def run(args):
    proxy_mode = args.case.startswith("proxy-")
    context = FaultProxy(host=credentials()["lanIp"]) if proxy_mode else nullcontext(None)
    start()
    evidence = None
    with context as proxy:
        if proxy:
            proxy.set_mode("drop-puback-only" if args.case == "proxy-early" else "drop-puback" if args.case == "proxy-puback" else "drop-confirmation")
        observer = Observer()
        try: uart = Uart(args.port)
        except Exception:
            observer.close()
            raise
        test = Acceptance(uart, observer, args.samples, str(uuid.uuid4()))
        try:
            deadline = time.monotonic() + 40
            while time.monotonic() < deadline:
                status = uart.refresh()
                if status.get("state") == "IDLE" and status.get("rx_ready") == "1": break
                require(status.get("state") == "IDLE", "A retained/measuring session already exists; refusing START")
                time.sleep(0.5)
            require(uart.status.get("rx_ready") == "1", "ESP32 LAN/MQTT is not ready; no measurement was started")
            if proxy: require(proxy.rx_connections > 0, "Proxy image must use broker port 18884; no measurement was started")
            if args.case in ("retry", "exhaust", "broker-restart"): stop()
            if args.case == "db-outage": compose("stop", "postgres")
            uart.send("START " + test.session_id)
            if args.samples == 60:
                test.rejected_command("RETRY " + test.session_id)
                test.rejected_command("START " + str(uuid.uuid4()))
            if args.case == "normal" or args.case == "proxy-early":
                test.wait(lambda: test.released, 120, "Normal preservation")
            elif args.case in ("retry", "db-outage"):
                test.wait(lambda: len(test.results) >= 2, 150, "Second result publication")
                test.retained()
                test.retained_guards()
                if args.case == "retry": start()
                else: compose("up", "-d", "--wait", "postgres")
            elif args.case == "broker-restart":
                test.wait(lambda: len(test.results) >= 1, 100, "First publication")
                test.retained()
                test.retained_guards()
                compose("restart", "broker")
                observer.close()
                deadline = time.monotonic() + 15
                while not port_ready(18883) and time.monotonic() < deadline: time.sleep(0.2)
                observer = Observer()
                test.observer = observer
                start()
            else:
                test.wait(lambda: uart.status.get("state") == "MANUAL_WAIT", 260, "Five-attempt exhaustion")
                test.retained()
                test.retained_guards()
                require(uart.status.get("attempt") == "5" and uart.status.get("tx_active") == "0",
                        "Device did not stop after five attempts")
                # Preparation/send failures consume attempts even when no complete PUBLISH reaches the broker.
                require(1 <= len(test.results) <= 5, "No preserved result or publications exceed the attempt limit")
                publications_at_exhaustion = len(test.results)
                # A full SDK retransmit interval after exhaustion must produce no orphaned publication.
                deadline = time.monotonic() + 65
                while time.monotonic() < deadline:
                    test.pump()
                    require(len(test.results) == publications_at_exhaustion and not test.released,
                            "Unsolicited transmission/release after exhaustion")
                test.retained()
                if args.case == "exhaust": start()  # queued committed confirmation may arrive during manual wait
                elif args.case == "proxy-puback":
                    require(proxy.pubacks_dropped == publications_at_exhaustion, "PUBACK fault did not cover every observed publication")
                    proxy.set_mode("pass")
                    uart.send("RETRY " + test.session_id)
                    test.wait(lambda: test.released or (uart.status.get("state") == "MANUAL_WAIT" and uart.status.get("round") == "2"),
                              360, "Manual RETRY round preservation")
                    require(test.released, "Manual RETRY round exhausted without a valid preservation confirmation")
                elif args.case == "proxy-negative":
                    require(bool(test.confirmations), "Server preservation confirmation was not independently observed")
                    proxy.set_mode("pass")
                    negative_confirmations(test)
            test.database()
            if args.case in ("retry", "exhaust", "proxy-puback", "proxy-negative"):
                minimum = len(test.results) - 1
                deadline = time.monotonic() + 10
                while time.monotonic() < deadline:
                    if int(sql("SELECT count(*) FROM tds_session_redelivery WHERE session_id='" + test.session_id + "'")) >= minimum: break
                    time.sleep(0.1)
                else: raise AcceptanceFailure("Committed redelivery diagnostics did not match repeated publications")
            if args.case == "proxy-early":
                require(not any("HWM_PUBACK success=1" in line for _, line in uart.history), "Expected preservation before successful PUBACK")
            test.completed_guards()
            evidence = {"case": args.case, "samples": args.samples, "sessionId": test.session_id,
                        "resultPublications": len(test.results), "uniquePayloadHashes": len({r["sha256"] for r in test.results}),
                        "validConfirmationFrames": len(test.confirmations), "deviceReleased": test.released,
                        "status": uart.status, "physicalDevice": True, "completedGuardsVerified": True}
            if proxy:
                evidence["proxy"] = {"txConnections": proxy.tx_connections, "publications": list(proxy.publications),
                                     "pubacksDropped": proxy.pubacks_dropped, "confirmationsDropped": proxy.confirmations_dropped}
        except Exception:
            CACHE.mkdir(parents=True, exist_ok=True)
            diagnostic = {"case": args.case, "sessionId": test.session_id, "status": uart.status,
                          "results": len(test.results), "released": test.released, "uart": list(uart.history)}
            if proxy:
                diagnostic["proxy"] = {"txConnections": proxy.tx_connections, "publications": list(proxy.publications)}
            (CACHE / ("failed-" + args.case + ".json")).write_text(json.dumps(diagnostic, indent=2), encoding="utf-8")
            raise
        finally:
            uart.close()
            observer.close()
            if args.case == "db-outage": compose("up", "-d", "--wait", "postgres")
            start()
    CACHE.mkdir(parents=True, exist_ok=True)
    (CACHE / ("accept-" + args.case + "-" + str(args.samples) + ".json")).write_text(json.dumps(evidence, indent=2), encoding="utf-8")
    print("PHYSICAL ACCEPTANCE PASS case=" + args.case + " samples=" + str(args.samples))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--case", choices=["normal", "retry", "exhaust", "db-outage", "broker-restart", "proxy-puback", "proxy-early", "proxy-negative"], default="normal")
    parser.add_argument("--samples", type=int, choices=[0, 1, 59, 60], default=60)
    parser.add_argument("--port", default="COM3")
    args = parser.parse_args()
    try:
        run(args)
    except AcceptanceFailure as failure:
        print("PHYSICAL ACCEPTANCE NOT VERIFIED/FAILED: " + str(failure), file=sys.stderr)
        sys.exit(1)
    except Exception as failure:
        print("PHYSICAL ACCEPTANCE error category=" + type(failure).__name__, file=sys.stderr)
        print("PHYSICAL ACCEPTANCE NOT VERIFIED/FAILED; check LAN, UART and device STATUS. Secret/raw logs withheld.", file=sys.stderr)
        sys.exit(1)
