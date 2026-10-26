package com.juca.homewatermonitorbackend.session.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MeasurementSessionView(UUID sessionId,String deviceId,UUID cultivationId,Instant startedAt,
        Instant requestedAt,Instant ingestedAt,Instant displayAt,String timeBasis,String preservationResult,
        int preservedSampleCount,int statsSampleCount,BigDecimal minTdsPpm,BigDecimal maxTdsPpm,
        BigDecimal avgTdsPpm,BigDecimal medianTdsPpm,BigDecimal temperatureAssumedC,String calibrationVersion,String source) { }
