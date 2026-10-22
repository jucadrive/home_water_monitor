package com.juca.homewatermonitorbackend.session.infrastructure.mqtt;

import com.juca.homewatermonitorbackend.session.application.control.ControlRuntime;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.Mqttv5ClientManager;
import org.springframework.integration.mqtt.inbound.Mqttv5PahoMessageDrivenChannelAdapter;
import java.util.Set;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(prefix="water-monitor.control",name="enabled",havingValue="true")
@EnableConfigurationProperties(ControlProperties.class)
public class ControlMqttConfiguration {
    @Bean Mqttv5ClientManager mqttControlPublisherManager(ControlProperties control,SessionMqttProperties mqtt) {
        if(!mqtt.enabled() || Set.of(control.publishClientId(),control.statusClientId(),mqtt.inboundClientId(),mqtt.outboundClientId()).size()!=4)
            throw new IllegalArgumentException("Distinct MQTT client IDs are required");
        var manager=new Mqttv5ClientManager(SessionMqttConfiguration.options(mqtt,false),control.publishClientId());
        manager.setPersistence(new MemoryPersistence());manager.setCompletionTimeout(10000);manager.setPhase(0);
        manager.setDisconnectCompletionTimeout(10000);manager.setQuiescentTimeout(10000);return manager;
    }
    @Bean ControlMqttPublisher controlMqttPublisher(@Qualifier("mqttControlPublisherManager") Mqttv5ClientManager manager) {
        return new ControlMqttPublisher(manager);
    }
    @Bean DeviceStatusReceiver deviceStatusReceiver(ControlRuntime runtime,SessionMqttProperties mqtt){return new DeviceStatusReceiver(runtime,mqtt.allowedDeviceIds());}
    @Bean DirectChannel controlStatusInput(DeviceStatusReceiver receiver){var channel=new DirectChannel();channel.subscribe(receiver);return channel;}
    @Bean Mqttv5PahoMessageDrivenChannelAdapter controlStatusInbound(ControlProperties control,SessionMqttProperties mqtt,
            @Qualifier("controlStatusInput") DirectChannel channel) {
        var subscriptions=mqtt.allowedDeviceIds().stream().sorted().map(d->{
            var s=new MqttSubscription("home/water/v1/"+d+"/status",1);s.setRetainAsPublished(true);s.setRetainHandling(2);return s;
        }).toArray(MqttSubscription[]::new);
        var adapter=new Mqttv5PahoMessageDrivenChannelAdapter(SessionMqttConfiguration.options(mqtt,false),control.statusClientId(),subscriptions);
        adapter.setPersistence(new MemoryPersistence());adapter.setManualAcks(true);adapter.setPayloadType(MqttMessage.class);
        adapter.setOutputChannel(channel);adapter.setCompletionTimeout(10000);adapter.setPhase(100);
        adapter.setDisconnectCompletionTimeout(10000);adapter.setQuiescentTimeout(10000);return adapter;
    }
}
