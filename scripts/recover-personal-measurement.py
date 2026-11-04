"""One existing-buffer RETRY round. Credentials stay in memory; no START or automatic POST retries."""
import argparse
import getpass
import json
import os
from pathlib import Path
import re
import sys
import time
import urllib.error
import urllib.request
import uuid

ORIGIN = "https://ppossak-api.jucadrive.com"


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class Api:
    def __init__(self):
        self.token = None
        self.opener = urllib.request.build_opener(NoRedirect())

    def request(self, method, path, body=None, key=None):
        headers = {"Accept": "application/json", "User-Agent": "ppossak-recovery/1.0"}
        if self.token:
            headers["Authorization"] = "Bearer " + self.token
        if body is not None:
            headers["Content-Type"] = "application/json"
        if key:
            headers["Idempotency-Key"] = key
        request = urllib.request.Request(ORIGIN + path, method=method, headers=headers,
                                         data=None if body is None else json.dumps(body).encode())
        try:
            with self.opener.open(request, timeout=20) as response:
                return json.loads(response.read(1_048_577))
        except urllib.error.HTTPError as error:
            # Only bounded classifications are exposed; never echo bodies or credential headers.
            phase = "LOGIN" if path == "/api/auth/login" else "READ" if method == "GET" else "RETRY"
            detail = ""
            try:
                info = json.loads(error.read(8192))
                if isinstance(info, dict):
                    code = info.get("code")
                    if isinstance(code, str) and code in {"AUTH_REQUIRED", "AUTH_FORBIDDEN", "AUTH_INVALID_CREDENTIALS", "AUTH_INPUT_INVALID", "AUTH_RATE_LIMITED", "CONTROL_BUSY", "CONTROL_UNAVAILABLE", "CONTROL_INPUT_INVALID"}:
                        detail = " code=" + code
                    elif "cloudflare_error" in info or "ray_id" in info and "error_code" in info:
                        detail = " source=CLOUDFLARE"
                        if type(info.get("error_code")) is int:
                            detail += " edge_code=" + str(info["error_code"])
            except (ValueError, OSError):
                if error.headers.get("cf-mitigated") == "challenge":
                    detail = " source=CLOUDFLARE_CHALLENGE"
            raise RuntimeError("API_HTTP_" + str(error.code) + " phase=" + phase + detail) from None
        except (urllib.error.URLError, TimeoutError, OSError):
            raise RuntimeError("API_RESPONSE_UNCERTAIN") from None


def write_state(path, value, create=False):
    flags = os.O_WRONLY | os.O_CREAT | (os.O_EXCL if create else os.O_TRUNC)
    with os.fdopen(os.open(path, flags, 0o600), "w", encoding="utf-8") as stream:
        json.dump(value, stream, indent=2)
        stream.flush()
        os.fsync(stream.fileno())


def preflight(status, command, device, session, original):
    snapshot = status.get("snapshot") or {}
    checks = [status.get("deviceId") == device, status.get("liveness") == "FRESH",
              snapshot.get("deviceId") == device, snapshot.get("state") == "MANUAL_WAIT",
              snapshot.get("sessionId") == session, snapshot.get("sampleCount") == 60,
              snapshot.get("finalized") is True, snapshot.get("attempt") == 5,
              snapshot.get("fault") is None, snapshot.get("controlReady") is True,
              snapshot.get("controlMode") == "MQTT",
              (snapshot.get("lastExecution") or {}).get("commandId") == original,
              command.get("commandId") == original, command.get("deviceId") == device,
              command.get("sessionId") == session, command.get("commandType") == "START",
              command.get("commandState") == "ACCEPTED",
              command.get("serverPreservation") == "NOT_OBSERVED", command.get("releaseConfirmedAt") is None]
    if not all(checks):
        raise RuntimeError("PREFLIGHT_CHANGED: no RETRY sent; review current status")


def summary(value):
    snapshot = value.get("snapshot") or {}
    # Whitelist diagnostic metadata only, never raw samples or complete server responses.
    return {key: snapshot.get(key) for key in ["state", "sessionId", "sampleCount", "finalized", "attempt", "round", "controlReady", "fault", "bootId"]} | {
        "liveness": value.get("liveness"), "receivedAt": value.get("receivedAt")}


