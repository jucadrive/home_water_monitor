"""Interactive local setup. No passwords, hashes, or tunnel tokens are printed.

Requires Python 3 and bcrypt (already present on the development PC).
Generated files are for manual transfer to the user's dedicated server only.
"""
import argparse
import getpass
import os
from pathlib import Path
import re
import secrets

import bcrypt


def write_new(path: Path, text: str) -> None:
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as stream:
        stream.write(text)


def create(directory: Path, username: str, password: str, tunnel_token: str | None = None) -> list[Path]:
    if not re.fullmatch(r"[A-Za-z0-9_-]{1,64}", username):
        raise ValueError("Invalid account name")
    if not 12 <= len(password.encode("utf-8")) <= 72:
        raise ValueError("Use a password with 12 to 72 UTF-8 bytes")
    if tunnel_token is not None and (not tunnel_token or not re.fullmatch(r"[A-Za-z0-9_+=/.-]+", tunnel_token)):
        raise ValueError("Invalid tunnel token format")
    directory = directory.resolve()
    directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    names = ["auth.env", "postgres.env"] + (["tunnel.token"] if tunnel_token is not None else [])
    if any((directory / name).exists() for name in names):
        raise FileExistsError("Refusing to overwrite existing secret files; select a new directory")
    password_hash = bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt(rounds=12)).decode("ascii")
    content = {
        "auth.env": f"PPOSSAK_AUTH_USERNAME={username}\nPPOSSAK_AUTH_PASSWORD_HASH={password_hash}\n",
        "postgres.env": "DB_NAME=ppossak\nDB_USERNAME=ppossak\nDB_PASSWORD=" + secrets.token_urlsafe(32) + "\nPPOSSAK_DB_PORT=15433\n",
    }
    if tunnel_token is not None:
        content["tunnel.token"] = tunnel_token
    created = []
    try:
        for name, text in content.items():
            path = directory / name
            write_new(path, text)
            created.append(path)
    except Exception:
        for path in created:
            path.unlink()
        raise
    return created


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", type=Path, default=Path(".tools/deployment/secrets"))
    parser.add_argument("--username", default="owner")
    parser.add_argument("--with-tunnel-token", action="store_true")
    args = parser.parse_args()
    password = getpass.getpass("App password (not SSH password): ")
    confirmation = getpass.getpass("Confirm app password: ")
    if password != confirmation:
        parser.exit(1, "Passwords do not match; no files written.\n")
    token = getpass.getpass("Cloudflare Tunnel token: ") if args.with_tunnel_token else None
    try:
        paths = create(args.directory, args.username, password, token)
    except (ValueError, FileExistsError, OSError):
        parser.exit(1, "Secret setup failed. Check input and use a new private output directory.\n")
    print("Created private configuration files (contents hidden):")
    for path in paths:
        print(path)
    print("Transfer manually; set server owner root and permissions 600. Never commit these files.")


if __name__ == "__main__":
    main()
