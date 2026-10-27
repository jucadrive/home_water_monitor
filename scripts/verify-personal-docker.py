"""Isolated Docker acceptance; never opens real deployment secrets or a remote server.

Requires Docker Compose >=2.30, Python bcrypt and a previously verified backend JAR.
Only the randomly named test project's containers/volumes are removed on exit.
"""
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
PROJECT = "ppossak-check-" + uuid.uuid4().hex[:12]
WORK = ROOT / ".tools" / "deployment" / PROJECT
PASSWORD = "docker-fixture-password-only"
checks = []
token_volume = None


def run(*args, check=True, data=None, timeout=300):
    result = subprocess.run(args, cwd=WORK, text=True, encoding="utf-8", input=data,
                            capture_output=True, timeout=timeout)
    if check and result.returncode:
        # Do not emit command output: compose can include resolved environment values.
        raise RuntimeError(f"Command failed ({result.returncode}): {args[0]}")
    return result


def compose(*args, **kwargs):
    return run("docker", "compose", "-p", PROJECT, "--env-file", "secrets/postgres.env", *args, **kwargs)


def passed(name):
    checks.append(name)
    print("PASS " + name, flush=True)


def request(path, body=None, token=None, key=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    if key:
        headers["Idempotency-Key"] = key
    req = urllib.request.Request(BASE + path, headers=headers,
                                 data=json.dumps(body).encode() if body is not None else None)
    try:
        response = urllib.request.urlopen(req, timeout=10)
    except urllib.error.HTTPError as error:
        response = error
    raw = response.read()
    return response.status, json.loads(raw) if raw else None


def login():
    status, body = request("/api/auth/login", {"username": "fixture-owner", "password": PASSWORD})
    assert status == 200
    return body["accessToken"]


WORK.mkdir(parents=True)
for name in ("compose.yml", "Dockerfile", "Healthcheck.java", ".dockerignore"):
    shutil.copy2(ROOT / "infra/personal" / name, WORK / name)
shutil.copy2(ROOT / "backend/home-water-monitor-backend/build/libs/home-water-monitor-backend-0.0.1-SNAPSHOT.jar",
             WORK / "app.jar")
spec = importlib.util.spec_from_file_location("secret_setup", ROOT / "scripts/create-personal-secrets.py")
setup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(setup)
setup.create(WORK / "secrets", "fixture-owner", PASSWORD, "invalid.fixture.token")
(WORK / "compose.override.yml").write_text(
    'services:\n  backend:\n    ports: ["127.0.0.1::8080"]\n', encoding="utf-8")

try:
    config = json.loads(compose("-f", "compose.yml", "config", "--format", "json").stdout)
    assert set(config["services"]) == {"backend", "postgres", "cloudflared"}
    assert all(not service.get("ports") for service in config["services"].values())
    expected = next(line.split("=", 1)[1] for line in
                    (WORK / "secrets/auth.env").read_text().splitlines()
                    if line.startswith("PPOSSAK_AUTH_PASSWORD_HASH="))
    rendered = config["services"]["backend"]["environment"]["PPOSSAK_AUTH_PASSWORD_HASH"]
    # Compose config escapes '$' for reusable output; inspect actual runtime below.
    assert rendered.replace("$$", "$") == expected
    passed("three services and no published production ports")
    print("Building isolated image and starting DB/backend...", flush=True)
    compose("up", "-d", "--build", "--wait", "--wait-timeout", "180", "backend", timeout=600)
    assert compose("exec", "-T", "backend", "printenv", "PPOSSAK_AUTH_PASSWORD_HASH").stdout.strip() == expected
    passed("image build, migrations, DB/backend healthy")
    address = compose("port", "backend", "8080").stdout.strip()
    assert address.startswith("127.0.0.1:")
    BASE = "http://" + address
    status, body = request("/api/cultivations")
    assert status == 401 and body["code"] == "AUTH_REQUIRED"
    assert request("/api/auth/login", {"username": "fixture-owner", "password": "wrong"})[0] == 401
    token = login()
    passed("unauthenticated JSON 401, wrong password denied, real login")
    key = str(uuid.uuid4())
    record = {"plantName": "fixture basil", "name": "docker acceptance", "startedAt": "2026-10-27T00:00:00Z", "memo": "isolated test only"}
    status, body = request("/api/cultivations", record, token, key)
    assert status == 201 and body["id"] == key
    assert request("/api/cultivations/" + key, token=token)[0] == 200
    assert request("/api/cultivations", record, token, key)[0] == 200
    passed("authenticated create/read and same-key retry")
    image = config["services"]["backend"]["image"]
    run("docker", "run", "--rm", "--network", PROJECT + "_default", "--entrypoint", "java",
        image, "-cp", "/app", "Healthcheck", "http://backend:8080/api/cultivations")
    passed("another container reaches backend by Compose service name")
    compose("restart", "postgres", "backend")
    deadline = time.monotonic() + 120
    while True:
        states = json.loads(compose("ps", "--format", "json", "backend").stdout)
        if isinstance(states, list):
            states = states[0]
        if states.get("Health") == "healthy":
            break
        if time.monotonic() >= deadline:
            raise RuntimeError("Backend did not recover after restart")
        time.sleep(2)
    # Docker Desktop can allocate a new ephemeral host port after restart.
    address = compose("port", "backend", "8080").stdout.strip()
    assert address.startswith("127.0.0.1:")
    BASE = "http://" + address
    assert request("/api/cultivations/" + key, token=token)[0] == 401
    token = login()
    assert request("/api/cultivations/" + key, token=token)[1]["cultivation"]["id"] == key
    passed("DB/backend restart preserves data and invalidates old tokens")
    dump = compose("exec", "-T", "postgres", "pg_dump", "-U", "ppossak", "-d", "ppossak").stdout
    compose("exec", "-T", "postgres", "createdb", "-U", "ppossak", "restore_check")
    compose("exec", "-T", "postgres", "psql", "-v", "ON_ERROR_STOP=1", "-U", "ppossak", "-d", "restore_check", data=dump)
    count = compose("exec", "-T", "postgres", "psql", "-U", "ppossak", "-d", "restore_check", "-Atc",
                    "SELECT count(*) FROM cultivation WHERE cultivation_id='" + key + "'").stdout.strip()
    assert count == "1"
    passed("pg_dump restores into separate test database with record intact")
    help_text = compose("run", "--rm", "--no-deps", "cloudflared", "tunnel", "run", "--help").stdout
    assert "--token-file" in help_text
    # No network and fake token: verifies image/user/mount/flag without contacting Cloudflare.
    probe = run("docker", "run", "--rm", "--network", "none", "--user", "65532:65532",
                "--mount", f"type=bind,source={WORK / 'secrets/tunnel.token'},target=/run/secrets/tunnel.token,readonly",
                config["services"]["cloudflared"]["image"], "tunnel", "--no-autoupdate", "run",
                "--token-file", "/run/secrets/tunnel.token", check=False)
    message = (probe.stdout + probe.stderr).lower()
    assert probe.returncode != 0 and "token" in message and "permission denied" not in message and "no such file" not in message
    passed("cloudflared token-file option and non-root mount read (offline fake token)")
    token_volume = PROJECT + "_token_permissions"
    run("docker", "volume", "create", token_volume)
    run("docker", "run", "--rm", "--network", "none", "-v", token_volume + ":/fixture",
        "--entrypoint", "sh", config["services"]["postgres"]["image"], "-c",
        "printf invalid.fixture.token > /fixture/tunnel.token; chown 0:65532 /fixture/tunnel.token; chmod 640 /fixture/tunnel.token")
    probe = run("docker", "run", "--rm", "--network", "none", "--user", "65532:65532",
        "-v", token_volume + ":/run/secrets:ro", config["services"]["cloudflared"]["image"],
        "tunnel", "--no-autoupdate", "run", "--token-file", "/run/secrets/tunnel.token", check=False)
    message = (probe.stdout + probe.stderr).lower()
    assert probe.returncode != 0 and "token" in message and "not valid" in message
    passed("Linux root:65532 mode640 token readable by non-root cloudflared (offline)")
finally:
    cleanup = compose("down", "--volumes", "--remove-orphans", check=False)
    if token_volume:
        run("docker", "volume", "rm", token_volume)
    (WORK / "result.json").write_text(json.dumps({"project": PROJECT, "checks": checks,
        "cleanupExitCode": cleanup.returncode, "publicHTTPS": "NOT RUN"}, indent=2), encoding="utf-8")
    if cleanup.returncode:
        raise RuntimeError("Isolated project cleanup failed")
    print("Isolated containers/volume removed; result: " + str(WORK / "result.json"), flush=True)
