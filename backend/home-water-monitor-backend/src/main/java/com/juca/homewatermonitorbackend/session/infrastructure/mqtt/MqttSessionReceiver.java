package com.juca.homewatermonitorbackend.session.infrastructure.mqtt;

import com.juca.homewatermonitorbackend.session.application.ProcessTdsSessionService;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.acks.SimpleAcknowledgment;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.integration.StaticMessageHeaderAccessor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Set;
import java.util.function.BiConsumer;

// DirectChannel에서 직렬 처리해 작업 큐를 만들지 않는다. 발행 연결은 수신 연결과 분리한다.
public class MqttSessionReceiver implements MessageHandler {
    private static final Logger log = LoggerFactory.getLogger(MqttSessionReceiver.class);
    private final MqttSessionCodec codec;
    private final ProcessTdsSessionService processing;
    private final Set<String> allowed;
    private final BiConsumer<String, byte[]> publisher;

    public MqttSessionReceiver(MqttSessionCodec codec, ProcessTdsSessionService processing, Set<String> allowed,
            BiConsumer<String, byte[]> publisher) {
        this.codec = codec;
        this.processing = processing;
        this.allowed = Set.copyOf(allowed);
        this.publisher = publisher;
    }

    @Override
    public void handleMessage(Message<?> message) {
        SimpleAcknowledgment ack = StaticMessageHeaderAccessor.getAcknowledgment(message);
        String stage = "INPUT";
        try {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                throw new IllegalStateException("MQTT_OUTER_TRANSACTION");
            }
            var mqtt = (MqttMessage) message.getPayload();
            if (mqtt.getQos() != 1 || mqtt.isRetained()
                    || !Boolean.TRUE.equals(mqtt.getProperties().getPayloadFormat())
                    || !"application/json".equals(mqtt.getProperties().getContentType())) {
                throw new IllegalArgumentException("MQTT_TRANSPORT_INVALID");
            }
            var command = codec.decode(mqtt.getPayload(), message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC,
                    String.class), allowed);
            stage = "STORAGE";
            // 이 호출은 별도 서비스 프록시의 실제 커밋까지 마친 뒤 반환한다.
            var result = processing.process(command);
            stage = "PUBLISH";
            int version = command.temperatureSource() == null ? 1 : 2;
            publisher.accept("home/water/v" + version + "/" + command.deviceId() + "/preservation",
                    codec.encode(command.deviceId(), result, version));
        } catch (RuntimeException error) {
            log.warn("MQTT processing failed stage={}", stage);
        } finally {
            if (ack != null) {
                try { ack.acknowledge(); }
                catch (RuntimeException error) { log.warn("MQTT acknowledgment failed"); }
            }
        }
    }
}
