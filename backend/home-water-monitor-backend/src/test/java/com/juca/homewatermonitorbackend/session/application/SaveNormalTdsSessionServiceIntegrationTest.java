package com.juca.homewatermonitorbackend.session.application;

import com.juca.homewatermonitorbackend.measurement.infrastructure.persistence.TdsMeasurementEntity;
import com.juca.homewatermonitorbackend.measurement.infrastructure.persistence.TdsMeasurementJpaRepository;
import com.juca.homewatermonitorbackend.session.infrastructure.persistence.TdsSessionEntity;
import com.juca.homewatermonitorbackend.session.infrastructure.persistence.TdsSessionJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// 웹 서버 없이 실제 Spring 서비스와 DB 설정을 함께 실행
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers // 테스트용 컨테이너의 시작과 종료를 관리
public class SaveNormalTdsSessionServiceIntegrationTest {

    // 기존 개발 DB와 별개인 테스트용 PostgreSQL
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    // 이 테스트의 DB 접속 정보를 컨테이너 기준으로 덮어씀
    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    // 직접 생성하지 않고 Spring의 트랜잭션 처리가 적용된 서비스 사용
    @Autowired
    private SaveNormalTdsSessionService service;

    // 저장 후 DB에서 세션과 원본을 다시 조회
    @Autowired
    private TdsSessionJpaRepository sessionJpaRepository;

    @Autowired
    private TdsMeasurementJpaRepository measurementJpaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 테스트에 @Transactional을 붙이지 않아 서비스의 실제 커밋을 확인
    @Test
    void savesNormalSessionAndAllMeasurementsAfterCommit() {

        // 준비: 테스트마다 새로운 세션 식별자와 가상 원본 생성
        UUID sessionId = UUID.randomUUID();
        SaveNormalTdsSessionCommand command = createCommand(sessionId);

        // 실행: 서비스가 자체 트랜잭션에서 저장하고 커밋
        UUID savedSessionId = service.save(command);

        // 검증: 반환한 식별자가 요청한 세션과 같은지 확인
        assertEquals(sessionId, savedSessionId);

        // 서비스 트랜잭션 종료 후 DB에서 세션을 다시 조회
        TdsSessionEntity session = sessionJpaRepository.findById(sessionId)
                .orElseThrow(() -> new AssertionError("커밋된 세션이 없습니다."));

        // 세션 식별 정보와 메타데이터가 보존됐는지 확인
        assertEquals(sessionId, session.getSessionId());
        assertEquals(command.deviceId(), session.getDeviceId());
        assertEquals(command.startedAt(), session.getStartedAt());
        assertNotNull(session.getIngestedAt());
        assertEquals(1_000, session.getSamplingIntervalMs());
        assertEquals(new BigDecimal("25.00"), session.getTemperatureAssumedC());
        assertEquals(command.calibrationVersion(), session.getCalibrationVersion());

        // 전체 원본과 통계 대상의 개수가 구분되어 저장됐는지 확인
        assertEquals(60, session.getExpectedSampleCount());
        assertEquals(60, session.getPreservedSampleCount());
        assertEquals(50, session.getStatsSampleCount());
        assertEquals("NORMAL", session.getPreservationResult());

        // 안정화 구간의 300ppm이 통계에 포함되지 않았는지 확인
        BigDecimal expectedStatistics = new BigDecimal("151.30");
        assertEquals(expectedStatistics, session.getMinTdsPpm());
        assertEquals(expectedStatistics, session.getMaxTdsPpm());
        assertEquals(expectedStatistics, session.getAvgTdsPpm());
        assertEquals(expectedStatistics, session.getMedianTdsPpm());

        // 특정 세션의 원본만 seq 오름차순으로 다시 조회
        List<TdsMeasurementEntity> measurements = measurementJpaRepository.findBySessionIdOrderBySeqAsc(sessionId);

        assertEquals(60, measurements.size());
        assertEquals(60L, measurementJpaRepository.countBySessionId(sessionId));

        // 원본 60개 각각의 내용과 순번을 검사
        for (int index = 0; index < measurements.size(); index++) {
            TdsMeasurementEntity measurement = measurements.get(index);

            int expectedSeq = index + 1;
            boolean expectedWarmup = expectedSeq <= 10;
            BigDecimal expectedPpm = expectedWarmup ? new BigDecimal("300.00") : new BigDecimal("151.30");

            // 자동 생성 ID와 세션 연결, 측정 내용이 보존됐는지 확인
            assertNotNull(measurement.getMeasurementId());
            assertEquals(sessionId, measurement.getSessionId());
            assertEquals(expectedSeq, measurement.getSeq());
            assertEquals(expectedSeq * 1_000, measurement.getElapsedMs());
            assertEquals(expectedWarmup, measurement.isWarmup());
            assertEquals(expectedPpm, measurement.getTdsPpm());

            // 이번 가상 데이터에서는 선택값인 전압을 제공하지 않음
            assertNull(measurement.getRawVoltageMv());
        }
    }

