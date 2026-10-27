import importlib.util
import ipaddress
from pathlib import Path
import tempfile
import unittest
from cryptography import x509
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import padding

ROOT = Path(__file__).resolve().parents[1]


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, ROOT / "scripts" / filename)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


tls = load("tls_generator", "create-mqtt-lan-tls.py")
personal = load("personal_config", "configure-personal-esp32.py")


class PersonalTlsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.directory = Path(cls.temp.name) / "tls"
        tls.create(cls.directory, "192.168.0.50")
        cls.ca = (cls.directory / "certs/ca.crt").read_bytes()

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def test_private_ipv4_only(self):
        for value in ("127.0.0.1", "0.0.0.0", "8.8.8.8", "224.0.0.1", "::1", "host:8883"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                tls.lan_address(value)

    def test_ca_signs_leaf_with_exact_ip_and_server_usage(self):
        ca = x509.load_pem_x509_certificate(self.ca)
        leaf = x509.load_pem_x509_certificate((self.directory / "certs/server.crt").read_bytes())
        ca.public_key().verify(leaf.signature, leaf.tbs_certificate_bytes, padding.PKCS1v15(), hashes.SHA256())
        self.assertTrue(ca.extensions.get_extension_for_class(x509.BasicConstraints).value.ca)
        self.assertFalse(leaf.extensions.get_extension_for_class(x509.BasicConstraints).value.ca)
        self.assertEqual([ipaddress.IPv4Address("192.168.0.50")],
            leaf.extensions.get_extension_for_class(x509.SubjectAlternativeName).value.get_values_for_type(x509.IPAddress))
        self.assertIn(x509.oid.ExtendedKeyUsageOID.SERVER_AUTH,
                      leaf.extensions.get_extension_for_class(x509.ExtendedKeyUsage).value)

    def test_no_overwrite_and_authority_is_separate(self):
        original = (self.directory / "certs/server.key").read_bytes()
        with self.assertRaises(FileExistsError):
            tls.create(self.directory, "192.168.0.51")
        self.assertEqual(original, (self.directory / "certs/server.key").read_bytes())
        self.assertTrue((self.directory / "authority/ca.key").is_file())
        self.assertFalse((self.directory / "certs/ca.key").exists())

    def test_personal_header_requires_tls_and_contains_no_private_key(self):
        wifi = {"ssid": "fixture-wifi", "password": "fixture-password", "authMode": "wpa2"}
        device = {"deviceId": "esp32-bench", "username": "esp32-bench", "password": "fixture-password-only"}
        header = personal.header(wifi, device, "192.168.0.50", self.ca)
        self.assertIn("mqtts://192.168.0.50:8883", header)
        self.assertIn("BEGIN CERTIFICATE", header)
        self.assertNotIn("PRIVATE KEY", header)
        self.assertIn("HWM_WIFI_OPEN 0", header)
        self.assertIn("HWM_SAMPLE_TARGET 60", header)

    def test_open_wifi_identity_injection_and_leaf_trust_are_rejected(self):
        wifi = {"ssid": "fixture", "password": "fixture-password"}
        device = {"deviceId": "esp32-bench", "username": "esp32-bench", "password": "fixture-password-only"}
        for change in ({"deviceId": "other/#"}, {"username": "x\n#define X 1"}, {"password": ""}):
            with self.assertRaises(ValueError):
                personal.header(wifi, device | change, "192.168.0.50", self.ca)
        with self.assertRaises(ValueError):
            personal.header({"ssid": "fixture", "password": "", "authMode": "open"}, device, "192.168.0.50", self.ca)
        with self.assertRaises(ValueError):
            personal.header(wifi, device, "192.168.0.50", (self.directory / "certs/server.crt").read_bytes())


if __name__ == "__main__":
    unittest.main()
