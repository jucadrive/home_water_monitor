import json
import time
import unittest
import uuid
from bench import Observer, publish
from fault_proxy import FaultProxy, variable_integer, take_packet, connect_client_id, publish_parts


class FramingTests(unittest.TestCase):
    def test_variable_integer_boundaries(self):
        for encoded, value in ((b"\x00", 0), (b"\x7f", 127), (b"\x80\x01", 128), (b"\xff\xff\xff\x7f", 268435455)):
            self.assertEqual((value, len(encoded)), variable_integer(encoded))

    def test_fragmented_integer(self):
        self.assertIsNone(variable_integer(b"\x80"))

    def test_invalid_integer(self):
        for encoded in (b"\xff\xff\xff\xff", b"\x80\x00"):
            with self.assertRaises(ValueError): variable_integer(encoded)

    def test_multiple_packets_and_partial(self):
        data = bytearray(b"\xc0\x00\x40\x02\x00")
        self.assertEqual((b"\xc0\x00", 2), take_packet(data))
        self.assertIsNone(take_packet(data))
        data.append(1)
        self.assertEqual((b"\x40\x02\x00\x01", 2), take_packet(data))
        self.assertEqual(0, len(data))

    def test_packet_limit(self):
        with self.assertRaises(ValueError): take_packet(bytearray(b"\x30\x81\x80\x04"))

    def test_connect(self):
        body = b"\x00\x04MQTT\x05\xc2\x00\x1e\x00\x00\x04test\x00\x04user\x00\x04pass"
        self.assertEqual("test", connect_client_id(body))

    def test_invalid_connect(self):
        for body in (b"", b"\x00\x04MQTT\x04\x00\x00\x1e\x00\x00\x04test", b"\x00\x04MQTT\x05\x00\x00\x1e\x00\x00\xffx"):
            with self.assertRaises(ValueError): connect_client_id(body)

    def test_publish_properties(self):
        packet_id, payload = publish_parts(0x32, b"\x00\x01t\x00\x07\x02\x01\x01{}")
        self.assertEqual(b"\x00\x07", packet_id)
        self.assertEqual(b"{}", payload)

    def test_invalid_publish(self):
        for body in (b"", b"\x00\x05a", b"\x00\x01t", b"\x00\x01t\x00\x07\x05\x01"):
            with self.assertRaises(ValueError): publish_parts(0x32, body)

    def test_no_public_listener(self):
        for host in ("0.0.0.0", "8.8.8.8", "169.254.1.2"):
            with self.assertRaises(ValueError): FaultProxy(host=host)


class BrokerFaultTests(unittest.TestCase):
    def setUp(self):
        self.tx_id = "probe-tx-" + uuid.uuid4().hex
        self.rx_id = "probe-rx-" + uuid.uuid4().hex
        self.proxy = FaultProxy(port=0, tx_id=self.tx_id, rx_id=self.rx_id).__enter__()
        self.uri = "tcp://127.0.0.1:" + str(self.proxy.port)

    def tearDown(self):
        self.proxy.__exit__()

    def send(self):
        return publish({"probe": True}, topic="home/water/v1/esp32-bench/events", role="device", broker_uri=self.uri, client_id=self.tx_id)

    def test_pass_and_payload_fingerprint(self):
        self.assertTrue(self.send())
        self.assertEqual(1, len(self.proxy.publications))
        self.assertEqual(64, len(self.proxy.publications[0]["sha256"]))
        self.assertEqual(1, self.proxy.tx_connections)
        self.assertEqual(1, self.proxy.publications[0]["connection"])
        self.assertFalse(self.proxy.publications[0]["duplicate"])

    def test_rejected_puback(self):
        self.proxy.set_mode("reject-puback")
        self.assertFalse(self.send())
        self.assertEqual(1, self.proxy.rejected)

    def test_missing_puback(self):
        self.proxy.set_mode("drop-puback")
        self.assertFalse(self.send())
        self.assertGreaterEqual(self.proxy.pubacks_dropped, 1)

    def test_lost_confirmation_is_acked_then_pass_recovers(self):
        observer = Observer(seconds=20, broker_uri=self.uri, client_id=self.rx_id)
        try:
            self.proxy.set_mode("drop-confirmation")
            self.assertTrue(publish({"probe": "lost"}))
            deadline = time.monotonic() + 2
            while time.monotonic() < deadline:
                self.assertIsNone(observer.receive(0.1))
            self.assertEqual(1, self.proxy.confirmations_dropped)
            self.proxy.set_mode("pass")
            self.assertTrue(publish({"probe": "received"}))
            deadline = time.monotonic() + 3
            wire = None
            while time.monotonic() < deadline and wire is None:
                wire = observer.receive()
            self.assertIsNotNone(wire)
            self.assertEqual({"probe": "received"}, json.loads(wire["payload"]))
        finally:
            observer.close()


if __name__ == "__main__":
    unittest.main()
