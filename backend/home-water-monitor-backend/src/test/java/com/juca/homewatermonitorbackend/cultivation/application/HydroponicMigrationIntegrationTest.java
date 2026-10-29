package com.juca.homewatermonitorbackend.cultivation.application;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class HydroponicMigrationIntegrationTest {
    @Container static final PostgreSQLContainer pg=new PostgreSQLContainer("postgres:18-alpine");
    @Test void upgradeFromV5PreservesEveryExistingColumnAndRow() {
        var source=new DriverManagerDataSource(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword());
        Flyway.configure().dataSource(source).target("5").load().migrate();var jdbc=new JdbcTemplate(source);
        UUID session=UUID.randomUUID(),redelivery=UUID.randomUUID(),command=UUID.randomUUID(),boot=UUID.randomUUID();
        jdbc.update("""
                insert into tds_session(session_id,device_id,started_at,ingested_at,expected_sample_count,sampling_interval_ms,
                preserved_sample_count,stats_sample_count,preservation_result,temperature_assumed_c,calibration_version)
                values (?,'legacy',null,now(),60,1000,1,0,'INCOMPLETE',25,'old-version')
                """,session);
        jdbc.update("insert into tds_measurement(session_id,seq,elapsed_ms,tds_ppm,is_warmup,raw_voltage_mv) values (?,1,1000,123.45,true,null)",session);
        jdbc.update("insert into tds_session_missing_sample select ?,generate_series(2,60)",session);
        jdbc.update("insert into tds_session_redelivery values (?,?,now(),1,1,0)",redelivery,session);
        jdbc.update("insert into tds_measurement_redelivery values (?,1,'IDENTICAL',1000,1000,123.45,123.45,true,true,null,null)",redelivery);
        jdbc.update("insert into tds_device_control(device_id,current_boot_id,held_session_id,updated_at) values ('legacy',?,?,now())",boot,session);
        jdbc.update("""
                insert into tds_device_command(command_id,idempotency_key,device_id,command_type,normalized_request,session_id,command_state,requested_at,updated_at)
                values (?,?,'legacy','START','{"type":"START"}',?,'REQUESTED',now(),now())
                """,command,UUID.randomUUID(),session);
        jdbc.update("update tds_device_control set active_command_id=? where device_id='legacy'",command);
        jdbc.update("insert into tds_device_observation(device_id,boot_id,status_seq,snapshot,received_at) values ('legacy',?,1,'{}',now())",boot);
        jdbc.update("insert into tds_device_boot values ('legacy',?,now(),null)",boot);
        var columns=new LinkedHashMap<String,String>();var before=new LinkedHashMap<String,List<String>>();
        var tables=jdbc.queryForList("select table_name from information_schema.tables where table_schema='public' and table_name like 'tds_%' order by table_name",String.class);
        for(String table:tables) {
            var names=jdbc.queryForList("select column_name from information_schema.columns where table_schema='public' and table_name=? order by ordinal_position",String.class,table);
            columns.put(table,String.join(",",names));before.put(table,snapshot(jdbc,table,columns.get(table)));
        }
        var upgraded=Flyway.configure().dataSource(source).load();assertEquals(5,upgraded.migrate().migrationsExecuted);upgraded.validate();
        for(String table:tables)assertEquals(before.get(table),snapshot(jdbc,table,columns.get(table)),table);
        assertEquals(1,jdbc.queryForObject("select count(*) from tds_session where temperature_source is null and compensation_version is null and temperature_assumed_c=25",Integer.class));
        assertEquals(1,jdbc.queryForObject("select count(*) from tds_measurement where temperature_c is null and temperature_elapsed_ms is null",Integer.class));
        assertEquals(0,jdbc.queryForObject("select count(*) from cultivation",Integer.class));
        assertEquals(1,jdbc.queryForObject("select count(*) from tds_device_command where cultivation_id is null",Integer.class));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update(
                "update tds_device_command set cultivation_id=? where command_id=?",UUID.randomUUID(),command));
    }
    List<String> snapshot(JdbcTemplate jdbc,String table,String columns) {
        // Identifiers come only from the isolated test schema, never from request input.
        return jdbc.queryForList("select to_jsonb(t)::text from (select "+columns+" from "+table+") t order by 1",String.class);
    }
}
