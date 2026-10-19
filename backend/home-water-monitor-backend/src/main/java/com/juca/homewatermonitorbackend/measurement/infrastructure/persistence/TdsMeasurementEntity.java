package com.juca.homewatermonitorbackend.measurement.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "tds_measurement")
@Getter
public class TdsMeasurementEntity {

    // DB가 INSERT 시 생성하는 내부 식별자
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "measurement_id")
    private Long measurementId;

    // 이 원본이 소속된 세션
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    // 세션 안에서 측정을 구분하는 순번
    @Column(name = "seq", nullable = false)
    private int seq;

    // 측정 시작 이후 경과시간
    @Column(name = "elapsed_ms", nullable = false)
    private int elapsedMs;

    // TDS 측정값
    @Column(name = "tds_ppm", nullable = false, precision = 9, scale = 2)
    private BigDecimal tdsPpm;

    // 초기 안정화 구간에 해당하는지
    @Column(name = "is_warmup", nullable = false)
    private boolean isWarmup;

    // 선택적으로 보존하는 원본 전압
    @Column(name = "raw_voltage_mv", precision = 9, scale = 2)
    private BigDecimal rawVoltageMv;

    // JPA가 엔티티를 생성할 때 사용
    protected TdsMeasurementEntity() {

    }

    // 애플리케이션에서 새 원본을 생성할 때 사용
    public TdsMeasurementEntity(
            UUID sessionId,
            int seq,
            int elapsedMs,
            BigDecimal tdsPpm,
            boolean isWarmup,
            BigDecimal rawVoltageMv
    ) {
        this.sessionId = sessionId;
        this.seq = seq;
        this.elapsedMs = elapsedMs;
        this.tdsPpm = tdsPpm;
        this.isWarmup = isWarmup;
        this.rawVoltageMv = rawVoltageMv;
    }
}
