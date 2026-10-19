package com.juca.homewatermonitorbackend.session.application;

import com.juca.homewatermonitorbackend.measurement.domain.TdsStatisticsCalculator;
import com.juca.homewatermonitorbackend.measurement.infrastructure.persistence.TdsMeasurementEntity;
import com.juca.homewatermonitorbackend.measurement.infrastructure.persistence.TdsMeasurementJpaRepository;
import com.juca.homewatermonitorbackend.session.infrastructure.persistence.TdsSessionEntity;
import com.juca.homewatermonitorbackend.session.infrastructure.persistence.TdsSessionPersistenceAdapter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// 정상 신규 세션의 검증·통계 계산·저장을 조율하는 서비스
@Service
public class SaveNormalTdsSessionService {

    // 현재 정상 저장 경로에서 요구하는 측정 개수와 간격
    private static final int EXPECTED_SAMPLE_COUNT = 60;
    private static final int SAMPLING_INTERVAL_MS = 1_000;

    // 신규 세션 저장과 원본 저장을 담당하는 객체
    private final TdsSessionPersistenceAdapter sessionPersistenceAdapter;
    private final TdsMeasurementJpaRepository measurementJpaRepository;

    // 외부 자원에 의존하지 않는 순수 통계 계산기
    private final TdsStatisticsCalculator statisticsCalculator = new TdsStatisticsCalculator();

    // Spring이 등록된 어댑터와 Repository를 생성자에 주입
    public SaveNormalTdsSessionService(
            TdsSessionPersistenceAdapter sessionPersistenceAdapter,
            TdsMeasurementJpaRepository measurementJpaRepository
    ) {
        this.sessionPersistenceAdapter = sessionPersistenceAdapter;
        this.measurementJpaRepository = measurementJpaRepository;
    }

    // 세션과 전체 원본 저장을 하나의 트랜잭션으로 처리
    @Transactional
    public UUID save(SaveNormalTdsSessionCommand command) {

        // DB 저장 전에 이 정상 경로가 처리할 수 있는 입력인지 확인
        validate(command);

        // 안정화 원본은 보존하되 통계 계산에서는 제외
        List<BigDecimal> statisticsValues = command.measurements().stream()
                .filter(measurement -> !measurement.isWarmup())
                .map(SaveNormalTdsSessionCommand.Measurement::tdsPpm)
                .toList();

        // 현재 경로에서는 통계 대상이 없는 목록을 처리하지 않음
        if (statisticsValues.isEmpty()) {
            throw new IllegalArgumentException(
                    "첫 정상 저장 경로에는 통계 대상 측정이 필요합니다."
            );
        }

        // 세션 메타데이터와 계산한  통계를 저장용 엔티티로 변환
        TdsSessionEntity session = new TdsSessionEntity(
                command.sessionId(),
                command.deviceId(),
                command.startedAt(),
                Instant.now(),  // 서버 처리 시각
                EXPECTED_SAMPLE_COUNT,
                command.samplingIntervalMs(),
                command.measurements().size(),  // 보존할 전체 원본 개수
                statisticsValues.size(),    // 안정화를 제외한 통계 대상 개수
                "NORMAL",
                command.temperatureAssumedC(),
                command.calibrationVersion(),
                statisticsCalculator.minimum(statisticsValues),
                statisticsCalculator.maximum(statisticsValues),
                statisticsCalculator.average(statisticsValues),
                statisticsCalculator.median(statisticsValues)
        );

        // 안정화 구간을 포함한 전체 원본을 저장용 엔티티로 변환
        List<TdsMeasurementEntity> measurements = command.measurements().stream()
                .map(measurement -> new TdsMeasurementEntity(
                        command.sessionId(),    // 모든 원본을 같은 세션에 연결
                        measurement.seq(),
                        measurement.elapsedMs(),
                        measurement.tdsPpm(),
                        measurement.isWarmup(),
                        measurement.rawVoltageMv()
                ))
                .toList();

        // 세션을 먼저 등록하고, 이를 참조하는 원본을 같은 트랜잭션에서 저장
        sessionPersistenceAdapter.insert(session);
        measurementJpaRepository.saveAll(measurements);

        // 실제 커밋은 이 메서드를 감싸는 Spring 트랜잭션 경계에서 수행
        return command.sessionId();
    }

