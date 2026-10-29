package com.juca.homewatermonitorbackend.session.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tds_session")
@Getter
@AllArgsConstructor
public class TdsSessionEntity {

    // 세션 식별자
    @Id
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    // 측정한 장치 식별자
    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    // 장치에서 측정을 시작한 시각
    @Column(name = "started_at")
    private Instant startedAt;

    // 서버가 세션 목록을 수신한 시각
    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    // 해당 세션에서 기대하는 원본 개수
    @Column(name = "expected_sample_count", nullable = false)
    private int expectedSampleCount;

    // 측정 간격: 밀리초 단위
    @Column(name = "sampling_interval_ms", nullable = false)
    private int samplingIntervalMs;

    // 실제 보존한 고유 원본 개수
    @Column(name = "preserved_sample_count", nullable = false)
    private int preservedSampleCount;

    // 안정화 원본 등을 제외한 통계 대상 개수
    @Column(name = "stats_sample_count", nullable = false)
    private int statsSampleCount;

    // 서버의 보존 결과: NORMAL 또는 INCOMPLETE
    @Column(name = "preservation_result", nullable = false, length = 16)
    private String preservationResult;

    // 실제 측정 온도가 아닌 계산에 사용한 가정 온도
    @Column(
            name = "temperature_assumed_c",
            nullable = true,
            precision = 5,
            scale = 2
    )
    private BigDecimal temperatureAssumedC;

    // 측정값에 적용된 교정 버전
    @Column(name = "calibration_version", nullable = false, length = 64)
    private String calibrationVersion;

    // 통계 대상 원본의 최솟값
    @Column(name = "min_tds_ppm", precision = 9, scale = 2)
    private BigDecimal minTdsPpm;

    // 통계 대상 원본의 최댓값
    @Column(name = "max_tds_ppm", precision = 9, scale = 2)
    private BigDecimal maxTdsPpm;

    // 통계 대상 원본의 평균
    @Column(name = "avg_tds_ppm", precision = 9, scale = 2)
    private BigDecimal avgTdsPpm;

    // 통계 대상 원본의 중앙값
    @Column(name = "median_tds_ppm", precision = 9, scale = 2)
    private BigDecimal medianTdsPpm;

    @Column(name = "temperature_source", length = 16)
    private String temperatureSource;
    @Column(name = "compensation_version", length = 64)
    private String compensationVersion;

    public TdsSessionEntity(UUID sessionId, String deviceId, Instant startedAt, Instant ingestedAt, int expectedSampleCount, int samplingIntervalMs, int preservedSampleCount, int statsSampleCount, String preservationResult, BigDecimal temperatureAssumedC, String calibrationVersion, BigDecimal minTdsPpm, BigDecimal maxTdsPpm, BigDecimal avgTdsPpm, BigDecimal medianTdsPpm) {
        this(sessionId, deviceId, startedAt, ingestedAt, expectedSampleCount, samplingIntervalMs, preservedSampleCount,
            statsSampleCount, preservationResult, temperatureAssumedC, calibrationVersion, minTdsPpm, maxTdsPpm,
            avgTdsPpm, medianTdsPpm, null, null);
    }
    public TdsSessionEntity temperatureContext(String source, String version) {
        this.temperatureSource = source; this.compensationVersion = version; return this;
    }

    protected TdsSessionEntity() {

    }
}
