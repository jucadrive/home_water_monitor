package com.juca.homewatermonitorbackend.cultivation.application;

import com.juca.homewatermonitorbackend.cultivation.domain.WaterMaintenance.*;
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
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@Testcontainers @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class WaterMaintenanceIntegrationTest {
    @Container static final PostgreSQLContainer pg=new PostgreSQLContainer("postgres:18-alpine");
    @DynamicPropertySource static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",pg::getJdbcUrl);r.add("spring.datasource.username",pg::getUsername);r.add("spring.datasource.password",pg::getPassword);
    }
    @Autowired CultivationService cultivations;
    @Autowired WaterMaintenanceService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    final Instant start=Instant.parse("2026-10-01T00:00:00Z");
    UUID cultivation() {
        UUID id=UUID.randomUUID();cultivations.create(id,new CultivationService.Create("basil","tank",start,null));return id;
    }
    WaterMaintenanceService.Create input(Type type,Scope scope,int days) {
        return new WaterMaintenanceService.Create(type,scope,start.plusSeconds(days*86400L),null,null);
    }
    @Test void timelineIsolationAndLastFullReplacementUseOccurrenceTime() {
        UUID a=cultivation(),b=cultivation();assertNull(service.lastFullReplacement(a));
        var full=service.create(a,UUID.randomUUID(),input(Type.REPLACE,Scope.FULL,1)).maintenance();
        service.create(a,UUID.randomUUID(),input(Type.REPLACE,Scope.PARTIAL,3));
        service.create(a,UUID.randomUUID(),input(Type.TOP_UP,null,4));
        service.create(a,UUID.randomUUID(),input(Type.NUTRIENT_ADD,null,5));
        service.create(b,UUID.randomUUID(),input(Type.REPLACE,Scope.FULL,6));
        assertEquals(full.occurredAt(),service.lastFullReplacement(a));assertEquals(4,service.list(a,50,0).size());
        assertEquals(Type.NUTRIENT_ADD,service.list(a,1,0).getFirst().type());
        assertEquals(1,service.list(b,50,0).size());assertTrue(service.list(a,50,4).isEmpty());
    }
    @Test void retriesConflictAndEndedActivityAreDistinct() {
        UUID a=cultivation(),b=cultivation(),key=UUID.randomUUID();var body=input(Type.TOP_UP,null,1);
        assertTrue(service.create(a,key,body).created());assertFalse(service.create(a,key,body).created());
        assertEquals(409,assertThrows(CultivationException.class,()->service.create(b,key,body)).status());
        cultivations.end(a);assertFalse(service.create(a,key,body).created());
        assertEquals("CULTIVATION_ENDED",assertThrows(CultivationException.class,()->service.create(a,UUID.randomUUID(),body)).code());
        assertEquals(404,assertThrows(CultivationException.class,()->service.list(UUID.randomUUID(),50,0)).status());
        assertThrows(IllegalArgumentException.class,()->service.create(b,UUID.randomUUID(),new WaterMaintenanceService.Create(Type.TOP_UP,null,start.minusSeconds(1),null,null)));
    }
    @Test void concurrentRegistrationsHaveOneDurableWinner() throws Exception {
        UUID a=cultivation(),key=UUID.randomUUID();var body=input(Type.TOP_UP,null,1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            var x=pool.submit(()->{gate.await();return service.create(a,key,body);});
            var y=pool.submit(()->{gate.await();return service.create(a,key,body);});gate.countDown();
            assertNotEquals(x.get(10,TimeUnit.SECONDS).created(),y.get(10,TimeUnit.SECONDS).created());
            assertEquals(1,service.list(a,50,0).size());
        }
    }
    @Test void closureSerializesWithRegistration() throws Exception {
        UUID a=cultivation();var tx=new TransactionTemplate(manager);
        try(var pool=Executors.newSingleThreadExecutor()) {
            Future<?> future=tx.execute(status->{
                cultivations.requireActive(a);
                var started=new CountDownLatch(1);
                var f=pool.submit(()->{started.countDown();return service.create(a,UUID.randomUUID(),input(Type.TOP_UP,null,1));});
                try {assertTrue(started.await(2,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}
                cultivations.end(a);return f;
            });
            var failure=assertThrows(ExecutionException.class,()->future.get(10,TimeUnit.SECONDS));
            assertInstanceOf(CultivationException.class,failure.getCause());assertTrue(service.list(a,50,0).isEmpty());
        }
    }
    @Test void commitFailureLeavesNoMaintenanceOrLastReplacement() {
        UUID a=cultivation(),key=UUID.randomUUID();var body=input(Type.REPLACE,Scope.FULL,1);
        jdbc.execute("create function maintenance_fail() returns trigger language plpgsql as $$ begin raise exception 'test commit'; end; $$");
        jdbc.execute("create constraint trigger ct_maintenance_fail after insert on water_maintenance deferrable initially deferred for each row execute function maintenance_fail()");
        try {
            assertThrows(RuntimeException.class,()->service.create(a,key,body));
            assertTrue(service.list(a,50,0).isEmpty());assertNull(service.lastFullReplacement(a));
        } finally {
            jdbc.execute("drop trigger ct_maintenance_fail on water_maintenance");jdbc.execute("drop function maintenance_fail()");
        }
        assertTrue(service.create(a,key,body).created());
    }
}
