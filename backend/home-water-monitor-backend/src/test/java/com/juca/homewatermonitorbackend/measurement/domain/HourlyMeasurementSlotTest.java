package com.juca.homewatermonitorbackend.measurement.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class HourlyMeasurementSlotTest {
    final Instant hour=Instant.parse("2026-10-29T03:00:00Z");
    @Test void nextFullHourIsDueAndUtcAlsoMeansKoreanFullHour() {
        assertTrue(HourlyMeasurementSlot.due(hour.plusMillis(1),hour.minusSeconds(20)));
        assertEquals(hour,HourlyMeasurementSlot.at(hour.plusSeconds(59)));
        assertEquals(12,hour.atOffset(java.time.ZoneOffset.ofHours(9)).getHour());
    }
    @Test void restartNeverStartsCurrentOrMissedHours() {
        assertFalse(HourlyMeasurementSlot.due(hour.plusSeconds(10),hour.plusSeconds(5)));
        assertFalse(HourlyMeasurementSlot.due(hour.plusSeconds(1800),hour.minusSeconds(86400)));
        assertTrue(HourlyMeasurementSlot.due(hour.plusSeconds(3600),hour.plusSeconds(5)));
    }
    @Test void lateCallbackAndBackwardClockDoNotCreateMeasurements() {
        assertTrue(HourlyMeasurementSlot.due(hour.plusSeconds(59),hour.minusSeconds(1)));
        assertFalse(HourlyMeasurementSlot.due(hour.plusSeconds(60),hour.minusSeconds(1)));
        assertFalse(HourlyMeasurementSlot.due(hour,hour.plusSeconds(3600)));
    }
}
