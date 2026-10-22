"""Bounded serial observer; opening a monitor must not intentionally reset the ESP32 RAM."""
from collections import deque
import time
import serial


class Uart:
    def __init__(self, port="COM3"):
        self.port = serial.Serial(baudrate=115200, timeout=0.05, write_timeout=2)
        self.port.dtr = self.port.rts = False
        self.port.port = port
        self.port.open()
        self.port.reset_input_buffer()
        self.buffer = bytearray()
        self.history = deque(maxlen=2000)
        self.status = {}

    def send(self, command):
        self.port.write((command + "\n").encode("ascii"))

    def receive(self):
        self.buffer.extend(self.port.read(4096))
        lines = []
        while b"\n" in self.buffer:
            line, _, tail = self.buffer.partition(b"\n")
            self.buffer = bytearray(tail)
            text = line.decode("utf-8", errors="replace").strip()
            if text.startswith("HWM_"):
                lines.append(text)
                self.history.append((time.monotonic(), text))
                if text.startswith("HWM_STATUS "):
                    self.status = dict(field.split("=", 1) for field in text.split()[1:] if "=" in field)
        if len(self.buffer) > 1024:
            raise RuntimeError("UART evidence line exceeds bound")
        return lines

    def refresh(self, seconds=25):
        # Controller may be stopping RX and TX SDK tasks (bounded network timeout per client).
        self.status = {}
        self.send("STATUS")
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            self.receive()
            if self.status:
                return self.status
        raise RuntimeError("UART STATUS did not arrive")

    def close(self):
        self.port.close()
