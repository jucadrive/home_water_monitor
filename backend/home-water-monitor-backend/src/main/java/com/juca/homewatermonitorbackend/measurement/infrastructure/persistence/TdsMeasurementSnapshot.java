package com.juca.homewatermonitorbackend.measurement.infrastructure.persistence;

import com.juca.homewatermonitorbackend.measurement.domain.TdsMeasurement;
import jakarta.persistence.Embeddable;
import lombok.Getter;

import java.math.BigDecimal;

// 진단 시점의 네 원본 필드를 기존/수신 스냅샷에 공통으로 매핑한다.
@Embeddable
@Getter
public class TdsMeasurementSnapshot {
    private int elapsedMs;
    private BigDecimal tdsPpm;
    private boolean isWarmup;
    private BigDecimal rawVoltageMv;

    protected TdsMeasurementSnapshot() {
    }

    public TdsMeasurementSnapshot(TdsMeasurement measurement) {
        elapsedMs = measurement.elapsedMs();
        tdsPpm = measurement.tdsPpm();
        isWarmup = measurement.isWarmup();
        rawVoltageMv = measurement.rawVoltageMv();
    }
}
