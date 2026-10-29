package com.juca.homewatermonitorbackend.session.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SaveNormalTdsSessionCommand(
        UUID sessionId,
        String deviceId,
        Instant startedAt,
        int samplingIntervalMs,
        BigDecimal temperatureAssumedC,
        String calibrationVersion,
        List<Measurement> measurements,
        String temperatureSource, String compensationVersion
) {

    public SaveNormalTdsSessionCommand(UUID sessionId, String deviceId, Instant startedAt, int samplingIntervalMs,
            BigDecimal temperatureAssumedC, String calibrationVersion, List<Measurement> measurements) {
        this(sessionId, deviceId, startedAt, samplingIntervalMs, temperatureAssumedC, calibrationVersion, measurements, null, null);
    }

    public SaveNormalTdsSessionCommand {
        measurements = List.copyOf(measurements);
    }

    public record Measurement(
            int seq,
            int elapsedMs,
            BigDecimal tdsPpm,
            boolean isWarmup,
            BigDecimal rawVoltageMv, BigDecimal temperatureC, Integer temperatureElapsedMs
    ) {
        public Measurement(int seq, int elapsedMs, BigDecimal tdsPpm, boolean isWarmup, BigDecimal rawVoltageMv) {
            this(seq, elapsedMs, tdsPpm, isWarmup, rawVoltageMv, null, null);
        }
    }
}
