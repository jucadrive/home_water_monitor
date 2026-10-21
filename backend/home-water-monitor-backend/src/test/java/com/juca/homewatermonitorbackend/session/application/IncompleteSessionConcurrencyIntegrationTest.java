package com.juca.homewatermonitorbackend.session.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.core.NestedExceptionUtils;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(30)
class IncompleteSessionConcurrencyIntegrationTest extends IncompleteConcurrencySupport {
    @Test
    void serializesConcurrentNewIncompleteListsAndPreservesBothEvents() throws Exception {
        assertNewRace(59, false);
    }

    @Test
    void comparesChangedContentAfterTheWinningOriginalCommits() throws Exception {
        assertNewRace(59, true);
    }

    @Test
    void handlesConcurrentEmptyFinalListsWithSixtyMissingDiagnostics() throws Exception {
        assertNewRace(0, false);
    }

    @Test
    void allowsTheWaitingInitialSaveAfterTheFirstTransactionRollsBack() throws Exception {
        var command = incomplete(59);
        try (var workers = Executors.newFixedThreadPool(2); var held = hold(workers, command, true, false)) {
            var waiting = workers.submit(() -> processing.process(command));
            try { awaitDatabaseWait(held, true); } finally { held.close(); }
            var error = assertThrows(ExecutionException.class, () -> held.future().get(8, TimeUnit.SECONDS));
            assertEquals("test_incomplete_rollback", error.getCause().getMessage());
            assertEquals(TdsSessionProcessingResult.Path.SAVED_NEW, waiting.get(8, TimeUnit.SECONDS).path());
        }
        assertIncomplete(command);
        assertEquals(0, headers.countBySessionId(command.sessionId()));
    }

    @Test
    void keepsTheWinnerForBothNormalAndIncompleteInitialSaveOrders() throws Exception {
        for (int firstCount : new int[]{59, 60}) {
            var full = TdsRedeliveryFixtures.command();
            var first = withSamples(full, full.measurements().subList(0, firstCount));
            var second = withSamples(full, full.measurements().subList(0, firstCount == 60 ? 59 : 60));
            try (var workers = Executors.newFixedThreadPool(2); var held = hold(workers, first, false, false)) {
                var waiting = workers.submit(() -> processing.process(second));
                try { awaitDatabaseWait(held, true); } finally { held.close(); }
                assertEquals(firstCount == 60 ? "NORMAL" : "INCOMPLETE",
                        held.future().get(8, TimeUnit.SECONDS).preservationResult());
                var error = assertThrows(ExecutionException.class, () -> waiting.get(8, TimeUnit.SECONDS));
                assertInstanceOf(IllegalArgumentException.class, error.getCause());
            }
            assertEquals(firstCount, snapshot(first.sessionId()).measurements().size());
            assertEquals(0, headers.countBySessionId(first.sessionId()));
            if (firstCount < 60) assertIncomplete(first);
            else assertTrue(missingSequences(first.sessionId()).isEmpty());
        }
    }

    @Test
    void allowsAnotherSessionWhileTheInitialIncompleteSaveIsUncommitted() throws Exception {
        var command = incomplete(59);
        var other = incomplete(0);
        try (var workers = Executors.newFixedThreadPool(2); var held = hold(workers, command, false, false)) {
            assertEquals("INCOMPLETE", workers.submit(() -> processing.process(other)).get(5, TimeUnit.SECONDS).preservationResult());
            assertIncomplete(other);
            assertAbsent(command.sessionId());
            assertFalse(held.future().isDone());
            held.close();
            held.future().get(8, TimeUnit.SECONDS);
        }
    }

    @Test
    void serializesExistingIncompleteRedeliveriesWithExactTotals() throws Exception {
        var command = incomplete(59);
        processing.process(command);
        var before = incompleteSnapshot(command.sessionId());
        try (var workers = Executors.newFixedThreadPool(2); var held = hold(workers, command, false, true)) {
            var waiting = workers.submit(() -> processing.process(command));
            try {
                awaitDatabaseWait(held, false);
                assertFalse(waiting.isDone());
                assertEquals(0, headers.countBySessionId(command.sessionId()));
            } finally { held.close(); }
            assertNotEquals(held.future().get(8, TimeUnit.SECONDS).redeliveryResult().redeliveryId(),
                    waiting.get(8, TimeUnit.SECONDS).redeliveryResult().redeliveryId());
        }
        assertEquals(2, headers.countBySessionId(command.sessionId()));
        assertEquals(118, detailCount(command.sessionId()));
        assertEquals(118, headers.totalsBySessionId(command.sessionId()).getIdenticalCount());
        assertEquals(before, incompleteSnapshot(command.sessionId()));
    }

    @Test
    void timesOutOnInitialInsertAndExistingRowWithoutRetryAndRecoversAfterRelease() throws Exception {
        for (boolean existing : new boolean[]{false, true}) {
            var command = incomplete(59);
            if (existing) processing.process(command);
            try (var workers = Executors.newFixedThreadPool(2); var held = hold(workers, command, false, existing)) {
                var waiting = workers.submit(() -> processing.process(command));
                awaitDatabaseWait(held, !existing);
                var error = assertThrows(ExecutionException.class, () -> waiting.get(8, TimeUnit.SECONDS));
                var sql = assertInstanceOf(SQLException.class, NestedExceptionUtils.getMostSpecificCause(error));
                assertEquals("55P03", sql.getSQLState());
                assertEquals(0, headers.countBySessionId(command.sessionId()));
                if (!existing) assertAbsent(command.sessionId());
                held.close();
                held.future().get(8, TimeUnit.SECONDS);
            }
            assertEquals(59, processing.process(command).redeliveryResult().identicalCount());
            assertIncomplete(command);
            assertEquals(existing ? 2 : 1, headers.countBySessionId(command.sessionId()));
        }
    }

    private void assertNewRace(int count, boolean changed) throws Exception {
        var command = incomplete(count);
        var incoming = changed ? TdsRedeliveryFixtures.map(command, m -> new SaveNormalTdsSessionCommand.Measurement(
                m.seq(), m.elapsedMs(), m.tdsPpm().add(BigDecimal.ONE), m.isWarmup(), m.rawVoltageMv())) : command;
        try (var workers = Executors.newFixedThreadPool(2); var held = hold(workers, command, false, false)) {
            var waiting = workers.submit(() -> processing.process(incoming));
            try {
                awaitDatabaseWait(held, true);
                assertFalse(waiting.isDone());
                assertAbsent(command.sessionId());
            } finally { held.close(); }
            assertEquals(TdsSessionProcessingResult.Path.SAVED_NEW, held.future().get(8, TimeUnit.SECONDS).path());
            var result = waiting.get(8, TimeUnit.SECONDS);
            assertEquals(TdsSessionProcessingResult.Path.PROCESSED_REDELIVERY, result.path());
            assertEquals(changed ? count : 0, result.redeliveryResult().conflictCount());
            assertEquals(changed ? 0 : count, result.redeliveryResult().identicalCount());
        }
        assertIncomplete(command);
        assertEquals(1, headers.countBySessionId(command.sessionId()));
        assertEquals(count, detailCount(command.sessionId()));
    }
}