    // 저장 도중 DB 오류가 발생하면 이번 변경 전체가 롤백되는지 확인
    @Test
    void rollsBackEntireSessionWhenMeasurementInsertFails() {

        // 이전에 커밋한 다른 세션은 오류 이후에도 유지되어야 함
        UUID preservedSessionId = UUID.randomUUID();
        service.save(createCommand(preservedSessionId));

        // 이번에 저장을 실패시킬 새로운 세션
        UUID failedSessionId = UUID.randomUUID();
        SaveNormalTdsSessionCommand command = createCommand(failedSessionId);

        // 실패 대상 세션의 seq=40만 거절하는 테스트 전용 제약
        // UUID는 테스트가 직접 생성한 값만 SQL에 사용
        jdbcTemplate.execute("""
                ALTER TABLE tds_measurement
                ADD CONSTRAINT ck_test_reject_seq_40
                CHECK (
                    session_id <> '%s'::uuid
                    OR seq <> 40
                 )
                """.formatted(failedSessionId));

        try {
            // 서비스의 입력 검증은  통과하고 실제 DB 저장에서 실패해야 함
            DataIntegrityViolationException exception = assertThrows(
                    DataIntegrityViolationException.class,
                    () -> service.save(command)
            );

            // 의도한 테스트 제약 때문에 실패했는지 확인
            assertTrue(
                    exception.getMostSpecificCause()
                            .getMessage()
                            .contains("ck_test_reject_seq_40")
            );

            // 서비스의 롤백이 끝난 뒤 이번 세션이 없는지 DB에서 확인
            assertFalse(sessionJpaRepository.existsById(failedSessionId));

            // 오류 전에 저장을 시도한 원본도 남아 있으면 안 됨
            assertEquals(0L, measurementJpaRepository.countBySessionId(failedSessionId));

            // 이전에 커밋한 다른 세션과 원본은 그대로 유지
            TdsSessionEntity preservedSession = sessionJpaRepository.findById(preservedSessionId)
                    .orElseThrow(() -> new AssertionError("기존 세션이 사라졌습니다."));

            assertEquals("NORMAL", preservedSession.getPreservationResult());
            assertEquals(60, preservedSession.getPreservedSampleCount());
            assertEquals(50, preservedSession.getStatsSampleCount());
            assertEquals(new BigDecimal("151.30"), preservedSession.getAvgTdsPpm());
            assertEquals(60L, measurementJpaRepository.countBySessionId(preservedSessionId));
        } finally {
            // 검증에 실패하더라도 임시 제약을 제거해 다른  테스트에 영향 방지
            jdbcTemplate.execute("""
                    ALTER TABLE tds_measurement
                    DROP CONSTRAINT ck_test_reject_seq_40
                    """);
        }
    }

