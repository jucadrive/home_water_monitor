package com.juca.homewatermonitorbackend.session.infrastructure.mqtt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import com.juca.homewatermonitorbackend.session.application.ProcessTdsSessionService;
import com.juca.homewatermonitorbackend.session.application.TdsSessionInputValidator;
import org.springframework.integration.mqtt.core.Mqttv5ClientManager;
import org.eclipse.paho.mqttv5.client.IMqttAsyncClient;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.support.MessageBuilder;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionMqttConfigurationTest {
    @Test void disabledDoesNotCreateMqttBeans() {
        new ApplicationContextRunner().withUserConfiguration(SessionMqttConfiguration.class)
                .run(context -> assertEquals(0, context.getBeansOfType(MqttSessionReceiver.class).size()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"tcp://user:secret@localhost:1883", "http://localhost:1883", "tcp://localhost", "garbage"})
    void invalidSettingsFailWithoutExposingValues(String uri) {
        var error = assertThrows(IllegalArgumentException.class, () ->
                new SessionMqttProperties(true, uri, "rx", "tx", Set.of("device"), "", ""));
        assertFalse(error.getMessage().contains(uri));
        assertNull(error.getCause());
    }

    @Test void refusesEmptyAllowlistOrCollidingIds() {
        assertThrows(IllegalArgumentException.class, () ->
                new SessionMqttProperties(true, "tcp://localhost:1883", "same", "same", Set.of("d"), "", ""));
        assertThrows(IllegalArgumentException.class, () ->
                new SessionMqttProperties(true, "tcp://localhost:1883", "rx", "tx", Set.of(), "", ""));
    }

    @Test void optionsHaveBoundedTimeoutAndReconnectAndSessionPolicy() {
        var config = new SessionMqttProperties(true, "tcp://localhost:1883", "rx", "tx", Set.of("d"), "", "");
        var inbound = SessionMqttConfiguration.options(config, true);
        assertFalse(inbound.isCleanStart());
        assertEquals(86400L, inbound.getSessionExpiryInterval());
        assertEquals(10, inbound.getConnectionTimeout());
        assertEquals(30, inbound.getKeepAliveInterval());
        assertTrue(inbound.isAutomaticReconnect());
        assertEquals(16000, inbound.getMaxReconnectDelay());
        assertTrue(SessionMqttConfiguration.options(config, false).isCleanStart());
        assertEquals(0L, SessionMqttConfiguration.options(config, false).getSessionExpiryInterval());
    }

    @Test void offlinePublisherFailsImmediatelyWithoutConnecting() throws Exception {
        var manager = mock(Mqttv5ClientManager.class);
        var client = mock(IMqttAsyncClient.class);
        when(manager.getClient()).thenReturn(client);
        var options = new org.eclipse.paho.mqttv5.client.MqttConnectionOptions();
        options.setServerURIs(new String[] {"tcp://localhost:1883"});
        when(manager.getConnectionInfo()).thenReturn(options);
        var publisher = new BoundedMqttPublisher(manager);
        assertThrows(IllegalStateException.class, () -> publisher.publish("topic", new MqttMessage(), null));
        verify(client, never()).connect(any());
        verify(client, never()).publish(anyString(), any(MqttMessage.class));
    }

    @Test void publisherWaitsForPubackOnlyWithinTenSecondsAndRejectsNegativeAck() throws Exception {
        var manager = mock(Mqttv5ClientManager.class);
        var client = mock(IMqttAsyncClient.class);
        var token = mock(IMqttToken.class);
        var options = new org.eclipse.paho.mqttv5.client.MqttConnectionOptions();
        options.setServerURIs(new String[] {"tcp://localhost:1883"});
        when(manager.getConnectionInfo()).thenReturn(options);
        when(manager.getClient()).thenReturn(client);
        when(client.isConnected()).thenReturn(true);
        when(client.publish(anyString(), any(MqttMessage.class))).thenReturn(token);
        when(token.getReasonCodes()).thenReturn(new int[] {128});
        var publisher = new BoundedMqttPublisher(manager);
        assertThrows(IllegalStateException.class, () -> publisher.publish("topic", new MqttMessage(), null));
        verify(token).waitForCompletion(10000);
    }
}
