"""Use the ESP-IDF Python environment (pyserial) for bounded UART commands/evidence."""
import argparse
import re
import sys
import time
import uuid

import serial


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["status", "start", "retry", "watch"])
    parser.add_argument("--port", default="COM3")
    parser.add_argument("--session-id", type=uuid.UUID)
    parser.add_argument("--seconds", type=float, default=5)
    parser.add_argument("--expect", help="Required HWM_ evidence marker")
    parser.add_argument("--expected-tests", type=int, help="Require this Unity test count, zero failures and zero ignores")
    args = parser.parse_args()
    if not 0 < args.seconds <= 600:
        parser.error("seconds must be between 0 and 600")
    if args.expected_tests is not None and args.expected_tests <= 0:
        parser.error("expected-tests must be positive")
    if args.command == "retry" and args.session_id is None:
        parser.error("retry requires the UUID of the retained session")
    command = args.command.upper()
    if args.command in ("start", "retry"):
        session_id = args.session_id or uuid.uuid4()
        command += " " + str(session_id)
    found = False
    tests_passed = False
    # Set modem lines before opening; ordinary monitoring must not deliberately reset RAM.
    port = serial.Serial(baudrate=115200, timeout=0.2, write_timeout=2)
    port.dtr = False
    port.rts = False
    port.port = args.port
    with port:
        port.reset_input_buffer()
        if args.command != "watch":
            port.write((command + "\n").encode("ascii"))
            print("PC_COMMAND " + command, flush=True)
        deadline = time.monotonic() + args.seconds
        buffered = bytearray()
        while time.monotonic() < deadline:
            chunk = port.read(256)
            buffered.extend(chunk)
            while b"\n" in buffered:
                line, _, tail = buffered.partition(b"\n")
                buffered = bytearray(tail)
                text = line.decode("utf-8", errors="replace").strip()
                # Avoid copying unrelated SDK logs or raw payloads into evidence.
                if text.startswith("HWM_"):
                    print(text, flush=True)
                    if args.expect and args.expect in text:
                        found = True
                    result = re.fullmatch(r"HWM_TEST_RESULT tests=(\d+) failed=(\d+) ignored=(\d+)", text)
                    if result and args.expected_tests:
                        tests_passed = tuple(map(int, result.groups())) == (args.expected_tests, 0, 0)
            if len(buffered) > 1024:
                buffered.clear()
    if args.expect and not found:
        print("Expected device evidence was not observed", file=sys.stderr)
        return 1
    if args.expected_tests and not tests_passed:
        print("Device test count/failures/ignores did not meet the required result", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (serial.SerialException, OSError):
        print("UART unavailable; close other monitors and check the cable/port", file=sys.stderr)
        sys.exit(2)
