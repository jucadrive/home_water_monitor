package com.juca.homewatermonitorbackend.cultivation.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

// A cultivation represents one independently maintained reservoir during one growing period.
public record Cultivation(UUID id, String plantName, String name, Instant startedAt, Instant endedAt,
                          String memo, Instant createdAt, Instant updatedAt) {
    public Cultivation {
        Objects.requireNonNull(id);
        plantName = text(plantName, 100, true);
        name = text(name, 100, true);
        memo = text(memo, 2000, false);
        startedAt = Objects.requireNonNull(startedAt).truncatedTo(ChronoUnit.MICROS);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
        if (startedAt.isAfter(createdAt) || endedAt != null && endedAt.isBefore(startedAt))
            throw new IllegalArgumentException("Invalid cultivation time");
    }
    public String status() { return endedAt == null ? "ACTIVE" : "ENDED"; }
    public String getStatus() { return status(); }
    public void requireActive() {
        if (endedAt != null) throw new IllegalStateException("Cultivation has ended");
    }
    public static String text(String value, int max, boolean required) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.length() > max || required && normalized.isBlank())
            throw new IllegalArgumentException("Invalid text");
        return normalized;
    }
}
