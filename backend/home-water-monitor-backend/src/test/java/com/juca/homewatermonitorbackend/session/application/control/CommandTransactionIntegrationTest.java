package com.juca.homewatermonitorbackend.session.application.control;

import com.juca.homewatermonitorbackend.session.infrastructure.mqtt.ControlMessageCodec;
import com.juca.homewatermonitorbackend.session.infrastructure.persistence.DeviceControlStore;
import com.juca.homewatermonitorbackend.session.application.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import static com.juca.homewatermonitorbackend.session.application.control.ControlModel.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@Testcontainers @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class CommandTransactionIntegrationTest {
    @Container static final PostgreSQLContainer pg=new PostgreSQLContainer("postgres:18-alpine");
    @DynamicPropertySource static void db(DynamicPropertyRegistry r){r.add("spring.datasource.url",pg::getJdbcUrl);r.add("spring.datasource.username",pg::getUsername);r.add("spring.datasource.password",pg::getPassword);}
    @Autowired CommandTransactionService tx;
    @Autowired DeviceControlStore store;
    @Autowired JdbcTemplate jdbc;
    @Autowired ProcessTdsSessionService sessions;
    final ControlMessageCodec codec=new ControlMessageCodec();
    ObjectNode idle(String device) throws Exception {
        var n=(ObjectNode)codec.tree(Files.readAllBytes(Path.of("../../docs/mqtt/v1/examples/device-status-idle.json")));
        n.put("deviceId",device).put("bootId",UUID.randomUUID().toString());return n;
    }
    Status status(ObjectNode n){return codec.decodeStatus(codec.encode(n),n.get("deviceId").asString());}
    String device(){return "test-"+UUID.randomUUID();}
    ControlCommand dispatched(String device,ObjectNode n) {
        var c=tx.reserve(UUID.randomUUID(),device,"START",null);tx.probing(c.commandId());
        assertTrue(tx.observe(status(n),true,UUID.randomUUID(),Instant.now()));
        return tx.dispatch(c.commandId(),status(n),"{}");
    }
    @Test void initialProbeDoesNotInterruptRequestAndNoSessionIsPrematurelyCreated() throws Exception {
        String d=device();var n=idle(d);var c=dispatched(d,n);
        assertEquals(State.DISPATCHING,c.commandState());
        assertEquals(0,jdbc.queryForObject("select count(*) from tds_session where session_id=?",Integer.class,c.sessionId()));
        assertEquals(c.commandId(),tx.reserve(c.idempotencyKey(),d,"START",null).commandId());
        assertThrows(ControlException.class,()->tx.reserve(c.idempotencyKey(),d,"RETRY",c.sessionId()));
        assertThrows(ControlException.class,()->tx.reserve(UUID.randomUUID(),d,"START",null));
    }
    @Test void timeoutNeedsFreshProbeAndPreservesEarlierWindow() throws Exception {
        String d=device();var n=idle(d);var c=dispatched(d,n);
        tx.finish(c.commandId(),State.UNKNOWN,"TIMEOUT");
        n.put("statusSeq",2);
        tx.observe(status(n),false,UUID.randomUUID(),Instant.now());
        assertEquals(State.UNKNOWN,store.command(c.commandId()).commandState());
        n.put("statusSeq",3);
        tx.observe(status(n),true,UUID.randomUUID(),Instant.now());
        assertEquals(State.NOT_EXECUTED,store.command(c.commandId()).commandState());
        assertEquals(c.windowId(),store.command(c.commandId()).windowId());
        assertNotNull(tx.reserve(UUID.randomUUID(),d,"START",null));
    }
    @Test void acceptedExecutionAndDbPreservationBothPrecedeSlotRelease() throws Exception {
        String d=device();var n=idle(d);var c=dispatched(d,n);
        n.put("statusSeq",2).putNull("controlWindow").put("state","MEASURING").put("sessionId",c.sessionId().toString()).put("round",1);
        n.set("lastExecution",codec.tree(codec.encode(new Execution(c.commandId(),"START",c.sessionId(),c.windowId()))));
        tx.observe(status(n),false,UUID.randomUUID(),Instant.now());
        assertEquals(State.ACCEPTED,store.command(c.commandId()).commandState());
        n.put("statusSeq",3).put("state","IDLE").putNull("sessionId").put("round",0);
        n.set("lastRelease",codec.tree(codec.encode(new Release(c.sessionId(),"INCOMPLETE",0,"PRESERVATION_CONFIRMED"))));
        tx.observe(status(n),false,UUID.randomUUID(),Instant.now());
        assertThrows(ControlException.class,()->tx.reserve(UUID.randomUUID(),d,"START",null));
        sessions.process(new SaveNormalTdsSessionCommand(c.sessionId(),d,null,1000,new java.math.BigDecimal("25"),"test",List.of()));
        n.put("statusSeq",4);tx.observe(status(n),true,UUID.randomUUID(),Instant.now());
        assertNotNull(tx.reserve(UUID.randomUUID(),d,"START",null));
        assertEquals(State.ACCEPTED,store.command(c.commandId()).commandState());
        var released=store.command(c.commandId()).releaseConfirmedAt();assertNotNull(released);
        n.put("bootId",UUID.randomUUID().toString()).put("statusSeq",1).putNull("lastRelease").putNull("lastExecution");
        tx.observe(status(n),true,UUID.randomUUID(),Instant.now());
        assertEquals(State.ACCEPTED,store.command(c.commandId()).commandState());
        assertEquals(released,store.command(c.commandId()).releaseConfirmedAt());
    }
    @Test void rebootAndOldBootReportsCannotEraseInterruptedOutcome() throws Exception {
        String d=device();var n=idle(d);var c=dispatched(d,n);var old=n.deepCopy();
        tx.finish(c.commandId(),State.UNKNOWN,"TIMEOUT");
        n.put("bootId",UUID.randomUUID().toString());
        assertFalse(tx.observe(status(n),false,UUID.randomUUID(),Instant.now()));
        assertTrue(tx.observe(status(n),true,UUID.randomUUID(),Instant.now()));
        assertEquals(State.INTERRUPTED_REBOOT,store.command(c.commandId()).commandState());
        old.put("statusSeq",100);
        assertFalse(tx.observe(status(old),true,UUID.randomUUID(),Instant.now()));
        assertEquals(n.get("bootId").asString(),store.observation(d).status().bootId().toString());
    }
    @Test void duplicateAndBackwardStatusDoNotExtendProbeConfirmation() throws Exception {
        String d=device();var n=idle(d);dispatched(d,n);var before=store.observation(d);
        assertFalse(tx.observe(status(n),true,UUID.randomUUID(),Instant.now()));
        assertEquals(before,store.observation(d));
    }
    @Test void restartClosesOnlyUndispatchedRequestsAndMarksDispatchUnknown() throws Exception {
        String a=device(),b=device();var requested=tx.reserve(UUID.randomUUID(),a,"START",null);
        var sent=dispatched(b,idle(b));tx.recover();
        assertEquals(State.NOT_DISPATCHED,store.command(requested.commandId()).commandState());
        assertEquals(State.UNKNOWN,store.command(sent.commandId()).commandState());
    }
    @Test void actualCommitFailureRollsBackReservationAndDoesNotConsumeIdempotencyKey() {
        UUID key=UUID.randomUUID();String d=device();
        jdbc.execute("create function control_commit_fail() returns trigger language plpgsql as $$ begin raise exception 'control_commit_test' using errcode='23514'; end; $$");
        jdbc.execute("create constraint trigger ct_control_commit_fail after insert on tds_device_command deferrable initially deferred for each row execute function control_commit_fail()");
        try {assertThrows(RuntimeException.class,()->tx.reserve(key,d,"START",null));assertNull(store.byKey(key));}
        finally {jdbc.execute("drop trigger ct_control_commit_fail on tds_device_command");jdbc.execute("drop function control_commit_fail()");}
        assertNotNull(tx.reserve(key,d,"START",null));
    }
    @Test void concurrentSameKeyAcrossDevicesHasOneDurableWinner() throws Exception {
        UUID key=UUID.randomUUID();
        try(var workers=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var gate=new java.util.concurrent.CountDownLatch(1);
            var a=workers.submit(()->{gate.await();return tx.reserve(key,device(),"START",null);});
            var b=workers.submit(()->{gate.await();return tx.reserve(key,device(),"START",null);});gate.countDown();
            int winners=0;
            for(var f:List.of(a,b))try{f.get(6,java.util.concurrent.TimeUnit.SECONDS);winners++;}catch(java.util.concurrent.ExecutionException expected){assertTrue(store.keyConflict(expected)||expected.getCause() instanceof ControlException);}
            assertEquals(1,winners);assertNotNull(store.byKey(key));
        }
    }
}
