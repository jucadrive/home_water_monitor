"""Bench-only bounded MQTT 5 TCP fault injector. Never stores CONNECT credentials/raw payloads."""
import hashlib
import ipaddress
import json
import select
import socket
import threading
import time
import uuid

MAX_PACKET = 65536
MODES = {"pass", "drop-confirmation", "drop-puback", "drop-puback-only", "reject-puback", "offline"}


def variable_integer(data, offset=0):
    value = 0
    for i in range(4):
        if offset + i >= len(data):
            return None
        digit = data[offset + i]
        value += (digit & 127) << (7 * i)
        if not digit & 128:
            if i and digit == 0:
                raise ValueError("Nonminimal variable integer")
            return value, offset + i + 1
    raise ValueError("Malformed variable integer")


def take_packet(buffer):
    if len(buffer) < 2:
        return None
    decoded = variable_integer(buffer, 1)
    if decoded is None:
        return None
    length, begin = decoded
    if length > MAX_PACKET:
        raise ValueError("Packet exceeds bench bound")
    end = begin + length
    if len(buffer) < end:
        return None
    packet = bytes(buffer[:end])
    del buffer[:end]
    return packet, begin


def connect_client_id(body):
    if len(body) < 13 or body[:7] != b"\x00\x04MQTT\x05":
        raise ValueError("MQTT 5 CONNECT required")
    decoded = variable_integer(body, 10)
    if decoded is None:
        raise ValueError("Incomplete CONNECT properties")
    length, begin = decoded
    begin += length
    if begin + 2 > len(body):
        raise ValueError("Missing client id")
    length = int.from_bytes(body[begin:begin + 2], "big")
    if begin + 2 + length > len(body):
        raise ValueError("Truncated client id")
    return body[begin + 2:begin + 2 + length].decode("utf-8")


def publish_parts(header, body):
    if len(body) < 2:
        raise ValueError("Truncated publish")
    end = 2 + int.from_bytes(body[:2], "big")
    if end > len(body):
        raise ValueError("Truncated topic")
    packet_id = b""
    qos = (header >> 1) & 3
    if qos:
        packet_id = body[end:end + 2]
        if len(packet_id) != 2:
            raise ValueError("Missing packet id")
        end += 2
    decoded = variable_integer(body, end)
    if decoded is None:
        raise ValueError("Missing publish properties")
    length, end = decoded
    end += length
    if end > len(body):
        raise ValueError("Truncated publish properties")
    return packet_id, body[end:]


