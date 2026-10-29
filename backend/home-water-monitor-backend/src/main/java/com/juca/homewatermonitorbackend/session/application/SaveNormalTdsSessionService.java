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
import java.util.List;
import java.util.UUID;

// 정상 신규 세션의 검증·통계 계산·저장을 조율하는 서비스
@Service
public class SaveNormalTdsSessionService {

    // 현재 정상 저장 경로에서 요구하는 측정 개수
    private static final int EXPECTED_SAMPLE_COUNT = 60;

    // 신규 세션 저장과 원본 저장을 담당하는 객체
    private final TdsSessionPersistenceAdapter sessionPersistenceAdapter;
    private final TdsMeasurementJpaRepository measurementJpaRepository;
    private final NormalTdsSessionInputValidator inputValidator;

    // 외부 자원에 의존하지 않는 순수 통계 계산기
    private final TdsStatisticsCalculator statisticsCalculator = new TdsStatisticsCalculator();

    // Spring이 등록된 어댑터와 Repository를 생성자에 주입
    public SaveNormalTdsSessionService(
            TdsSessionPersistenceAdapter sessionPersistenceAdapter,
            TdsMeasurementJpaRepository measurementJpaRepository,
            NormalTdsSessionInputValidator inputValidator
    ) {
        this.sessionPersistenceAdapter = sessionPersistenceAdapter;
        this.measurementJpaRepository = measurementJpaRepository;
        this.inputValidator = inputValidator;
    }

    // 세션과 전체 원본 저장을 하나의 트랜잭션으로 처리
    @Transactional
    public UUID save(SaveNormalTdsSessionCommand command) {

        // DB 저장 전에 이 정상 경로가 처리할 수 있는 입력인지 확인
        inputValidator.validate(command);

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
                        measurement.rawVoltageMv(), measurement.temperatureC(), measurement.temperatureElapsedMs()
                ))
                .toList();

        // 세션을 먼저 등록하고, 이를 참조하는 원본을 같은 트랜잭션에서 저장
        sessionPersistenceAdapter.insert(session.temperatureContext(command.temperatureSource(), command.compensationVersion()));
        measurementJpaRepository.saveAll(measurements);

        // 실제 커밋은 이 메서드를 감싸는 Spring 트랜잭션 경계에서 수행
        return command.sessionId();
    }

}
