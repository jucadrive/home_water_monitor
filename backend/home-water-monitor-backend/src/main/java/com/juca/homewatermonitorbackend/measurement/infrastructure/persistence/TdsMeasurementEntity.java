package com.juca.homewatermonitorbackend.measurement.infrastructure.persistence;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "tds_measurement")
public class TdsMeasurementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "measurement_id")
    private Long measurementId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "seq", nullable = false)
    private int seq;

    @Column(name = "elapsed_ms", nullable = false)
    private int elapsedMs;

    @Column(name = "tds_ppm", nullable = false, precision = 9, scale = 2)
    private BigDecimal tdsPpm;

    @Column(name = "is_warmup", nullable = false)
    private boolean isWarmup;

    @Column(name = "raw_voltage_mv", precision = 9, scale = 2)
    private BigDecimal rawVoltageMv;

    protected TdsMeasurementEntity() {

    }
}
