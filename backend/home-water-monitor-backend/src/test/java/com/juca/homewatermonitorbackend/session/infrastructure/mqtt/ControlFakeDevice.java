package com.juca.homewatermonitorbackend.session.infrastructure.mqtt;

import org.eclipse.paho.mqttv5.client.*;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import tools.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

// 모의 장치는 서버/브로커 검증용이다. 실제 firmware 판정 테스트를 대체하지 않는다.
class ControlFakeDevice implements AutoCloseable {
    final ControlMessageCodec codec=new ControlMessageCodec();
    final MqttAsyncClient client;
    final ObjectNode status;
    final AtomicInteger executions=new AtomicInteger(),startsReceived=new AtomicInteger();
    volatile boolean ignoreStart;
    private UUID window;
    private long sequence;
    ControlFakeDevice(String uri) throws Exception {
        status=(ObjectNode)codec.tree(Files.readAllBytes(Path.of("../../docs/mqtt/v1/examples/device-status-idle.json")));
        status.put("bootId",UUID.randomUUID().toString()).putNull("controlWindow").putNull("replyToProbeId");
        client=new MqttAsyncClient(uri,"fake-"+UUID.randomUUID(),new MemoryPersistence());
        client.setCallback(new MqttCallback() {
            public void messageArrived(String topic,MqttMessage message){handle(message);}
            public void disconnected(MqttDisconnectResponse response){}
            public void mqttErrorOccurred(MqttException e){}
            public void deliveryComplete(IMqttToken token){}
            public void connectComplete(boolean reconnect,String serverURI){}
            public void authPacketArrived(int code,MqttProperties p){}
        });
        var options=new MqttConnectionOptions();options.setCleanStart(true);options.setConnectionTimeout(5);
        client.connect(options).waitForCompletion(5000);
        client.subscribe("home/water/v1/esp32-bench/commands",1).waitForCompletion(5000);
    }
    synchronized void handle(MqttMessage message) {
        var command=codec.tree(message.getPayload());String type=command.get("eventType").asString();
        if("STATE_REQUEST".equals(type)) {
            status.put("replyToProbeId",command.get("probeId").asString());
            if("IDLE".equals(status.get("state").asString()) || "MANUAL_WAIT".equals(status.get("state").asString())) {
                window=UUID.randomUUID();status.putObject("controlWindow").put("windowId",window.toString()).put("validForMs",10000);
            } else status.putNull("controlWindow");
            send();return;
        }
        startsReceived.incrementAndGet();
        if(ignoreStart)return;
        if(window==null || !window.toString().equals(command.get("windowId").asString()))return;
        window=null;executions.incrementAndGet();
        boolean retry="RETRY".equals(type);
        status.put("state",retry?"PREPARING":"MEASURING").put("sessionId",command.get("sessionId").asString())
                .put("round",retry?2:1).put("attempt",retry?1:0).put("finalized",retry);
        var e=status.putObject("lastExecution");
        e.put("commandId",command.get("commandId").asString()).put("commandType",type)
                .put("sessionId",command.get("sessionId").asString()).put("windowId",command.get("windowId").asString());
        status.putObject("commandResult").put("commandId",command.get("commandId").asString()).put("commandType",type)
                .put("sessionId",command.get("sessionId").asString()).put("outcome","ACCEPTED").putNull("reason");
        emit();
    }
    synchronized void emit(){status.putNull("replyToProbeId").putNull("controlWindow");send();}
    private void send() {
        status.put("statusSeq",++sequence);
        var m=new MqttMessage(codec.encode(status));m.setQos(1);
        var p=new MqttProperties();p.setPayloadFormat(true);p.setContentType("application/json");p.setMessageExpiryInterval(10L);m.setProperties(p);
        try{client.publish("home/water/v1/esp32-bench/status",m);}catch(MqttException e){throw new IllegalStateException(e);}
    }
    @Override public void close() throws Exception {
        if(client.isConnected())client.disconnect().waitForCompletion(5000);client.close(true);
    }
}
