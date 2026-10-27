"""Hardware-free acceptance against the real Docker deployment/JAR.

Creates only an isolated random project and generated test credentials. Never reads
user deployment secrets, contacts a mini PC, or connects a real ESP32.
"""
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import time
import urllib.error
import urllib.request
import uuid
from personal_mqtt_probe import Wire, FakeDevice

ROOT = Path(__file__).resolve().parents[1]
PROJECT = "ppossak-mqtt-check-" + uuid.uuid4().hex[:10]
WORK = ROOT / ".tools/deployment" / PROJECT
PASSWORD = "mqtt-http-fixture-password-only"
checks, observers = [], []
BASE = None


def module(name, file):
    spec = importlib.util.spec_from_file_location(name, ROOT / "scripts" / file)
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


def run(*args, check=True, data=None, timeout=300):
    result = subprocess.run(args, cwd=WORK, input=data, text=True, encoding="utf-8",
                            capture_output=True, timeout=timeout)
    if check and result.returncode:
        raise RuntimeError("Local test command failed: " + args[0] + " (output withheld)")
    return result


def compose(*args, **kwargs):
    return run("docker", "compose", "-p", PROJECT, "--env-file", "secrets/postgres.env",
               "-f", "compose.yml", "-f", "compose.mqtt.yml", "-f", "test.override.yml", *args, **kwargs)


def passed(label):
    checks.append(label)
    print("PASS " + label, flush=True)


