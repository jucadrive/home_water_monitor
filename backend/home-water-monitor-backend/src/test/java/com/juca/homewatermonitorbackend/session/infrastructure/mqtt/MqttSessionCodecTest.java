package com.juca.homewatermonitorbackend.session.infrastructure.mqtt;

import com.juca.homewatermonitorbackend.session.application.TdsSessionInputValidator;
import com.juca.homewatermonitorbackend.session.application.TdsSessionProcessingResult;
import com.juca.homewatermonitorbackend.session.application.TdsSessionRedeliveryResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MqttSessionCodecTest {
    static final String DEVICE = "esp32-water-01";
    static final String TOPIC = "home/water/v1/" + DEVICE + "/events";
    static final JsonMapper JSON = JsonMapper.builder().build();
    final MqttSessionCodec codec = new MqttSessionCodec(new TdsSessionInputValidator());

    static byte[] example(String name) throws Exception {
        return Files.readAllBytes(Path.of("../../docs/mqtt/v1/examples/" + name + ".json"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"normal", "incomplete", "empty"})
    void decodesContractExamples(String suffix) throws Exception {
        var command = codec.decode(example("session-result-" + suffix), TOPIC, Set.of(DEVICE));
        assertEquals(suffix.equals("normal") ? 60 : suffix.equals("empty") ? 0 : 1, command.measurements().size());
        assertEquals(DEVICE, command.deviceId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"normal", "incomplete", "empty", "identical", "conflict", "empty-redelivery"})
    void encodesExactlyTheConfirmationExamples(String suffix) throws Exception {
        var expected = JSON.readTree(example("session-preserved-" + suffix));
        var id = UUID.fromString(expected.get("sessionId").asString());
        int count = expected.get("preservedSampleCount").intValue();
        String preserved = expected.get("preservationResult").asString();
        var comparison = expected.get("comparison");
        var redelivery = comparison.isNull() ? null : new TdsSessionRedeliveryResult(UUID.randomUUID(), id,
                preserved, count, comparison.get("identicalCount").intValue(), comparison.get("conflictCount").intValue());
        var result = new TdsSessionProcessingResult(id, preserved, count, redelivery == null
                ? TdsSessionProcessingResult.Path.SAVED_NEW : TdsSessionProcessingResult.Path.PROCESSED_REDELIVERY, redelivery);
        assertEquals(expected, JSON.readTree(codec.encode(DEVICE, result)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"schemaVersion", "eventType", "deviceId", "sessionId", "startedAt", "expectedSamples",
            "samplingIntervalMs", "temperatureAssumedC", "calibrationVersion", "measurements"})
    void rejectsMissingRequiredFields(String field) throws Exception {
        var root = (tools.jackson.databind.node.ObjectNode) JSON.readTree(example("session-result-empty"));
        root.remove(field);
        assertInvalid(JSON.writeValueAsBytes(root));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "[]", "{}", "null", "NaN", "{} {}", "\uFEFF{}"})
    void rejectsInvalidJson(String json) { assertInvalid(json.getBytes(StandardCharsets.UTF_8)); }

    @Test
    void rejectsDuplicateKeysExtraFieldsTypeCoercionAndSemanticErrors() throws Exception {
        String base = new String(example("session-result-incomplete"), StandardCharsets.UTF_8);
        for (String changed : new String[] {
                base.replaceFirst("\\{", "{\"schemaVersion\":1,"),
                base.replaceFirst("\\{", "{\"unknown\":1,"),
                base.replace("\"schemaVersion\": 1", "\"schemaVersion\": \"1\""),
                base.replace("\"seq\": 1", "\"seq\": 1.5"),
                base.replace("\"tdsPpm\": 151.3", "\"tdsPpm\": 151.301"),
                base.replace("\"elapsedMs\": 1000", "\"elapsedMs\": -1"),
                base.replace("\"expectedSamples\": 60", "\"expectedSamples\": 59")}) {
            assertNotEquals(base, changed, "mutation must change fixture");
            assertInvalid(changed.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    void validatesUtf8SizeTopicAllowlistAndDecimalWithoutRounding() throws Exception {
        byte[] empty = example("session-result-empty");
        assertThrows(IllegalArgumentException.class, () -> codec.decode(empty, TOPIC + "/x", Set.of(DEVICE)));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(empty, TOPIC, Set.of("other")));
        assertInvalid(new byte[] {(byte) 0xc3, 0x28});
        byte[] limit = java.util.Arrays.copyOf(empty, 16384);
        java.util.Arrays.fill(limit, empty.length, limit.length, (byte) ' ');
        assertNotNull(codec.decode(limit, TOPIC, Set.of(DEVICE)));
        assertInvalid(java.util.Arrays.copyOf(limit, 16385));
    }

    private void assertInvalid(byte[] bytes) {
        var error = assertThrows(IllegalArgumentException.class, () -> codec.decode(bytes, TOPIC, Set.of(DEVICE)));
        assertEquals("MQTT_INPUT_INVALID", error.getMessage());
        assertNull(error.getCause());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0000-01-01T00:00:00Z", "2026-02-30T00:00:00Z", "2026-01-01T00:00:60Z",
            "2026-01-01T00:00:00+00:00", "2026-01-01T00:00:00.1234567890Z"})
    void rejectsInvalidCalendarAndTimestampRepresentations(String timestamp) throws Exception {
        var root = (tools.jackson.databind.node.ObjectNode) JSON.readTree(example("session-result-empty"));
        root.put("startedAt", timestamp);
        assertInvalid(JSON.writeValueAsBytes(root));
    }

    @Test void acceptsNanosecondTimestampAndDecimalTrailingZerosButRejectsRangeAndPrecision() throws Exception {
        var root = (tools.jackson.databind.node.ObjectNode) JSON.readTree(example("session-result-incomplete"));
        root.put("startedAt", "2024-02-29T00:00:00.123456789Z");
        var sample = (tools.jackson.databind.node.ObjectNode) root.get("measurements").get(0);
        sample.put("tdsPpm", new java.math.BigDecimal("151.300"));
        var decoded = codec.decode(JSON.writeValueAsBytes(root), TOPIC, Set.of(DEVICE));
        assertEquals(123456789, decoded.startedAt().getNano());
        assertEquals(0, decoded.measurements().get(0).tdsPpm().compareTo(new java.math.BigDecimal("151.30")));
        for (String value : new String[] {"10000000", "-10000000", "0.001"}) {
            sample.put("tdsPpm", new java.math.BigDecimal(value));
            assertInvalid(JSON.writeValueAsBytes(root));
        }
    }

    @Test void rejectsDuplicateSequenceUnknownNestedPropertyAndAllWarmupNormalList() throws Exception {
        var root = (tools.jackson.databind.node.ObjectNode) JSON.readTree(example("session-result-normal"));
        var sample = (tools.jackson.databind.node.ObjectNode) root.get("measurements").get(1);
        sample.put("seq", 1);
        assertInvalid(JSON.writeValueAsBytes(root));
        sample.put("seq", 2).put("unexpected", 1);
        assertInvalid(JSON.writeValueAsBytes(root));
        sample.remove("unexpected");
        for (var measurement : root.get("measurements")) ((tools.jackson.databind.node.ObjectNode) measurement).put("isWarmup", true);
        assertInvalid(JSON.writeValueAsBytes(root));
    }
}
