package com.juca.homewatermonitorbackend.session.application;

import com.juca.homewatermonitorbackend.measurement.infrastructure.persistence.TdsMeasurementRedeliveryJpaRepository;
import com.juca.homewatermonitorbackend.session.infrastructure.persistence.TdsSessionRedeliveryJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

// 테스트 자체를 트랜잭션으로 감싸지 않아 서비스의 커밋·롤백을 독립 조회로 확인한다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "water-monitor.redelivery.lock-timeout-ms=1500")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class RedeliveryIntegrationSupport {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired SaveNormalTdsSessionService initialSave;
    @Autowired ProcessNormalTdsSessionRedeliveryService redelivery;
    @Autowired JdbcTemplate jdbc;
    @Autowired TdsSessionRedeliveryJpaRepository headers;
    @Autowired TdsMeasurementRedeliveryJpaRepository details;
    @Autowired PlatformTransactionManager transactions;

    SaveNormalTdsSessionCommand storedCommand() {
        var command = TdsRedeliveryFixtures.command();
        initialSave.save(command);
        return command;
    }

    // 세션·원본의 모든 컬럼을 비교하여 의도하지 않은 변경을 탐지한다.
    Snapshot snapshot(UUID id) {
        return new Snapshot(jdbc.queryForMap("select * from tds_session where session_id = ?", id),
                jdbc.queryForList("select * from tds_measurement where session_id = ? order by seq", id));
    }

    void assertUnchanged(UUID id, Snapshot before) {
        assertEquals(before, snapshot(id));
    }

    long detailCount(UUID sessionId) {
        return jdbc.queryForObject("""
                select count(*) from tds_measurement_redelivery d join tds_session_redelivery h
                on h.redelivery_id = d.redelivery_id where h.session_id = ?
                """, Long.class, sessionId);
    }

    record Snapshot(Map<String, Object> session, List<Map<String, Object>> measurements) { }
}
