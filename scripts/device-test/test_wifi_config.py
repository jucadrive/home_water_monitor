"""Configuration boundaries: open Wi-Fi must be an explicit, password-free choice."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("configure_esp32", Path(__file__).parents[1] / "configure-esp32.py")
config = importlib.util.module_from_spec(spec)
spec.loader.exec_module(config)


class WifiConfigurationTest(unittest.TestCase):
    def test_explicit_open(self):
        self.assertTrue(config.validate_wifi({"ssid": "bench", "authMode": "open", "password": ""}))

    def test_existing_protected_configuration(self):
        self.assertFalse(config.validate_wifi({"ssid": "bench", "password": "test-only"}))
        self.assertFalse(config.validate_wifi({"ssid": "bench", "authMode": "wpa2", "password": "x" * 63}))

    def test_invalid_configuration_rejected(self):
        cases = [None, {}, {"ssid": "bench", "password": ""},
                 {"ssid": "bench", "authMode": "open", "password": "test-only"},
                 {"ssid": "bench", "authMode": "other", "password": "test-only"},
                 {"ssid": "bench", "password": "x" * 7},
                 {"ssid": "bench", "password": "x" * 64},
                 {"ssid": "x" * 33, "password": "test-only"},
                 {"ssid": "bench\n", "password": "test-only"},
                 {"ssid": "bench", "password": "test\x7fonly"}]
        for case in cases:
            with self.subTest(case_index=cases.index(case)), self.assertRaises(ValueError):
                config.validate_wifi(case)


if __name__ == "__main__":
    unittest.main()