    // 첫 정상 저장 경로의 기본 입력 조건 금증
    private void validate(SaveNormalTdsSessionCommand command) {

        // 요청과 세션 식별자는 반드시 필요
        if (command == null || command.sessionId() == null) {
            throw new IllegalArgumentException("세션 ID가 필요합니다.");
        }

        // 필수 메타데이터가 현재 DB 컬럼에 저장 가능한지 확인
        requireText(command.deviceId(), 64, "deviceId");
        requireText(command.calibrationVersion(), 64, "calibrationVersion");
        requireDecimal(command.temperatureAssumedC(), 5, "temperatureAssumedC");

        // 현재 정상 저장 경로는 1초 간격 측정만 처리
        if (command.samplingIntervalMs() != SAMPLING_INTERVAL_MS) {
            throw new IllegalArgumentException("첫 정상 저장 경로의 측정 간격은 1000ms입니다.");
        }

        // 불완전 목록은 이후 별도 보존 경로에서 처리
        if (command.measurements().size() != EXPECTED_SAMPLE_COUNT) {
            throw new IllegalArgumentException("첫 정상 저장 경로에는 원본 60개가  필요합니다.");
        }

        // 이미 등장한 seq를 기억해 목록 내부 중복 검사
        Set<Integer> sequences = new HashSet<>();

        for (SaveNormalTdsSessionCommand.Measurement measurement : command.measurements()) {

            // 개수뿐 아니라 필요한 순번 범위도 확인
            if (measurement.seq() < 1 || measurement.seq() > EXPECTED_SAMPLE_COUNT) {
                throw new IllegalArgumentException("seq는 1부터 60까지여야 합니다.");
            }

            // Set.add()는 같은 값이 이미 존재하면 false 반환
            if (!sequences.add(measurement.seq())) {
                throw new IllegalArgumentException("목록 안에 중복 seq가 있습니다: " + measurement.seq());
            }

            // 측정 시작 이후 경과시간은 음수일 수 없음
            if (measurement.elapsedMs() < 0) {
                throw new IllegalArgumentException("elapsedMs는 음수일 수 없습니다.");
            }

            // TDS는 필수이며 NUMERIC(9, 2)에 저장 가능해야 함
            requireDecimal(measurement.tdsPpm(), 9, "tdsPpm");

            // 원본 전압은 선택값이므로 존재할 때만 검사
            if (measurement.rawVoltageMv() != null) {
                requireDecimal(measurement.rawVoltageMv(), 9, "rawVoltageMv");
            }
        }
    }

    // 필수 문자열의 공백 여부와 DB 컬럼 길이 검사
    private void requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(field + "는 비어 있지 않은 " + maxLength + "자 이하 문자열이어야 합니다.");
        }
    }

    // 현재 DB의 NUMERIC(precision, 2)에 저장 가능한 수치인지 검사
    private void requireDecimal(BigDecimal value, int precision, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + "가 필요합니다.");
        }

        // 151.300처럼 불필요한 끝자리 0은 허용하고 실제 소수 자릿수 검사
        if (value.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException(field + "는 소수 둘째 자리까지 표현할 수 있습니다.");
        }

        // 소수 두 자리를 제외한 정수부의 허용 범위 계산
        BigDecimal limit = BigDecimal.TEN.pow(precision - 2);

        // 양수와 음수 모두 절대새값으로 저장 범위 확인
        if (value.abs().compareTo(limit) >= 0) {
            throw new IllegalArgumentException(field + "가 DB에 저장 가능한 범위를 벗어났습니다.");
        }
    }
}
