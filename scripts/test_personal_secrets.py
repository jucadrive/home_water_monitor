import importlib.util
from pathlib import Path
import tempfile
import unittest
import bcrypt

spec = importlib.util.spec_from_file_location("personal_secrets", Path(__file__).with_name("create-personal-secrets.py"))
setup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(setup)


class SecretSetupTest(unittest.TestCase):
    def test_creates_hash_and_random_database_password_without_persisting_password(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory)
            setup.create(target, "fixture-owner", "fixture-password-only", "fixture-token")
            auth = (target / "auth.env").read_text()
            self.assertNotIn("fixture-password-only", auth)
            values = dict(line.split("=", 1) for line in auth.splitlines())
            self.assertTrue(bcrypt.checkpw(b"fixture-password-only", values["PPOSSAK_AUTH_PASSWORD_HASH"].encode()))
            self.assertEqual("fixture-token", (target / "tunnel.token").read_text())
            self.assertIn("DB_PASSWORD=", (target / "postgres.env").read_text())

    def test_refuses_overwrite_and_invalid_input(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory)
            (target / "auth.env").write_text("keep")
            with self.assertRaises(FileExistsError):
                setup.create(target, "owner", "fixture-password-only")
            self.assertEqual("keep", (target / "auth.env").read_text())
            self.assertFalse((target / "postgres.env").exists())
            with self.assertRaises(ValueError):
                setup.create(target, "owner", "short")
            with self.assertRaises(ValueError):
                setup.create(target, "bad\nname", "fixture-password-only")


if __name__ == "__main__":
    unittest.main()
