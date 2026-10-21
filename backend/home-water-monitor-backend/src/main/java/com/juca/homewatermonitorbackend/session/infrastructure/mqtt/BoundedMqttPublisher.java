package com.juca.homewatermonitorbackend.session.infrastructure.mqtt;

import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.springframework.integration.mqtt.core.Mqttv5ClientManager;
import org.springframework.integration.mqtt.outbound.Mqttv5PahoMessageHandler;
import org.springframework.messaging.Message;

// 기본 어댑터의 연결 대기 루프를 사용하지 않는다. 오프라인이면 즉시 실패, PUBACK은 유한 시간만 기다린다.
public class BoundedMqttPublisher extends Mqttv5PahoMessageHandler implements org.springframework.context.SmartLifecycle {
    private final Mqttv5ClientManager manager;

    public BoundedMqttPublisher(Mqttv5ClientManager manager) {
        super(manager);
        this.manager = manager;
        setCompletionTimeout(10000);
        setAsync(false);
    }

    @Override public int getPhase() { return 50; }

    @Override
    protected void publish(String topic, Object mqttMessage, Message<?> springMessage) {
        var client = manager.getClient();
        if (client == null || !client.isConnected()) throw new IllegalStateException("MQTT_PUBLISH_OFFLINE");
        try {
            var token = client.publish(topic, (MqttMessage) mqttMessage);
            token.waitForCompletion(getCompletionTimeout());
            if (token.getReasonCodes() != null) {
                for (int reason : token.getReasonCodes()) {
                    if (reason >= 128) throw new IllegalStateException("MQTT_PUBLISH_REJECTED");
                }
            }
        } catch (MqttException error) {
            throw new IllegalStateException("MQTT_PUBLISH_FAILED");
        }
    }
}
