"""PC-simulated device smoke test; this is deliberately not physical ESP32 acceptance."""
import json
import sys
import time
import uuid
from bench import Observer, publish, sql


def main():
    observer = Observer(seconds=30)
    try:
        session_id = str(uuid.uuid4())
        result = {"schemaVersion": 1, "eventType": "SESSION_RESULT", "deviceId": "esp32-bench", "sessionId": session_id,
                  "startedAt": None, "expectedSamples": 60, "samplingIntervalMs": 1000, "temperatureAssumedC": 25,
                  "calibrationVersion": "simulated-pc-smoke", "measurements": []}
        if not publish(result, topic="home/water/v1/esp32-bench/events", role="device"):
            raise RuntimeError("PC result publish failed")
        seen_result = seen_confirmation = False
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline and not (seen_result and seen_confirmation):
            wire = observer.receive()
            if not wire:
                continue
            body = json.loads(wire["payload"])
            if body.get("sessionId") != session_id:
                continue
            if wire["qos"] != 1 or wire["retained"] or not wire["pfi"] or wire["contentType"] != "application/json":
                raise RuntimeError("Wire metadata did not match MQTT 5 contract")
            if body.get("eventType") == "SESSION_RESULT":
                seen_result = True
            elif body.get("eventType") == "SESSION_PRESERVED":
                seen_confirmation = body.get("preservedSampleCount") == 0 and body.get("preservationResult") == "INCOMPLETE" and body.get("comparison") is None
        if not seen_result or not seen_confirmation:
            raise RuntimeError("PC smoke did not receive expected result/confirmation")
        if sql("SELECT preserved_sample_count || ':' || preservation_result FROM tds_session WHERE session_id='" + session_id + "'") != "0:INCOMPLETE":
            raise RuntimeError("Independent DB check failed")
        if publish({"invalid": True}, role="device"):
            raise RuntimeError("Device account unexpectedly published preservation")
        print("PC smoke PASS: MQTT 5 metadata, empty result, committed DB, confirmation, device ACL denial")
        print("Physical ESP32 network/buffer acceptance remains separate")
    finally:
        observer.close()


if __name__ == "__main__":
    try:
        main()
    except Exception:
        print("PC smoke FAILED; credentials/raw payload/logs withheld", file=sys.stderr)
        sys.exit(1)