    // 모든 원본 저장 후 커밋이 실패해도 이번 변경 전체가 사라지는지 확인
    @Test
    void rollsBackEntireSessionWhenCommitFails() {

        // 이번 테스트에서만 커밋을 거절할 세션
        UUID failedSessionId = UUID.randomUUID();
        SaveNormalTdsSessionCommand command = createCommand(failedSessionId);

        // 실행 시점에 원본 개수를 확인하고 의도적으로 실패시키는 DB 함수
        jdbcTemplate.execute("""
            CREATE FUNCTION test_reject_session_commit()
            RETURNS trigger
            LANGUAGE plpgsql
            AS $$
            DECLARE
                sample_count BIGINT;
            BEGIN
                -- 같은 트랜잭션에서 저장한 원본 개수를 확인
                SELECT COUNT(*)
                INTO sample_count
                FROM tds_measurement
                WHERE session_id = NEW.session_id;

                -- 오류 메시지에 원본 개수를 넣어 60개 저장 이후인지 검증
                RAISE EXCEPTION
                    'test_commit_rejected_after_%_samples', sample_count
                    USING ERRCODE = '23514';
            END;
            $$;
            """);

        try {
            // 세션 INSERT 후 등록되지만 실행은 트랜잭션 종료까지 지연
            // 테스트가 생성한 UUID에 해당하는 세션만 대상으로 지정
            jdbcTemplate.execute("""
                CREATE CONSTRAINT TRIGGER ct_test_reject_session_commit
                AFTER INSERT ON tds_session
                DEFERRABLE INITIALLY DEFERRED
                FOR EACH ROW
                WHEN (NEW.session_id = '%s'::uuid)
                EXECUTE FUNCTION test_reject_session_commit()
                """.formatted(failedSessionId));

            // 커밋 실패가 호출자에게 예외로 전달되어야 하며 정상 반환하면 안 됨
            RuntimeException exception = assertThrows(
                    RuntimeException.class,
                    () -> service.save(command)
            );

            // 커밋 오류는 여러 예외로 감싸질 수 있으므로 실제 DB 원인 검사
            Throwable cause =
                    NestedExceptionUtils.getMostSpecificCause(exception);

            SQLException sqlException = assertInstanceOf(
                    SQLException.class,
                    cause
            );

            // 임시 함수에서 지정한 제약 위반 오류인지 확인
            assertEquals("23514", sqlException.getSQLState());

            // 원본 60개가 모두 보이는 시점에 발생한 오류인지 확인
            assertTrue(
                    sqlException.getMessage()
                            .contains("test_commit_rejected_after_60_samples")
            );

            // 커밋 실패 처리 후 세션과 통계가 남아 있지 않아야 함
            assertFalse(sessionJpaRepository.existsById(failedSessionId));

            // 저장을 끝냈던 원본 60개도 전부 롤백되어야 함
            assertEquals(
                    0L,
                    measurementJpaRepository.countBySessionId(failedSessionId)
            );
        } finally {
            // 함수를 참조하는 트리거를 먼저 제거
            jdbcTemplate.execute("""
                DROP TRIGGER IF EXISTS ct_test_reject_session_commit
                ON tds_session
                """);

            // 테스트용 DB 함수도 제거해 다음 테스트에 영향 방지
            jdbcTemplate.execute("""
                DROP FUNCTION IF EXISTS test_reject_session_commit()
                """);
        }
    }

    // 합의한 정상 가상 세션: 안정화 10개와 통계 대상 50개
    private SaveNormalTdsSessionCommand createCommand(UUID sessionId) {
        List<SaveNormalTdsSessionCommand.Measurement> measurements = new ArrayList<>();

        for (int seq = 1; seq <= 60; seq++) {
            boolean warmup = seq <= 10;

            BigDecimal ppm = warmup ? new BigDecimal("300.0") : new BigDecimal("151.3");

            // 1초 시점부터 60초 시점까지의 가상 측정
            measurements.add(
                    new SaveNormalTdsSessionCommand.Measurement(
                            seq,
                            seq * 1_000,
                            ppm,
                            warmup,
                            null
                    )
            );
        }

        // 실제 장치 측정이 아닌 테스트용 메타데이터
        return new SaveNormalTdsSessionCommand(
                sessionId,
                "test-device-01",
                Instant.parse("2026-10-19T00:00:00Z"),
                1_000,
                new BigDecimal("25.00"),
                "test-calibration-v1",
                measurements
        );
    }
}
