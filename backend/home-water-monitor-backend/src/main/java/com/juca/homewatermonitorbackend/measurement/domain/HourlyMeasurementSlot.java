package com.juca.homewatermonitorbackend.measurement.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** A startup never compensates for a missed hour; late callbacks do not cause bursts. */
public final class HourlyMeasurementSlot {
    private HourlyMeasurementSlot() { }
    public static Instant at(Instant now) { return now.truncatedTo(ChronoUnit.HOURS); }
    public static boolean due(Instant now, Instant startedAt) {
        var slot = at(now);
        return !slot.isBefore(startedAt) && !now.isBefore(startedAt) && now.isBefore(slot.plusSeconds(60));
    }
}
