"""Release only a matching retained bench buffer, using independently verified committed DB preservation."""
import json
import time
from accept import require
from bench import CACHE, credentials, publish, sql
from fault_proxy import FaultProxy
from server import start
from uart import Uart


def main():
    start()
    with FaultProxy(host=credentials()["lanIp"]):
        uart = Uart()
        try:
            status = uart.refresh()
            if status.get("state") == "IDLE":
                require(status.get("samples") == "0", "Idle buffer is not empty")
                print("BUFFER RECOVERY: already idle, no message sent")
                return
            import uuid
            session = str(uuid.UUID(status["session"]))
            count = int(status["samples"])
            require(status.get("finalized") == "1", "Buffer is not finalized")
            expected = "NORMAL" if count == 60 else "INCOMPLETE"
            require(sql("SELECT preserved_sample_count || ':' || preservation_result FROM tds_session WHERE session_id='" + session + "'") == str(count) + ":" + expected,
                    "Committed preservation does not match the device")
            require(sql("SELECT count(*) FROM tds_measurement WHERE session_id='" + session + "'") == str(count), "Committed originals do not match the count")
            deadline = time.monotonic() + 60
            while uart.status.get("rx_ready") != "1" and time.monotonic() < deadline:
                uart.refresh()
                time.sleep(0.2)
            require(uart.status.get("rx_ready") == "1", "Recovery receiver not ready")
            confirmation = dict(schemaVersion=1, eventType="SESSION_PRESERVED", deviceId="esp32-bench", sessionId=session,
                                preservationResult=expected, preservedSampleCount=count, comparison=None)
            require(publish(confirmation), "Committed confirmation recovery publish failed")
            deadline = time.monotonic() + 40
            released = False
            while time.monotonic() < deadline:
                released |= any(line.startswith("HWM_BUFFER_RELEASED session=" + session + " ") for line in uart.receive())
                if released: break
            status = uart.refresh()
            require(released and status.get("state") == "IDLE" and status.get("samples") == "0", "Committed confirmation did not release the buffer")
            (CACHE / "buffer-recovery.json").write_text(json.dumps({"sessionId": session, "dbCommitVerified": True, "deviceReleased": True, "status": status}, indent=2), encoding="utf-8")
            print("BUFFER RECOVERY PASS: DB preservation verified, device idle and empty")
        finally:
            uart.close()


if __name__ == "__main__":
    try:
        main()
    except Exception as failure:
        print("BUFFER RECOVERY FAILED category=" + type(failure).__name__)
        raise SystemExit(1)
