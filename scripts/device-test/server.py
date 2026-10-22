"""Run/stop only the isolated acceptance backend, identifying its own PID and creation time."""
import argparse
import json
import os
import subprocess
import sys
import time
import uuid
import psutil
from bench import CACHE, FLAGS, JAR, credentials, java_home, port_ready

STATE = CACHE / "server-state.json"


def owned_process():
    if not STATE.exists():
        return None
    state = json.loads(STATE.read_text())
    try:
        process = psutil.Process(state["pid"])
        command = process.cmdline()
        if abs(process.create_time() - state["created"]) < 0.01 and str(JAR) in command and "-Dhwm.bench.instance=" + state["instance"] in command:
            return process
    except (psutil.NoSuchProcess, psutil.AccessDenied):
        return None
    return None


def start(control=False, broker_uri="tcp://127.0.0.1:18883"):
    if broker_uri not in ("tcp://127.0.0.1:18883", "tcp://127.0.0.1:18884"):
        raise RuntimeError("Bench broker must be an explicit loopback test port")
    process = owned_process()
    if process:
        if json.loads(STATE.read_text()).get("control", False) != control:
            raise RuntimeError("Owned server control mode differs; stop explicitly before changing mode")
        if json.loads(STATE.read_text()).get("brokerUri", "tcp://127.0.0.1:18883") != broker_uri:
            raise RuntimeError("Owned server broker differs; stop explicitly before changing it")
        if not port_ready(18080):
            raise RuntimeError("Owned server exists but is not ready")
        return
    if port_ready(18080):
        raise RuntimeError("Port 18080 is occupied by an unrelated process")
    values = credentials()
    env = os.environ.copy()
    env.update({"DB_NAME": "hwm_device_test", "DB_USERNAME": "hwm_device_test", "DB_PASSWORD": values["dbPassword"],
                "SPRING_DATASOURCE_URL": "jdbc:postgresql://127.0.0.1:25432/hwm_device_test", "MQTT_ENABLED": "true",
                "MQTT_BROKER_URI": broker_uri, "MQTT_INBOUND_CLIENT_ID": "bench-server-in",
                "MQTT_OUTBOUND_CLIENT_ID": "bench-server-out", "MQTT_ALLOWED_DEVICE_IDS": "esp32-bench",
                "MQTT_USERNAME": "water-server", "MQTT_PASSWORD": values["serverPassword"], "SERVER_ADDRESS": "127.0.0.1", "SERVER_PORT": "18080"})
    env["MQTT_CONTROL_ENABLED"] = str(control).lower()
    env["MQTT_STATUS_CLIENT_ID"] = "bench-server-status"
    env["MQTT_CONTROL_PUBLISH_CLIENT_ID"] = "bench-server-control"
    instance = str(uuid.uuid4())
    CACHE.mkdir(parents=True, exist_ok=True)
    with (CACHE / "backend.log").open("ab") as log:
        child = subprocess.Popen([str(java_home() / "bin/java"), "-Dhwm.bench.instance=" + instance, "-jar", str(JAR)],
                                 env=env, stdout=log, stderr=subprocess.STDOUT, creationflags=FLAGS)
    process = psutil.Process(child.pid)
    STATE.write_text(json.dumps({"pid": child.pid, "created": process.create_time(), "instance": instance, "control": control, "brokerUri": broker_uri}), encoding="utf-8")
    deadline = time.monotonic() + 45
    while time.monotonic() < deadline:
        if child.poll() is not None:
            raise RuntimeError("Bench backend exited before readiness")
        if port_ready(18080):
            return
        time.sleep(0.25)
    raise RuntimeError("Bench backend readiness timed out")


def stop():
    process = owned_process()
    if process:
        process.terminate()
        process.wait(timeout=10)
    if STATE.exists():
        STATE.unlink()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=["start", "stop"])
    parser.add_argument("--control", action="store_true")
    parser.add_argument("--broker-uri", default="tcp://127.0.0.1:18883")
    args = parser.parse_args()
    try:
        if args.action == "start": start(control=args.control, broker_uri=args.broker_uri)
        else: stop()
        print("Isolated device-test backend " + args.action + " completed")
    except (OSError, ValueError, KeyError, RuntimeError, psutil.Error):
        print("Device-test backend operation failed; secret/raw logs withheld", file=sys.stderr)
        sys.exit(1)
