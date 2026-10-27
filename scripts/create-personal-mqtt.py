"""Create NEW MQTT-only credentials; never reads/replaces login, DB or Tunnel files.

Requires local Docker and the project's pinned Mosquitto image. Passwords are sent
to mosquitto_passwd on stdin, never command arguments. No secret values are printed.
"""
import argparse
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import uuid

IMAGE = "eclipse-mosquitto:2.0.22"


def acl(device_id):
    topic = "home/water/v1/" + device_id + "/"
    return ("user water-server\n" + "".join("topic " + mode + " " + topic + name + "\n"
            for mode, name in (("read", "events"), ("read", "status"),
                               ("write", "commands"), ("write", "preservation")))
            + "\nuser esp32-bench\n" + "".join("topic " + mode + " " + topic + name + "\n"
            for mode, name in (("write", "events"), ("write", "status"),
                               ("read", "commands"), ("read", "preservation"))))


def write_new(path, content):
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as stream:
        stream.write(content)


def create(directory, device_id="esp32-bench"):
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]{0,63}", device_id):
        raise ValueError("Invalid device ID")
    directory = Path(directory).resolve()
    directory.mkdir(parents=True, mode=0o700, exist_ok=True)
    names = ("mqtt.env", "device-mqtt.json", "mosquitto.passwd", "mosquitto.acl")
    if any((directory / name).exists() for name in names):
        raise FileExistsError("Refusing to overwrite MQTT configuration")
    server_password, device_password = secrets.token_urlsafe(32), secrets.token_urlsafe(32)
    staging = directory / ("staging-" + uuid.uuid4().hex)
    staging.mkdir(mode=0o700)
    created = []
    try:
        for index, (username, password) in enumerate((("water-server", server_password),
                                                      ("esp32-bench", device_password))):
            args = ["docker", "run", "--rm", "-i", "--network", "none", "--entrypoint", "mosquitto_passwd",
                    "--mount", f"type=bind,source={staging},target=/out", IMAGE,
                    "-H", "sha512-pbkdf2"]
            if index == 0:
                args.append("-c")
            result = subprocess.run([*args, "/out/mosquitto.passwd", username],
                                    input=password + "\n" + password + "\n", text=True,
                                    encoding="utf-8", capture_output=True, timeout=30)
            if result.returncode:
                raise RuntimeError("MQTT password hashing failed; values withheld")
        content = {
            "mqtt.env": "\n".join(("MQTT_BROKER_URI=tcp://broker:1883", "MQTT_USERNAME=water-server",
                "MQTT_PASSWORD=" + server_password, "MQTT_ALLOWED_DEVICE_IDS=" + device_id,
                "MQTT_INBOUND_CLIENT_ID=ppossak-results-rx", "MQTT_OUTBOUND_CLIENT_ID=ppossak-results-tx",
                "MQTT_STATUS_CLIENT_ID=ppossak-status-rx", "MQTT_CONTROL_PUBLISH_CLIENT_ID=ppossak-control-tx", "")),
            "device-mqtt.json": json.dumps({"deviceId": device_id, "username": "esp32-bench",
                                            "password": device_password}, indent=2) + "\n",
            "mosquitto.acl": acl(device_id),
            "mosquitto.passwd": (staging / "mosquitto.passwd").read_text(encoding="utf-8"),
        }
        for name, value in content.items():
            target = directory / name
            write_new(target, value)
            created.append(target)
        return created
    except BaseException:
        for path in created:
            path.unlink()
        raise
    finally:
        password_file = staging / "mosquitto.passwd"
        if password_file.exists():
            password_file.unlink()
        staging.rmdir()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", type=Path, default=Path(".tools/deployment/mqtt"))
    parser.add_argument("--device-id", default="esp32-bench")
    args = parser.parse_args()
    try:
        paths = create(args.directory, args.device_id)
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError):
        parser.exit(1, "MQTT setup failed. Check Docker/image and use a new private folder. Values withheld.\n")
    print("Created MQTT-only files; contents hidden:")
    for path in paths:
        print(path)
    print("Keep device-mqtt.json on the development PC for later ESP32 setup; never commit secrets.")


if __name__ == "__main__":
    main()
