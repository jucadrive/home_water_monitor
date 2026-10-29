package com.juca.homewatermonitorbackend.session.infrastructure.mqtt;

import com.juca.homewatermonitorbackend.session.application.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.*;
import java.util.UUID;
import java.util.List;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="server.address=127.0.0.1")
@Testcontainers @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@Timeout(150)
class ControlMqttIntegrationTest {
    @Container static final GenericContainer<?> broker=new GenericContainer<>("eclipse-mosquitto:2.0.22")
            .withExposedPorts(1883).withCopyToContainer(Transferable.of("listener 1883\nallow_anonymous true\n"),"/mosquitto/config/mosquitto.conf");
    @Container static final PostgreSQLContainer pg=new PostgreSQLContainer("postgres:18-alpine");
    static String uri(){return "tcp://"+broker.getHost()+":"+broker.getMappedPort(1883);}
    @DynamicPropertySource static void settings(DynamicPropertyRegistry r){
        r.add("spring.datasource.url",pg::getJdbcUrl);r.add("spring.datasource.username",pg::getUsername);r.add("spring.datasource.password",pg::getPassword);
        r.add("water-monitor.mqtt.enabled",()->true);r.add("water-monitor.mqtt.broker-uri",ControlMqttIntegrationTest::uri);
        r.add("water-monitor.mqtt.inbound-client-id",()->"results-rx");r.add("water-monitor.mqtt.outbound-client-id",()->"results-tx");
        r.add("water-monitor.mqtt.allowed-device-ids",()->"esp32-bench");r.add("water-monitor.control.enabled",()->true);
        r.add("water-monitor.control.status-client-id",()->"status-rx");r.add("water-monitor.control.publish-client-id",()->"control-tx");
    }
    @Autowired Environment environment;
    @Autowired ProcessTdsSessionService sessions;
    @Autowired javax.sql.DataSource dataSource;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired com.juca.homewatermonitorbackend.session.application.control.CommandTransactionService control;
    @Autowired com.juca.homewatermonitorbackend.session.application.control.ControlRuntime runtime;
    @Autowired com.juca.homewatermonitorbackend.session.infrastructure.persistence.AutomaticMeasurementRunStore automaticRuns;
    final ControlMessageCodec codec=new ControlMessageCodec();
    @Test void hourlyStartUsesRealMqttTemperaturePreservationAndExistingReleaseProtection() throws Exception {
        var target=UUID.randomUUID();var slot=java.time.Instant.parse("2026-11-01T00:00:00Z");
        request("/api/cultivations","{\"plantName\":\"basil\",\"name\":\"automatic\",\"startedAt\":\"2026-10-01T00:00:00Z\"}",target);
        var auto=new com.juca.homewatermonitorbackend.session.application.automatic.AutomaticMeasurementService(
            new com.juca.homewatermonitorbackend.session.application.automatic.AutomaticMeasurementProperties(true,"esp32-bench",target),automaticRuns,runtime,slot.minusSeconds(1));
        try(var device=new ControlFakeDevice(uri())) {
            auto.runAt(slot);auto.runAt(slot.plusSeconds(20));
            var id=jdbc.queryForObject("select command_id from tds_automatic_measurement_run where cultivation_id=?",UUID.class,target);
            awaitState(id.toString(),"ACCEPTED",10);assertEquals(1,device.executions.get());
            assertEquals(409,start(UUID.randomUUID()).status());
            var session=jdbc.queryForObject("select session_id from tds_device_command where command_id=?",UUID.class,id);
            var result=(tools.jackson.databind.node.ObjectNode)tools.jackson.databind.json.JsonMapper.builder().build().readTree(java.nio.file.Files.readAllBytes(
                java.nio.file.Path.of("../../docs/mqtt/v2/examples/session-result-normal.json")));
            result.put("sessionId",session.toString()).put("deviceId","esp32-bench");
            var message=new org.eclipse.paho.mqttv5.common.MqttMessage(codec.encode(result));message.setQos(1);
            var properties=new org.eclipse.paho.mqttv5.common.packet.MqttProperties();properties.setPayloadFormat(true);properties.setContentType("application/json");message.setProperties(properties);
            device.client.publish("home/water/v2/esp32-bench/events",message).waitForCompletion(5000);
            var deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            while(jdbc.queryForObject("select count(*) from tds_session where session_id=?",Integer.class,session)==0 && System.nanoTime()<deadline)Thread.sleep(25);
            assertEquals(60,jdbc.queryForObject("select count(temperature_c) from tds_measurement where session_id=?",Integer.class,session));
            assertEquals(50,jdbc.queryForObject("select stats_sample_count from tds_session where session_id=?",Integer.class,session));
            assertEquals(target,jdbc.queryForObject("select cultivation_id from tds_device_command where command_id=?",UUID.class,id));
            synchronized(device) {
                device.status.put("state","IDLE").putNull("sessionId").put("sampleCount",0).put("finalized",false).put("round",0).put("attempt",0);
                device.status.putObject("lastRelease").put("sessionId",session.toString()).put("preservationResult","NORMAL").put("preservedSampleCount",60).put("reason","PRESERVATION_CONFIRMED");device.emit();
            }
            while(!"RELEASE_CONFIRMED".equals(command(id.toString()).body().get("deviceBuffer").asString()) && System.nanoTime()<deadline)Thread.sleep(25);
            assertEquals("RELEASE_CONFIRMED",command(id.toString()).body().get("deviceBuffer").asString());
        } finally {
            jdbc.update("update tds_device_control set active_command_id=null,held_session_id=null where held_session_id in (select session_id from tds_device_command where cultivation_id=?)",target);
        }
    }
    @Test void offlineHourlyRequestRecordsExistingNotDispatchedReasonWithoutCreatingResults() throws Exception {
        var target=UUID.randomUUID();var slot=java.time.Instant.parse("2026-11-01T01:00:00Z");
        request("/api/cultivations","{\"plantName\":\"basil\",\"name\":\"offline\",\"startedAt\":\"2026-10-01T00:00:00Z\"}",target);
        var auto=new com.juca.homewatermonitorbackend.session.application.automatic.AutomaticMeasurementService(
            new com.juca.homewatermonitorbackend.session.application.automatic.AutomaticMeasurementProperties(true,"esp32-bench",target),automaticRuns,runtime,slot.minusSeconds(1));
        auto.runAt(slot);auto.runAt(slot.plusSeconds(10));
        var id=jdbc.queryForObject("select command_id from tds_automatic_measurement_run where cultivation_id=?",UUID.class,target);
        awaitState(id.toString(),"NOT_DISPATCHED",10);
        assertNotNull(jdbc.queryForObject("select reason from tds_device_command where command_id=?",String.class,id));
        assertEquals(0,jdbc.queryForObject("select count(*) from tds_session s join tds_device_command c using(session_id) where c.command_id=?",Integer.class,id));
        assertEquals(1,jdbc.queryForObject("select count(*) from tds_device_command where cultivation_id=?",Integer.class,target));
    }
    record Reply(int status,JsonNode body){}
    Reply request(String path,String body,UUID key) throws Exception {
        try(var client=HttpClient.newHttpClient()) {
            var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+environment.getProperty("local.server.port")+path)).timeout(java.time.Duration.ofSeconds(8));
            b.header("Authorization", "Bearer " + com.juca.homewatermonitorbackend.security.AuthHttpFixture.token("http://127.0.0.1:" + environment.getProperty("local.server.port")));
            if(body!=null)b.header("Content-Type","application/json").header("Idempotency-Key",key.toString()).POST(HttpRequest.BodyPublishers.ofString(body));
            else b.GET();
            var response=client.send(b.build(),HttpResponse.BodyHandlers.ofByteArray());
            // 조회 응답은 타임스탬프 문자열을 포함하므로 wire 전용 UUID/문자 검증과 분리한다.
            return new Reply(response.statusCode(),tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.body()));
        }
    }
    Reply start(UUID key)throws Exception{return request("/api/devices/esp32-bench/measurement-commands","{\"type\":\"START\"}",key);}
    Reply command(String id)throws Exception{return request("/api/measurement-commands/"+id,null,null);}
    void awaitState(String id,String state,long seconds)throws Exception {
        long end=System.nanoTime()+seconds*1000000000L;
        while(System.nanoTime()<end){if(state.equals(command(id).body().get("commandState").asString()))return;Thread.sleep(50);}
        assertEquals(state,command(id).body().get("commandState").asString());
    }
    @Test void cultivationRestStartMqttResultAndQueriesKeepReservedTargetAfterClosure() throws Exception {
        UUID cultivation=UUID.randomUUID(),key=UUID.randomUUID();
        var created=request("/api/cultivations","{\"plantName\":\"basil\",\"name\":\"tank\",\"startedAt\":\"2026-10-01T00:00:00Z\"}",cultivation);
        assertEquals(201,created.status());String path="/api/cultivations/"+cultivation;
        try(var device=new ControlFakeDevice(uri())) {
            var first=request(path+"/measurement-commands","{\"deviceId\":\"esp32-bench\"}",key);assertEquals(202,first.status());
            String id=first.body().get("commandId").asString(),session=first.body().get("sessionId").asString();
            awaitState(id,"ACCEPTED",10);assertEquals(409,start(key).status());
            assertEquals(200,request(path+"/measurement-commands","{\"deviceId\":\"esp32-bench\"}",key).status());
            assertEquals(200,request(path+"/end","{}",UUID.randomUUID()).status());
            var result=(tools.jackson.databind.node.ObjectNode)tools.jackson.databind.json.JsonMapper.builder().build().readTree(java.nio.file.Files.readAllBytes(
                    java.nio.file.Path.of("../../docs/mqtt/v1/examples/session-result-normal.json")));
            result.put("sessionId",session).put("deviceId","esp32-bench").putNull("startedAt").put("calibrationVersion","sen0244-uncalibrated-v1");
            var m=new org.eclipse.paho.mqttv5.common.MqttMessage(codec.encode(result));m.setQos(1);
            var p=new org.eclipse.paho.mqttv5.common.packet.MqttProperties();p.setPayloadFormat(true);p.setContentType("application/json");m.setProperties(p);
            device.client.publish("home/water/v1/esp32-bench/events",m).waitForCompletion(5000);
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(6);
            while(request(path+"/measurement-sessions",null,null).body().isEmpty() && System.nanoTime()<deadline)Thread.sleep(25);
            var history=request(path+"/measurement-sessions",null,null);assertEquals(1,history.body().size());
            assertEquals(session,history.body().get(0).get("sessionId").asString());assertEquals("REQUESTED",history.body().get(0).get("timeBasis").asString());
            assertEquals(1,request(path+"/tds-trend",null,null).body().size());
            assertEquals(60,request("/api/measurement-sessions/"+session,null,null).body().get("measurements").size());
            synchronized(device) {
                device.status.put("state","IDLE").putNull("sessionId").put("sampleCount",0).put("finalized",false).put("round",0).put("attempt",0);
                device.status.putObject("lastRelease").put("sessionId",session).put("preservationResult","NORMAL").put("preservedSampleCount",60).put("reason","PRESERVATION_CONFIRMED");device.emit();
            }
            while(!"RELEASE_CONFIRMED".equals(command(id).body().get("deviceBuffer").asString()) && System.nanoTime()<deadline)Thread.sleep(25);
            assertEquals("RELEASE_CONFIRMED",command(id).body().get("deviceBuffer").asString());
        } finally {
            // Isolate a failed simulation from the other scenarios in this shared test database.
            jdbc.update("update tds_device_control set active_command_id=null,held_session_id=null where held_session_id in (select session_id from tds_device_command where cultivation_id=?)",cultivation);
        }
    }
    @Test void blockedControlStatusTransactionDoesNotBlockResultCommitAndPreservationConfirmation()throws Exception {
        var reserved=control.reserve(UUID.randomUUID(),"esp32-bench","START",null);
        var confirmation=new java.util.concurrent.CompletableFuture<JsonNode>();
        UUID resultId=UUID.randomUUID();
        var client=new org.eclipse.paho.mqttv5.client.MqttClient(uri(),"independence-"+UUID.randomUUID(),
                new org.eclipse.paho.mqttv5.client.persist.MemoryPersistence());
        try(var connection=dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try(var statement=connection.prepareStatement("select device_id from tds_device_control where device_id='esp32-bench' for update")) {
                statement.executeQuery().close();
            }
            var options=new org.eclipse.paho.mqttv5.client.MqttConnectionOptions();options.setCleanStart(true);client.connect(options);
            // Pinned Paho's String[]/listener overload recurses into itself; use its subscription overload.
            client.subscribe(new org.eclipse.paho.mqttv5.common.MqttSubscription[]{
                    new org.eclipse.paho.mqttv5.common.MqttSubscription("home/water/v1/esp32-bench/preservation",1)},
                    new org.eclipse.paho.mqttv5.client.IMqttMessageListener[]{(topic,message)->{
                var body=codec.tree(message.getPayload());
                if(resultId.toString().equals(body.get("sessionId").asString()))confirmation.complete(body);
            }});
            var status=(tools.jackson.databind.node.ObjectNode)codec.tree(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
                    "../../docs/mqtt/v1/examples/device-status-idle.json")));
            status.put("bootId",UUID.randomUUID().toString()).putNull("replyToProbeId").putNull("controlWindow");
            var properties=new org.eclipse.paho.mqttv5.common.packet.MqttProperties();
            properties.setPayloadFormat(true);properties.setContentType("application/json");properties.setMessageExpiryInterval(10L);
            var statusMessage=new org.eclipse.paho.mqttv5.common.MqttMessage(codec.encode(status));statusMessage.setQos(1);statusMessage.setProperties(properties);
            client.publish("home/water/v1/esp32-bench/status",statusMessage);
            long waitEnd=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(2);boolean waiting=false;
            while(System.nanoTime()<waitEnd) {
                waiting=jdbc.queryForObject("select exists(select 1 from pg_stat_activity where wait_event_type='Lock' and query like '%tds_device_control%')",Boolean.class);
                if(waiting)break;Thread.sleep(10);
            }
            assertTrue(waiting,"Actual control row lock contention must be present");
            var result=(tools.jackson.databind.node.ObjectNode)codec.tree(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
                    "../../docs/mqtt/v1/examples/session-result-empty.json")));
            result.put("sessionId",resultId.toString()).put("deviceId","esp32-bench");
            var resultMessage=new org.eclipse.paho.mqttv5.common.MqttMessage(codec.encode(result));resultMessage.setQos(1);resultMessage.setProperties(properties);
            client.publish("home/water/v1/esp32-bench/events",resultMessage);
            assertEquals("INCOMPLETE",confirmation.get(5,java.util.concurrent.TimeUnit.SECONDS).get("preservationResult").asString());
            assertEquals(1,jdbc.queryForObject("select count(*) from tds_session where session_id=?",Integer.class,resultId));
            connection.rollback();client.disconnect();
        } finally {
            try {if(client.isConnected())client.disconnect();client.close();}
            finally {control.finish(reserved.commandId(),com.juca.homewatermonitorbackend.session.application.control.ControlModel.State.NOT_DISPATCHED,"TEST_FINISHED");}
        }
    }
    @Test void realRestMqttCommandStatusRetryPreservationReleaseAndUnknownReconciliation()throws Exception {
        try(var device=new ControlFakeDevice(uri())) {
            UUID key=UUID.randomUUID();var first=start(key);assertEquals(202,first.status());
            String id=first.body().get("commandId").asString(),session=first.body().get("sessionId").asString();
            awaitState(id,"ACCEPTED",10);assertEquals(1,device.executions.get());
            var again=start(key);assertEquals(200,again.status());assertEquals(id,again.body().get("commandId").asString());
            assertEquals(409,start(UUID.randomUUID()).status());
            synchronized(device){device.status.put("state","MANUAL_WAIT").put("finalized",true).put("attempt",5);device.emit();}
            Thread.sleep(150);
            var retry=request("/api/devices/esp32-bench/measurement-commands","{\"type\":\"RETRY\",\"sessionId\":\""+session+"\"}",UUID.randomUUID());
            assertEquals(202,retry.status());String retryId=retry.body().get("commandId").asString();
            awaitState(retryId,"ACCEPTED",10);assertEquals(2,device.executions.get());assertEquals(session,retry.body().get("sessionId").asString());
            sessions.process(new SaveNormalTdsSessionCommand(UUID.fromString(session),"esp32-bench",null,1000,new java.math.BigDecimal("25"),"test",List.of()));
            synchronized(device){device.status.put("state","IDLE").putNull("sessionId").put("sampleCount",0).put("finalized",false).put("round",0).put("attempt",0);
                device.status.putObject("lastRelease").put("sessionId",session).put("preservationResult","INCOMPLETE").put("preservedSampleCount",0).put("reason","PRESERVATION_CONFIRMED");device.emit();}
            Thread.sleep(200);
            var view=command(retryId).body();assertEquals("INCOMPLETE",view.get("serverPreservation").asString());
            assertEquals("RELEASE_CONFIRMED",view.get("deviceBuffer").asString());
            device.ignoreStart=true;var unknown=start(UUID.randomUUID());assertEquals(202,unknown.status());
            String unknownId=unknown.body().get("commandId").asString();awaitState(unknownId,"UNKNOWN",15);
            assertEquals(409,start(UUID.randomUUID()).status());
            awaitState(unknownId,"NOT_EXECUTED",35);assertEquals(2,device.executions.get());assertEquals(3,device.startsReceived.get());
            assertEquals(404,request("/api/devices/not-registered/status",null,null).status());
            assertEquals(400,request("/api/devices/esp32-bench/measurement-commands","{\"type\":\"START\",\"extra\":1}",UUID.randomUUID()).status());
        }
    }
}
