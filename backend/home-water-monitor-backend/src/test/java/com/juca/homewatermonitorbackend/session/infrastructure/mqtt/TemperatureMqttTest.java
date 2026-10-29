package com.juca.homewatermonitorbackend.session.infrastructure.mqtt;

import com.juca.homewatermonitorbackend.session.application.TdsSessionInputValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class TemperatureMqttTest {
    static ObjectNode input(String suffix) throws Exception {
        return (ObjectNode) MqttSessionCodecTest.JSON.readTree(Files.readAllBytes(
            Path.of("../../docs/mqtt/v2/examples/session-result-" + suffix + ".json")));
    }
    final MqttSessionCodec codec = new MqttSessionCodec(new TdsSessionInputValidator());
    void decode(ObjectNode root) {
        codec.decode(MqttSessionCodecTest.JSON.writeValueAsBytes(root), "home/water/v2/esp32-water-01/events", Set.of("esp32-water-01"));
    }
    @ParameterizedTest @ValueSource(strings = {"normal","incomplete","empty"})
    void v2AcceptsDocumentedExamples(String suffix) throws Exception { decode(input(suffix)); }
    @ParameterizedTest @ValueSource(strings = {"missing","old","future","range","precision","assumed","extra","source","formula","version"})
    void invalidTemperatureOrContractIsRejected(String mutation) throws Exception {
        var root = input("normal"); var m = (ObjectNode)root.get("measurements").get(0);
        switch (mutation) {
            case "missing" -> m.remove("temperatureC");
            case "old" -> { m.put("elapsedMs", 5000); m.put("temperatureElapsedMs", 0); }
            case "future" -> m.put("temperatureElapsedMs", m.get("elapsedMs").intValue()+1);
            case "range" -> m.put("temperatureC", 51);
            case "precision" -> m.put("temperatureC", new java.math.BigDecimal("25.00001"));
            case "assumed" -> root.put("temperatureAssumedC", 25);
            case "extra" -> m.put("unexpected", true);
            case "source" -> root.put("temperatureSource", "UNKNOWN");
            case "formula" -> root.put("compensationVersion", "unknown");
            case "version" -> root.put("schemaVersion", 1);
            default -> fail();
        }
        assertThrows(IllegalArgumentException.class, () -> decode(root));
    }
    @Test void v2CannotBePublishedAsV1() throws Exception {
        var root=input("normal");
        assertThrows(IllegalArgumentException.class, () -> codec.decode(MqttSessionCodecTest.JSON.writeValueAsBytes(root),
            MqttSessionCodecTest.TOPIC, Set.of(MqttSessionCodecTest.DEVICE)));
    }
}
