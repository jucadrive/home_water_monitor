package com.juca.homewatermonitorbackend.measurement.infrastructure.persistence;

import com.juca.homewatermonitorbackend.measurement.domain.TdsMeasurement;
import com.juca.homewatermonitorbackend.measurement.domain.TdsMeasurementComparisonResult;
import jakarta.persistence.*;
import lombok.Getter;

import java.util.UUID;

// 기존 원본을 변경하지 않고 비교 당시 두 값과 판정을 보존한다.
@Entity
@Table(name = "tds_measurement_redelivery")
@Getter
public class TdsMeasurementRedeliveryEntity {
    @EmbeddedId
    private TdsMeasurementRedeliveryId id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TdsMeasurementComparisonResult comparisonResult;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "elapsedMs", column = @Column(name = "original_elapsed_ms", nullable = false)),
            @AttributeOverride(name = "tdsPpm", column = @Column(name = "original_tds_ppm", nullable = false, precision = 9, scale = 2)),
            @AttributeOverride(name = "isWarmup", column = @Column(name = "original_is_warmup", nullable = false)),
            @AttributeOverride(name = "rawVoltageMv", column = @Column(name = "original_raw_voltage_mv", precision = 9, scale = 2)),
            @AttributeOverride(name = "temperatureC", column = @Column(name = "original_temperature_c", precision = 7, scale = 4)),
            @AttributeOverride(name = "temperatureElapsedMs", column = @Column(name = "original_temperature_elapsed_ms"))
    })
    private TdsMeasurementSnapshot original;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "elapsedMs", column = @Column(name = "incoming_elapsed_ms", nullable = false)),
            @AttributeOverride(name = "tdsPpm", column = @Column(name = "incoming_tds_ppm", nullable = false, precision = 9, scale = 2)),
            @AttributeOverride(name = "isWarmup", column = @Column(name = "incoming_is_warmup", nullable = false)),
            @AttributeOverride(name = "rawVoltageMv", column = @Column(name = "incoming_raw_voltage_mv", precision = 9, scale = 2)),
            @AttributeOverride(name = "temperatureC", column = @Column(name = "incoming_temperature_c", precision = 7, scale = 4)),
            @AttributeOverride(name = "temperatureElapsedMs", column = @Column(name = "incoming_temperature_elapsed_ms"))
    })
    private TdsMeasurementSnapshot incoming;

    protected TdsMeasurementRedeliveryEntity() {
    }

    public TdsMeasurementRedeliveryEntity(UUID redeliveryId, TdsMeasurement original, TdsMeasurement incoming) {
        this.id = new TdsMeasurementRedeliveryId(redeliveryId, incoming.seq());
        this.comparisonResult = original.compareWith(incoming);
        this.original = new TdsMeasurementSnapshot(original);
        this.incoming = new TdsMeasurementSnapshot(incoming);
    }
}
