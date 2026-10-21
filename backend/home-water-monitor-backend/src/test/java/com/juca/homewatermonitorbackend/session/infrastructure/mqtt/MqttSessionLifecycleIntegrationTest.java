package com.juca.homewatermonitorbackend.session.infrastructure.mqtt;

import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.mqtt.inbound.Mqttv5PahoMessageDrivenChannelAdapter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@Timeout(60)
class MqttSessionLifecycleIntegrationTest extends MqttIntegrationSupport {
    @Autowired Mqttv5PahoMessageDrivenChannelAdapter mqttInbound;

    private void publish(tools.jackson.databind.node.ObjectNode root) throws Exception {
        var message = new MqttMessage(MqttSessionCodecTest.JSON.writeValueAsBytes(root));
        message.setQos(1);
        var properties = new MqttProperties();
        properties.setContentType("application/json");
        properties.setPayloadFormat(true);
        message.setProperties(properties);
        device.publish(MqttSessionCodecTest.TOPIC, message).waitForCompletion(5000);
    }

    @Test void stoppedSubscriberDoesNotProcessAndPersistentSessionDeliversAfterRestart() throws Exception {
        var root = input("empty");
        mqttInbound.stop();
        try {
            publish(root);
            noConfirmation();
            assertEquals(0, count("tds_session", id(root)));
        } finally { mqttInbound.start(); }
        assertNotNull(completions.poll(15, TimeUnit.SECONDS));
        confirmation();
        assertEquals(1, count("tds_session", id(root)));
    }

    @Test void receiveWindowAndDirectProcessingKeepOnlyOneMessageInApplicationAtATime() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        doAnswer(invocation -> {
            calls.incrementAndGet();
            entered.countDown();
            assertTrue(release.await(10, TimeUnit.SECONDS));
            try { return invocation.callRealMethod(); }
            finally { completions.add(true); }
        }).when(receiver).handleMessage(any());
        var first = input("empty");
        var second = input("empty");
        try {
            publish(first);
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            publish(second);
            noConfirmation();
            assertEquals(1, calls.get());
            assertEquals(0, count("tds_session", id(second)));
        } finally { release.countDown(); }
        assertNotNull(completions.poll(15, TimeUnit.SECONDS));
        assertNotNull(completions.poll(15, TimeUnit.SECONDS));
        confirmation();
        confirmation();
        assertEquals(2, calls.get());
        assertEquals(1, count("tds_session", id(first)));
        assertEquals(1, count("tds_session", id(second)));
    }
}
