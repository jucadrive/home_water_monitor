package com.juca.homewatermonitorbackend.session.infrastructure.mqtt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(90)
class MqttSessionIntegrationTest extends MqttIntegrationSupport {
    @ParameterizedTest @ValueSource(strings = {"normal", "incomplete", "empty"})
    void realBrokerAndDatabasePreserveThenConfirm(String suffix) throws Exception {
        var root = input(suffix);
        send(root);
        var ack = confirmation();
        int count = root.get("measurements").size();
        assertEquals(id(root).toString(), ack.get("sessionId").asString());
        assertEquals(count, ack.get("preservedSampleCount").intValue());
        assertEquals(count == 60 ? "NORMAL" : "INCOMPLETE", ack.get("preservationResult").asString());
        assertTrue(ack.get("comparison").isNull());
        // 별도 JDBC 연결에서 조회해 실제 커밋이 끝났음을 확인한다.
        assertEquals(1, count("tds_session", id(root)));
        assertEquals(count, count("tds_measurement", id(root)));
        assertEquals(60 - count, count("tds_session_missing_sample", id(root)));
        send(root);
        ack = confirmation();
        assertEquals(count, ack.get("comparison").get("identicalCount").intValue());
        assertEquals(0, ack.get("comparison").get("conflictCount").intValue());
        assertEquals(1, count("tds_session_redelivery", id(root)));
    }

    @Test void conflictKeepsOriginalAndCommitsDiagnosticsBeforeConfirmation() throws Exception {
        var root = input("incomplete");
        send(root);
        confirmation();
        var before = jdbc.queryForMap("select * from tds_measurement where session_id = ?", id(root));
        ((ObjectNode) root.get("measurements").get(0)).put("tdsPpm", new BigDecimal("999.99"));
        send(root);
        var ack = confirmation();
        assertEquals(1, ack.get("comparison").get("conflictCount").intValue());
        assertEquals(before, jdbc.queryForMap("select * from tds_measurement where session_id = ?", id(root)));
        assertEquals(1, count("tds_session_redelivery", id(root)));
    }

    @Test void incompleteAdditionAndRemovalAreRejectedWithoutConfirmation() throws Exception {
        var root = input("incomplete");
        send(root);
        confirmation();
        var samples = (ArrayNode) root.get("measurements");
        var second = ((ObjectNode) samples.get(0)).deepCopy().put("seq", 2);
        samples.add(second);
        send(root);
        noConfirmation();
        samples.removeAll();
        send(root);
        noConfirmation();
        assertEquals(1, count("tds_measurement", id(root)));
        assertEquals(0, count("tds_session_redelivery", id(root)));
    }

    @Test void retainedAndInvalidContentTypeDoNotReachStorage() throws Exception {
        var root = input("empty");
        send(root, true, "application/json");
        noConfirmation();
        send(root, false, "text/plain");
        noConfirmation();
        assertEquals(0, count("tds_session", id(root)));
    }

    @Test void actualWriteFailureHasNoConfirmationAndCanBeRetried() throws Exception {
        var root = input("incomplete");
        jdbc.execute("alter table tds_measurement add constraint ck_mqtt_test check (seq <> 1) not valid");
        try {
            send(root);
            noConfirmation();
            assertEquals(0, count("tds_session", id(root)));
            assertEquals(0, count("tds_measurement", id(root)));
        } finally { jdbc.execute("alter table tds_measurement drop constraint ck_mqtt_test"); }
        send(root);
        confirmation();
    }

    @Test void actualDeferredCommitFailureHasNoConfirmationAndRollsBackEverything() throws Exception {
        var root = input("incomplete");
        jdbc.execute("""
                create function mqtt_test_commit_failure() returns trigger language plpgsql as $$
                begin raise exception 'mqtt_test_commit_failure' using errcode = '23514'; end; $$
                """);
        jdbc.execute("""
                create constraint trigger ct_mqtt_test_commit_failure after insert on tds_session
                deferrable initially deferred for each row execute function mqtt_test_commit_failure()
                """);
        try {
            send(root);
            noConfirmation();
            assertEquals(0, count("tds_session", id(root)));
            assertEquals(0, count("tds_measurement", id(root)));
            assertEquals(0, count("tds_session_missing_sample", id(root)));
        } finally {
            jdbc.execute("drop trigger ct_mqtt_test_commit_failure on tds_session");
            jdbc.execute("drop function mqtt_test_commit_failure()");
        }
        send(root);
        confirmation();
    }

    @Test void publishingFailureKeepsCommitAndDeviceRedeliveryRecoversConfirmation() throws Exception {
        var root = input("incomplete");
        mqttOutboundManager.stop();
        try {
            send(root);
            noConfirmation();
            assertEquals(1, count("tds_session", id(root)));
            assertEquals(1, count("tds_measurement", id(root)));
        } finally { mqttOutboundManager.start(); }
        await(mqttOutboundManager::isConnected);
        send(root);
        var ack = confirmation();
        assertEquals(1, ack.get("comparison").get("identicalCount").intValue());
        assertEquals(1, count("tds_measurement", id(root)));
    }

    @Test void brokerRestartReconnectsAndResubscribesWithoutApplicationRestart() throws Exception {
        int before = probe.subscribed.get();
        closeDevice();
        device = null;
        // Docker Desktop은 컨테이너 재시작 때 무작위 포트를 바꿀 수 있어 브로커 프로세스만 재시작한다.
        assertEquals(0, broker.execInContainer("sh", "-c", "kill -TERM $(pidof mosquitto)").getExitCode());
        var ports = broker.getDockerClient().inspectContainerCmd(broker.getContainerId()).exec()
                .getNetworkSettings().getPorts().getBindings();
        assertEquals(broker.getMappedPort(1883).toString(),
                ports.get(com.github.dockerjava.api.model.ExposedPort.tcp(1883))[0].getHostPortSpec(),
                "broker endpoint must remain stable across restart");
        await(() -> probe.subscribed.get() > before && mqttOutboundManager.isConnected());
        openDevice();
        var root = input("empty");
        send(root);
        confirmation();
        assertEquals(1, count("tds_session", id(root)));
    }
}
