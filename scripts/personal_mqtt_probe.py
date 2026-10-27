"""Test-only MQTT wire helper; reuses the backend's pinned Paho, not real bench credentials."""
import base64
import importlib.util
import json
import os
from pathlib import Path
import queue
import subprocess
import threading
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("bench_compile_only", ROOT / "scripts/device-test/bench.py")
bench = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bench)


class Wire:
    def __init__(self, work, uri, username, password):
        self.work = Path(work)
        self.command = bench.mqtt_command()  # Does not call bench.credentials().
        self.env = os.environ.copy()
        self.env.update(HWM_TEST_BROKER_URI=uri, HWM_TEST_MQTT_USERNAME=username,
                        HWM_TEST_MQTT_PASSWORD=password, HWM_TEST_INCLUDE_EXPIRY="1")
        self.process = None
        self.lines = queue.Queue(maxsize=256)

    def publish(self, topic, body, expiry=None):
        path = self.work / ("packet-" + uuid.uuid4().hex + ".json")
        path.write_text(json.dumps(body, separators=(",", ":")), encoding="utf-8")
        args = [*self.command, "publish", topic, str(path), "1", "false", "true", "application/json"]
        if expiry is not None:
            args.append(str(expiry))
        try:
            result = subprocess.run(args, env=self.env, capture_output=True, text=True,
                                    encoding="utf-8", timeout=20)
            return result.returncode == 0 and "PUBLISHED" in result.stdout
        finally:
            path.unlink()

    def listen(self, topics):
        self.process = subprocess.Popen([*self.command, "listen", "600", *topics], env=self.env,
            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True, encoding="utf-8")
        def read():
            for line in self.process.stdout:
                self.lines.put(line.strip(), timeout=3)
        self.reader = threading.Thread(target=read, daemon=True)
        self.reader.start()
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            try:
                line = self.lines.get(timeout=1)
            except queue.Empty:
                continue
            if line == "READY":
                return self
            if "FAILED" in line:
                break
        self.close()
        raise RuntimeError("MQTT test observer not ready")

    def receive(self, timeout=0.1):
        try:
            parts = self.lines.get(timeout=timeout).split("\t")
        except queue.Empty:
            return None
        if len(parts) != 8 or parts[0] != "MESSAGE":
            return None
        return {"topic": base64.b64decode(parts[1]).decode(), "qos": int(parts[2]),
                "retained": parts[3] == "true", "pfi": parts[4] == "true",
                "contentType": base64.b64decode(parts[5]).decode(),
                "body": json.loads(base64.b64decode(parts[6])),
                "expiry": None if parts[7] == "null" else int(parts[7])}

    def close(self):
        if self.process:
            if self.process.poll() is None:
                self.process.terminate()
            self.process.wait(timeout=5)
            self.reader.join(timeout=2)
            self.process.stdout.close()


class FakeDevice:
    """Only a protocol simulator: no firmware, sensor, Wi-Fi or real measurement."""
    topic = "home/water/v1/esp32-bench/"

    def __init__(self, wire, committed):
        self.wire, self.committed = wire, committed
        self.status = json.loads((ROOT / "docs/mqtt/v1/examples/device-status-idle.json").read_text())
        self.status.update(bootId=str(uuid.uuid4()), replyToProbeId=None, controlWindow=None)
        self.sequence = 0
        self.starts = 0
        self.released = False
        self.window = None
        wire.listen([self.topic + "commands", self.topic + "preservation"])

    def emit(self):
        self.sequence += 1
        self.status["statusSeq"] = self.sequence
        assert self.wire.publish(self.topic + "status", self.status, expiry=10)

    def pump(self):
        message = self.wire.receive()
        if not message:
            return
        body = message["body"]
        assert message["qos"] == 1 and not message["retained"] and message["pfi"]
        assert message["contentType"] == "application/json"
        if body.get("eventType") == "STATE_REQUEST":
            self.status["replyToProbeId"] = body["probeId"]
            self.window = str(uuid.uuid4()) if self.status["state"] == "IDLE" else None
            self.status["controlWindow"] = {"windowId": self.window, "validForMs": 10000} if self.window else None
            self.emit()
            self.status.update(replyToProbeId=None, controlWindow=None)
        elif body.get("eventType") == "START":
            assert body["windowId"] == self.window and body["targetBootId"] == self.status["bootId"]
            self.starts += 1
            self.status.update(state="MEASURING", sessionId=body["sessionId"], round=1)
            execution = {"commandId": body["commandId"], "commandType": "START",
                         "sessionId": body["sessionId"], "windowId": body["windowId"]}
            self.status["lastExecution"] = execution
            self.status["commandResult"] = {key: value for key, value in execution.items() if key != "windowId"}
            self.status["commandResult"].update(outcome="ACCEPTED", reason=None)
            self.emit()
        elif message["topic"].endswith("/preservation") and body.get("sessionId") == self.status["sessionId"]:
            assert body["preservedSampleCount"] == 60
            self.committed(body["sessionId"])
            self.status["lastRelease"] = {"sessionId": body["sessionId"], "preservationResult": "NORMAL",
                "preservedSampleCount": 60, "reason": "PRESERVATION_CONFIRMED"}
            self.status.update(state="IDLE", sessionId=None, sampleCount=0, finalized=False, round=0, attempt=0)
            self.released = True
            self.emit()

    def close(self):
        self.wire.close()
