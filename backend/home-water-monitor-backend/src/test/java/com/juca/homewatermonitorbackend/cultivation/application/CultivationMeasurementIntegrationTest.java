package com.juca.homewatermonitorbackend.cultivation.application;

import com.juca.homewatermonitorbackend.session.application.*;
import com.juca.homewatermonitorbackend.session.application.control.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@Testcontainers @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class CultivationMeasurementIntegrationTest {
    @Container static final PostgreSQLContainer pg=new PostgreSQLContainer("postgres:18-alpine");
    @DynamicPropertySource static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",pg::getJdbcUrl);r.add("spring.datasource.username",pg::getUsername);r.add("spring.datasource.password",pg::getPassword);
    }
    @Autowired CultivationService cultivations;
    @Autowired CommandTransactionService control;
    @Autowired ProcessTdsSessionService sessions;
    @Autowired MeasurementQueryService query;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    UUID cultivation() {
        UUID id=UUID.randomUUID();cultivations.create(id,new CultivationService.Create("basil","tank",Instant.parse("2026-10-01T00:00:00Z"),null));return id;
    }
    ControlCommand reserve(UUID c) { return control.reserve(UUID.randomUUID(),"d-"+UUID.randomUUID(),"START",null,c); }
    SaveNormalTdsSessionCommand result(UUID session,String device,String version,int count) {
        var samples=IntStream.rangeClosed(1,count).mapToObj(i->new SaveNormalTdsSessionCommand.Measurement(i,i*1000,new BigDecimal("100"),i<=10,null)).toList();
        return new SaveNormalTdsSessionCommand(session,device,null,1000,new BigDecimal("25"),version,samples);
    }
    @Test void reservationAndLateResultKeepTargetWithoutCreatingPrematureSession() {
        UUID a=cultivation(),b=cultivation();var command=reserve(a);
        assertEquals(0,jdbc.queryForObject("select count(*) from tds_session where session_id=?",Integer.class,command.sessionId()));
        cultivations.end(a);
        assertEquals(command.commandId(),control.reserve(command.idempotencyKey(),command.deviceId(),"START",null,a).commandId());
        assertThrows(CultivationException.class,()->control.reserve(UUID.randomUUID(),command.deviceId(),"START",null,a));
        var input=result(command.sessionId(),command.deviceId(),"sen0244-uncalibrated-v1",60);
        sessions.process(input);sessions.process(input);
        var detail=query.detail(command.sessionId());assertEquals(a,detail.session().cultivationId());
        assertEquals("REQUESTED",detail.session().timeBasis());assertNull(detail.session().startedAt());
        assertEquals(60,detail.measurements().size());assertEquals(50,detail.session().statsSampleCount());
        assertEquals(0,new BigDecimal("100").compareTo(detail.session().medianTdsPpm()));
        assertEquals(1,query.list(a,false,true,50,0).size());assertTrue(query.list(b,false,false,50,0).isEmpty());
        assertEquals(1,jdbc.queryForObject("select count(*) from tds_session_redelivery where session_id=?",Integer.class,command.sessionId()));
    }
    @Test void rejectsSourceMismatchAndKeepsIncompleteAndLegacySemantics() {
        UUID a=cultivation();var command=reserve(a);
        assertThrows(IllegalArgumentException.class,()->sessions.process(result(command.sessionId(),"other","test",0)));
        assertThrows(ControlException.class,()->query.detail(command.sessionId()));
        sessions.process(result(command.sessionId(),command.deviceId(),"sen0244-uncalibrated-v1",3));
        assertNull(query.detail(command.sessionId()).session().medianTdsPpm());assertNull(query.latest(a));
        var simulated=reserve(a);sessions.process(result(simulated.sessionId(),simulated.deviceId(),"simulated-v1",60));
        assertEquals("SIMULATED",query.detail(simulated.sessionId()).session().source());assertNull(query.latest(a));
        UUID legacy=UUID.randomUUID();sessions.process(result(legacy,"legacy","test",0));
        assertNull(query.detail(legacy).session().cultivationId());assertEquals("INGESTED",query.detail(legacy).session().timeBasis());
        assertTrue(query.list(null,true,false,200,0).stream().anyMatch(s->s.sessionId().equals(legacy)));
    }
    @Test void startIdempotencyDistinguishesAllTargetCombinations() {
        UUID a=cultivation(),b=cultivation();var command=reserve(a);
        assertThrows(ControlException.class,()->control.reserve(command.idempotencyKey(),command.deviceId(),"START",null,b));
        assertThrows(ControlException.class,()->control.reserve(command.idempotencyKey(),command.deviceId(),"START",null));
        var old=reserve(null);
        assertThrows(ControlException.class,()->control.reserve(old.idempotencyKey(),old.deviceId(),"START",null,a));
        assertThrows(CultivationException.class,()->reserve(UUID.randomUUID()));
        jdbc.update("update tds_device_command set command_state='ACCEPTED',target_boot_id=?,window_id=?,dispatch_payload='{}',dispatching_at=now() where command_id=?",
                UUID.randomUUID(),UUID.randomUUID(),command.commandId());
        cultivations.end(a);
        // A retained original result can be retried after closure, without assigning a new cultivation.
        var retry=control.reserve(UUID.randomUUID(),command.deviceId(),"RETRY",command.sessionId());
        assertEquals(command.sessionId(),retry.sessionId());assertNull(retry.cultivationId());
    }
    @Test void commandCommitFailureRollsBackTargetAndReservation() {
        UUID c=cultivation(),key=UUID.randomUUID();String device="rollback-"+UUID.randomUUID();
        jdbc.execute("create function target_fail() returns trigger language plpgsql as $$ begin raise exception 'test commit'; end; $$");
        jdbc.execute("create constraint trigger ct_target_fail after insert on tds_device_command deferrable initially deferred for each row execute function target_fail()");
        try {
            assertThrows(RuntimeException.class,()->control.reserve(key,device,"START",null,c));
            assertEquals(0,jdbc.queryForObject("select count(*) from tds_device_command where idempotency_key=?",Integer.class,key));
            assertEquals(0,jdbc.queryForObject("select count(*) from tds_device_control where device_id=?",Integer.class,device));
        } finally {jdbc.execute("drop trigger ct_target_fail on tds_device_command");jdbc.execute("drop function target_fail()");}
        assertEquals(c,control.reserve(key,device,"START",null,c).cultivationId());
    }
    @Test void closureWinsAgainstConcurrentStartReservation() throws Exception {
        UUID c=cultivation();var tx=new TransactionTemplate(manager);
        try(var pool=Executors.newSingleThreadExecutor()) {
            Future<?> f=tx.execute(status->{
                cultivations.requireActive(c);
                var began=new CountDownLatch(1);
                var pending=pool.submit(()->{began.countDown();return reserve(c);});
                try {assertTrue(began.await(2,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}
                cultivations.end(c);return pending;
            });
            assertInstanceOf(CultivationException.class,assertThrows(ExecutionException.class,()->f.get(10,TimeUnit.SECONDS)).getCause());
            assertEquals(0,jdbc.queryForObject("select count(*) from tds_device_command where cultivation_id=?",Integer.class,c));
        }
    }
}