def request(path, body=None, token=None, key=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    if key:
        headers["Idempotency-Key"] = key
    req = urllib.request.Request(BASE + path, headers=headers,
                                 data=json.dumps(body).encode() if body is not None else None)
    try:
        response = urllib.request.urlopen(req, timeout=10)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        raw = response.read()
        return response.status, json.loads(raw) if raw else None


def login():
    status, body = request("/api/auth/login", {"username": "fixture-owner", "password": PASSWORD})
    assert status == 200
    return body["accessToken"]


def wait(predicate, device=None, seconds=65):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        if device:
            device.pump()
        if predicate():
            return
        time.sleep(0.1)
    raise AssertionError("Timed out waiting for local acceptance condition")


def sql(query):
    return compose("exec", "-T", "postgres", "psql", "-v", "ON_ERROR_STOP=1", "-U", "ppossak",
                   "-d", "ppossak", "-Atc", query).stdout.strip()


def committed(session):
    assert sql("SELECT count(*) FROM tds_measurement WHERE session_id='" + session + "'") == "60"
    assert sql("SELECT preservation_result FROM tds_session WHERE session_id='" + session + "'") == "NORMAL"


def wire(role="device", wrong=False):
    address = compose("port", "broker", "1883").stdout.strip()
    assert address.startswith("127.0.0.1:")
    account = credentials if role == "device" else {"username": mqtt_env["MQTT_USERNAME"], "password": mqtt_env["MQTT_PASSWORD"]}
    result = Wire(WORK, "tcp://" + address, account["username"], "wrong-fixture" if wrong else account["password"])
    observers.append(result)
    return result


def cycle(token, cultivation, device, name):
    key = str(uuid.uuid4())
    status, command = request("/api/cultivations/" + cultivation + "/measurement-commands",
                              {"deviceId": "esp32-bench"}, token, key)
    assert status == 202
    path = "/api/measurement-commands/" + command["commandId"]
    wait(lambda: request(path, token=token)[1]["commandState"] == "ACCEPTED", device)
    assert device.starts == 1
    assert request("/api/cultivations/" + cultivation + "/measurement-commands",
                   {"deviceId": "esp32-bench"}, token, key)[0] == 200
    result = json.loads((ROOT / "docs/mqtt/v1/examples/session-result-normal.json").read_text())
    result.update(deviceId="esp32-bench", sessionId=command["sessionId"], startedAt=None, calibrationVersion="simulated-v1")
    assert device.wire.publish(device.topic + "events", result)
    wait(lambda: device.released and request(path, token=token)[1]["deviceBuffer"] == "RELEASE_CONFIRMED", device)
    status, detail = request("/api/measurement-sessions/" + command["sessionId"], token=token)
    assert status == 200 and len(detail["measurements"]) == 60
    assert detail["session"]["source"] == "SIMULATED" and detail["session"]["cultivationId"] == cultivation
    assert request("/api/cultivations/" + cultivation + "/tds-trend", token=token)[1] == []
    assert device.starts == 1
    passed(name + ": START/status/result/commit-before-confirm/release and SIMULATED exclusion")
    return command["sessionId"]


WORK.mkdir(parents=True)
for name in ("compose.yml", "compose.mqtt.yml", "Dockerfile", "Healthcheck.java", ".dockerignore", "mosquitto.conf"):
    shutil.copy2(ROOT / "infra/personal" / name, WORK / name)
shutil.copy2(ROOT / "backend/home-water-monitor-backend/build/libs/home-water-monitor-backend-0.0.1-SNAPSHOT.jar", WORK / "app.jar")
module("login_setup", "create-personal-secrets.py").create(WORK / "secrets", "fixture-owner", PASSWORD, "invalid.fixture.token")
module("mqtt_setup", "create-personal-mqtt.py").create(WORK / "secrets/mqtt")
credentials = json.loads((WORK / "secrets/mqtt/device-mqtt.json").read_text())
mqtt_env = dict(line.split("=", 1) for line in (WORK / "secrets/mqtt/mqtt.env").read_text().splitlines())
(WORK / "test.override.yml").write_text('services:\n  backend:\n    ports: ["127.0.0.1::8080"]\n  broker:\n    ports: ["127.0.0.1::1883"]\n', encoding="utf-8")
success = False
try:
    config = json.loads(run("docker", "compose", "--env-file", "secrets/postgres.env", "-f", "compose.yml",
                            "-f", "compose.mqtt.yml", "config", "--format", "json").stdout)
    assert set(config["services"]) == {"backend", "postgres", "cloudflared", "broker"}
    assert all(not service.get("ports") for service in config["services"].values())
    assert len(config["services"]["backend"]["environment"]) > 10
    passed("four services, no published ports, merged login/DB/MQTT configuration")
    print("Building isolated deployment and starting authenticated broker/backend...", flush=True)
    compose("up", "-d", "--build", "--wait", "--wait-timeout", "180", "backend", timeout=600)
    passed("non-root persistent broker and MQTT-enabled backend healthy")
    BASE = "http://" + compose("port", "backend", "8080").stdout.strip()
    assert request("/api/cultivations")[0] == 401
    token = login()
    anonymous = compose("exec", "-T", "broker", "mosquitto_pub", "-V", "mqttv5", "-h", "127.0.0.1", "-t", "probe", "-m", "fixture", "-q", "1", check=False)
    assert anonymous.returncode != 0
    assert not wire(wrong=True).publish(FakeDevice.topic + "events", {"fixture": True})
    passed("REST authentication retained; anonymous and wrong MQTT credentials rejected")
    assert not wire().publish(FakeDevice.topic + "commands", {"fixture": True})
    assert not wire(role="server").publish(FakeDevice.topic + "status", {"fixture": True})
    assert not wire().publish("home/water/v1/other-device/events", {"fixture": True})
    passed("device cannot command, server cannot forge status, foreign-device publish rejected")
    reader = wire(role="server").listen([FakeDevice.topic + "preservation"])
    marker = {"aclProbe": str(uuid.uuid4())}
    assert wire(role="server").publish(FakeDevice.topic + "preservation", marker)
    limit = time.monotonic() + 2
    while time.monotonic() < limit:
        message = reader.receive()
        assert message is None or message["body"] != marker
    reader.close()
    passed("server cannot read preservation topic despite publish permission")
    cultivation = str(uuid.uuid4())
    assert request("/api/cultivations", {"plantName": "fixture", "name": "MQTT isolated test",
        "startedAt": "2026-10-27T00:00:00Z", "memo": "not a real measurement"}, token, cultivation)[0] == 201
    device = FakeDevice(wire(), committed)
    sessions = [cycle(token, cultivation, device, "initial connection")]
    device.close()
    compose("restart", "broker")
    compose("up", "-d", "--wait", "--wait-timeout", "90", "broker")
    device = FakeDevice(wire(), committed)
    wait(lambda: request("/api/devices/esp32-bench/status", token=token)[1].get("snapshot", {}).get("bootId") == device.status["bootId"], device)
    sessions.append(cycle(token, cultivation, device, "broker restart/reconnect"))
    device.close()
    compose("restart", "backend")
    compose("up", "-d", "--wait", "--wait-timeout", "180", "backend")
    BASE = "http://" + compose("port", "backend", "8080").stdout.strip()
    assert request("/api/cultivations", token=token)[0] == 401
    token = login()
    device = FakeDevice(wire(), committed)
    sessions.append(cycle(token, cultivation, device, "backend restart/reconnect"))
    assert request("/api/cultivations/" + cultivation, token=token)[1]["cultivation"]["id"] == cultivation
    assert len(request("/api/cultivations/" + cultivation + "/measurement-sessions", token=token)[1]) == 3
    for session in sessions:
        committed(session)
    passed("restarts preserve cultivation and all 180 raw samples; old login token invalidated")
    success = True
finally:
    for observer in observers:
        observer.close()
    cleanup = compose("down", "--volumes", "--remove-orphans", check=False)
    (WORK / "result.json").write_text(json.dumps({"project": PROJECT, "success": success, "checks": checks,
        "cleanupExitCode": cleanup.returncode, "hardware": "NOT RUN", "remoteServer": "NOT RUN"}, indent=2), encoding="utf-8")
    if cleanup.returncode:
        raise RuntimeError("Isolated test cleanup failed")
    print("Test-only containers/volumes removed. Result: " + str(WORK / "result.json"), flush=True)
