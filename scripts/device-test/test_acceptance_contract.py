"""Independent wire contract limits used by the physical acceptance observer."""
import unittest
from accept import valid_wire_metadata


class WireContractTest(unittest.TestCase):
    def test_payload_boundary(self):
        for size, expected in ((16383, True), (16384, True), (16385, False)):
            with self.subTest(size=size):
                self.assertEqual(expected, valid_wire_metadata(dict(qos=1, retained=False, pfi=True,
                    contentType="application/json", payload=b" " * size)))

    def test_invalid_transport_metadata(self):
        base = dict(qos=1, retained=False, pfi=True, contentType="application/json", payload=b"{}")
        for fields in (dict(qos=0), dict(retained=True), dict(pfi=False), dict(contentType="")):
            with self.subTest(fields=fields):
                self.assertFalse(valid_wire_metadata(dict(base, **fields)))


if __name__ == "__main__":
    unittest.main()
