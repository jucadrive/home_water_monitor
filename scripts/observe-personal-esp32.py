"""Read-only UART acceptance observer: never sends START/RETRY or resets the board.

Run with the ESP-IDF Python (pyserial). App operation is performed by the user.
Raw HWM evidence and sample values stay in a new gitignored local result file.
"""
import argparse
import importlib.util
import json
from pathlib import Path
import re
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("uart_observer", ROOT / "scripts/device-test/uart.py")
uart_module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(uart_module)
SAMPLE = re.compile(r"HWM_SENSOR_SAMPLE seq=(\d+) tds_hundredths=(-?\d+) voltage_hundredths=(-?\d+)")
RELEASE = re.compile(r"HWM_BUFFER_RELEASED session=([0-9a-f-]{36}) samples=0")


def summarize(lines, status):
    samples = [tuple(map(int, match.groups())) for line in lines if (match := SAMPLE.fullmatch(line))]
    releases = [match.group(1) for line in lines if (match := RELEASE.fullmatch(line))]
    faults = [line for line in lines if line.startswith(("HWM_SENSOR_FAULT", "HWM_FAULT "))]
    idle = status.get("state") == "IDLE" and status.get("session") == "none" and status.get("samples") == "0"
    success = len(samples) == 60 and [row[0] for row in samples] == list(range(1, 61)) and len(releases) == 1 and not faults and idle
    return {"uartNormalFlowVerified": success, "observedSamples": len(samples), "releaseSessions": releases,
            "faultCount": len(faults), "finalStatus": status, "serverApiDatabase": "USER_CONFIRMATION_REQUIRED"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", required=True)
    parser.add_argument("--seconds", type=int, default=300)
    args = parser.parse_args()
    if not 1 <= args.seconds <= 600:
        parser.error("seconds must be 1..600")
    directory = ROOT / ".tools/esp32-personal"
    directory.mkdir(parents=True, exist_ok=True)
    output = directory / ("accept-" + uuid.uuid4().hex + ".json")
    lines = []
    device = uart_module.Uart(args.port)
    summary = {}
    try:
        status = device.refresh()
        assert status.get("state") == "IDLE" and status.get("samples") == "0" and status.get("session") == "none"
        assert status.get("wifi_up") == "1" and status.get("rx_ready") == "1"
        print("READY read-only observer; start ONE measurement in the app", flush=True)
        deadline = time.monotonic() + args.seconds
        next_status = time.monotonic() + 3
        released = False
        while time.monotonic() < deadline:
            for line in device.receive():
                lines.append(line)
                sample = SAMPLE.fullmatch(line)
                if sample and (int(sample.group(1)) == 1 or int(sample.group(1)) % 10 == 0):
                    print("SAMPLES " + sample.group(1), flush=True)
                if RELEASE.fullmatch(line):
                    released = True
                    print("BUFFER_RELEASED", flush=True)
                if line.startswith(("HWM_SENSOR_FAULT", "HWM_FAULT ")):
                    print(line, flush=True)
            if released and device.status.get("state") == "IDLE" and device.status.get("samples") == "0":
                break
            if time.monotonic() >= next_status:
                device.send("STATUS")
                next_status = time.monotonic() + 3
        summary = summarize(lines, device.status)
    finally:
        device.close()
        if not summary:
            summary = summarize(lines, device.status)
        output.write_text(json.dumps({"summary": summary, "uart": lines}, indent=2), encoding="utf-8")
        print("UART evidence: " + str(output), flush=True)
    print(json.dumps(summary), flush=True)
    return 0 if summary["uartNormalFlowVerified"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
