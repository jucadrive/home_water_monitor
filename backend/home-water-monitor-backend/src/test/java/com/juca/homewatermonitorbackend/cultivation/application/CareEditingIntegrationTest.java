package com.juca.homewatermonitorbackend.cultivation.application;

import com.juca.homewatermonitorbackend.cultivation.domain.WaterMaintenance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class CareEditingIntegrationTest {
    @Container static final PostgreSQLContainer pg=new PostgreSQLContainer("postgres:18-alpine");
    @DynamicPropertySource static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",pg::getJdbcUrl);r.add("spring.datasource.username",pg::getUsername);r.add("spring.datasource.password",pg::getPassword);
    }
    @Autowired CultivationService cultivations;
    @Autowired WaterMaintenanceService maintenance;
    @Autowired CareEditingService edits;
    @Autowired JdbcTemplate jdbc;
    @org.springframework.boot.test.web.server.LocalServerPort int port;
    final Instant start=Instant.now().minusSeconds(86400).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    CultivationService.Create input() { return new CultivationService.Create("basil","first",start,"note"); }
    UUID create() { var id=UUID.randomUUID();cultivations.create(id,input());return id; }
    CareEditingService.Profile profile(long version,String name) { return new CareEditingService.Profile(version,"basil",name,""); }
    WaterMaintenanceService.Create water() { return new WaterMaintenanceService.Create(WaterMaintenance.Type.REPLACE,WaterMaintenance.Scope.FULL,start.plusSeconds(60),new BigDecimal("1000"),"water"); }
    CareEditingService.Correction correction(long v,Instant at) {
        return new CareEditingService.Correction(v,WaterMaintenance.Type.TOP_UP,null,at,new BigDecimal("50"),"corrected");
    }
    int receipts(UUID key) { return jdbc.queryForObject("select count(*) from care_edit_receipt where request_key=?",Integer.class,key); }
    @Test void profileKeepsIdentityAndOriginalRegistrationAndReplaysBeforeVersionCheck() {
        var id=create();var key=UUID.randomUUID();var before=cultivations.get(id);
        String response=edits.profile(id,key,profile(0,"renamed"));
        var after=cultivations.get(id);assertEquals(1,after.version());assertEquals("renamed",after.name());assertEquals("",after.memo());
        assertEquals(before.startedAt(),after.startedAt());assertEquals(before.createdAt(),after.createdAt());
        assertFalse(cultivations.create(id,input()).created());assertEquals(after,cultivations.get(id));
        edits.profile(id,UUID.randomUUID(),profile(1,"latest"));
        assertEquals(response,edits.profile(id,key,profile(0,"renamed")));assertEquals("latest",cultivations.get(id).name());
        assertEquals("IDEMPOTENCY_CONFLICT",assertThrows(CultivationException.class,()->edits.profile(id,key,profile(0,"other"))).code());
        assertEquals(1,receipts(key));
        assertNotNull(jdbc.queryForObject("select before_body from care_edit_receipt where request_key=?",String.class,key));
    }
    @Test void noOpAndConflictDoNotAddChangeOrReceiptOnConflict() {
        var id=create();var key=UUID.randomUUID();
        edits.profile(id,key,new CareEditingService.Profile(0," basil "," first "," note "));
        assertEquals(0,cultivations.get(id).version());assertNull(jdbc.queryForObject("select before_body from care_edit_receipt where request_key=?",String.class,key));
        var failed=UUID.randomUUID();assertEquals("VERSION_CONFLICT",assertThrows(CultivationException.class,()->edits.profile(id,failed,profile(1,"bad"))).code());
        assertEquals(0,receipts(failed));
    }
    @Test void managementCorrectionUpdatesReplacementQueryButNotInitialRegistration() {
        var c=create();var key=UUID.randomUUID();var m=maintenance.create(c,key,water()).maintenance();
        edits.correct(c,key,UUID.randomUUID(),correction(0,start.plusSeconds(120)));
        var after=edits.get(c,key);assertEquals(1,after.version());assertEquals(m.createdAt(),after.createdAt());assertNull(maintenance.lastFullReplacement(c));
        assertFalse(maintenance.create(c,key,water()).created());assertEquals(after,edits.get(c,key));
        var editKey=UUID.randomUUID();var cmd=correction(1,start.plusSeconds(180));var response=edits.correct(c,key,editKey,cmd);
        assertEquals(response,edits.correct(c,key,editKey,cmd));assertEquals(2,edits.get(c,key).version());
        assertEquals(404,assertThrows(CultivationException.class,()->edits.get(create(),key)).status());
    }
    @Test void correctionsAfterRegistrationAndEndAreAllowedWithinPeriodOnly() {
        var c=create();var m=maintenance.create(c,UUID.randomUUID(),water()).maintenance();
        var end=cultivations.end(c);assertEquals(1,end.version());
        edits.profile(c,UUID.randomUUID(),profile(1,"ended rename"));
        edits.correct(c,m.id(),UUID.randomUUID(),correction(0,end.endedAt()));
        assertThrows(IllegalArgumentException.class,()->edits.correct(c,m.id(),UUID.randomUUID(),correction(1,start.minusSeconds(1))));
        assertThrows(IllegalArgumentException.class,()->edits.correct(c,m.id(),UUID.randomUUID(),correction(1,end.endedAt().plusSeconds(1))));
        assertThrows(IllegalArgumentException.class,()->edits.correct(c,m.id(),UUID.randomUUID(),correction(1,Instant.now().plusSeconds(60))));
        assertEquals("CULTIVATION_ENDED",assertThrows(CultivationException.class,()->maintenance.create(c,UUID.randomUUID(),water())).code());
    }
    @Test void concurrentDifferentKeysHaveOneWinnerAndSameKeyOneReceipt() throws Exception {
        var id=create();try(var pool=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);var command=profile(0,"winner");
            Callable<String> change=()->{gate.await();try { edits.profile(id,UUID.randomUUID(),command);return "ok"; }catch(CultivationException e){return e.code();}};
            var a=pool.submit(change);var b=pool.submit(change);gate.countDown();
            assertEquals(Set.of("ok","VERSION_CONFLICT"),Set.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS)));
            var key=UUID.randomUUID();var next=profile(1,"same");
            var x=pool.submit(()->edits.profile(id,key,next));var y=pool.submit(()->edits.profile(id,key,next));
            assertEquals(x.get(10,TimeUnit.SECONDS),y.get(10,TimeUnit.SECONDS));assertEquals(1,receipts(key));assertEquals(2,cultivations.get(id).version());
        }
    }
    @Test void deferredFailureRollsBackChangeReceiptAndAudit() {
        var id=create();var key=UUID.randomUUID();
        jdbc.execute("create function edit_fail() returns trigger language plpgsql as $$ begin raise exception 'test commit'; end; $$");
        jdbc.execute("create constraint trigger ct_edit_fail after insert on care_edit_receipt deferrable initially deferred for each row execute function edit_fail()");
        try { assertThrows(RuntimeException.class,()->edits.profile(id,key,profile(0,"no commit")));assertEquals(0,receipts(key));assertEquals(0,cultivations.get(id).version()); }
        finally { jdbc.execute("drop trigger ct_edit_fail on care_edit_receipt");jdbc.execute("drop function edit_fail()"); }
        edits.profile(id,key,profile(0,"no commit"));assertEquals(1,receipts(key));
    }
    @Test void endAndProfileRaceNeverLoseLifecycleOrVersion() throws Exception {
        var id=create();try(var pool=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            var edit=pool.submit(()->{gate.await();try { edits.profile(id,UUID.randomUUID(),profile(0,"race"));return true; }
                catch(CultivationException e){assertEquals("VERSION_CONFLICT",e.code());return false;}});
            var end=pool.submit(()->{gate.await();return cultivations.end(id);});gate.countDown();
            boolean changed=edit.get(10,TimeUnit.SECONDS);end.get(10,TimeUnit.SECONDS);
            var result=cultivations.get(id);assertEquals("ENDED",result.status());assertEquals(changed?2:1,result.version());
        }
    }
    java.net.http.HttpResponse<String> http(String path,String method,String body,UUID key) throws Exception {
        var b=java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:"+port+path))
                .header("Authorization","Bearer "+com.juca.homewatermonitorbackend.security.AuthHttpFixture.token("http://localhost:"+port)).header("Content-Type","application/json");
        if(key!=null)b.header("Idempotency-Key",key.toString());
        return java.net.http.HttpClient.newHttpClient().send(b.method(method,java.net.http.HttpRequest.BodyPublishers.ofString(body)).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
    }
    @Test void strictHttpContractAndIsoTimestamps() throws Exception {
        var id=create();String path="/api/cultivations/"+id+"/profile";
        String body="{\"expectedVersion\":0,\"plantName\":\"mint\",\"name\":\"second\",\"memo\":\"\"}";
        var result=http(path,"PUT",body,UUID.randomUUID());assertEquals(200,result.statusCode(),result.body());
        var json=tools.jackson.databind.json.JsonMapper.builder().build().readTree(result.body());
        assertEquals("mint",json.get("plantName").asString());assertEquals(1,json.get("version").asLong());
        assertNotNull(Instant.parse(json.get("updatedAt").asString()));assertEquals("ACTIVE",json.get("status").asString());
        assertEquals(400,http(path,"PUT",body.replace(",\"memo\":\"\"",""),UUID.randomUUID()).statusCode());
        assertEquals(400,http(path,"PUT",body.replace("\"memo\":\"\"","\"memo\":null"),UUID.randomUUID()).statusCode());
        assertEquals(400,http(path,"PUT",body.replace("\"memo\":\"\"","\"memo\":\"\",\"startedAt\":null"),UUID.randomUUID()).statusCode());
        assertEquals(400,http(path,"PUT",body.replace("Version\":0","Version\":0.5"),UUID.randomUUID()).statusCode());
        assertEquals(409,http(path,"PUT",body,UUID.randomUUID()).statusCode());
        var m=maintenance.create(id,UUID.randomUUID(),water()).maintenance();String mp="/api/cultivations/"+id+"/water-maintenances/"+m.id();
        String correction="{\"expectedVersion\":0,\"type\":\"TOP_UP\",\"replacementScope\":null,\"occurredAt\":\""+start.plusSeconds(80)+"\",\"amountMl\":null,\"memo\":\"fixed\"}";
        assertEquals(200,http(mp,"PUT",correction,UUID.randomUUID()).statusCode());assertEquals(200,http(mp,"GET","",null).statusCode());
        assertEquals(400,http(mp,"PUT",correction.replace("null,\"memo","\"bad\",\"memo"),UUID.randomUUID()).statusCode());
        assertEquals(400,http(mp,"PUT",correction.replace(start.plusSeconds(80).toString(),"bad-date"),UUID.randomUUID()).statusCode());
        assertEquals(404,http("/api/cultivations/"+create()+"/water-maintenances/"+m.id(),"GET","",null).statusCode());
    }
}
