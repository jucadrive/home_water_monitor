"""Actual ESP32 control acceptance; secrets and MQTT bodies stay in local files/memory."""
import argparse
import json
import sys
import time
import uuid
import urllib.request
import urllib.error
import hashlib
from decimal import Decimal
from concurrent.futures import ThreadPoolExecutor
from bench import CACHE, Observer, publish, sql
from server import start, stop
from uart import Uart
from fault_proxy import FaultProxy

BASE = "http://127.0.0.1:18080"
TOPIC = "home/water/v1/esp32-bench/"
class AcceptanceFailure(RuntimeError):
    """Only fixed test labels may be included in terminal output."""


def require(condition, label):
    if not condition:
        raise AcceptanceFailure(label)


def http(path, body=None, key=None):
    headers = {"Content-Type": "application/json"}
    if key: headers["Idempotency-Key"] = key
    request = urllib.request.Request(BASE + path, data=None if body is None else json.dumps(body).encode(), headers=headers)
    try:
        response = urllib.request.urlopen(request, timeout=5)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        return response.status, json.load(response)


class ControlAcceptance:
    def __init__(self, port):
        self.uart = Uart(port)
        self.observer = Observer(topics=[TOPIC + s for s in ("events", "preservation", "commands", "status")], include_expiry=True)
        self.wires = []
        self.snapshots = []
        self.evidence = {}
        self.next_uart_status = 0

    def pump(self):
        if time.monotonic() >= self.next_uart_status:
            self.uart.send("STATUS")
            self.next_uart_status = time.monotonic() + 2
        self.uart.receive()
        wire = self.observer.receive(0.02)
        if wire:
            body = json.loads(wire["payload"], parse_float=Decimal)
            # Guard cases deliberately publish invalid commands. Every DEVICE_STATUS remains strict.
            if wire["topic"].endswith("/status"):
                require(wire["qos"] == 1 and not wire["retained"] and wire["pfi"]
                        and wire["contentType"] == "application/json" and len(wire["payload"]) <= 4096
                        and wire["expiry"] is not None and 1 <= wire["expiry"] <= 10, "control wire metadata")
            self.wires.append((wire, body))
            if body.get("eventType") == "DEVICE_STATUS": self.snapshots.append(body)

    def wait(self, predicate, seconds, label):
        deadline = time.monotonic() + seconds
        progress = time.monotonic()
        while time.monotonic() < deadline:
            self.pump()
            if predicate(): return
            if time.monotonic() - progress > 20:
                print("CONTROL_ACCEPT_PROGRESS stage=" + label, flush=True)
                progress = time.monotonic()
        raise AcceptanceFailure(label)

    def publish(self, body, **kwargs):
        require(publish(body, topic=TOPIC + "commands", expiry=10, **kwargs), "command injection publish")

    def probe(self):
        probe = str(uuid.uuid4())
        self.publish({"schemaVersion": 1, "eventType": "STATE_REQUEST", "deviceId": "esp32-bench", "probeId": probe})
        self.wait(lambda: any(s["replyToProbeId"] == probe for s in self.snapshots), 8, "probe reply")
        return next(s for s in reversed(self.snapshots) if s["replyToProbeId"] == probe)

    @staticmethod
    def command(snapshot):
        return {"schemaVersion": 1, "eventType": "START", "deviceId": "esp32-bench", "commandId": str(uuid.uuid4()),
                "sessionId": str(uuid.uuid4()), "targetBootId": snapshot["bootId"],
                "windowId": snapshot["controlWindow"]["windowId"], "expectedSamples": 60, "samplingIntervalMs": 1000}

    def rejected(self, command, reason):
        self.publish(command)
        self.wait(lambda: any(s.get("commandResult") and s["commandResult"]["commandId"] == command["commandId"]
                             and s["commandResult"]["reason"] == reason for s in self.snapshots), 8, reason)
        require(self.uart.refresh()["state"] == "IDLE", "guard started measurement")

    def guards(self):
        stop()
        status = self.uart.refresh()
        require(status.get("state") == "IDLE" and status.get("samples") == "0", "guards initial buffer")
        snapshot = self.probe()
        command = self.command(snapshot); command["targetBootId"] = str(uuid.uuid4())
        self.rejected(command, "BOOT_MISMATCH")
        command = self.command(snapshot); command["windowId"] = str(uuid.uuid4())
        self.rejected(command, "WINDOW_INVALID")
        old = self.command(snapshot)
        self.probe()
        self.rejected(old, "WINDOW_INVALID")
        snapshot = self.probe(); expired = self.command(snapshot)
        until = time.monotonic() + 10.2
        self.wait(lambda: time.monotonic() >= until, 12, "wait window expiry")
        self.rejected(expired, "WINDOW_EXPIRED")
        # The broker propagates retained=true (RAP); neither an invalid JSON nor a retained command may execute.
        snapshot = self.probe(); retained = self.command(snapshot)
        require(publish(retained, topic=TOPIC + "commands", expiry=10, retained=True), "retained test publish")
        self.publish(b'{"schemaVersion":1,"schemaVersion":1}')
        until = time.monotonic() + 2
        self.wait(lambda: time.monotonic() >= until, 4, "invalid transport settle")
        require(self.uart.refresh()["state"] == "IDLE", "invalid command executed")
        self.evidence["guards"] = ["BOOT_MISMATCH", "WINDOW_INVALID", "new_probe_invalidates", "WINDOW_EXPIRED", "retained", "duplicate_json"]

    def uart_mode(self):
        stop()
        require(self.uart.refresh().get("state") == "IDLE", "UART mode initial buffer")
        snapshot = self.probe()
        require(snapshot["controlMode"] == "UART" and not snapshot["controlReady"] and snapshot["controlWindow"] is None,
                "UART mode denies execution window")
        message = {"schemaVersion": 1, "eventType": "START", "deviceId": "esp32-bench", "commandId": str(uuid.uuid4()),
                   "sessionId": str(uuid.uuid4()), "targetBootId": snapshot["bootId"], "windowId": str(uuid.uuid4()),
                   "expectedSamples": 60, "samplingIntervalMs": 1000}
        self.rejected(message, "WINDOW_INVALID")
        self.evidence["uartMode"] = {"controlMode": "UART", "controlReady": False, "executionWindow": None,
                                     "mqttStartRejected": True, "finalStatus": self.uart.status}

    def normal(self, proxy=None, hold_release=False):
        start(control=True, broker_uri="tcp://127.0.0.1:18884" if proxy else "tcp://127.0.0.1:18883")
        require(self.uart.refresh().get("state") == "IDLE", "normal initial buffer")
        self.wait(lambda: http("/api/devices/esp32-bench/status")[1].get("liveness") == "FRESH", 35, "server fresh")
        key = str(uuid.uuid4()); path = "/api/devices/esp32-bench/measurement-commands"
        if proxy is None:
            keys = [key, str(uuid.uuid4())]
            with ThreadPoolExecutor(max_workers=2) as callers:
                replies = list(callers.map(lambda k: http(path, {"type": "START"}, k), keys))
            require(sorted(r[0] for r in replies) == [202, 409], "concurrent distinct START reservation")
            index = next(i for i, reply in enumerate(replies) if reply[0] == 202)
            key = keys[index]; code, accepted = replies[index]
            self.evidence["concurrentStart"] = {"accepted": 1, "rejected": 1}
        else:
            code, accepted = http(path, {"type": "START"}, key)
        require(code == 202, "REST new command")
        command_id, session_id = accepted["commandId"], accepted["sessionId"]
        query = "/api/measurement-commands/" + command_id
        code, duplicate = http(path, {"type": "START"}, key)
        require(code == 200 and duplicate["commandId"] == command_id, "REST idempotency")
        require(http(path, {"type": "RETRY", "sessionId": session_id}, key)[0] == 409, "REST changed request")
        require(http(path, {"type": "START"}, str(uuid.uuid4()))[0] == 409, "REST concurrent busy")
        if proxy:
            self.wait(lambda: http(query)[1].get("commandState") == "UNKNOWN", 15, "lost status UNKNOWN")
            require(http(path, {"type": "START"}, str(uuid.uuid4()))[0] == 409, "UNKNOWN start guard")
            require(self.uart.refresh().get("state") == "MEASURING", "unknown execution observation")
            require(proxy.statuses_dropped > 0 and len(proxy.control_commands) == 1, "fault injection and no automatic command retry")
            self.evidence["unknown"] = {"statusesDropped": proxy.statuses_dropped, "businessPublications": len(proxy.control_commands),
                                        "state": "UNKNOWN", "deviceState": "MEASURING", "newStartBlocked": True}
            if hold_release:
                self.wait(lambda: self.uart.status.get("state") == "IDLE", 100, "release report withheld")
                before = http(query)[1]
                require(before["serverPreservation"] == "NORMAL" and before["deviceBuffer"] != "RELEASE_CONFIRMED",
                        "DB and release observation separation")
                require(http(path, {"type": "START"}, str(uuid.uuid4()))[0] == 409, "unobserved release blocks START")
                self.evidence["releaseLoss"] = {"serverPreservation": before["serverPreservation"], "deviceBuffer": before["deviceBuffer"],
                                               "actualDeviceState": "IDLE", "newStartBlocked": True}
                stop()
                start(control=True, broker_uri="tcp://127.0.0.1:18884")
            proxy.set_mode("pass")
        self.wait(lambda: http(query)[1].get("commandState") == "ACCEPTED", 35 if proxy else 15, "command accepted")
        self.wait(lambda: any(b.get("commandId") == command_id and b.get("eventType") == "START" for _, b in self.wires), 5, "wire START")
        original = next(b for _, b in self.wires if b.get("commandId") == command_id and b.get("eventType") == "START")
        command_wire = next(w for w, b in self.wires if b is original)
        require(command_wire["qos"] == 1 and not command_wire["retained"] and command_wire["pfi"]
                and command_wire["contentType"] == "application/json" and command_wire["expiry"] is not None
                and 1 <= command_wire["expiry"] <= 10 and len(command_wire["payload"]) <= 4096, "server command metadata")
        self.publish(original)
        changed = dict(original, sessionId=str(uuid.uuid4()))
        self.publish(changed)
        self.wait(lambda: any(s.get("commandResult") and s["commandResult"]["reason"] == "COMMAND_CONFLICT" for s in self.snapshots), 8, "MQTT conflict")
        self.uart.send("START " + str(uuid.uuid4())); self.uart.send("RETRY " + session_id)
        self.wait(lambda: sum("reason=control_mode" in line for _, line in self.uart.history) >= 2, 4, "USB mode rejection")
        self.wait(lambda: http(query)[1].get("deviceBuffer") == "RELEASE_CONFIRMED", 110, "preservation and release")
        result = http(query)[1]
        require(result["commandState"] == "ACCEPTED" and result["serverPreservation"] == "NORMAL", "separate command axes")
        require(sql("SELECT count(*) FROM tds_measurement WHERE session_id='" + session_id + "'") == "60", "database 60 originals")
        require(self.uart.refresh().get("state") == "IDLE" and self.uart.status.get("samples") == "0", "final buffer")
        results = [b for _, b in self.wires if b.get("eventType") == "SESSION_RESULT" and b.get("sessionId") == session_id]
        require(bool(results) and len(results[0]["measurements"]) == 60, "wire final list")
        require(all(len(b["measurements"]) == 60 for b in results), "duplicate resampled")
        rows = json.loads(sql("SELECT json_agg(json_build_object('seq',seq,'elapsedMs',elapsed_ms,'tdsPpm',tds_ppm,"
                              "'isWarmup',is_warmup,'rawVoltageMv',raw_voltage_mv) ORDER BY seq) FROM tds_measurement WHERE session_id='"
                              + session_id + "'"), parse_float=Decimal)
        require(rows == results[0]["measurements"], "wire database original field equality")
        self.publish(original)
        until = time.monotonic() + 2
        self.wait(lambda: time.monotonic() >= until, 4, "late duplicate")
        require(self.uart.refresh().get("state") == "IDLE", "late duplicate restarted")
        self.evidence["normal"] = {"commandId": command_id, "sessionId": session_id, "axes": result,
                                   "resultPublications": len(results), "rawCount": 60, "finalStatus": self.uart.status}

    def close(self):
        self.uart.close(); self.observer.close()

    def recover_held(self):
        start(control=True)
        held = self.uart.refresh()
        require(held.get("state") == "MANUAL_WAIT" and held.get("samples") == "60", "recovery held buffer")
        self.wait(lambda: http("/api/devices/esp32-bench/status")[1].get("liveness") == "FRESH", 35, "recovery fresh")
        session_id = held["session"]
        code, result = http("/api/devices/esp32-bench/measurement-commands", {"type": "RETRY", "sessionId": session_id}, str(uuid.uuid4()))
        require(code == 202, "recovery RETRY reservation")
        query = "/api/measurement-commands/" + result["commandId"]
        self.wait(lambda: http(query)[1].get("deviceBuffer") == "RELEASE_CONFIRMED", 40, "recovery confirmed release")
        require(self.uart.refresh().get("state") == "IDLE", "recovery final IDLE")
        self.evidence["recoveredHeldSession"] = {"sessionId": session_id, "axes": http(query)[1], "finalStatus": self.uart.status}

    def reboot(self):
        start(control=True, broker_uri="tcp://127.0.0.1:18884")
        require(self.uart.refresh().get("state") == "IDLE", "reboot initial buffer")
        status_path = "/api/devices/esp32-bench/status"
        self.wait(lambda: http(status_path)[1].get("liveness") == "FRESH", 35, "reboot fresh")
        old_boot = http(status_path)[1]["snapshot"]["bootId"]
        code, result = http("/api/devices/esp32-bench/measurement-commands", {"type": "START"}, str(uuid.uuid4()))
        require(code == 202, "reboot START")
        query = "/api/measurement-commands/" + result["commandId"]
        self.wait(lambda: http(query)[1].get("commandState") == "ACCEPTED", 15, "reboot accepted")
        self.wait(lambda: int(self.uart.status.get("samples", "0")) > 0, 10, "reboot nonempty buffer")
        print("CONTROL_ACCEPT_ACTION press_EN_only", flush=True)
        self.wait(lambda: http(query)[1].get("commandState") == "INTERRUPTED_REBOOT", 180, "new boot reconciliation")
        view = http(query)[1]
        require(view["deviceBuffer"] == "UNKNOWN_AFTER_REBOOT" and view["releaseConfirmedAt"] is None
                and view["serverPreservation"] == "NOT_OBSERVED", "reboot does not imply preservation or release")
        require(self.uart.refresh().get("state") == "IDLE" and self.uart.status.get("samples") == "0", "reboot RAM state")
        require(sql("SELECT count(*) FROM tds_session WHERE session_id='" + result["sessionId"] + "'") == "0", "reboot no fabricated empty session")
        stop()
        snapshot = self.probe(); old_command = self.command(snapshot); old_command["targetBootId"] = old_boot
        self.rejected(old_command, "BOOT_MISMATCH")
        self.evidence["reboot"] = {"axes": view, "oldBootRejected": True, "finalStatus": self.uart.status}

    def retry(self, proxy):
        start(control=True, broker_uri="tcp://127.0.0.1:18884")
        require(self.uart.refresh().get("state") == "IDLE", "retry initial buffer")
        self.wait(lambda: http("/api/devices/esp32-bench/status")[1].get("liveness") == "FRESH", 35, "retry server fresh")
        path = "/api/devices/esp32-bench/measurement-commands"
        code, first = http(path, {"type": "START"}, str(uuid.uuid4()))
        require(code == 202, "retry setup START")
        first_query = "/api/measurement-commands/" + first["commandId"]
        session_id = first["sessionId"]
        self.wait(lambda: http(first_query)[1].get("commandState") == "ACCEPTED", 15, "retry setup accepted")
        self.wait(lambda: self.uart.status.get("state") == "MANUAL_WAIT", 330, "five attempts MANUAL_WAIT")
        held = self.uart.refresh()
        require(held.get("samples") == "60" and held.get("attempt") == "5" and held.get("round") == "1", "retained final buffer")
        before = [w for w, b in self.wires if b.get("eventType") == "SESSION_RESULT" and b.get("sessionId") == session_id]
        require(len(before) == 5 and proxy.results_dropped == 5, "five publications only")
        hashes = {hashlib.sha256(w["payload"]).hexdigest() for w in before}
        require(len(hashes) == 1 and sql("SELECT count(*) FROM tds_session WHERE session_id='" + session_id + "'") == "0", "frozen unpreserved originals")
        until = time.monotonic() + 65
        self.wait(lambda: time.monotonic() >= until, 68, "manual wait no orphan publication")
        require(len([b for _, b in self.wires if b.get("eventType") == "SESSION_RESULT" and b.get("sessionId") == session_id]) == 5,
                "automatic publication after exhaustion")
        require(http(path, {"type": "START"}, str(uuid.uuid4()))[0] == 409, "held buffer blocks START")
        require(http(path, {"type": "RETRY", "sessionId": str(uuid.uuid4())}, str(uuid.uuid4()))[0] == 409, "wrong RETRY session")
        proxy.set_mode("pass")
        key = str(uuid.uuid4()); body = {"type": "RETRY", "sessionId": session_id}
        code, retried = http(path, body, key)
        require(code == 202 and retried["sessionId"] == session_id and retried["commandId"] != first["commandId"], "new RETRY same session")
        require(http(path, body, key)[0] == 200, "RETRY REST duplicate")
        query = "/api/measurement-commands/" + retried["commandId"]
        self.wait(lambda: http(query)[1].get("deviceBuffer") == "RELEASE_CONFIRMED", 35, "RETRY release")
        self.wait(lambda: any(b.get("eventType") == "RETRY" and b.get("commandId") == retried["commandId"] for _, b in self.wires), 5, "RETRY wire")
        message = next(b for _, b in self.wires if b.get("eventType") == "RETRY" and b.get("commandId") == retried["commandId"])
        self.publish(message)
        until = time.monotonic() + 2
        self.wait(lambda: time.monotonic() >= until, 4, "late RETRY duplicate")
        all_results = [w for w, b in self.wires if b.get("eventType") == "SESSION_RESULT" and b.get("sessionId") == session_id]
        require(len(all_results) == 6 and {hashlib.sha256(w["payload"]).hexdigest() for w in all_results} == hashes, "RETRY original payload unchanged")
        require(http(query)[1]["commandState"] == "ACCEPTED" and http(first_query)[1]["releaseConfirmedAt"] is not None, "START RETRY completion history")
        require(self.uart.refresh().get("state") == "IDLE" and self.uart.status.get("samples") == "0", "RETRY final buffer")
        require(http(path, body, str(uuid.uuid4()))[0] == 409, "RETRY after release refused")
        self.evidence["retry"] = {"sessionId": session_id, "resultPublications": 6, "uniquePayloadHashes": 1,
                                  "quietSeconds": 65, "finalStatus": self.uart.status, "axes": http(query)[1]}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", default="COM3")
    parser.add_argument("--case", choices=["normal", "guards", "both", "unknown", "release-loss", "retry", "recover-held", "reboot", "uart-mode"], default="both")
    args = parser.parse_args()
    test = ControlAcceptance(args.port)
    try:
        if args.case in ("guards", "both"): test.guards()
        if args.case in ("normal", "both"): test.normal()
        if args.case == "recover-held": test.recover_held()
        if args.case == "uart-mode": test.uart_mode()
        if args.case in ("unknown", "release-loss", "retry", "reboot"):
            stop()
            with FaultProxy(port=18884, tx_id="bench-server-control", rx_id="bench-server-status") as proxy:
                proxy.set_mode("drop-results" if args.case in ("retry", "reboot") else "drop-execution-status")
                try:
                    if args.case == "retry": test.retry(proxy)
                    elif args.case == "reboot": test.reboot()
                    else: test.normal(proxy, hold_release=args.case == "release-loss")
                finally: stop()
            start(control=True)
        CACHE.mkdir(parents=True, exist_ok=True)
        (CACHE / ("control-" + args.case + ".json")).write_text(json.dumps(test.evidence, indent=2), encoding="utf-8")
        print("PHYSICAL CONTROL ACCEPTANCE PASS case=" + args.case)
    finally:
        CACHE.mkdir(parents=True, exist_ok=True)
        (CACHE / "control-last-diagnostic.json").write_text(json.dumps({"evidence": test.evidence, "status": test.uart.status,
            "uart": list(test.uart.history), "snapshots": test.snapshots[-20:]}, indent=2), encoding="utf-8")
        test.close()


if __name__ == "__main__":
    try: main()
    except AcceptanceFailure as error:
        print("PHYSICAL CONTROL ACCEPTANCE FAILED stage=" + str(error), file=sys.stderr)
        sys.exit(1)
    except Exception as error:
        print("PHYSICAL CONTROL ACCEPTANCE FAILED category=" + type(error).__name__ + "; raw/secret output withheld", file=sys.stderr)
        sys.exit(1)
