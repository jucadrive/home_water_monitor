package com.juca.homewatermonitorbackend.session.infrastructure.mqtt;

import com.juca.homewatermonitorbackend.session.application.ProcessTdsSessionService;
import com.juca.homewatermonitorbackend.session.application.TdsSessionInputValidator;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.Mqttv5ClientManager;
import org.springframework.integration.mqtt.event.MqttConnectionFailedEvent;
import org.springframework.integration.mqtt.event.MqttProtocolErrorEvent;
import org.springframework.integration.mqtt.inbound.Mqttv5PahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.support.MessageBuilder;
import java.nio.charset.StandardCharsets;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "water-monitor.mqtt", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(SessionMqttProperties.class)
public class SessionMqttConfiguration {
    @Bean
    MqttSessionCodec mqttSessionCodec(TdsSessionInputValidator validator) { return new MqttSessionCodec(validator); }

    @Bean
    Mqttv5ClientManager mqttOutboundManager(SessionMqttProperties config) {
        var manager = new Mqttv5ClientManager(options(config, false), config.outboundClientId());
        manager.setPersistence(new MemoryPersistence());
        manager.setCompletionTimeout(10000);
        manager.setDisconnectCompletionTimeout(10000);
        manager.setQuiescentTimeout(10000);
        manager.setPhase(0);
        return manager;
    }

    @Bean
    BoundedMqttPublisher mqttPublisher(Mqttv5ClientManager mqttOutboundManager) {
        return new BoundedMqttPublisher(mqttOutboundManager);
    }

    @Bean
    MqttSessionReceiver mqttSessionReceiver(MqttSessionCodec codec, ProcessTdsSessionService processing,
            SessionMqttProperties config, BoundedMqttPublisher publisher) {
        return new MqttSessionReceiver(codec, processing, config.allowedDeviceIds(), (topic, bytes) -> {
            var mqtt = new MqttMessage(bytes);
            mqtt.setQos(1);
            mqtt.setRetained(false);
            var properties = new MqttProperties();
            properties.setContentType("application/json");
            properties.setPayloadFormat(true);
            mqtt.setProperties(properties);
            publisher.handleMessage(MessageBuilder.withPayload(mqtt).setHeader(MqttHeaders.TOPIC, topic).build());
        });
    }

    @Bean
    DirectChannel mqttSessionInput(MqttSessionReceiver receiver) {
        var channel = new DirectChannel();
        channel.subscribe(receiver);
        return channel;
    }

    @Bean
    Mqttv5PahoMessageDrivenChannelAdapter mqttInbound(SessionMqttProperties config, DirectChannel mqttSessionInput) {
        var subscriptions = config.allowedDeviceIds().stream().sorted().map(id -> {
            var subscription = new MqttSubscription("home/water/v1/" + id + "/events", 1);
            subscription.setRetainAsPublished(true);
            subscription.setRetainHandling(2);
            return subscription;
        }).toArray(MqttSubscription[]::new);
        var adapter = new Mqttv5PahoMessageDrivenChannelAdapter(options(config, true), config.inboundClientId(), subscriptions);
        adapter.setPersistence(new MemoryPersistence());
        adapter.setPayloadType(MqttMessage.class);
        adapter.setManualAcks(true);
        adapter.setCompletionTimeout(10000);
        adapter.setDisconnectCompletionTimeout(10000);
        adapter.setQuiescentTimeout(10000);
        adapter.setPhase(100);
        adapter.setOutputChannel(mqttSessionInput);
        return adapter;
    }

    static MqttConnectionOptions options(SessionMqttProperties config, boolean inbound) {
        var options = new MqttConnectionOptions();
        options.setServerURIs(new String[] {config.brokerUri()});
        options.setCleanStart(!inbound);
        options.setSessionExpiryInterval(inbound ? 86400L : 0L);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(30);
        options.setAutomaticReconnect(true);
        // Paho 1.2.5의 실제 재연결 루프는 getMaxReconnectDelay()를 사용한다.
        options.setMaxReconnectDelay(16000);
        options.setReceiveMaximum(1);
        options.setMaximumPacketSize(65536L);
        if (config.username() != null && !config.username().isBlank()) options.setUserName(config.username());
        if (config.password() != null) options.setPassword(config.password().getBytes(StandardCharsets.UTF_8));
        return options;
    }

    @EventListener
    public void connectionFailure(MqttConnectionFailedEvent event) {
        org.slf4j.LoggerFactory.getLogger(SessionMqttConfiguration.class).warn("MQTT connection/subscription failed");
    }

    @EventListener
    public void protocolFailure(MqttProtocolErrorEvent event) {
        org.slf4j.LoggerFactory.getLogger(SessionMqttConfiguration.class).warn("MQTT protocol failed");
    }
}
