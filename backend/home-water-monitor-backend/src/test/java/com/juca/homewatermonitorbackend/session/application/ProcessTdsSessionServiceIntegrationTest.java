package com.juca.homewatermonitorbackend.session.application;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

class ProcessTdsSessionServiceIntegrationTest extends IncompleteIntegrationSupport {
    @Test
    void routesNewAndExistingNormalAndIncompleteSessionsWithTheirOriginalResults() {
        for (int count : new int[]{0, 1, 59, 60}) {
            var command = incomplete(count);
            var saved = processing.process(command);
            assertEquals(command.sessionId(), saved.sessionId());
            assertEquals(count == 60 ? "NORMAL" : "INCOMPLETE", saved.preservationResult());
            assertEquals(count, saved.preservedSampleCount());
            assertEquals(TdsSessionProcessingResult.Path.SAVED_NEW, saved.path());
            assertNull(saved.redeliveryResult());
            var result = processing.process(command);
            assertEquals(saved.preservationResult(), result.preservationResult());
            assertEquals(count, result.redeliveryResult().identicalCount());
            assertEquals(TdsSessionProcessingResult.Path.PROCESSED_REDELIVERY, result.path());
            if (count < 60) assertIncomplete(command);
        }
    }

    @Test
    void retainsNormalEntryRestrictionsAndRejectsPartialRedeliveryOfANormalSession() {
        var normal = TdsRedeliveryFixtures.command();
        processing.process(normal);
        var before = snapshot(normal.sessionId());
        assertThrows(IllegalArgumentException.class,
                () -> processing.process(withSamples(normal, normal.measurements().subList(0, 59))));
        assertUnchanged(normal.sessionId(), before);
        var partial = incomplete(59);
        assertThrows(IllegalArgumentException.class, () -> initialSave.save(partial));
        assertAbsent(partial.sessionId());
        var warmup = TdsRedeliveryFixtures.map(incomplete(60), m -> new SaveNormalTdsSessionCommand.Measurement(
                m.seq(), m.elapsedMs(), m.tdsPpm(), true, m.rawVoltageMv()));
        assertThrows(IllegalArgumentException.class, () -> processing.process(warmup));
        assertAbsent(warmup.sessionId());
    }

    @Test
    void rejectsExternalTransactionsAndMalformedInputWithoutWriting() {
        var command = incomplete(0);
        assertThrows(IllegalTransactionStateException.class, () -> new TransactionTemplate(transactions)
                .execute(status -> processing.process(command)));
        assertAbsent(command.sessionId());
        assertThrows(IllegalArgumentException.class, () -> processing.process(null));
        var duplicate = incomplete(1);
        assertThrows(IllegalArgumentException.class, () -> processing.process(withSamples(duplicate,
                java.util.List.of(duplicate.measurements().getFirst(), duplicate.measurements().getFirst()))));
        assertAbsent(duplicate.sessionId());
    }
}
