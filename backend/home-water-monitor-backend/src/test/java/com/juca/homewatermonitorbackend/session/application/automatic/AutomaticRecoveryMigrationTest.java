package com.juca.homewatermonitorbackend.session.application.automatic;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class AutomaticRecoveryMigrationTest {
    @Container static final PostgreSQLContainer pg=new PostgreSQLContainer("postgres:18-alpine");
    @Test void v12PreservesExistingHourlyLedgerAndAddsAnEmptyRecoveryLedger() {
        var source=new DriverManagerDataSource(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword());
        Flyway.configure().dataSource(source).target("11").load().migrate();
        var jdbc=new JdbcTemplate(source);var plant=UUID.randomUUID();
        jdbc.update("""
            insert into cultivation(cultivation_id,plant_name,name,started_at,memo,created_at,updated_at,
                initial_plant_name,initial_name,initial_memo)
            values (?,'basil','tank',now(),'memo',now(),now(),'basil','tank','memo')
            """,plant);
        jdbc.update("""
            insert into tds_automatic_measurement_run(device_id,scheduled_at,cultivation_id,idempotency_key,outcome,reason)
            values ('sensor',date_trunc('hour',now()),?,?,'SKIPPED','CONTROL_BUSY')
            """,plant,UUID.randomUUID());
        var before=jdbc.queryForObject("select to_jsonb(a)::text from tds_automatic_measurement_run a",String.class);
        var flyway=Flyway.configure().dataSource(source).load();
        assertEquals(1,flyway.migrate().migrationsExecuted);flyway.validate();
        assertEquals(before,jdbc.queryForObject("select to_jsonb(a)::text from tds_automatic_measurement_run a",String.class));
        assertEquals(0,jdbc.queryForObject("select count(*) from tds_automatic_recovery",Integer.class));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("""
            insert into tds_automatic_recovery values ('missing',date_trunc('hour',now()),?,?,?,null,'PREPARED',null,now(),now())
            """,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID()));
    }
}
