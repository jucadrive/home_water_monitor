import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("mqtt_setup", Path(__file__).with_name("create-personal-mqtt.py"))
setup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(setup)


class MqttSetupTest(unittest.TestCase):
    def test_invalid_device_cannot_inject_topic_or_configuration(self):
        with tempfile.TemporaryDirectory() as folder, patch.object(setup.subprocess, "run") as runner:
            for device in ("../other", "esp32/#", "a\nuser admin", "", "a" * 65):
                with self.assertRaises(ValueError):
                    setup.create(folder, device)
            runner.assert_not_called()
            self.assertEqual([], list(Path(folder).iterdir()))

    def test_existing_file_is_never_replaced(self):
        with tempfile.TemporaryDirectory() as folder, patch.object(setup.subprocess, "run") as runner:
            original = Path(folder) / "mqtt.env"
            original.write_text("existing configuration")
            with self.assertRaises(FileExistsError):
                setup.create(folder)
            self.assertEqual("existing configuration", original.read_text())
            runner.assert_not_called()

    def test_hashing_failure_rolls_back_only_new_files(self):
        with tempfile.TemporaryDirectory() as folder:
            sentinel = Path(folder) / "auth.env"
            sentinel.write_text("preserve unrelated file")
            failure = subprocess.CompletedProcess([], 1, "", "")
            with patch.object(setup.subprocess, "run", return_value=failure):
                with self.assertRaises(RuntimeError):
                    setup.create(folder)
            self.assertEqual([sentinel], list(Path(folder).iterdir()))

    def test_acl_has_exact_direction_and_no_wildcard(self):
        text = setup.acl("esp32-bench")
        self.assertNotIn("#", text)
        self.assertNotIn("+", text)
        server, device = text.split("\nuser esp32-bench\n")
        self.assertIn("topic read home/water/v1/esp32-bench/events", server)
        self.assertIn("topic write home/water/v1/esp32-bench/commands", server)
        self.assertIn("topic write home/water/v1/esp32-bench/events", device)
        self.assertIn("topic read home/water/v1/esp32-bench/commands", device)

    def test_passwords_use_stdin_and_hash_file_contains_no_plaintext(self):
        with tempfile.TemporaryDirectory() as folder:
            inputs = []
            def hashing(args, **kwargs):
                password, confirmation, _ = kwargs["input"].split("\n")
                self.assertEqual(password, confirmation)
                self.assertNotIn(password, " ".join(args))
                self.assertIn("--network", args)
                inputs.append(password)
                mount = args[args.index("--mount") + 1]
                staging = Path(mount.split("source=", 1)[1].split(",target=", 1)[0])
                with (staging / "mosquitto.passwd").open("a") as stream:
                    stream.write("fixture:hash-only\n")
                return subprocess.CompletedProcess(args, 0, "", "")
            with patch.object(setup.subprocess, "run", side_effect=hashing):
                setup.create(folder)
            self.assertNotEqual(inputs[0], inputs[1])
            text = (Path(folder) / "mosquitto.passwd").read_text()
            self.assertTrue(all(password not in text for password in inputs))


if __name__ == "__main__":
    unittest.main()
