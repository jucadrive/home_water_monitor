"""Local-only bench helpers. Captured payloads and credentials never go to summary output."""
import base64
import json
import os
from pathlib import Path
import queue
import re
import shutil
import socket
import subprocess
import threading
import time
import zipfile

ROOT = Path(__file__).resolve().parents[2]
BENCH = ROOT / "infra/device-test"
CACHE = ROOT / ".tools/device-test"
JAR = ROOT / "backend/home-water-monitor-backend/build/libs/home-water-monitor-backend-0.0.1-SNAPSHOT.jar"
FLAGS = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0


def credentials():
    return json.loads((BENCH / "secrets.local.json").read_text(encoding="utf-8"))


def java_home():
    choices = [Path(v) for v in (os.environ.get("HWM_JAVA_HOME"), os.environ.get("JAVA_HOME")) if v]
    choices += sorted(Path("C:/dev").glob("jdk-25*"), reverse=True)
    if shutil.which("java"):
        choices.append(Path(shutil.which("java")).resolve().parents[1])
    for home in choices:
        executable = home / "bin" / ("java.exe" if os.name == "nt" else "java")
        if not executable.exists():
            continue
        version = subprocess.run([str(executable), "-version"], capture_output=True, text=True)
        if re.search(r'version "25(?:[.\"])', version.stderr):
            return home
    raise RuntimeError("JDK 25 is required")


def compose(*args):
    result = subprocess.run(["docker", "compose", "-f", str(BENCH / "compose.yml"), "--env-file", str(BENCH / ".env"), *args],
                            capture_output=True, text=True, creationflags=FLAGS)
    if result.returncode:
        raise RuntimeError("Device bench container operation failed")
    return result.stdout


def sql(query):
    # Uses the isolated container's local socket; no development DB/password is involved.
    return compose("exec", "-T", "postgres", "psql", "-U", "hwm_device_test", "-d", "hwm_device_test", "-v", "ON_ERROR_STOP=1", "-At", "-c", query).strip()


def port_ready(port):
    try:
        with socket.create_connection(("127.0.0.1", port), timeout=0.3):
            return True
    except OSError:
        return False


def mqtt_command():
    CACHE.mkdir(parents=True, exist_ok=True)
    library = CACHE / "paho-1.2.5.jar"
    with zipfile.ZipFile(JAR) as archive:
        content = archive.read("BOOT-INF/lib/org.eclipse.paho.mqttv5.client-1.2.5.jar")
    if not library.exists() or library.read_bytes() != content:
        library.write_bytes(content)
    source = ROOT / "scripts/device-test/BenchMqtt.java"
    classes = CACHE / "classes"
    target = classes / "BenchMqtt.class"
    home = java_home()
    if not target.exists() or target.stat().st_mtime < max(source.stat().st_mtime, library.stat().st_mtime):
        classes.mkdir(exist_ok=True)
        compile_result = subprocess.run([str(home / "bin/javac"), "-d", str(classes), "-cp", str(library), str(source)], capture_output=True, creationflags=FLAGS)
        if compile_result.returncode:
            raise RuntimeError("Bench MQTT helper compilation failed")
    return [str(home / "bin/java"), "-cp", str(classes) + os.pathsep + str(library), "BenchMqtt"]


def mqtt_environment(role):
    values = credentials()
    env = os.environ.copy()
    names = {"inspector": ("water-inspector", "inspectorPassword"), "server": ("water-server", "serverPassword"), "device": ("esp32-bench", "devicePassword")}
    username, password_key = names[role]
    env["HWM_TEST_MQTT_USERNAME"] = username
    env["HWM_TEST_MQTT_PASSWORD"] = values[password_key]
    return env


class Observer:
    def __init__(self, seconds=600, *, broker_uri=None, client_id=None, topics=None, include_expiry=False):
        self.lines = queue.Queue(maxsize=256)
        env = mqtt_environment("inspector")
        if include_expiry: env["HWM_TEST_INCLUDE_EXPIRY"] = "1"
        if broker_uri: env["HWM_TEST_BROKER_URI"] = broker_uri
        if client_id: env["HWM_TEST_CLIENT_ID"] = client_id
        topics = topics or ["home/water/v1/esp32-bench/events", "home/water/v1/esp32-bench/preservation"]
        self.process = subprocess.Popen(mqtt_command() + ["listen", str(seconds), *topics],
                                        env=env, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True, creationflags=FLAGS)
        self.overflow = False
        def read():
            for line in self.process.stdout:
                try:
                    self.lines.put_nowait((time.monotonic(), line.rstrip("\n")))
                except queue.Full:
                    self.overflow = True
        self.reader = threading.Thread(target=read, daemon=True)
        self.reader.start()
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            try:
                _, line = self.lines.get(timeout=max(0.1, deadline - time.monotonic()))
            except queue.Empty:
                break
            if line == "READY":
                return
            if line.startswith("BENCH_MQTT_FAILED"):
                break
        self.close()
        raise RuntimeError("MQTT observer did not become ready")

    def receive(self, timeout=0.1):
        if self.overflow:
            raise RuntimeError("Observer queue overflow")
        try:
            received, line = self.lines.get(timeout=timeout)
        except queue.Empty:
            return None
        parts = line.split("\t")
        if len(parts) not in (7, 8) or parts[0] != "MESSAGE":
            return None
        return {"received": received, "topic": base64.b64decode(parts[1]).decode("utf-8"), "qos": int(parts[2]),
                "retained": parts[3] == "true", "pfi": parts[4] == "true", "contentType": base64.b64decode(parts[5]).decode("utf-8"),
                "payload": base64.b64decode(parts[6]),
                "expiry": int(parts[7]) if len(parts) == 8 and parts[7] != "null" else None}

    def close(self):
        if self.process.poll() is None:
            self.process.terminate()
            self.process.wait(timeout=5)
        self.reader.join(timeout=1)
        self.process.stdout.close()


def publish(payload, *, topic="home/water/v1/esp32-bench/preservation", role="server", qos=1, retained=False, pfi="true", content_type="application/json", broker_uri=None, client_id=None, expiry=None):
    CACHE.mkdir(parents=True, exist_ok=True)
    file = CACHE / "publish-payload.json"
    file.write_bytes(payload if isinstance(payload, bytes) else json.dumps(payload, separators=(",", ":")).encode("utf-8"))
    env = mqtt_environment(role)
    if broker_uri: env["HWM_TEST_BROKER_URI"] = broker_uri
    if client_id: env["HWM_TEST_CLIENT_ID"] = client_id
    expiry_args = [] if expiry is None else [str(expiry)]
    result = subprocess.run(mqtt_command() + ["publish", topic, str(file), str(qos), str(retained).lower(), pfi, content_type, *expiry_args],
                            env=env, capture_output=True, text=True, timeout=20, creationflags=FLAGS)
    return result.returncode == 0 and "PUBLISHED" in result.stdout
