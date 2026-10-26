package com.juca.homewatermonitorbackend.cultivation.application;

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
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class CultivationIntegrationTest {
    @Container static final PostgreSQLContainer pg=new PostgreSQLContainer("postgres:18-alpine");
    @DynamicPropertySource static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",pg::getJdbcUrl);r.add("spring.datasource.username",pg::getUsername);
        r.add("spring.datasource.password",pg::getPassword);
    }
    @Autowired CultivationService service;
    @Autowired JdbcTemplate jdbc;
    @org.springframework.boot.test.web.server.LocalServerPort int port;
    CultivationService.Create input() {
        return new CultivationService.Create(" 바질 ","베란다",Instant.parse("2026-10-01T00:00:00.123456Z"),null);
    }
    java.net.http.HttpResponse<String> http(String path,String method,String body,UUID key) throws Exception {
        var b=java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:"+port+path));
        if(key!=null)b.header("Idempotency-Key",key.toString());
        b.header("Content-Type","application/json");
        return java.net.http.HttpClient.newHttpClient().send(b.method(method,java.net.http.HttpRequest.BodyPublishers.ofString(body)).build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
    }
    @Test void httpCreationLifecycleAndInputErrorsMatchContract() throws Exception {
        UUID key=UUID.randomUUID();String path="/api/cultivations";
        String body="{\"plantName\":\"basil\",\"name\":\"first\",\"startedAt\":\"2026-10-01T00:00:00Z\"}";
        var first=http(path,"POST",body,key);assertEquals(201,first.statusCode());
        var json=tools.jackson.databind.json.JsonMapper.builder().build().readTree(first.body());
        assertEquals("ACTIVE",json.get("status").asString());
        assertEquals(200,http(path,"POST",body,key).statusCode());
        assertEquals(409,http(path,"POST",body.replace("basil","lettuce"),key).statusCode());
        assertEquals(200,http(path+"/"+key+"/end","POST","",null).statusCode());
        assertEquals(400,http(path,"POST",body,null).statusCode());
        assertEquals(400,http(path+"?limit=201","GET","",null).statusCode());
        assertEquals(404,http(path+"/"+UUID.randomUUID(),"GET","",null).statusCode());
    }
    @Test void createQueryEndAndReplayPreserveOriginalLifecycle() {
        UUID key=UUID.randomUUID();var first=service.create(key,input());
        assertTrue(first.created());assertEquals(key,first.cultivation().id());
        assertEquals("ACTIVE",service.get(key).status());
        assertTrue(service.list(200,0).stream().anyMatch(c->c.id().equals(key)));
        var ended=service.end(key);assertEquals("ENDED",ended.status());
        assertEquals(ended.endedAt(),service.end(key).endedAt());
        var replay=service.create(key,input());assertFalse(replay.created());assertEquals("ENDED",replay.cultivation().status());
        assertEquals(409,assertThrows(CultivationException.class,()->service.requireActive(key)).status());
        var changed=new CultivationService.Create("상추",input().name(),input().startedAt(),null);
        assertEquals("IDEMPOTENCY_CONFLICT",assertThrows(CultivationException.class,()->service.create(key,changed)).code());
    }
    @Test void concurrentRegistrationHasOneCreationAndConcurrentEndKeepsOneTimestamp() throws Exception {
        UUID key=UUID.randomUUID();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            var a=pool.submit(()->{gate.await();return service.create(key,input());});
            var b=pool.submit(()->{gate.await();return service.create(key,input());});gate.countDown();
            var x=a.get(10,TimeUnit.SECONDS);var y=b.get(10,TimeUnit.SECONDS);
            assertNotEquals(x.created(),y.created());assertEquals(x.cultivation(),y.cultivation());
            var c=pool.submit(()->service.end(key));var d=pool.submit(()->service.end(key));
            assertEquals(c.get(10,TimeUnit.SECONDS).endedAt(),d.get(10,TimeUnit.SECONDS).endedAt());
        }
    }
    @Test void invalidAndMissingInputsDoNotWrite() {
        assertThrows(IllegalArgumentException.class,()->service.create(UUID.randomUUID(),new CultivationService.Create("","x",Instant.now(),null)));
        assertThrows(IllegalArgumentException.class,()->service.create(UUID.randomUUID(),new CultivationService.Create("a","b",Instant.now().plusSeconds(60),null)));
        assertThrows(IllegalArgumentException.class,()->service.list(201,0));
        assertThrows(IllegalArgumentException.class,()->service.list(10,-1));
        assertEquals(404,assertThrows(CultivationException.class,()->service.get(UUID.randomUUID())).status());
        assertEquals(404,assertThrows(CultivationException.class,()->service.end(UUID.randomUUID())).status());
    }
    @Test void deferredCommitFailureRollsBackRegistration() {
        UUID key=UUID.randomUUID();
        jdbc.execute("create function cultivation_fail() returns trigger language plpgsql as $$ begin raise exception 'test commit'; end; $$");
        jdbc.execute("create constraint trigger ct_cultivation_fail after insert on cultivation deferrable initially deferred for each row execute function cultivation_fail()");
        try {
            assertThrows(RuntimeException.class,()->service.create(key,input()));
            assertEquals(0,jdbc.queryForObject("select count(*) from cultivation where cultivation_id=?",Integer.class,key));
        } finally {
            jdbc.execute("drop trigger ct_cultivation_fail on cultivation");jdbc.execute("drop function cultivation_fail()");
        }
        assertTrue(service.create(key,input()).created());
    }
}
