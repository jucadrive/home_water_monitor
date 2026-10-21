package com.juca.homewatermonitorbackend.session.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

// 비교·저장 테스트에 같은 정상 입력을 제공하며 실제 센서 측정으로 해석하지 않는다.
final class TdsRedeliveryFixtures {
    private TdsRedeliveryFixtures() { }

    static SaveNormalTdsSessionCommand command() {
        return new SaveNormalTdsSessionCommand(UUID.randomUUID(), "test-device", Instant.parse("2026-10-21T00:00:00Z"),
                1000, new BigDecimal("25.00"), "calibration-1", IntStream.rangeClosed(1, 60)
                .mapToObj(seq -> new SaveNormalTdsSessionCommand.Measurement(seq, seq * 1000,
                        new BigDecimal(seq <= 10 ? "300.0" : "151.3"), seq <= 10, new BigDecimal("750.0")))
                .toList());
    }

    static SaveNormalTdsSessionCommand map(SaveNormalTdsSessionCommand source,
            UnaryOperator<SaveNormalTdsSessionCommand.Measurement> change) {
        return new SaveNormalTdsSessionCommand(source.sessionId(), source.deviceId(), source.startedAt(),
                source.samplingIntervalMs(), source.temperatureAssumedC(), source.calibrationVersion(),
                source.measurements().stream().map(change).toList());
    }

    static SaveNormalTdsSessionCommand startedAt(SaveNormalTdsSessionCommand source, Instant time) {
        return new SaveNormalTdsSessionCommand(source.sessionId(), source.deviceId(), time,
                source.samplingIntervalMs(), source.temperatureAssumedC(), source.calibrationVersion(), source.measurements());
    }
}
