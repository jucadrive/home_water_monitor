"""Local-only TLS listener acceptance with fresh test CA/credentials, no real server."""
import importlib.util
import json
from pathlib import Path
import shutil
import socket
import ssl
import struct
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[1]
PROJECT = "ppossak-tls-check-" + uuid.uuid4().hex[:10]
WORK = ROOT / ".tools/deployment" / PROJECT
checks = []


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, ROOT / "scripts" / filename)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def run(*args, timeout=180, check=True):
    result = subprocess.run(args, cwd=WORK, capture_output=True, text=True, encoding="utf-8", timeout=timeout)
    if check and result.returncode:
        raise RuntimeError("TLS fixture command failed; output withheld")
    return result


def compose(*args, **kwargs):
    return run("docker", "compose", "-p", PROJECT, "--env-file", "secrets/postgres.env",
        "--env-file", "secrets/mqtt-lan/mqtt-lan.env", "-f", "compose.yml", "-f", "compose.mqtt.yml",
        "-f", "compose.mqtt-lan.yml", "-f", "test.override.yml", *args, **kwargs)


def passed(label):
    checks.append(label)
    print("PASS " + label, flush=True)


def variable(value):
    output = bytearray()
    while True:
        digit = value % 128
        value //= 128
        output.append(digit | (128 if value else 0))
        if not value:
            return bytes(output)


def text(value):
    data = value.encode()
    return struct.pack("!H", len(data)) + data


def packet(sock, header, body):
    sock.sendall(bytes([header]) + variable(len(body)) + body)


def read(sock):
    def exact(size):
        output = b""
        while len(output) < size:
            part = sock.recv(size - len(output))
            if not part:
                raise RuntimeError("Unexpected fixture MQTT EOF")
            output += part
        return output
    kind = exact(1)[0]
    length, multiplier = 0, 1
    for _ in range(4):
        digit = exact(1)[0]
        length += (digit & 127) * multiplier
        if not digit & 128:
            return kind, exact(length)
        multiplier *= 128
    raise RuntimeError("Invalid fixture MQTT length")


def connect(role="device", wrong=False, ca=None, peer="192.168.0.50", anonymous=False):
    context = ssl.create_default_context(cafile=str(ca or WORK / "secrets/mqtt-lan/certs/ca.crt"))
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    raw = socket.create_connection(("127.0.0.1", PORT), timeout=5)
    try:
        sock = context.wrap_socket(raw, server_hostname=peer)
    except BaseException:
        raw.close()
        raise
    flags = 2 if anonymous else 0xC2
    body = text("MQTT") + bytes([5, flags, 0, 30, 0]) + text("fixture-" + uuid.uuid4().hex)
    if not anonymous:
        username, password = (device["username"], device["password"]) if role == "device" else (env["MQTT_USERNAME"], env["MQTT_PASSWORD"])
        body += text(username) + text("wrong-fixture" if wrong else password)
    packet(sock, 0x10, body)
    kind, answer = read(sock)
    assert kind == 0x20
    return sock, answer[1]


def publish(role, topic):
    sock, code = connect(role)
    assert code == 0
    try:
        packet(sock, 0x32, text(topic) + b"\0\1\0" + b'{"fixture":true}')
        kind, answer = read(sock)
        assert kind == 0x40
        return answer[2] if len(answer) >= 3 else 0
    finally:
        sock.close()


WORK.mkdir(parents=True)
for file in ("compose.yml", "compose.mqtt.yml", "compose.mqtt-lan.yml", "mosquitto.conf", "mosquitto-lan.conf"):
    shutil.copy2(ROOT / "infra/personal" / file, WORK / file)
load("auth_fixture", "create-personal-secrets.py").create(WORK / "secrets", "fixture-owner", "fixture-password-only", "invalid.fixture.token")
load("mqtt_fixture", "create-personal-mqtt.py").create(WORK / "secrets/mqtt")
tls = load("tls_fixture", "create-mqtt-lan-tls.py")
tls.create(WORK / "secrets/mqtt-lan", "192.168.0.50")
tls.create(WORK / "wrong-ca", "192.168.0.51")
device = json.loads((WORK / "secrets/mqtt/device-mqtt.json").read_text())
env = dict(line.split("=", 1) for line in (WORK / "secrets/mqtt/mqtt.env").read_text().splitlines())
(WORK / "test.override.yml").write_text('services:\n  broker:\n    ports: !override ["127.0.0.1::8883"]\n', encoding="utf-8")
success = False
try:
    production = json.loads(run("docker", "compose", "--env-file", "secrets/postgres.env",
        "--env-file", "secrets/mqtt-lan/mqtt-lan.env", "-f", "compose.yml", "-f", "compose.mqtt.yml",
        "-f", "compose.mqtt-lan.yml", "config", "--format", "json").stdout)
    ports = production["services"]["broker"]["ports"]
    assert len(ports) == 1 and ports[0]["host_ip"] == "192.168.0.50" and ports[0]["target"] == 8883
    assert not production["services"]["backend"].get("ports")
    passed("production overlay publishes only selected LAN IPv4 TLS port")
    compose("up", "-d", "--wait", "--wait-timeout", "90", "broker")
    PORT = int(compose("port", "broker", "8883").stdout.strip().rsplit(":", 1)[1])
    sock, code = connect()
    assert code == 0
    sock.close()
    passed("TLS CA chain, exact IP SAN and generated MQTT credentials accepted")
    for kwargs in ({"ca": WORK / "wrong-ca/certs/ca.crt"}, {"peer": "192.168.0.51"}):
        try:
            sock, code = connect(**kwargs)
        except ssl.SSLCertVerificationError:
            continue
        sock.close()
        raise AssertionError("Invalid TLS identity unexpectedly accepted")
    passed("wrong CA and wrong peer IP rejected with certificate verification")
    for kwargs in ({"wrong": True}, {"anonymous": True}):
        sock, code = connect(**kwargs)
        sock.close()
        assert code >= 128
    passed("TLS does not bypass MQTT authentication")
    topic = "home/water/v1/esp32-bench/"
    assert publish("device", topic + "events") < 128
    assert publish("server", topic + "commands") < 128
    assert publish("device", topic + "commands") >= 128
    assert publish("server", topic + "status") >= 128
    passed("existing directional ACL enforced over TLS")
    success = True
finally:
    cleanup = compose("down", "--volumes", "--remove-orphans", check=False)
    (WORK / "result.json").write_text(json.dumps({"success": success, "checks": checks,
        "cleanupExitCode": cleanup.returncode, "realESP32": "NOT RUN", "remoteServer": "NOT RUN"}, indent=2), encoding="utf-8")
    if cleanup.returncode:
        raise RuntimeError("TLS test-only cleanup failed")
    print("Local TLS fixture cleaned. Result: " + str(WORK / "result.json"), flush=True)
