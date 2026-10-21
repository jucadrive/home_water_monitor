package com.juca.homewatermonitorbackend.session.application;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TdsSessionInputValidatorTest {
    private final TdsSessionInputValidator validator = new TdsSessionInputValidator();

    @Test
    void acceptsEmptyAndPartialFinalListsButRetainsNormalEntryCountRestriction() {
        for (int count : new int[]{0, 1, 59, 60}) {
            var command = IncompleteIntegrationSupport.incomplete(count);
            assertDoesNotThrow(() -> validator.validate(command));
            if (count < 60) assertThrows(IllegalArgumentException.class,
                    () -> new NormalTdsSessionInputValidator(validator).validate(command));
        }
    }

    @Test
    void rejectsOverlongRepeatedAndOutOfRangeSequencesWithoutFiltering() {
        var full = TdsRedeliveryFixtures.command();
        var tooMany = new ArrayList<>(full.measurements());
        tooMany.add(full.measurements().getFirst());
        for (var list : List.of(tooMany, List.of(full.measurements().getFirst(), full.measurements().getFirst()),
                List.of(new SaveNormalTdsSessionCommand.Measurement(61, 0, java.math.BigDecimal.ONE, false, null)))) {
            assertThrows(IllegalArgumentException.class,
                    () -> validator.validate(IncompleteIntegrationSupport.withSamples(full, list)));
        }
    }
}
