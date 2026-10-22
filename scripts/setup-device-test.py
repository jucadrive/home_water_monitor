"""Prepare only the isolated, approved device bench; never print secret values."""
import argparse
import ipaddress
import json
from pathlib import Path
import secrets
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
BENCH = ROOT / "infra/device-test"
IMAGE = "eclipse-mosquitto:2.0.22"


def docker(*args):
    result = subprocess.run(["docker", *args], capture_output=True, text=True)
    if result.returncode:
        diagnostic = result.stderr[-2000:]
        local = json.loads((BENCH / "secrets.local.json").read_text(encoding="utf-8"))
        for key, value in local.items():
            if "Password" in key and isinstance(value, str):
                diagnostic = diagnostic.replace(value, "[redacted]")
        print(diagnostic, file=sys.stderr)
        raise RuntimeError("Device bench Docker operation failed (raw output withheld)")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--lan-ip", required=True, type=ipaddress.IPv4Address)
    parser.add_argument("--rebind", action="store_true", help="Move this bench's LAN listener without changing its credentials/DB")
    args = parser.parse_args()
    if not any(args.lan_ip in ipaddress.ip_network(network) for network in ("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16")):
        parser.error("Select the private LAN address reachable from ESP32")
    credentials_path = BENCH / "secrets.local.json"
    if credentials_path.exists():
        credentials = json.loads(credentials_path.read_text(encoding="utf-8"))
        if credentials.get("lanIp") != str(args.lan_ip):
            if not args.rebind:
                raise RuntimeError("Existing bench belongs to another interface; explicit rebind required")
            credentials["lanIp"] = str(args.lan_ip)
            credentials_path.write_text(json.dumps(credentials, indent=2) + "\n", encoding="utf-8")
    else:
        credentials = {"lanIp": str(args.lan_ip), "deviceId": "esp32-bench", "dbPassword": secrets.token_hex(24),
                       "serverPassword": secrets.token_hex(24), "devicePassword": secrets.token_hex(24),
                       "inspectorPassword": secrets.token_hex(24)}
        credentials_path.write_text(json.dumps(credentials, indent=2) + "\n", encoding="utf-8")
    private = BENCH / "private"
    private.mkdir(exist_ok=True)
    password_file = private / "passwords"
    if not password_file.exists():
        docker("pull", IMAGE)
        for i, (name, key) in enumerate((("water-server", "serverPassword"), ("esp32-bench", "devicePassword"), ("water-inspector", "inspectorPassword"))):
            switches = ["-b", "-c"] if i == 0 else ["-b"]
            docker("run", "--rm", "--entrypoint", "mosquitto_passwd", "--mount", f"type=bind,source={private},target=/out",
                   IMAGE, *switches, "/out/passwords", name, credentials[key])
    (BENCH / ".env").write_text(f"LAN_BIND_IP={credentials['lanIp']}\nDB_PASSWORD={credentials['dbPassword']}\n", encoding="utf-8")
    wifi = ROOT / "firmware/esp32/wifi.local.json"
    if not wifi.exists():
        wifi.write_text(json.dumps({"ssid": "", "password": ""}, indent=2) + "\n", encoding="utf-8")
    docker("compose", "-f", str(BENCH / "compose.yml"), "--env-file", str(BENCH / ".env"), "up", "-d", "--wait")
    print("Device-only authenticated broker and PostgreSQL are ready; existing stack unchanged")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, KeyError, RuntimeError):
        print("Device bench setup failed; no secret/raw command output printed", file=sys.stderr)
        sys.exit(1)
