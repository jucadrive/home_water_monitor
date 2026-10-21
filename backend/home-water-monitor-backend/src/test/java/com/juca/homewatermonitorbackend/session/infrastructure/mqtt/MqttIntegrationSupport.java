package com.juca.homewatermonitorbackend.session.infrastructure.mqtt;

import org.eclipse.paho.mqttv5.client.*;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.integration.mqtt.core.Mqttv5ClientManager;
import org.springframework.integration.mqtt.event.MqttSubscribedEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.images.builder.Transferable;
import tools.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(MqttIntegrationSupport.Events.class)
abstract class MqttIntegrationSupport {
    // 익명 접속은 무작위 호스트 포트의 격리된 테스트 브로커에만 사용한다.
    @Container static final GenericContainer<?> broker = new GenericContainer<>("eclipse-mosquitto:2.0.22")
            .withCommand("sh", "-c", "while true; do mosquitto -c /mosquitto/config/mosquitto.conf; sleep 1; done")
            .withExposedPorts(1883).withCopyToContainer(Transferable.of(
                    "listener 1883\nallow_anonymous true\npersistence false\n"), "/mosquitto/config/mosquitto.conf");
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    static String uri() { return "tcp://" + broker.getHost() + ":" + broker.getMappedPort(1883); }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("water-monitor.mqtt.enabled", () -> true);
        registry.add("water-monitor.mqtt.broker-uri", MqttIntegrationSupport::uri);
        registry.add("water-monitor.mqtt.inbound-client-id", () -> "server-rx");
        registry.add("water-monitor.mqtt.outbound-client-id", () -> "server-tx");
        registry.add("water-monitor.mqtt.allowed-device-ids", () -> MqttSessionCodecTest.DEVICE);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired Mqttv5ClientManager mqttOutboundManager;
    @Autowired Probe probe;
    @MockitoSpyBean MqttSessionReceiver receiver;
    MqttAsyncClient device;
    final BlockingQueue<MqttMessage> confirmations = new LinkedBlockingQueue<>(10);
    final BlockingQueue<Boolean> completions = new LinkedBlockingQueue<>(10);

    @BeforeEach void connectDevice() throws Exception {
        await(() -> probe.subscribed.get() > 0 && mqttOutboundManager.isConnected());
        doAnswer(invocation -> {
            try { return invocation.callRealMethod(); }
            finally { completions.add(true); }
        }).when(receiver).handleMessage(any());
        openDevice();
    }

    void openDevice() throws Exception {
        device = new MqttAsyncClient(uri(), "device-" + UUID.randomUUID(), new MemoryPersistence());
        device.setCallback(new MqttCallback() {
            public void messageArrived(String topic, MqttMessage message) { confirmations.add(message); }
            public void disconnected(MqttDisconnectResponse response) { }
            public void mqttErrorOccurred(MqttException error) { }
            public void deliveryComplete(IMqttToken token) { }
            public void connectComplete(boolean reconnect, String serverURI) { }
            public void authPacketArrived(int reasonCode, MqttProperties properties) { }
        });
        var options = new MqttConnectionOptions();
        options.setCleanStart(true);
        options.setConnectionTimeout(5);
        device.connect(options).waitForCompletion(5000);
        var subscription = new MqttSubscription("home/water/v1/esp32-water-01/preservation", 1);
        subscription.setRetainAsPublished(true);
        device.subscribe(new MqttSubscription[] {subscription}).waitForCompletion(5000);
    }

    @AfterEach void closeDevice() throws Exception {
        if (device != null) {
            if (device.isConnected()) device.disconnect().waitForCompletion(5000);
            device.close(true);
        }
    }

    ObjectNode input(String suffix) throws Exception {
        var root = (ObjectNode) MqttSessionCodecTest.JSON.readTree(MqttSessionCodecTest.example("session-result-" + suffix));
        root.put("sessionId", UUID.randomUUID().toString());
        return root;
    }

    void send(ObjectNode root) throws Exception { send(root, false, "application/json"); }
    void send(ObjectNode root, boolean retained, String contentType) throws Exception {
        var message = new MqttMessage(MqttSessionCodecTest.JSON.writeValueAsBytes(root));
        message.setQos(1);
        message.setRetained(retained);
        var properties = new MqttProperties();
        properties.setPayloadFormat(true);
        properties.setContentType(contentType);
        message.setProperties(properties);
        device.publish(MqttSessionCodecTest.TOPIC, message).waitForCompletion(5000);
        assertNotNull(completions.poll(15, TimeUnit.SECONDS), "server processing must complete");
    }

    tools.jackson.databind.JsonNode confirmation() throws Exception {
        var message = confirmations.poll(5, TimeUnit.SECONDS);
        assertNotNull(message, "expected business preservation confirmation");
        assertEquals(1, message.getQos());
        assertFalse(message.isRetained());
        assertEquals("application/json", message.getProperties().getContentType());
        assertEquals(true, message.getProperties().getPayloadFormat());
        assertNull(message.getProperties().getMessageExpiryInterval());
        return MqttSessionCodecTest.JSON.readTree(message.getPayload());
    }

    UUID id(ObjectNode root) { return UUID.fromString(root.get("sessionId").asString()); }
    long count(String table, UUID id) {
        return jdbc.queryForObject("select count(*) from " + table + " where session_id = ?", Long.class, id);
    }
    void noConfirmation() throws Exception { assertNull(confirmations.poll(300, TimeUnit.MILLISECONDS)); }
    static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(50);
        assertTrue(condition.getAsBoolean(), "condition did not become true");
    }

    static class Probe {
        final AtomicInteger subscribed = new AtomicInteger();
        @EventListener public void subscribed(MqttSubscribedEvent event) { subscribed.incrementAndGet(); }
    }
    @TestConfiguration static class Events { @Bean Probe probe() { return new Probe(); } }
}
