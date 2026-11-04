"""Offline recovery guards and mocked shell lifecycle: never calls the personal server."""
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import urllib.request
import urllib.error
from email.message import Message

ROOT = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location("recovery", ROOT / "scripts/recover-personal-measurement.py")
recovery = importlib.util.module_from_spec(spec)
spec.loader.exec_module(recovery)
DEVICE, SESSION, ORIGINAL, RETRY = "esp32-bench", "51742d0d-0d0f-4d8f-99a8-56dff5cc4d5b", "c250f6c9-677d-4deb-86a8-530a4976bc38", "11111111-1111-4111-8111-111111111111"


class FakeApi:
    def __init__(self, uncertain=False):
        self.calls, self.uncertain = [], uncertain
        self.snapshot = {"deviceId": DEVICE, "state": "MANUAL_WAIT", "sessionId": SESSION, "sampleCount": 60,
                         "finalized": True, "attempt": 5, "fault": None, "controlReady": True,
                         "controlMode": "MQTT", "lastExecution": {"commandId": ORIGINAL}}

    def request(self, method, path, body=None, key=None):
        self.calls.append((method, path, body, key))
        if path.endswith("/status"):
            return {"deviceId": DEVICE, "liveness": "FRESH", "snapshot": self.snapshot}
        if method == "POST":
            if self.uncertain:
                raise RuntimeError("API_RESPONSE_UNCERTAIN")
            return {"commandId": RETRY, "sessionId": SESSION}
        return {"commandId": ORIGINAL if path.endswith(ORIGINAL) else RETRY, "deviceId": DEVICE,
                "sessionId": SESSION, "commandType": "START" if path.endswith(ORIGINAL) else "RETRY",
                "commandState": "ACCEPTED", "serverPreservation": "NOT_OBSERVED" if path.endswith(ORIGINAL) else "NORMAL",
                "deviceBuffer": "HELD" if path.endswith(ORIGINAL) else "RELEASE_CONFIRMED",
                "releaseConfirmedAt": None if path.endswith(ORIGINAL) else "2026-11-01T08:00:00Z"}


