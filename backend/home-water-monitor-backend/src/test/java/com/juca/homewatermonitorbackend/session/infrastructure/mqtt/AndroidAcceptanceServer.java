package com.juca.homewatermonitorbackend.session.infrastructure.mqtt;

import com.juca.homewatermonitorbackend.HomeWaterMonitorBackendApplication;
import com.juca.homewatermonitorbackend.session.application.*;
import com.github.dockerjava.api.model.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

// Uses a real server, PostgreSQL and MQTT, but generated measurements. Never controls a physical ESP32.
public class AndroidAcceptanceServer {
    public static void main(String[] args) throws Exception {
        Path examples=Path.of(args[0]),directory=Path.of(args[1]);Files.createDirectories(directory);
        try(var pg=new PostgreSQLContainer("postgres:18-alpine");
            var broker=new GenericContainer<>("eclipse-mosquitto:2.0.22")
                    .withExposedPorts(1883).withCopyToContainer(Transferable.of("listener 1883\nallow_anonymous true\n"),"/mosquitto/config/mosquitto.conf")) {
            pg.withCreateContainerCmdModifier(c->c.getHostConfig().withPortBindings(new PortBinding(Ports.Binding.bindIpAndPort("127.0.0.1",0),new ExposedPort(5432))));
            broker.withCreateContainerCmdModifier(c->c.getHostConfig().withPortBindings(new PortBinding(Ports.Binding.bindIpAndPort("127.0.0.1",0),new ExposedPort(1883))));
            pg.start();broker.start();String uri="tcp://127.0.0.1:"+broker.getMappedPort(1883);
            var app=new SpringApplication(HomeWaterMonitorBackendApplication.class);
            app.addInitializers(context->TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                    "spring.datasource.url="+pg.getJdbcUrl(),"spring.datasource.username="+pg.getUsername(),"spring.datasource.password="+pg.getPassword(),
                    "server.address=127.0.0.1","server.port=0","water-monitor.mqtt.enabled=true","water-monitor.mqtt.broker-uri="+uri,
                    "water-monitor.mqtt.inbound-client-id=android-results-rx","water-monitor.mqtt.outbound-client-id=android-results-tx",
                    "water-monitor.mqtt.allowed-device-ids=esp32-bench","water-monitor.mqtt.username=","water-monitor.mqtt.password=",
                    "water-monitor.control.enabled=true","water-monitor.control.status-client-id=android-status-rx","water-monitor.control.publish-client-id=android-control-tx"));
            try(var context=app.run();var device=new DemonstrationDevice(uri,examples)) {
                // A historical, unlinked empty session exercises legacy access without fabricating statistics.
                context.getBean(ProcessTdsSessionService.class).process(new SaveNormalTdsSessionCommand(UUID.randomUUID(),
                        "legacy-fixture",null,1000,new BigDecimal("25"),"simulated-v1",List.of()));
                int port=((WebServerApplicationContext)context).getWebServer().getPort();
                var ready=JsonMapper.builder().build().createObjectNode().put("origin","http://127.0.0.1:"+port)
                        .put("port",port).put("deviceId","esp32-bench").put("source","SIMULATED");
                Files.writeString(directory.resolve("ready.json"),ready.toString());
                System.out.println("ANDROID_ACCEPTANCE_READY loopback=true source=SIMULATED");
                long deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(30);
                while(!Files.exists(directory.resolve("stop")) && System.nanoTime()<deadline) {
                    if(device.failure.get()!=null)throw new IllegalStateException("ACCEPTANCE_DEVICE_FAILURE",device.failure.get());
                    Thread.sleep(250);
                }
            }
        } finally {
            Files.writeString(directory.resolve("stopped"),"closed");
            System.out.println("ANDROID_ACCEPTANCE_STOPPED");
        }
    }
    private static class DemonstrationDevice extends ControlFakeDevice {
        final AtomicReference<Throwable> failure=new AtomicReference<>();
        final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
        final Map<String,byte[]> results=new HashMap<>();
        final Path examples;
        DemonstrationDevice(String uri,Path examples) throws Exception {
            super(uri);this.examples=examples;
            client.subscribe("home/water/v1/esp32-bench/preservation",1).waitForCompletion(5000);
        }
        @Override synchronized void handle(MqttMessage message) {
            try {
                var body=codec.tree(message.getPayload());String type=body.get("eventType").asString();
                if("SESSION_PRESERVED".equals(type)) {
                    if(body.get("sessionId").asString().equals(status.get("sessionId").asString())) {
                        status.put("state","IDLE").putNull("sessionId").put("sampleCount",0).put("finalized",false).put("round",0).put("attempt",0);
                        status.putObject("lastRelease").put("sessionId",body.get("sessionId").asString())
                                .put("preservationResult",body.get("preservationResult").asString()).put("preservedSampleCount",body.get("preservedSampleCount").intValue())
                                .put("reason","PRESERVATION_CONFIRMED");emit();
                    }
                    return;
                }
                int before=executions.get();super.handle(message);
                if(executions.get()>before) {
                    String session=body.get("sessionId").asString();
                    if("START".equals(type)) {
                        var result=(ObjectNode)JsonMapper.builder().build().readTree(Files.readAllBytes(examples.resolve("session-result-normal.json")));
                        result.put("sessionId",session).put("deviceId","esp32-bench").putNull("startedAt").put("calibrationVersion","simulated-v1");
                        results.put(session,codec.encode(result));
                    }
                    worker.schedule(()->transmit(session),1500,TimeUnit.MILLISECONDS);
                }
            } catch(Exception error) { failure.set(error); }
        }
        private synchronized void transmit(String session) {
            try {
                var m=new MqttMessage(Objects.requireNonNull(results.get(session)));m.setQos(1);
                var p=new MqttProperties();p.setPayloadFormat(true);p.setContentType("application/json");m.setProperties(p);
                client.publish("home/water/v1/esp32-bench/events",m).waitForCompletion(5000);
            } catch(Exception error) { failure.set(error); }
        }
        @Override public void close() throws Exception { worker.shutdownNow();super.close(); }
    }
}