class FaultProxy:
    def __init__(self, host="127.0.0.1", port=18884, upstream_port=18883, tx_id="hwm-esp32-bench-tx", rx_id="hwm-esp32-bench-rx"):
        address = ipaddress.IPv4Address(host)
        private = any(address in ipaddress.ip_network(n) for n in ("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16"))
        if not address.is_loopback and not private:
            raise ValueError("Proxy listener must be loopback or an explicitly selected private LAN IP")
        self.host, self.port, self.upstream_port = host, port, upstream_port
        self.tx_id, self.rx_id = tx_id, rx_id
        self.mode = "pass"
        self.stop = threading.Event()
        self.lock = threading.Lock()
        self.sockets = set()
        self.threads = []
        self.publications = []
        self.pubacks_dropped = self.confirmations_dropped = self.rejected = self.errors = 0
        self.rx_connections = 0
        self.tx_connections = 0
        self.listener = None

    def set_mode(self, mode):
        if mode not in MODES:
            raise ValueError("Unknown fault mode")
        self.mode = mode

    def __enter__(self):
        self.listener = socket.socket()
        self.listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            self.listener.bind((self.host, self.port))
            self.port = self.listener.getsockname()[1]
            self.listener.listen(8)
        except OSError:
            self.listener.close()
            raise
        self.listener.settimeout(0.2)
        self.accept_thread = threading.Thread(target=self.accept, daemon=True)
        self.accept_thread.start()
        return self

    def accept(self):
        while not self.stop.is_set():
            try:
                client, _ = self.listener.accept()
            except socket.timeout:
                continue
            except OSError:
                return
            with self.lock:
                if len(self.sockets) >= 16 or self.mode == "offline":
                    client.close()
                    continue
                self.sockets.add(client)
            thread = threading.Thread(target=self.forward, args=(client,), daemon=True)
            self.threads = [previous for previous in self.threads if previous.is_alive()]
            self.threads.append(thread)
            thread.start()

    def forward(self, client):
        upstream = None
        try:
            upstream = socket.create_connection(("127.0.0.1", self.upstream_port), timeout=2)
            with self.lock:
                self.sockets.add(upstream)
            client.settimeout(2)
            upstream.settimeout(2)
            buffers = {client: bytearray(), upstream: bytearray()}
            client_id = ""
            tx_connection = None
            while not self.stop.is_set() and self.mode != "offline":
                readable, _, _ = select.select([client, upstream], [], [], 0.2)
                for source in readable:
                    chunk = source.recv(4096)
                    if not chunk:
                        return
                    buffer = buffers[source]
                    buffer.extend(chunk)
                    while (framed := take_packet(buffer)) is not None:
                        packet, begin = framed
                        kind, body = packet[0] >> 4, packet[begin:]
                        target = upstream if source is client else client
                        if source is client and kind == 1:
                            client_id = connect_client_id(body)
                            if client_id == self.rx_id:
                                with self.lock: self.rx_connections += 1
                            if client_id == self.tx_id:
                                with self.lock:
                                    self.tx_connections += 1
                                    tx_connection = self.tx_connections
                        if source is client and kind == 3 and client_id == self.tx_id:
                            _, payload = publish_parts(packet[0], body)
                            session = "invalid"
                            try:
                                session = str(uuid.UUID(json.loads(payload)["sessionId"]))
                            except (ValueError, TypeError, KeyError):
                                pass
                            with self.lock:
                                self.publications.append({"time": time.monotonic(), "sha256": hashlib.sha256(payload).hexdigest(),
                                                          "sessionId": session, "connection": tx_connection, "duplicate": bool(packet[0] & 8)})
                                self.publications = self.publications[-100:]
                        if source is upstream and kind == 4 and client_id == self.tx_id:
                            if self.mode in ("drop-puback", "drop-puback-only"):
                                with self.lock: self.pubacks_dropped += 1
                                continue
                            if self.mode == "reject-puback":
                                if len(body) < 2: raise ValueError("Missing PUBACK id")
                                target.sendall(b"\x40\x04" + body[:2] + b"\x87\x00")
                                with self.lock: self.rejected += 1
                                continue
                        if source is upstream and kind == 3 and client_id == self.rx_id and self.mode in ("drop-confirmation", "drop-puback", "reject-puback"):
                            packet_id, _ = publish_parts(packet[0], body)
                            if ((packet[0] >> 1) & 3) != 1:
                                raise ValueError("Bench confirmation must use QoS 1")
                            # Model application confirmation loss while completing broker-side QoS delivery.
                            upstream.sendall(b"\x40\x02" + packet_id)
                            with self.lock: self.confirmations_dropped += 1
                            continue
                        target.sendall(packet)
                    if len(buffer) > MAX_PACKET + 5:
                        raise ValueError("Unbounded packet assembly")
        except (OSError, ValueError, UnicodeError):
            if not self.stop.is_set():
                with self.lock: self.errors += 1
        finally:
            for stream in (client, upstream):
                if stream:
                    with self.lock: self.sockets.discard(stream)
                    stream.close()

    def __exit__(self, *_):
        self.stop.set()
        if self.listener: self.listener.close()
        with self.lock: sockets = list(self.sockets)
        for stream in sockets:
            try: stream.shutdown(socket.SHUT_RDWR)
            except OSError: pass
        self.accept_thread.join(timeout=1)
        for thread in self.threads: thread.join(timeout=1)
