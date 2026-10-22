"""Run bench-tool tests and a PC-simulated server smoke, preserving an existing bench server."""
from pathlib import Path
import sys
import unittest
from server import owned_process, start, stop
import smoke


def main():
    already_running = owned_process() is not None
    try:
        start()
        suite = unittest.defaultTestLoader.discover(str(Path(__file__).parent), pattern="test_*.py")
        if suite.countTestCases() == 0:
            raise RuntimeError("No host tests found")
        result = unittest.TextTestRunner(verbosity=1).run(suite)
        print(f"HOST_TEST_RESULT tests={result.testsRun} failed={len(result.failures)} errors={len(result.errors)} skipped={len(result.skipped)}")
        if not result.wasSuccessful() or result.skipped:
            raise RuntimeError("Host tests did not meet acceptance")
        smoke.main()
    finally:
        if not already_running:
            stop()
    print("ESP32 HOST VERIFICATION PASS (physical MQTT acceptance is separate)")


if __name__ == "__main__":
    try:
        main()
    except Exception:
        print("ESP32 HOST VERIFICATION FAILED; secret/raw logs withheld", file=sys.stderr)
        sys.exit(1)