class RecoveryTest(unittest.TestCase):
    def setUp(self):
        target = ROOT / ".tools/control-recovery-tests"
        target.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir=target)
        self.addCleanup(self.temp.cleanup)
        self.folder = Path(self.temp.name)
        self.path = self.folder / "request.json"

    def run_recovery(self, api, apply=True):
        with contextlib.redirect_stdout(io.StringIO()):
            return recovery.recover(api, DEVICE, SESSION, ORIGINAL, self.path, apply, sleep=lambda _: None)

    def test_read_only_preflight_never_posts(self):
        api = FakeApi()
        self.assertEqual(0, self.run_recovery(api, False))
        self.assertFalse(self.path.exists())
        self.assertTrue(all(c[0] == "GET" for c in api.calls))

    def test_changed_state_session_fault_or_readiness_blocks_post(self):
        for key, value in [("state", "IDLE"), ("sessionId", RETRY), ("sampleCount", 59), ("fault", "CONTROL_UNAVAILABLE"), ("controlReady", False)]:
            with self.subTest(key=key):
                api = FakeApi(); api.snapshot[key] = value
                with self.assertRaisesRegex(RuntimeError, "PREFLIGHT_CHANGED"):
                    self.run_recovery(api)
                self.assertTrue(all(c[0] == "GET" for c in api.calls))
                self.assertFalse(self.path.exists())

    def test_one_exact_retry_round_and_receipt_without_credentials(self):
        api = FakeApi(); self.assertEqual(0, self.run_recovery(api))
        posts = [c for c in api.calls if c[0] == "POST"]
        self.assertEqual(1, len(posts)); self.assertEqual({"type": "RETRY", "sessionId": SESSION}, posts[0][2])
        saved = json.loads(self.path.read_text()); self.assertEqual(posts[0][3], saved["key"])
        self.assertEqual(RETRY, saved["commandId"])
        self.assertNotIn("token", self.path.read_text().lower()); self.assertNotIn("password", self.path.read_text().lower())

    def test_lost_response_retains_key_and_restart_does_not_post_again(self):
        with self.assertRaisesRegex(RuntimeError, "UNCERTAIN"):
            self.run_recovery(FakeApi(True))
        original = self.path.read_text(); second = FakeApi()
        with self.assertRaisesRegex(RuntimeError, "UNCERTAIN_SAVED_REQUEST"):
            self.run_recovery(second)
        self.assertEqual(original, self.path.read_text()); self.assertTrue(all(c[0] == "GET" for c in second.calls))

    def test_saved_command_resumes_reads_only(self):
        self.run_recovery(FakeApi()); api = FakeApi(); api.snapshot["state"] = "IDLE"
        self.assertEqual(0, self.run_recovery(api)); self.assertTrue(all(c[0] == "GET" for c in api.calls))

    def test_redirect_does_not_forward_login_or_bearer(self):
        req = urllib.request.Request(recovery.ORIGIN, headers={"Authorization": "Bearer fixture"})
        self.assertIsNone(recovery.NoRedirect().redirect_request(req, None, 302, "", {}, "https://other.example"))

    def test_explicit_tool_agent_and_redacted_login_failure(self):
        seen=[]
        class Edge:
            def open(self, request, timeout):
                seen.append(request.get_header("User-agent"))
                raise urllib.error.HTTPError(request.full_url,403,"Forbidden",Message(),
                    io.BytesIO(b'{"cloudflare_error":true,"error_code":1234,"detail":"fixture-password-do-not-print"}'))
        api=recovery.Api(); api.opener=Edge()
        with self.assertRaisesRegex(RuntimeError,"API_HTTP_403 phase=LOGIN source=CLOUDFLARE edge_code=1234") as error:
            api.request("POST","/api/auth/login",{"username":"fixture","password":"fixture-password-do-not-print"})
        self.assertEqual(["ppossak-recovery/1.0"],seen)
        self.assertNotIn("fixture-password",str(error.exception))

    def test_shell_enable_capture_restore_preserves_config_inode_and_security(self):
        bash = Path("C:/Program Files/Git/bin/bash.exe")
        if not bash.exists():
            found = shutil.which("bash")
            self.assertIsNotNone(found); bash = Path(found)
        bindir = self.folder / "bin"; bindir.mkdir()
        for name, text in {
            "sudo": '#!/usr/bin/env bash\nexec "$@"\n',
            "docker": '''#!/usr/bin/env bash
case "$1" in
 ps) if [[ "$*" == *service=broker* ]]; then echo broker-fixture; else echo backend-fixture; fi ;;
 inspect) if [[ "$*" == *Mounts* ]]; then echo "$PWD/mosquitto-lan.conf"; else echo 'fixed-start 0'; fi ;;
 kill) if [[ -f "$PWD/fail-hup-once" ]]; then rm -- "$PWD/fail-hup-once"; exit 1; fi; echo fixture ;;
 logs) echo 'Received PUBLISH from esp32-bench-tx home/water/v2/esp32-bench/events (123 bytes)' ;;
 *) exit 3 ;;
esac
''',
        }.items():
            p = bindir / name; p.write_text(text, encoding="utf-8", newline="\n"); p.chmod(0o755)
        config = self.folder / "mosquitto-lan.conf"
        original = "allow_anonymous false\npassword_file /run/secrets/mosquitto.passwd\nlog_type warning\nconnection_messages false\nlistener 8883\nrequire_certificate false\n"
        config.write_text(original, newline="\n"); inode = config.stat().st_ino
        env = os.environ.copy(); env["PATH"] = str(bindir) + os.pathsep + env["PATH"]
        script = ROOT / "scripts/mqtt-recovery-logging.sh"
        def run(action):
            result = subprocess.run([str(bash), str(script), action], cwd=self.folder, env=env, capture_output=True, text=True, encoding="utf-8")
            self.assertEqual(0, result.returncode, result.stderr + result.stdout)
        run("enable"); self.assertEqual(inode, config.stat().st_ino)
        self.assertIn("log_type all", config.read_text()); self.assertIn("allow_anonymous false", config.read_text())
        run("capture"); run("restore")
        self.assertEqual(original, config.read_text()); self.assertEqual(inode, config.stat().st_ino)
        self.assertFalse((self.folder / ".mqtt-recovery-logging-directory").exists())
        (self.folder / "fail-hup-once").write_text("fixture")
        failed = subprocess.run([str(bash), str(script), "enable"], cwd=self.folder, env=env, capture_output=True, text=True, encoding="utf-8")
        self.assertEqual(2, failed.returncode, failed.stdout + failed.stderr)
        self.assertIn("ORIGINAL_CONFIG_RESTORED", failed.stdout)
        self.assertEqual(original, config.read_text())
        self.assertFalse((self.folder / ".mqtt-recovery-logging-directory").exists())


if __name__ == "__main__":
    unittest.main()
