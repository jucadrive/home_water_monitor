package com.juca.homewatermonitorbackend.measurement.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Spring·DB 없이 동일·충돌 정책과 비교 대상 오류를 검증한다.
class TdsMeasurementTest {
    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void identifiesSameContent() {
        assertEquals(TdsMeasurementComparisonResult.IDENTICAL,
                sample(1_000, "151.3", false, "750.0")
                        .compareWith(sample(1_000, "151.3", false, "750.0")));
    }

    @Test
    void ignoresDecimalScaleInBothNumericFields() {
        TdsMeasurement original = sample(1_000, "151.3", false, "750.0");
        TdsMeasurement incoming = sample(1_000, "151.30", false, "750.00");
        assertEquals(TdsMeasurementComparisonResult.IDENTICAL, original.compareWith(incoming));
        assertEquals(TdsMeasurementComparisonResult.IDENTICAL, incoming.compareWith(original));
    }

    @Test
    void identifiesSameContentWhenBothVoltagesAreMissing() {
        assertEquals(TdsMeasurementComparisonResult.IDENTICAL,
                sample(1_000, "151.3", false, null)
                        .compareWith(sample(1_000, "151.30", false, null)));
    }

    @Test
    void detectsConflictWhenEachComparedFieldChanges() {
        TdsMeasurement original = sample(1_000, "151.3", false, "750.0");
        // 각 사례에서 한 필드만 변경하여 비교 필드 누락을 탐지한다.
        List<TdsMeasurement> changed = List.of(
                sample(1_000, "151.4", false, "750.0"),
                sample(2_000, "151.3", false, "750.0"),
                sample(1_000, "151.3", true, "750.0"),
                sample(1_000, "151.3", false, "751.0")
        );
        for (TdsMeasurement incoming : changed) {
            assertEquals(TdsMeasurementComparisonResult.CONFLICT, original.compareWith(incoming), incoming.toString());
            assertEquals(TdsMeasurementComparisonResult.CONFLICT, incoming.compareWith(original), incoming.toString());
        }
    }

    @Test
    void detectsMissingVoltageInEitherDirection() {
        TdsMeasurement missing = sample(1_000, "151.3", false, null);
        TdsMeasurement present = sample(1_000, "151.3", false, "750.0");
        assertEquals(TdsMeasurementComparisonResult.CONFLICT, missing.compareWith(present));
        assertEquals(TdsMeasurementComparisonResult.CONFLICT, present.compareWith(missing));
    }

    @Test
    void rejectsDifferentSessions() {
        TdsMeasurement original = sample(1_000, "151.3", false, "750.0");
        TdsMeasurement anotherSession = new TdsMeasurement(
                UUID.fromString("00000000-0000-0000-0000-000000000002"), 1, 1_000,
                new BigDecimal("151.3"), false, new BigDecimal("750.0"));
        assertThrows(IllegalArgumentException.class, () -> original.compareWith(anotherSession));
    }

    @Test
    void rejectsDifferentSequences() {
        TdsMeasurement original = sample(1_000, "151.3", false, "750.0");
        TdsMeasurement anotherSequence = new TdsMeasurement(
                SESSION_ID, 2, 1_000, new BigDecimal("151.3"), false, new BigDecimal("750.0"));
        assertThrows(IllegalArgumentException.class, () -> original.compareWith(anotherSequence));
    }

    @Test
    void rejectsMissingComparisonTarget() {
        assertThrows(NullPointerException.class,
                () -> sample(1_000, "151.3", false, null).compareWith(null));
    }

    @Test
    void rejectsMissingRequiredValues() {
        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> new TdsMeasurement(null, 1, 0, BigDecimal.ONE, false, null)),
                () -> assertThrows(NullPointerException.class,
                        () -> new TdsMeasurement(SESSION_ID, 1, 0, null, false, null))
        );
    }

    @Test
    void rejectsNonPositiveSequences() {
        for (int seq : new int[]{0, -1}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new TdsMeasurement(SESSION_ID, seq, 0, BigDecimal.ONE, false, null));
        }
    }

    @Test
    void rejectsNegativeElapsedTime() {
        assertThrows(IllegalArgumentException.class, () -> sample(-1, "151.3", false, null));
    }

    @Test
    void acceptsFirstSequenceAtZeroElapsedTime() {
        TdsMeasurement boundary = sample(0, "151.3", false, null);
        assertEquals(TdsMeasurementComparisonResult.IDENTICAL,
                boundary.compareWith(sample(0, "151.30", false, null)));
    }

    @Test
    void comparesExactValuesWithoutRoundingOrChangingInputs() {
        // 소수 둘째 자리로 반올림하면 사라질 차이도 원본 비교에서는 충돌이다.
        TdsMeasurement original = sample(1_000, "151.301", false, "750.001");
        assertEquals(TdsMeasurementComparisonResult.CONFLICT,
                original.compareWith(sample(1_000, "151.302", false, "750.001")));
        assertEquals(TdsMeasurementComparisonResult.CONFLICT,
                original.compareWith(sample(1_000, "151.301", false, "750.002")));
        assertEquals(new BigDecimal("151.301"), original.tdsPpm());
        assertEquals(new BigDecimal("750.001"), original.rawVoltageMv());
    }

    // 같은 식별자를 사용하고 비교 대상 내용만 바꾸는 테스트 원본을 만든다.
    private TdsMeasurement sample(int elapsedMs, String ppm, boolean warmup, String voltage) {
        return new TdsMeasurement(SESSION_ID, 1, elapsedMs, new BigDecimal(ppm), warmup,
                voltage == null ? null : new BigDecimal(voltage));
    }
}
