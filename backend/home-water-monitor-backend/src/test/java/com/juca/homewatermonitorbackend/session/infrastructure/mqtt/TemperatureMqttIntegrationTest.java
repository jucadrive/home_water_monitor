package com.juca.homewatermonitorbackend.session.infrastructure.mqtt;

import com.juca.homewatermonitorbackend.session.application.MeasurementQueryService;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(90)
class TemperatureMqttIntegrationTest extends MqttIntegrationSupport {
    @Autowired MeasurementQueryService query;
    @Override void openDevice() throws Exception {
        super.openDevice(); device.subscribe("home/water/v2/esp32-water-01/preservation",1).waitForCompletion(5000);
    }
    ObjectNode temperatureInput(String suffix) throws Exception {
        var root=TemperatureMqttTest.input(suffix); root.put("sessionId",UUID.randomUUID().toString()); return root;
    }
    void sendV2(ObjectNode root) throws Exception {
        var message=new MqttMessage(MqttSessionCodecTest.JSON.writeValueAsBytes(root)); message.setQos(1);
        var p=new MqttProperties(); p.setContentType("application/json");p.setPayloadFormat(true);message.setProperties(p);
        device.publish("home/water/v2/esp32-water-01/events",message).waitForCompletion(5000);
        assertNotNull(completions.poll(15,TimeUnit.SECONDS));
    }
    @ParameterizedTest @ValueSource(strings={"normal","incomplete","empty"})
    void committedTemperatureCanBeQueriedAndIdenticalRetransmissionConfirmed(String suffix) throws Exception {
        var root=temperatureInput(suffix); sendV2(root);var ack=confirmation();
        assertEquals(2,ack.get("schemaVersion").intValue());
        assertEquals(root.get("measurements").size(),count("tds_measurement",id(root)));
        var detail=query.detail(id(root));
        assertEquals("SIMULATED",detail.session().temperatureSource()); assertNull(detail.session().temperatureAssumedC());
        assertEquals("sen0244-voltage-atc-v1",detail.session().compensationVersion());
        if(!detail.measurements().isEmpty()) assertEquals(0,new BigDecimal("25").compareTo(detail.measurements().getFirst().temperatureC()));
        sendV2(root); var again=confirmation();
        assertEquals(root.get("measurements").size(),again.get("comparison").get("identicalCount").intValue());
        assertEquals(0,again.get("comparison").get("conflictCount").intValue());
    }
    @Test void changedTemperatureIsDiagnosedWithoutOverwritingOriginal() throws Exception {
        var root=temperatureInput("normal");sendV2(root);confirmation();
        ((ObjectNode)root.get("measurements").get(0)).put("temperatureC",26);
        sendV2(root);var ack=confirmation();assertEquals(1,ack.get("comparison").get("conflictCount").intValue());
        assertEquals(0,new BigDecimal("25").compareTo(query.detail(id(root)).measurements().getFirst().temperatureC()));
        assertEquals(0,new BigDecimal("26").compareTo(jdbc.queryForObject(
            "select m.incoming_temperature_c from tds_measurement_redelivery m join tds_session_redelivery s using (redelivery_id) where s.session_id=? and m.seq=1",BigDecimal.class,id(root))));
    }
    @Test void changedTemperatureTimestampIsAlsoConflict() throws Exception {
        var root=temperatureInput("normal");sendV2(root);confirmation();
        var m=(ObjectNode)root.get("measurements").get(0);m.put("temperatureElapsedMs",m.get("elapsedMs").intValue()-1);
        sendV2(root);assertEquals(1,confirmation().get("comparison").get("conflictCount").intValue());
    }
    @Test void changedSourceOrInvalidSampleHasNoPreservationConfirmation() throws Exception {
        var root=temperatureInput("normal");sendV2(root);confirmation();root.put("temperatureSource","DS18B20");
        sendV2(root);noConfirmation(); assertEquals(0,count("tds_session_redelivery",id(root)));
        root.put("sessionId",UUID.randomUUID().toString());((ObjectNode)root.get("measurements").get(59)).putNull("temperatureC");
        sendV2(root);noConfirmation();assertEquals(0,count("tds_session",id(root)));
    }
    @Test void adcWithSimulatedTemperatureIsExcludedButMeasuredTemperatureCanEnterRealTrend() throws Exception {
        var root=temperatureInput("normal");root.put("calibrationVersion","sen0244-uncalibrated-v1");
        sendV2(root);confirmation();UUID simulated=id(root);
        assertTrue(query.list(null,true,true,200,0).stream().noneMatch(s->s.sessionId().equals(simulated)));
        root.put("sessionId",UUID.randomUUID().toString()).put("temperatureSource","DS18B20");
        sendV2(root);confirmation();UUID measured=id(root);
        assertTrue(query.list(null,true,true,200,0).stream().anyMatch(s->s.sessionId().equals(measured)));
    }
    @Test void v1SessionCannotBeReinterpretedAsV2MeasuredTemperature() throws Exception {
        var legacy=input("normal");legacy.put("calibrationVersion","simulated-v1");send(legacy);confirmation();
        var incoming=temperatureInput("normal");incoming.put("sessionId",id(legacy).toString());
        sendV2(incoming);noConfirmation();
        var stored=query.detail(id(legacy));assertNull(stored.session().temperatureSource());
        assertEquals(0,new BigDecimal("25").compareTo(stored.session().temperatureAssumedC()));
        assertTrue(stored.measurements().stream().allMatch(m->m.temperatureC()==null));
        assertEquals(0,count("tds_session_redelivery",id(legacy)));
    }
}
