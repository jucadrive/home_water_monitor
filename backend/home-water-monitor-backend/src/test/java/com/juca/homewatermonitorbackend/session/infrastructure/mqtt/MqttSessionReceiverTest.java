package com.juca.homewatermonitorbackend.session.infrastructure.mqtt;

import com.juca.homewatermonitorbackend.session.application.*;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.junit.jupiter.api.Test;
import org.springframework.integration.IntegrationMessageHeaderAccessor;
import org.springframework.integration.acks.SimpleAcknowledgment;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Set;
import java.util.function.BiConsumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MqttSessionReceiverTest {
    final ProcessTdsSessionService service = mock(ProcessTdsSessionService.class);
    @SuppressWarnings("unchecked")
    final BiConsumer<String, byte[]> publisher = mock(BiConsumer.class);
    final SimpleAcknowledgment ack = mock(SimpleAcknowledgment.class);
    final MqttSessionCodec codec = new MqttSessionCodec(new TdsSessionInputValidator());
    final MqttSessionReceiver receiver = new MqttSessionReceiver(codec, service, Set.of(MqttSessionCodecTest.DEVICE), publisher);

    org.springframework.messaging.Message<MqttMessage> message() throws Exception {
        var mqtt = new MqttMessage(MqttSessionCodecTest.example("session-result-empty"));
        mqtt.setQos(1);
        mqtt.setProperties(new org.eclipse.paho.mqttv5.common.packet.MqttProperties());
        mqtt.getProperties().setContentType("application/json");
        mqtt.getProperties().setPayloadFormat(true);
        return MessageBuilder.withPayload(mqtt).setHeader(MqttHeaders.RECEIVED_TOPIC, MqttSessionCodecTest.TOPIC)
                .setHeader(IntegrationMessageHeaderAccessor.ACKNOWLEDGMENT_CALLBACK, ack).build();
    }

    @Test void processesThenPublishesThenAcknowledges() throws Exception {
        var command = codec.decode(MqttSessionCodecTest.example("session-result-empty"), MqttSessionCodecTest.TOPIC,
                Set.of(MqttSessionCodecTest.DEVICE));
        when(service.process(any())).thenReturn(new TdsSessionProcessingResult(command.sessionId(), "INCOMPLETE", 0,
                TdsSessionProcessingResult.Path.SAVED_NEW, null));
        receiver.handleMessage(message());
        var order = inOrder(service, publisher, ack);
        order.verify(service).process(any());
        order.verify(publisher).accept(eq("home/water/v1/esp32-water-01/preservation"), any());
        order.verify(ack).acknowledge();
    }

    @Test void storageFailureSendsNoConfirmationButAcknowledges() throws Exception {
        when(service.process(any())).thenThrow(new IllegalStateException("sensitive cause"));
        receiver.handleMessage(message());
        verifyNoInteractions(publisher);
        verify(ack).acknowledge();
    }

    @Test void invalidTransportDoesNotCallStorage() throws Exception {
        var message = message();
        message.getPayload().setRetained(true);
        receiver.handleMessage(message);
        verifyNoInteractions(service, publisher);
        verify(ack).acknowledge();
    }

    @Test void outerTransactionIsRejected() throws Exception {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { receiver.handleMessage(message()); }
        finally { TransactionSynchronizationManager.clear(); }
        verifyNoInteractions(service, publisher);
        verify(ack).acknowledge();
    }

    @Test void publisherFailureDoesNotRepeatStorageAndAcknowledges() throws Exception {
        var id = java.util.UUID.randomUUID();
        when(service.process(any())).thenReturn(new TdsSessionProcessingResult(id, "INCOMPLETE", 0,
                TdsSessionProcessingResult.Path.SAVED_NEW, null));
        doThrow(new IllegalStateException()).when(publisher).accept(anyString(), any());
        receiver.handleMessage(message());
        verify(service, times(1)).process(any());
        verify(ack).acknowledge();
    }
}
