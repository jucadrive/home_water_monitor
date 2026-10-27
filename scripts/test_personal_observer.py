import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("personal_observer", Path(__file__).with_name("observe-personal-esp32.py"))
observer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(observer)


class ObserverEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.samples = [f"HWM_SENSOR_SAMPLE seq={seq} tds_hundredths=30000 voltage_hundredths=75000" for seq in range(1, 61)]
        self.release = "HWM_BUFFER_RELEASED session=11111111-1111-4111-8111-111111111111 samples=0"
        self.idle = {"state": "IDLE", "session": "none", "samples": "0"}

    def test_sixty_samples_release_and_empty_buffer_are_all_required(self):
        self.assertTrue(observer.summarize(self.samples + [self.release], self.idle)["uartNormalFlowVerified"])
        self.assertFalse(observer.summarize(self.samples, self.idle)["uartNormalFlowVerified"])
        self.assertFalse(observer.summarize(self.samples[:-1] + [self.release], self.idle)["uartNormalFlowVerified"])

    def test_duplicate_or_extra_sample_does_not_pass(self):
        duplicate = self.samples[:-1] + [self.samples[-2], self.release]
        self.assertFalse(observer.summarize(duplicate, self.idle)["uartNormalFlowVerified"])
        self.assertFalse(observer.summarize(self.samples + self.samples + [self.release], self.idle)["uartNormalFlowVerified"])

    def test_fault_or_unreleased_buffer_does_not_pass(self):
        self.assertFalse(observer.summarize(self.samples + [self.release, "HWM_SENSOR_FAULT category=fixture"], self.idle)["uartNormalFlowVerified"])
        pending = {"state": "MANUAL_WAIT", "session": "11111111-1111-4111-8111-111111111111", "samples": "60"}
        self.assertFalse(observer.summarize(self.samples + [self.release], pending)["uartNormalFlowVerified"])


if __name__ == "__main__":
    unittest.main()