def recover(api, device, session, original, path, apply=False, seconds=360, sleep=time.sleep, clock=time.monotonic):
    status_path = "/api/devices/" + device + "/status"
    status = api.request("GET", status_path)
    print("DEVICE " + json.dumps(summary(status)), flush=True)
    initial = api.request("GET", "/api/measurement-commands/" + original)
    print("ORIGINAL " + json.dumps({key: initial.get(key) for key in ["commandState", "reason", "serverPreservation", "deviceBuffer", "releaseConfirmedAt"]}), flush=True)
    if path.exists():
        saved = json.loads(path.read_text(encoding="utf-8"))
        if (saved.get("device"), saved.get("session"), saved.get("original")) != (device, session, original):
            raise RuntimeError("SAVED_REQUEST_MISMATCH")
        if not saved.get("commandId"):
            raise RuntimeError("UNCERTAIN_SAVED_REQUEST: keep the saved key; do not delete or resubmit")
        command_id = saved["commandId"]  # Resume GET polling only; never create a second command.
    else:
        preflight(status, initial, device, session, original)
        if not apply:
            print("PREFLIGHT_OK: read-only; use --apply for one RETRY round")
            return 0
        saved = {"device": device, "session": session, "original": original,
                 "key": str(uuid.uuid4()), "body": {"type": "RETRY", "sessionId": session}, "commandId": None}
        path.parent.mkdir(parents=True, exist_ok=True)
        write_state(path, saved, create=True)  # Exact key/body are durable before the only POST.
        response = api.request("POST", "/api/devices/" + device + "/measurement-commands", saved["body"], saved["key"])
        command_id = str(uuid.UUID(response["commandId"]))
        if response.get("sessionId") != session:
            raise RuntimeError("RESPONSE_SESSION_MISMATCH: saved key retained")
        saved["commandId"] = command_id
        write_state(path, saved)
        print("RETRY_COMMAND " + command_id, flush=True)
    deadline = clock() + seconds
    while clock() < deadline:
        command = api.request("GET", "/api/measurement-commands/" + command_id)
        if (command.get("sessionId"), command.get("deviceId"), command.get("commandType")) != (session, device, "RETRY"):
            raise RuntimeError("POLL_IDENTITY_MISMATCH")
        status = api.request("GET", status_path)
        fields = {key: command.get(key) for key in ["commandState", "reason", "serverPreservation", "deviceBuffer", "releaseConfirmedAt"]}
        print("OBSERVE " + json.dumps(fields | summary(status)), flush=True)
        if command.get("serverPreservation") == "NORMAL" and command.get("deviceBuffer") == "RELEASE_CONFIRMED" and command.get("releaseConfirmedAt"):
            print("RECOVERED: NORMAL / RELEASE_CONFIRMED; no new measurement was requested")
            return 0
        if command.get("commandState") in {"REJECTED", "NOT_DISPATCHED", "NOT_EXECUTED", "INTERRUPTED_REBOOT"}:
            raise RuntimeError("RETRY_STOPPED: keep board powered; inspect broker/backend logs")
        snapshot = status.get("snapshot") or {}
        if command.get("commandState") == "ACCEPTED" and snapshot.get("state") == "MANUAL_WAIT" and (snapshot.get("lastExecution") or {}).get("commandId") == command_id and snapshot.get("attempt") == 5:
            raise RuntimeError("RETRY_EXHAUSTED: original buffer retained; no further POST sent")
        sleep(3)
    raise RuntimeError("OBSERVATION_TIMEOUT: keep saved request and board powered")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--device", default="esp32-bench")
    parser.add_argument("--session", required=True, type=uuid.UUID)
    parser.add_argument("--command", required=True, type=uuid.UUID)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]{0,63}", args.device):
        parser.error("Invalid device")
    api = Api()
    username = input("App login username (not SSH username): ").strip()
    password = getpass.getpass("App login password (hidden): ")
    api.token = api.request("POST", "/api/auth/login", {"username": username, "password": password})["accessToken"]
    del password
    root = Path(__file__).resolve().parent.parent
    state = root / ".tools" / "control-recovery" / (str(args.session) + ".json")
    return recover(api, args.device, str(args.session), str(args.command), state, args.apply)


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (RuntimeError, ValueError, KeyError, OSError) as error:
        print(str(error) if isinstance(error, RuntimeError) else "LOCAL_OR_RESPONSE_ERROR: no new automatic retry", file=sys.stderr)
        sys.exit(2)
