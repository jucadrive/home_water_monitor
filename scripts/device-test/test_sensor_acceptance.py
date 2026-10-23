"""The real-sensor observer rejects simulation, wrong calculations, and substituted samples."""
import copy
from decimal import Decimal
import unittest
from sensor_accept import sensor_values, CALIBRATION, AcceptanceFailure


class SensorWireTest(unittest.TestCase):
    def body(self):
        return {"calibrationVersion": CALIBRATION, "temperatureAssumedC": 25,
                "measurements": [{"seq": 1, "elapsedMs": 1000, "isWarmup": True,
                                  "tdsPpm": Decimal("367.48"), "rawVoltageMv": Decimal("1000.00")} ]}

    def test_real_and_empty_prefix(self):
        self.assertEqual(1, len(sensor_values(self.body())))
        b = self.body(); b["measurements"] = []
        self.assertEqual([], sensor_values(b))

    def test_source_and_temperature(self):
        for changes in ({"calibrationVersion": "simulated-v1"}, {"temperatureAssumedC": 26}):
            with self.subTest(changes=changes), self.assertRaises(AcceptanceFailure):
                sensor_values(dict(self.body(), **changes))

    def test_invalid_measurements(self):
        for changes in ({"tdsPpm": Decimal("367.47")}, {"rawVoltageMv": Decimal("1000.01")},
                        {"seq": 2}, {"elapsedMs": 999}, {"elapsedMs": 1251}, {"isWarmup": False},
                        {"tdsPpm": Decimal("1000.01")}, {"rawVoltageMv": Decimal("2300.01")}):
            b = copy.deepcopy(self.body()); b["measurements"][0].update(changes)
            with self.subTest(changes=changes), self.assertRaises(AcceptanceFailure):
                sensor_values(b)

    def test_one_count_rounding(self):
        b = self.body(); b["measurements"][0].update(tdsPpm=Decimal("0.05"), rawVoltageMv=Decimal("0.13"))
        self.assertEqual(1, len(sensor_values(b)))


if __name__ == "__main__":
    unittest.main()
