package com.juca.homewatermonitorbackend.session.application.automatic;

import com.juca.homewatermonitorbackend.cultivation.application.CultivationService;
import com.juca.homewatermonitorbackend.session.application.control.*;
import com.juca.homewatermonitorbackend.session.infrastructure.persistence.AutomaticMeasurementRunStore;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@Testcontainers @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class AutomaticMeasurementIntegrationTest {
    @Container static final PostgreSQLContainer pg=new PostgreSQLContainer("postgres:18-alpine");
    @DynamicPropertySource static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",pg::getJdbcUrl);r.add("spring.datasource.username",pg::getUsername);r.add("spring.datasource.password",pg::getPassword);
    }
    @Autowired AutomaticMeasurementRunStore runs;
    @Autowired CommandTransactionService commands;
    @Autowired CultivationService cultivations;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationContext context;
    final Instant hour=Instant.parse("2026-10-29T03:00:00Z");
    UUID cultivation; String device;
    @BeforeEach void target() {
        cultivation=UUID.randomUUID();device="auto-"+UUID.randomUUID();
        cultivations.create(cultivation,new CultivationService.Create("basil","tank",hour.minusSeconds(3600),""));
    }
    AutomaticMeasurementService service() {
        var runtime=mock(ControlRuntime.class);
        when(runtime.submit(any(),eq(device),eq("START"),isNull(),eq(cultivation))).thenAnswer(i->
            new ControlRuntime.Submission(commands.reserve(i.getArgument(0),device,"START",null,cultivation),true));
        return new AutomaticMeasurementService(new AutomaticMeasurementProperties(true,device,cultivation),runs,runtime,hour.minusSeconds(1));
    }
    String outcome(Instant slot) {
        return jdbc.queryForObject("select outcome from tds_automatic_measurement_run where device_id=? and scheduled_at=?",String.class,device,java.sql.Timestamp.from(slot));
    }
    int count(String table) { return jdbc.queryForObject("select count(*) from "+table+" where device_id=?",Integer.class,device); }
    @Test void offByDefaultDoesNotRegisterATimer() {
        assertFalse(context.containsBean("hourlyMeasurementJob"));assertFalse(context.containsBean("automaticMeasurementService"));
    }
    @Test @Timeout(30) void concurrentServersAndRepeatedTicksProduceOnlyOneCommand() throws Exception {
        try(var executor=Executors.newFixedThreadPool(6)) {
            var start=new CountDownLatch(1);var futures=new ArrayList<Future<?>>();
            for(int i=0;i<6;i++)futures.add(executor.submit(()->{start.await();service().runAt(hour);return null;}));
            start.countDown();for(var future:futures)future.get(20,TimeUnit.SECONDS);
        }
        service().runAt(hour.plusSeconds(30));
        assertEquals(1,count("tds_automatic_measurement_run"));assertEquals(1,count("tds_device_command"));
        assertEquals("SUBMITTED",outcome(hour));
    }
    @Test void manualRequestAndHeldBufferPreventAutomaticStartWithoutChangingTheOriginal() {
        var manual=commands.reserve(UUID.randomUUID(),device,"START",null,cultivation);
        service().runAt(hour);service().runAt(hour);
        assertEquals("SKIPPED",outcome(hour));assertEquals(1,count("tds_device_command"));
        assertEquals(manual.commandId(),jdbc.queryForObject("select active_command_id from tds_device_control where device_id=?",UUID.class,device));
    }
    @Test void automaticWinnerUsesExistingBusyProtectionAgainstManualAndNextHour() {
        service().runAt(hour);
        assertEquals("CONTROL_BUSY",assertThrows(ControlException.class,()->commands.reserve(UUID.randomUUID(),device,"START",null,cultivation)).code());
        service().runAt(hour.plusSeconds(3600));
        assertEquals("SKIPPED",outcome(hour.plusSeconds(3600)));assertEquals(1,count("tds_device_command"));
        assertEquals(1,jdbc.queryForObject("select count(*) from tds_automatic_measurement_run where device_id=? and command_id is not null",Integer.class,device));
    }
    @Test void endedCultivationIsLoggedAndCreatesNoCommand() {
        cultivations.end(cultivation);service().runAt(hour);
        assertEquals("SKIPPED",outcome(hour));assertEquals(0,count("tds_device_command"));
        assertEquals("CULTIVATION_ENDED",jdbc.queryForObject("select reason from tds_automatic_measurement_run where device_id=?",String.class,device));
    }
    @Test void restartDoesNotReplayClaimWithNoCommandAndNextHourMarksItUnknown() {
        var key=UUID.randomUUID();assertTrue(runs.claim(device,cultivation,hour,key));
        service().runAt(hour);assertEquals(0,count("tds_device_command"));
        service().runAt(hour.plusSeconds(3600));
        assertEquals("UNKNOWN",outcome(hour));assertEquals(1,count("tds_device_command"));
    }
    @Test void restartCanLinkAnAlreadyCommittedCommandWithoutResendingIt() {
        var key=UUID.randomUUID();assertTrue(runs.claim(device,cultivation,hour,key));
        var original=commands.reserve(key,device,"START",null,cultivation);
        service().runAt(hour.plusSeconds(3600));
        assertEquals("SUBMITTED",outcome(hour));assertEquals(1,count("tds_device_command"));
        assertEquals(original.commandId(),jdbc.queryForObject("select command_id from tds_automatic_measurement_run where device_id=? and scheduled_at=?",UUID.class,device,java.sql.Timestamp.from(hour)));
    }
}
