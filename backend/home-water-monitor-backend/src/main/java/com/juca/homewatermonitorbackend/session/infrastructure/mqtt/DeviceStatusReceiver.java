package com.juca.homewatermonitorbackend.session.infrastructure.mqtt;

import com.juca.homewatermonitorbackend.session.application.control.ControlRuntime;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.springframework.integration.StaticMessageHeaderAccessor;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import java.time.Instant;
import java.util.Set;

public class DeviceStatusReceiver implements MessageHandler {
    private final ControlRuntime runtime;
    private final Set<String> allowed;
    private final ControlMessageCodec codec=new ControlMessageCodec();
    public DeviceStatusReceiver(ControlRuntime runtime,Set<String> allowed){this.runtime=runtime;this.allowed=Set.copyOf(allowed);}
    @Override public void handleMessage(Message<?> message) {
        long received=System.nanoTime();Instant receivedAt=Instant.now();
        Runnable ack=()->{try{var a=StaticMessageHeaderAccessor.getAcknowledgment(message);if(a!=null)a.acknowledge();}
            catch(RuntimeException e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Control status ACK failed");}};
        try {
            var m=(MqttMessage)message.getPayload();var p=m.getProperties();
            if(m.getQos()!=1||m.isRetained()||p==null||!Boolean.TRUE.equals(p.getPayloadFormat())
                    ||!"application/json".equals(p.getContentType())||p.getMessageExpiryInterval()==null
                    ||p.getMessageExpiryInterval()<1||p.getMessageExpiryInterval()>10)throw new IllegalArgumentException();
            String topic=message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC,String.class);
            String device=allowed.stream().filter(d->("home/water/v1/"+d+"/status").equals(topic)).findFirst().orElseThrow();
            runtime.status(codec.decodeStatus(m.getPayload(),device),received,receivedAt,ack);
        } catch(RuntimeException e){ack.run();org.slf4j.LoggerFactory.getLogger(getClass()).warn("Control status input rejected");}
    }
}
