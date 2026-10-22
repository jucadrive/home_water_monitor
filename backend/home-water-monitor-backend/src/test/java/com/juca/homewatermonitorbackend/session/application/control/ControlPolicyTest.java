package com.juca.homewatermonitorbackend.session.application.control;

import com.juca.homewatermonitorbackend.session.infrastructure.mqtt.ControlMessageCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ControlPolicyTest {
    final ControlMessageCodec codec = new ControlMessageCodec();
    byte[] example() throws Exception {
        return Files.readAllBytes(Path.of("../../docs/mqtt/v1/examples/device-status-idle.json"));
    }
    @Test void probeAndLivenessBoundariesAndExecutionMode() throws Exception {
        var s = codec.decodeStatus(example(), "esp32-bench");
        assertTrue(ControlPolicy.freshProbe(s, s.replyToProbeId(), 4999, 5000));
        assertFalse(ControlPolicy.freshProbe(s, s.replyToProbeId(), 5000, 5000));
        assertFalse(ControlPolicy.freshProbe(s, UUID.randomUUID(), 0, 5000));
        assertTrue(ControlPolicy.executable(s, "START", null));
        assertFalse(ControlPolicy.executable(s, "RETRY", UUID.randomUUID()));
        assertEquals("FRESH", ControlPolicy.liveness(true, 45000));
        assertEquals("STALE", ControlPolicy.liveness(true, 45001));
        assertEquals("UNKNOWN", ControlPolicy.liveness(false, 0));
    }
    @ParameterizedTest @ValueSource(strings = {"schemaVersion", "bootId", "statusSeq", "fault", "lastRelease", "commandResult", "finalized"})
    void missingEvenNullableFieldsAreRejected(String field) throws Exception {
        var n = (ObjectNode) codec.tree(example()); n.remove(field);
        assertThrows(IllegalArgumentException.class, () -> codec.decodeStatus(codec.encode(n), "esp32-bench"));
    }
    @Test void rejectsCoercionUnknownPropertiesDuplicatesAndInvalidSemantics() throws Exception {
        var base = (ObjectNode) codec.tree(example());
        var variants = new ObjectNode[] {base.deepCopy().put("statusSeq", "1"), base.deepCopy().put("statusSeq", 1.1),
                base.deepCopy().put("unexpected", 1), base.deepCopy().put("sampleCount", 1),
                base.deepCopy().put("controlReady", false), base.deepCopy().put("state", "wrong"),
                base.deepCopy().put("controlMode", "UART"), base.deepCopy().put("bootId", UUID.randomUUID().toString().replace("-", ""))};
        for (var n : variants) assertThrows(IllegalArgumentException.class, () -> codec.decodeStatus(codec.encode(n), "esp32-bench"));
        var duplicate = ("{\"statusSeq\":2," + new String(example(), java.nio.charset.StandardCharsets.UTF_8).substring(1)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> codec.decodeStatus(duplicate, "esp32-bench"));
    }
    @Test void sequenceAndBootNeverGoBackwards() throws Exception {
        var base = (ObjectNode) codec.tree(example()); var current = codec.decodeStatus(codec.encode(base), "esp32-bench");
        assertFalse(ControlPolicy.newer(current, current));
        assertTrue(ControlPolicy.newer(current, codec.decodeStatus(codec.encode(base.put("statusSeq", 2)), "esp32-bench")));
        assertFalse(ControlPolicy.newer(current, codec.decodeStatus(codec.encode(base.put("bootId", UUID.randomUUID().toString())), "esp32-bench")));
    }
}
