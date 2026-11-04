package com.juca.homewatermonitorbackend.cultivation.application;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class CareEditingMigrationTest {
    @Container static final PostgreSQLContainer pg=new PostgreSQLContainer("postgres:18-alpine");
    @Test void v10RowsRetainEveryColumnAndAcquireInitialInputAndZeroVersion() {
        var source=new DriverManagerDataSource(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword());
        Flyway.configure().dataSource(source).target("10").load().migrate();var jdbc=new JdbcTemplate(source);
        var c=UUID.randomUUID();var m=UUID.randomUUID();
        jdbc.update("""
                insert into cultivation values (?,'basil','name',now()-interval '2 days',null,'memo',now(),now())
                """,c);
        jdbc.update("""
                insert into water_maintenance values (?,?,'REPLACE','FULL',now()-interval '1 day',1000,'water',now())
                """,m,c);
        var beforeC=jdbc.queryForObject("select to_jsonb(c)::text from cultivation c",String.class);
        var beforeM=jdbc.queryForObject("select to_jsonb(m)::text from water_maintenance m",String.class);
        var flyway=Flyway.configure().dataSource(source).load();assertEquals(2,flyway.migrate().migrationsExecuted);flyway.validate();
        assertEquals(beforeC,jdbc.queryForObject("""
                select (to_jsonb(c)-'version'-'initial_plant_name'-'initial_name'-'initial_memo')::text from cultivation c
                """,String.class));
        assertEquals(beforeM,jdbc.queryForObject("""
                select (to_jsonb(m)-'version'-'updated_at'-'initial_type'-'initial_scope'-'initial_occurred_at'-'initial_amount_ml'-'initial_memo')::text
                from water_maintenance m
                """,String.class));
        assertTrue(jdbc.queryForObject("select version=0 and initial_name=name and initial_plant_name=plant_name and initial_memo=memo from cultivation",Boolean.class));
        assertTrue(jdbc.queryForObject("""
                select version=0 and initial_type=type and initial_scope=replacement_scope and initial_occurred_at=occurred_at
                and initial_amount_ml=amount_ml and initial_memo=memo and updated_at=created_at from water_maintenance
                """,Boolean.class));
        assertEquals(0,jdbc.queryForObject("select count(*) from care_edit_receipt",Integer.class));
    }
}
