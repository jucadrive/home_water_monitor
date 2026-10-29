package com.juca.homewatermonitorbackend.session.infrastructure.mqtt;

import com.juca.homewatermonitorbackend.session.application.SaveNormalTdsSessionCommand;
import com.juca.homewatermonitorbackend.session.application.TdsSessionInputValidator;
import com.juca.homewatermonitorbackend.session.application.TdsSessionProcessingResult;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

// 외부 JSON의 강제 형변환/반올림 없이 계약을 검증한 뒤 기존 application 명령으로 변환한다.
public class MqttSessionCodec {
    private final JsonMapper mapper = JsonMapper.builder(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(16)
                    .maxStringLength(16384).maxNumberLength(16384).build()).build())
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final TdsSessionInputValidator validator;

    public MqttSessionCodec(TdsSessionInputValidator validator) { this.validator = validator; }

    public SaveNormalTdsSessionCommand decode(byte[] bytes, String topic, Set<String> allowed) {
        try {
            require(bytes.length > 0 && bytes.length <= 16384);
            String json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            require(!json.startsWith("\uFEFF"));
            JsonNode root = mapper.readTree(json);
            boolean v2 = integer(root.get("schemaVersion")) == 2;
            if (v2) fields(root, "schemaVersion", "eventType", "deviceId", "sessionId", "startedAt", "expectedSamples",
                    "samplingIntervalMs", "temperatureSource", "compensationVersion", "calibrationVersion", "measurements");
            else fields(root, "schemaVersion", "eventType", "deviceId", "sessionId", "startedAt", "expectedSamples",
                    "samplingIntervalMs", "temperatureAssumedC", "calibrationVersion", "measurements");
            require((v2 || integer(root.get("schemaVersion")) == 1) && text(root.get("eventType")).equals("SESSION_RESULT"));
            String device = text(root.get("deviceId"));
            require(device.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}") && allowed.contains(device)
                    && topic.equals("home/water/v" + (v2 ? 2 : 1) + "/" + device + "/events"));
            String id = text(root.get("sessionId"));
            require(id.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
            require(integer(root.get("expectedSamples")) == 60);
            String calibration = text(root.get("calibrationVersion"));
            require(calibration.length() <= 64 && calibration.matches("(?s).*\\S.*")
                    && calibration.chars().noneMatch(c -> c < 32 || c == 127));
            JsonNode samples = root.get("measurements");
            require(samples.isArray() && samples.size() <= 60);
            var measurements = new ArrayList<SaveNormalTdsSessionCommand.Measurement>();
            for (JsonNode sample : samples) {
                if (v2) fields(sample, "seq", "elapsedMs", "tdsPpm", "isWarmup", "rawVoltageMv", "temperatureC", "temperatureElapsedMs");
                else fields(sample, "seq", "elapsedMs", "tdsPpm", "isWarmup", "rawVoltageMv");
                require(sample.get("isWarmup").isBoolean());
                measurements.add(new SaveNormalTdsSessionCommand.Measurement(integer(sample.get("seq")),
                        integer(sample.get("elapsedMs")), decimal(sample.get("tdsPpm"), false),
                        sample.get("isWarmup").booleanValue(), decimal(sample.get("rawVoltageMv"), true),
                        v2 ? decimal(sample.get("temperatureC"), false) : null, v2 ? integer(sample.get("temperatureElapsedMs")) : null));
            }
            require(measurements.size() < 60 || measurements.stream().anyMatch(m -> !m.isWarmup()));
            var command = new SaveNormalTdsSessionCommand(UUID.fromString(id), device, startedAt(root.get("startedAt")),
                    integer(root.get("samplingIntervalMs")), v2 ? null : decimal(root.get("temperatureAssumedC"), false),
                    calibration, measurements, v2 ? text(root.get("temperatureSource")) : null, v2 ? text(root.get("compensationVersion")) : null);
            validator.validate(command);
            return command;
        } catch (Exception error) {
            // 파서 예외에는 원문이 포함될 수 있으므로 외부 진단에는 전달하지 않는다.
            throw new IllegalArgumentException("MQTT_INPUT_INVALID");
        }
    }

    public byte[] encode(String device, TdsSessionProcessingResult result) { return encode(device, result, 1); }
    public byte[] encode(String device, TdsSessionProcessingResult result, int version) {
        var root = mapper.createObjectNode();
        root.put("schemaVersion", version).put("eventType", "SESSION_PRESERVED").put("deviceId", device)
                .put("sessionId", result.sessionId().toString()).put("preservationResult", result.preservationResult())
                .put("preservedSampleCount", result.preservedSampleCount());
        if (result.redeliveryResult() == null) root.putNull("comparison");
        else root.putObject("comparison").put("identicalCount", result.redeliveryResult().identicalCount())
                .put("conflictCount", result.redeliveryResult().conflictCount());
        return mapper.writeValueAsBytes(root);
    }

    private static void fields(JsonNode node, String... names) {
        require(node != null && node.isObject() && new HashSet<>(node.propertyNames()).equals(Set.of(names)));
    }

    private static String text(JsonNode node) {
        require(node.isString());
        String value = node.asString();
        // JSON 이스케이프로 들어온 서로 짝이 맞지 않는 surrogate도 거절한다.
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) require(++i < value.length() && Character.isLowSurrogate(value.charAt(i)));
            else require(!Character.isLowSurrogate(c));
        }
        return value;
    }

    private static int integer(JsonNode node) {
        require(node.isNumber());
        return node.decimalValue().intValueExact();
    }

    private static java.math.BigDecimal decimal(JsonNode node, boolean nullable) {
        if (nullable && node.isNull()) return null;
        require(node.isNumber());
        return node.decimalValue();
    }

    private static Instant startedAt(JsonNode node) {
        if (node.isNull()) return null;
        String value = text(node);
        require(value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-5][0-9](\\.[0-9]{1,9})?Z")
                && !value.startsWith("0000"));
        LocalDateTime.parse(value.substring(0, value.length() - 1));
        return Instant.parse(value);
    }

    private static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException("MQTT_INPUT_INVALID");
    }
}
