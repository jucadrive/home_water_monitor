package com.juca.homewatermonitorbackend.session.infrastructure.mqtt;

import com.juca.homewatermonitorbackend.session.application.control.ControlModel.*;
import com.juca.homewatermonitorbackend.session.application.control.ControlPolicy;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

// 신규 제어 전용 codec. 기존 결과 수신 codec/계약을 변경하지 않는다.
public class ControlMessageCodec {
    private final JsonMapper mapper = JsonMapper.builder(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(12).maxStringLength(4096)
                    .maxNumberLength(4096).build()).build())
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                    DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();

    public Status decodeStatus(byte[] bytes, String device) {
        try {
            var tree = tree(bytes);
            Status s = mapper.treeToValue(tree, Status.class);
            ControlPolicy.validate(s);
            ControlPolicy.require(device.equals(s.deviceId()));
            return s;
        } catch (RuntimeException e) { throw new IllegalArgumentException("CONTROL_INPUT_INVALID"); }
    }
    public JsonNode tree(byte[] bytes) {
        try {
            ControlPolicy.require(bytes.length > 0 && bytes.length <= 4096);
            var text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            ControlPolicy.require(!text.startsWith("\uFEFF"));
            var tree = mapper.readTree(text);
            ControlPolicy.require(tree != null && tree.isObject());
            check(tree);
            return tree;
        } catch (Exception e) { throw new IllegalArgumentException("CONTROL_INPUT_INVALID"); }
    }
    private void check(JsonNode node) {
        if (node.isNumber()) node.decimalValue().longValueExact();
        if (node.isString()) {
            var s = node.asString();
            ControlPolicy.require(s.chars().noneMatch(c -> c < 32 || c == 127 || Character.isSurrogate((char)c)));
        }
        if (node.isObject()) for (var property : node.properties()) {
            if (property.getKey().endsWith("Id") && !"deviceId".equals(property.getKey()) && !property.getValue().isNull()) {
                ControlPolicy.require(property.getValue().isString()
                        && property.getValue().asString().matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"));
            }
            check(property.getValue());
        }
        if (node.isArray()) for (var item : node) check(item);
    }
    public byte[] encode(Object value) { return mapper.writeValueAsBytes(value); }
    public String json(Object value) { return mapper.writeValueAsString(value); }
    public Status storedStatus(String json) { return decodeStatus(json.getBytes(StandardCharsets.UTF_8), tree(json.getBytes(StandardCharsets.UTF_8)).get("deviceId").asString()); }
}
