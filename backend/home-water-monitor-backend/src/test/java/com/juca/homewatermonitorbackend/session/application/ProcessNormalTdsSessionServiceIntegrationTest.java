package com.juca.homewatermonitorbackend.session.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProcessNormalTdsSessionServiceIntegrationTest extends RedeliveryIntegrationSupport {
    @Autowired ProcessNormalTdsSessionService routing;

    @Test
    void savesThenRoutesIdenticalAndConflictingListsWithoutChangingOriginals() {
        var command = TdsRedeliveryFixtures.command();
        var saved = routing.process(command);
        assertEquals(NormalTdsSessionProcessingResult.Path.SAVED_NEW, saved.path());
        assertEquals(command.sessionId(), saved.sessionId());
        assertNull(saved.redeliveryResult());
        assertEquals("NORMAL", saved.preservationResult());
        assertEquals(60, saved.preservedSampleCount());
        assertEquals(60, snapshot(command.sessionId()).measurements().size());
        assertEquals(0, headers.countBySessionId(command.sessionId()));
        var before = snapshot(command.sessionId());
        var session = before.session();
        assertEquals(50, session.get("stats_sample_count"));
        for (String column : new String[]{"min_tds_ppm", "max_tds_ppm", "avg_tds_ppm", "median_tds_ppm"}) {
            assertEquals(0, new BigDecimal("151.30").compareTo((BigDecimal) session.get(column)));
        }
        var identical = routing.process(command);
        assertEquals(NormalTdsSessionProcessingResult.Path.PROCESSED_REDELIVERY, identical.path());
        assertEquals(60, identical.redeliveryResult().identicalCount());
        var changed = TdsRedeliveryFixtures.map(command, m -> new SaveNormalTdsSessionCommand.Measurement(
                m.seq(), m.elapsedMs(), m.tdsPpm().add(BigDecimal.ONE), m.isWarmup(), m.rawVoltageMv()));
        assertEquals(60, routing.process(changed).redeliveryResult().conflictCount());
        assertEquals(2, headers.countBySessionId(command.sessionId()));
        assertEquals(120, detailCount(command.sessionId()));
        assertUnchanged(command.sessionId(), before);
    }

    @Test
    void rejectsMetadataIncompleteAndMissingOriginalsWithoutCreatingNewData() {
        var command = storedCommand();
        var before = snapshot(command.sessionId());
        var changed = new SaveNormalTdsSessionCommand(command.sessionId(), "other-device", command.startedAt(),
                1000, command.temperatureAssumedC(), command.calibrationVersion(), command.measurements());
        assertThrows(IllegalArgumentException.class, () -> routing.process(changed));
        assertUnchanged(command.sessionId(), before);
        jdbc.update("update tds_session set preservation_result = 'INCOMPLETE' where session_id = ?", command.sessionId());
        var incomplete = snapshot(command.sessionId());
        assertThrows(IllegalArgumentException.class, () -> routing.process(command));
        assertUnchanged(command.sessionId(), incomplete);
        jdbc.update("update tds_session set preservation_result = 'NORMAL' where session_id = ?", command.sessionId());
        jdbc.update("delete from tds_measurement where session_id = ? and seq = 60", command.sessionId());
        var missing = snapshot(command.sessionId());
        assertThrows(IllegalArgumentException.class, () -> routing.process(command));
        assertUnchanged(command.sessionId(), missing);
        assertEquals(0, headers.countBySessionId(command.sessionId()));
        assertEquals(0, detailCount(command.sessionId()));
    }

    @Test
    void requiresStatisticsOnlyForNewSessions() {
        var command = TdsRedeliveryFixtures.command();
        var allWarmup = TdsRedeliveryFixtures.map(command, m -> new SaveNormalTdsSessionCommand.Measurement(
                m.seq(), m.elapsedMs(), m.tdsPpm(), true, m.rawVoltageMv()));
        assertThrows(IllegalArgumentException.class, () -> routing.process(allWarmup));
        assertEquals(0, sessionCount(command.sessionId()));
        assertEquals(0L, jdbc.queryForObject("select count(*) from tds_measurement where session_id = ?",
                Long.class, command.sessionId()));
        routing.process(command);
        var before = snapshot(command.sessionId());
        assertEquals(50, routing.process(allWarmup).redeliveryResult().conflictCount());
        assertUnchanged(command.sessionId(), before);
    }

    @Test
    void rejectsExternalTransactionBeforeAnyDatabaseChange() {
        var command = TdsRedeliveryFixtures.command();
        assertThrows(IllegalTransactionStateException.class, () -> new TransactionTemplate(transactions)
                .execute(status -> routing.process(command)));
        assertEquals(0, sessionCount(command.sessionId()));
        assertEquals(0, headers.countBySessionId(command.sessionId()));
    }

    private long sessionCount(UUID id) {
        return jdbc.queryForObject("select count(*) from tds_session where session_id = ?", Long.class, id);
    }
}
