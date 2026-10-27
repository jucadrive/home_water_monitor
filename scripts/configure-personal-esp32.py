"""Generate a NEW personal TLS header without replacing existing bench credentials.

Wi-Fi values are entered locally with echo disabled, or read from an explicit private
file. Only the public CA certificate is embedded; no CA/server private key is used.
"""
import argparse
import getpass
import importlib.util
import json
from pathlib import Path
import re
import sys
from cryptography import x509
from cryptography.hazmat.primitives import serialization

ROOT = Path(__file__).resolve().parents[1]


def load(name, file):
    spec = importlib.util.spec_from_file_location(name, ROOT / "scripts" / file)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


wifi_rules = load("wifi_rules", "configure-esp32.py")
tls_rules = load("tls_rules", "create-mqtt-lan-tls.py")


def header(wifi, device, broker_ip, ca_pem):
    if wifi_rules.validate_wifi(wifi):
        raise ValueError("Personal deployment requires protected Wi-Fi")
    address = tls_rules.lan_address(broker_ip)
    for name in ("deviceId", "username"):
        if not isinstance(device.get(name), str) or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]{0,63}", device[name]):
            raise ValueError("Invalid MQTT identity")
    if not isinstance(device.get("password"), str) or not re.fullmatch(r"[A-Za-z0-9_-]{16,128}", device["password"]):
        raise ValueError("Use generated device credentials")
    cert = x509.load_pem_x509_certificate(ca_pem)
    if not cert.extensions.get_extension_for_class(x509.BasicConstraints).value.ca:
        raise ValueError("A trusted CA certificate is required")
    fields = {"HWM_WIFI_SSID": wifi["ssid"], "HWM_WIFI_PASSWORD": wifi["password"],
              "HWM_BROKER_URI": "mqtts://" + str(address) + ":8883", "HWM_DEVICE_ID": device["deviceId"],
              "HWM_MQTT_USERNAME": device["username"], "HWM_MQTT_PASSWORD": device["password"],
              "HWM_MQTT_CA_PEM": cert.public_bytes(serialization.Encoding.PEM).decode("ascii")}
    lines = ["/* Private generated personal configuration. Never share or commit. */", "#pragma once"]
    lines.extend("#define " + name + " " + json.dumps(value, ensure_ascii=True) for name, value in fields.items())
    lines.extend(["#define HWM_WIFI_OPEN 0", "#define HWM_SAMPLE_TARGET 60", ""])
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--broker-ip", required=True)
    parser.add_argument("--device-file", type=Path, default=ROOT / ".tools/deployment/mqtt/device-mqtt.json")
    parser.add_argument("--ca-file", type=Path, default=ROOT / ".tools/deployment/mqtt-lan/certs/ca.crt")
    parser.add_argument("--wifi-file", type=Path)
    parser.add_argument("--output", type=Path, default=ROOT / "firmware/esp32/main/local_personal_config.h")
    args = parser.parse_args()
    try:
        if args.output.exists():
            raise FileExistsError("Refusing to overwrite personal configuration")
        device = json.loads(args.device_file.read_text(encoding="utf-8"))
        ca_pem = args.ca_file.read_bytes()
        wifi = json.loads(args.wifi_file.read_text(encoding="utf-8")) if args.wifi_file else {
            "ssid": getpass.getpass("Home 2.4GHz Wi-Fi SSID (hidden): "), "authMode": "wpa2",
            "password": getpass.getpass("Wi-Fi password (hidden): ")}
        text = header(wifi, device, args.broker_ip, ca_pem)
        tls_rules.write_new(args.output, text.encode("utf-8"))
    except (OSError, ValueError, KeyError, TypeError, x509.ExtensionNotFound):
        parser.exit(1, "Personal configuration failed; check local inputs. Values withheld; existing header preserved.\n")
    print("Personal TLS header created. Bench header and credentials preserved; values hidden.")


if __name__ == "__main__":
    main()
