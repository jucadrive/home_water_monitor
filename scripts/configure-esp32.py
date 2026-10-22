"""Generate a gitignored C header from local Wi-Fi and bench credentials without logging them."""
import argparse
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]


def validate_wifi(wifi):
    if not isinstance(wifi, dict):
        raise ValueError("Wi-Fi configuration is invalid")
    if not isinstance(wifi.get("ssid"), str) or not 1 <= len(wifi["ssid"].encode("utf-8")) <= 32:
        raise ValueError("Wi-Fi configuration is missing/invalid")
    mode = wifi.get("authMode", "wpa2")
    password = wifi.get("password")
    if mode not in ("open", "wpa2") or not isinstance(password, str):
        raise ValueError("Wi-Fi configuration is missing/invalid")
    if (mode == "open" and password != "") or (mode == "wpa2" and not 8 <= len(password.encode("utf-8")) <= 63):
        raise ValueError("Wi-Fi authentication mode/password mismatch")
    if any(ord(ch) < 32 or ord(ch) == 127 for key in ("ssid", "password") for ch in wifi[key]):
        raise ValueError("Wi-Fi configuration is invalid")
    return mode == "open"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--samples", type=int, choices=[0, 1, 59, 60], default=60)
    parser.add_argument("--broker-port", type=int, choices=[18883, 18884], default=18883)
    args = parser.parse_args()
    wifi = json.loads((ROOT / "firmware/esp32/wifi.local.json").read_text(encoding="utf-8"))
    open_wifi = validate_wifi(wifi)
    bench = json.loads((ROOT / "infra/device-test/secrets.local.json").read_text(encoding="utf-8"))
    fields = {"HWM_WIFI_SSID": wifi["ssid"], "HWM_WIFI_PASSWORD": wifi["password"],
              "HWM_BROKER_URI": "mqtt://" + bench["lanIp"] + ":" + str(args.broker_port),
              "HWM_DEVICE_ID": bench["deviceId"], "HWM_MQTT_USERNAME": "esp32-bench",
              "HWM_MQTT_PASSWORD": bench["devicePassword"]}
    lines = ["/* Generated locally. Contains credentials; never share or commit. */", "#pragma once"]
    lines.extend(f"#define {key} {json.dumps(value, ensure_ascii=False)}" for key, value in fields.items())
    lines.append(f"#define HWM_WIFI_OPEN {int(open_wifi)}")
    lines.append(f"#define HWM_SAMPLE_TARGET {args.samples}")
    (ROOT / "firmware/esp32/main/local_config.h").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("Local firmware configuration generated (secret values withheld)")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, KeyError, TypeError):
        print("Local Wi-Fi/bench configuration is missing or invalid; values withheld", file=sys.stderr)
        sys.exit(1)
