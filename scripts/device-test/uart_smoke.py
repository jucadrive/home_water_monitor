"""Physical UART grammar checks only. Does not start a measurement or need Wi-Fi/MQTT."""
import sys
import time
from uart import Uart


def main():
    uart = Uart()
    try:
        status = uart.refresh()
        if status.get("state") != "IDLE" or status.get("samples") != "0":
            raise RuntimeError("Existing session present; refusing UART smoke")
        cases = [b"STATUS extra\n", b"status\n", b"START invalid\n", b"STATUS\x00\n", b"STATUS\rX\n", b"x" * 100 + b"\n", b"\xff\n"]
        for data in cases:
            uart.port.write(data)
            deadline = time.monotonic() + 2
            rejected = False
            while time.monotonic() < deadline:
                if any(line.startswith("HWM_COMMAND_REJECTED") for line in uart.receive()):
                    rejected = True
                    break
            if not rejected:
                raise RuntimeError("Invalid UART command did not produce rejection")
        uart.port.write(b"STATUS\r\n")
        deadline = time.monotonic() + 2
        crlf = False
        while time.monotonic() < deadline:
            if any(line.startswith("HWM_STATUS") for line in uart.receive()): crlf = True; break
        status = uart.refresh()
        if not crlf or status.get("state") != "IDLE" or status.get("samples") != "0":
            raise RuntimeError("UART test changed session state or rejected CRLF")
        print("Physical UART smoke PASS: 7 invalid lines rejected, CRLF/STATUS accepted, no measurement started")
    finally:
        uart.close()


if __name__ == "__main__":
    try:
        main()
    except Exception:
        print("Physical UART smoke NOT VERIFIED/FAILED; check port and retained state", file=sys.stderr)
        sys.exit(1)
