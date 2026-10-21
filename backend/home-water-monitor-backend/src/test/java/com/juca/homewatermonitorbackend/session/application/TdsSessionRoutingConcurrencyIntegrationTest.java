package com.juca.homewatermonitorbackend.session.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.core.NestedExceptionUtils;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(25)
class TdsSessionRoutingConcurrencyIntegrationTest extends RoutingConcurrencySupport {
    @Test
    void commitsOneOriginalAndOneIdenticalRedeliveryForConcurrentNewRequests() throws Exception {
        assertConcurrentRedelivery(false);
    }

    @Test
    void keepsTheFirstCommittedOriginalWhenTheConcurrentListHasDifferentContent() throws Exception {
        assertConcurrentRedelivery(true);
    }

    @Test
    void savesTheWaitingRequestAsNewWhenTheFirstTransactionRollsBack() throws Exception {
        var command = TdsRedeliveryFixtures.command();
        try (var workers = Executors.newFixedThreadPool(2);
             var held = holdFirstSave(workers, command, true)) {
            var waiting = workers.submit(() -> routing.process(command));
            try {
                awaitInsertWait(held);
                assertFalse(waiting.isDone());
            } finally {
                held.close();
            }
            var error = assertThrows(ExecutionException.class, () -> held.future().get(8, TimeUnit.SECONDS));
            assertEquals("test_first_save_rollback", error.getCause().getMessage());
            assertEquals(NormalTdsSessionProcessingResult.Path.SAVED_NEW, waiting.get(8, TimeUnit.SECONDS).path());
        }
        assertEquals(1, sessionCount(command.sessionId()));
        assertEquals(60, snapshot(command.sessionId()).measurements().size());
        assertEquals(0, headers.countBySessionId(command.sessionId()));
        assertEquals(0, detailCount(command.sessionId()));
    }

    @Test
    void rejectsChangedMetadataAfterTheWinningTransactionCommits() throws Exception {
        var command = TdsRedeliveryFixtures.command();
        var changed = new SaveNormalTdsSessionCommand(command.sessionId(), "different-device", command.startedAt(),
                1000, command.temperatureAssumedC(), command.calibrationVersion(), command.measurements());
        try (var workers = Executors.newFixedThreadPool(2);
             var held = holdFirstSave(workers, command, false)) {
            var waiting = workers.submit(() -> routing.process(changed));
            try {
                awaitInsertWait(held);
            } finally {
                held.close();
            }
            held.future().get(8, TimeUnit.SECONDS);
            var error = assertThrows(ExecutionException.class, () -> waiting.get(8, TimeUnit.SECONDS));
            assertInstanceOf(IllegalArgumentException.class, error.getCause());
        }
        assertEquals(command.deviceId(), snapshot(command.sessionId()).session().get("device_id"));
        assertEquals(60, snapshot(command.sessionId()).measurements().size());
        assertEquals(0, headers.countBySessionId(command.sessionId()));
        assertEquals(0, detailCount(command.sessionId()));
    }

    @Test
    void allowsAnIndependentNewSessionToCommitWhileTheFirstSaveIsUncommitted() throws Exception {
        var first = TdsRedeliveryFixtures.command();
        var independent = TdsRedeliveryFixtures.command();
        try (var workers = Executors.newFixedThreadPool(2);
             var held = holdFirstSave(workers, first, false)) {
            var other = workers.submit(() -> routing.process(independent));
            assertEquals(NormalTdsSessionProcessingResult.Path.SAVED_NEW, other.get(5, TimeUnit.SECONDS).path());
            assertEquals(1, sessionCount(independent.sessionId()));
            assertEquals(0, sessionCount(first.sessionId()));
            assertFalse(held.future().isDone());
            held.close();
            held.future().get(8, TimeUnit.SECONDS);
        }
    }

    @Test
    void timesOutDuringInsertWithoutRecoveryAndAllowsANewCallAfterRelease() throws Exception {
        var command = TdsRedeliveryFixtures.command();
        try (var workers = Executors.newFixedThreadPool(2);
             var held = holdFirstSave(workers, command, false)) {
            var waiting = workers.submit(() -> routing.process(command));
            awaitInsertWait(held);
            var error = assertThrows(ExecutionException.class, () -> waiting.get(8, TimeUnit.SECONDS));
            var sql = assertInstanceOf(SQLException.class, NestedExceptionUtils.getMostSpecificCause(error));
            assertEquals("55P03", sql.getSQLState());
            assertNoNewData(command.sessionId());
            assertFalse(held.future().isDone());
            held.close();
            held.future().get(8, TimeUnit.SECONDS);
        }
        var before = snapshot(command.sessionId());
        assertEquals(60, routing.process(command).redeliveryResult().identicalCount());
        assertEquals(1, headers.countBySessionId(command.sessionId()));
        assertUnchanged(command.sessionId(), before);
    }

    private void assertConcurrentRedelivery(boolean changeContent) throws Exception {
        var command = TdsRedeliveryFixtures.command();
        var incoming = changeContent ? TdsRedeliveryFixtures.map(command,
                m -> new SaveNormalTdsSessionCommand.Measurement(m.seq(), m.elapsedMs(),
                        m.tdsPpm().add(BigDecimal.ONE), m.isWarmup(), m.rawVoltageMv())) : command;
        Snapshot firstSnapshot;
        try (var workers = Executors.newFixedThreadPool(2);
             var held = holdFirstSave(workers, command, false)) {
            var waiting = workers.submit(() -> routing.process(incoming));
            try {
                awaitInsertWait(held);
                assertFalse(waiting.isDone());
                assertNoNewData(command.sessionId());
            } finally {
                held.close();
            }
            assertEquals(NormalTdsSessionProcessingResult.Path.SAVED_NEW,
                    held.future().get(8, TimeUnit.SECONDS).path());
            firstSnapshot = snapshot(command.sessionId());
            var result = waiting.get(8, TimeUnit.SECONDS);
            assertEquals(NormalTdsSessionProcessingResult.Path.PROCESSED_REDELIVERY, result.path());
            assertEquals(changeContent ? 60 : 0, result.redeliveryResult().conflictCount());
            assertEquals(changeContent ? 0 : 60, result.redeliveryResult().identicalCount());
        }
        assertEquals(1, sessionCount(command.sessionId()));
        assertEquals(60, firstSnapshot.measurements().size());
        assertEquals(new BigDecimal("300.00"), firstSnapshot.measurements().getFirst().get("tds_ppm"));
        assertEquals(new BigDecimal("151.30"), firstSnapshot.session().get("avg_tds_ppm"));
        assertEquals(1, headers.countBySessionId(command.sessionId()));
        assertEquals(60, detailCount(command.sessionId()));
        assertUnchanged(command.sessionId(), firstSnapshot);
    }
}
