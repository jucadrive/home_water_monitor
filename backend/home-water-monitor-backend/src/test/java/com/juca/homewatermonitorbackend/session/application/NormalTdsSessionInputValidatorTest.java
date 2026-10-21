package com.juca.homewatermonitorbackend.session.application;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// DB 없이 신규/재수신 공통 검증의 경계와 거절 조건을 확인한다.
class NormalTdsSessionInputValidatorTest {
    private final NormalTdsSessionInputValidator validator = new NormalTdsSessionInputValidator(new TdsSessionInputValidator());

    @Test
    void acceptsValidPrecisionAndAllWarmupForComparison() {
        var command = TdsRedeliveryFixtures.command();
        assertDoesNotThrow(() -> validator.validate(TdsRedeliveryFixtures.map(command, m ->
                new SaveNormalTdsSessionCommand.Measurement(m.seq(), 0, new BigDecimal("151.300"), true, null))));
    }

    @Test
    void rejectsWrongCountsAndRepeatedOrOutOfRangeSequences() {
        var original = TdsRedeliveryFixtures.command();
        var shortList = original.measurements().subList(0, 59);
        var longList = new ArrayList<>(original.measurements());
        longList.add(original.measurements().getFirst());
        for (var list : List.of(shortList, longList)) {
            var command = new SaveNormalTdsSessionCommand(original.sessionId(), original.deviceId(), original.startedAt(),
                    1000, original.temperatureAssumedC(), original.calibrationVersion(), list);
            assertThrows(IllegalArgumentException.class, () -> validator.validate(command));
        }
        for (int seq : new int[]{0, 61, 1}) {
            var command = TdsRedeliveryFixtures.map(original, m ->
                    new SaveNormalTdsSessionCommand.Measurement(seq, m.elapsedMs(), m.tdsPpm(), m.isWarmup(), m.rawVoltageMv()));
            assertThrows(IllegalArgumentException.class, () -> validator.validate(command));
        }
    }

    @Test
    void rejectsMissingRequiredValuesAndInvalidMetadata() {
        var c = TdsRedeliveryFixtures.command();
        assertThrows(IllegalArgumentException.class, () -> validator.validate(null));
        for (var invalid : List.of(
                new SaveNormalTdsSessionCommand(null, c.deviceId(), c.startedAt(), 1000, c.temperatureAssumedC(), c.calibrationVersion(), c.measurements()),
                new SaveNormalTdsSessionCommand(c.sessionId(), " ", c.startedAt(), 1000, c.temperatureAssumedC(), c.calibrationVersion(), c.measurements()),
                new SaveNormalTdsSessionCommand(c.sessionId(), c.deviceId(), c.startedAt(), 500, c.temperatureAssumedC(), c.calibrationVersion(), c.measurements()),
                new SaveNormalTdsSessionCommand(c.sessionId(), c.deviceId(), c.startedAt(), 1000, null, c.calibrationVersion(), c.measurements()),
                new SaveNormalTdsSessionCommand(c.sessionId(), c.deviceId(), c.startedAt(), 1000, new BigDecimal("1000"), c.calibrationVersion(), c.measurements()),
                new SaveNormalTdsSessionCommand(c.sessionId(), c.deviceId(), c.startedAt(), 1000, c.temperatureAssumedC(), "x".repeat(65), c.measurements()))) {
            assertThrows(IllegalArgumentException.class, () -> validator.validate(invalid));
        }
    }

    @Test
    void rejectsInvalidSampleNumbersWithoutRoundingThem() {
        var c = TdsRedeliveryFixtures.command();
        for (var bad : List.of(
                new SaveNormalTdsSessionCommand.Measurement(1, -1, BigDecimal.ONE, false, null),
                new SaveNormalTdsSessionCommand.Measurement(1, 0, null, false, null),
                new SaveNormalTdsSessionCommand.Measurement(1, 0, new BigDecimal("151.301"), false, null),
                new SaveNormalTdsSessionCommand.Measurement(1, 0, new BigDecimal("10000000"), false, null),
                new SaveNormalTdsSessionCommand.Measurement(1, 0, BigDecimal.ONE, false, new BigDecimal("750.001")),
                new SaveNormalTdsSessionCommand.Measurement(1, 0, BigDecimal.ONE, false, new BigDecimal("-10000000")))) {
            assertThrows(IllegalArgumentException.class, () -> validator.validate(
                    TdsRedeliveryFixtures.map(c, m -> m.seq() == 1 ? bad : m)));
        }
    }
}
