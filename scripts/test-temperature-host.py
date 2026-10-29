"""Execute production C math/session code in a network-isolated pinned GCC image.

No main/private firmware configuration is mounted. Outputs go to an ignored folder.
"""
from decimal import Decimal, ROUND_HALF_UP
import argparse
import json
from pathlib import Path
import random
import subprocess

IMAGE = "gcc@sha256:4e2d22617a3b4af1ad0150ba6ca3cb8a975c44c1abac6f9a09870b93cb5c363b"
ROOT = Path(__file__).resolve().parents[1]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / ".tools/temperature/host")
    args = parser.parse_args()
    output = args.output.resolve(); output.mkdir(parents=True, exist_ok=True)
    base = ["docker", "run", "--rm", "--network", "none", "--cap-drop", "ALL",
            "--security-opt", "no-new-privileges", "--mount", f"type=bind,source={output},target=/out"]
    def run(extra, text=None):
        return subprocess.run(base + (["-i"] if text is not None else []) + extra,
                              input=text, text=True, capture_output=True, check=True).stdout
    mounts = []
    for name in ("sensor", "session"):
        mounts += ["--mount", f"type=bind,source={ROOT / 'firmware/esp32/components' / name},target=/{name},readonly"]
    mounts += ["--mount", f"type=bind,source={ROOT / 'firmware/esp32/test/temperature_host.c'},target=/test.c,readonly"]
    run(mounts + [IMAGE, "gcc", "-std=c11", "-Wall", "-Wextra", "-Werror", "-fsanitize=undefined,address", "-g",
                  "-I/sensor/include", "-I/session/include", "/sensor/tds_math.c", "/sensor/temperature.c",
                  "/sensor/tds_sensor.c", "/session/session.c", "/session/control.c", "/session/preservation.c",
                  "/test.c", "-lm", "-o", "/out/temperature-host"])
    invariants = run([IMAGE, "/out/temperature-host"])
    assert "TEMPERATURE_HOST_INVARIANTS_PASS" in invariants
    rng = random.Random(20261028)
    vectors = [(n, t) for n in [0, 1, 4000, 6000, 7999, 8000, 8001, 12000, 16000, 18400]
               for t in [0, 625, 100000, 200000, 250000, 300000, 400000, 500000]]
    vectors += [(rng.randrange(18401), rng.randrange(801)*625) for _ in range(2000)]
    actual = run([IMAGE, "/out/temperature-host", "--vectors"], "".join(f"{n} {t}\n" for n,t in vectors)).splitlines()
    assert len(actual) == len(vectors)
    for (raw, temperature), line in zip(vectors, actual):
        v = Decimal(raw) / 8000 / (1 + Decimal('.02') * (Decimal(temperature)/10000-25))
        ppm = (Decimal('133.42')*v**3-Decimal('255.86')*v**2+Decimal('857.39')*v)/2
        valid = 0 <= ppm <= 1000
        ok, value, voltage = map(int, line.split())
        assert bool(ok) == valid, (raw, temperature, "range")
        if valid:
            assert value == int((ppm*100).quantize(Decimal(1), rounding=ROUND_HALF_UP)), (raw,temperature,"ppm")
            assert voltage == int((Decimal(raw)*Decimal('12.5')).quantize(Decimal(1), rounding=ROUND_HALF_UP))
    files = []
    for temperature in [200000,250000,300000]:
        for prefix in [0,1,59,60]:
            text = run([IMAGE,"/out/temperature-host","--payload",str(temperature),str(prefix)])
            payload = json.loads(text)
            assert len(payload['measurements']) == prefix and payload['schemaVersion'] == 2
            path = output / f"c-temperature-{temperature}-prefix-{prefix}.json"
            path.write_text(text, encoding="utf-8"); files.append(path.name)
    result = {"status":"PASS", "decimal_vectors":len(vectors),"incomplete_prefixes":60,
              "sanitizers":"address,undefined", "c_payloads":files, "image":IMAGE}
    (output/"result.json").write_text(json.dumps(result,indent=2),encoding="utf-8")
    print(json.dumps(result))

if __name__ == "__main__": main()
