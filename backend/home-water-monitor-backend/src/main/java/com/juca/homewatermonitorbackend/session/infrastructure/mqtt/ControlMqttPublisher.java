package com.juca.homewatermonitorbackend.session.infrastructure.mqtt;

import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.springframework.integration.mqtt.core.Mqttv5ClientManager;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

// 명령과 결과 발행의 연결/미완료 자원을 분리한다. timeout은 발행 취소가 아니다.
public class ControlMqttPublisher {
    private final Mqttv5ClientManager manager;
    private final Semaphore capacity=new Semaphore(16);
    private final Set<IMqttToken> pending=ConcurrentHashMap.newKeySet();
    public ControlMqttPublisher(Mqttv5ClientManager manager) { this.manager=manager; }
    private void reap() {
        for(var token:pending) if(token.isComplete() && pending.remove(token))capacity.release();
    }
    public void publish(String device,byte[] bytes,long deadlineNano) {
        reap(); var client=manager.getClient();
        if(client==null || !client.isConnected() || !capacity.tryAcquire())throw new IllegalStateException("CONTROL_PUBLISH_UNAVAILABLE");
        IMqttToken token=null;
        try {
            long remaining=(deadlineNano-System.nanoTime())/1000000;
            if(remaining<=0)throw new IllegalStateException("CONTROL_DEADLINE");
            var m=new MqttMessage(bytes);m.setQos(1);m.setRetained(false);
            var p=new MqttProperties();p.setContentType("application/json");p.setPayloadFormat(true);p.setMessageExpiryInterval(10L);m.setProperties(p);
            token=client.publish("home/water/v1/"+device+"/commands",m);pending.add(token);
            token.waitForCompletion(Math.max(1,(deadlineNano-System.nanoTime())/1000000));
            if(token.getReasonCodes()!=null)for(int reason:token.getReasonCodes())if(reason>=128)throw new IllegalStateException("CONTROL_PUBLISH_REJECTED");
        } catch(Exception e) { throw new IllegalStateException("CONTROL_PUBLISH_FAILED"); }
        finally { if(token==null)capacity.release(); else reap(); }
    }
}
