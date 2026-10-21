package com.juca.homewatermonitorbackend.session.application;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ProcessIncompleteTdsSessionRedeliveryIntegrationTest extends IncompleteIntegrationSupport {
    @Test
    void comparesBySequenceAndNumericValueWhilePreservingTheOriginalIncompleteResult() {
        var command = incomplete(59);
        incompleteSave.save(command);
        var before = incompleteSnapshot(command.sessionId());
        var reordered = withSamples(command, command.measurements().reversed());
        var rescaled = TdsRedeliveryFixtures.map(reordered, m -> new SaveNormalTdsSessionCommand.Measurement(
                m.seq(), m.elapsedMs(), m.tdsPpm().setScale(3), m.isWarmup(), m.rawVoltageMv().setScale(3)));
        for (var incoming : List.of(command, rescaled)) {
            var result = incompleteRedelivery.process(incoming);
            assertEquals("INCOMPLETE", result.preservationResult());
            assertEquals(59, result.preservedSampleCount());
            assertEquals(59, result.identicalCount());
            assertEquals(0, result.conflictCount());
        }
        assertEquals(2, headers.countBySessionId(command.sessionId()));
        assertEquals(118, detailCount(command.sessionId()));
        assertEquals(118, headers.totalsBySessionId(command.sessionId()).getIdenticalCount());
        assertEquals(before, incompleteSnapshot(command.sessionId()));
    }

    @Test
    void snapshotsAllFourFieldsAndBothNullVoltageDirectionsWithoutChangingOriginals() {
        var command = TdsRedeliveryFixtures.map(incomplete(59), m -> new SaveNormalTdsSessionCommand.Measurement(
                m.seq(), m.elapsedMs(), m.tdsPpm(), m.isWarmup(), m.seq() == 5 || m.seq() == 7 ? null : m.rawVoltageMv()));
        incompleteSave.save(command);
        var before = incompleteSnapshot(command.sessionId());
        var changed = TdsRedeliveryFixtures.map(command, m -> new SaveNormalTdsSessionCommand.Measurement(m.seq(),
                m.seq() == 2 ? m.elapsedMs() + 1 : m.elapsedMs(),
                m.seq() == 1 ? m.tdsPpm().add(BigDecimal.ONE) : m.tdsPpm(),
                m.seq() == 3 ? !m.isWarmup() : m.isWarmup(),
                m.seq() == 4 ? null : m.seq() == 5 || m.seq() == 6 ? new BigDecimal("751") : m.rawVoltageMv()));
        var result = incompleteRedelivery.process(changed);
        assertEquals(6, result.conflictCount());
        assertEquals(53, result.identicalCount());
        var saved = details.findByIdRedeliveryIdOrderByIdSeqAsc(result.redeliveryId());
        assertEquals(59, saved.size());
        for (var detail : saved) {
            assertEquals(detail.getId().getSeq() <= 6 ? "CONFLICT" : "IDENTICAL", detail.getComparisonResult().name());
        }
        assertEquals(before, incompleteSnapshot(command.sessionId()));
    }

    @Test
    void recordsEmptyListRedeliveriesAsEventsWithZeroDetails() {
        var command = incomplete(0);
        processing.process(command);
        for (int repeat = 0; repeat < 2; repeat++) {
            var result = processing.process(command);
            assertEquals("INCOMPLETE", result.preservationResult());
            assertEquals(0, result.preservedSampleCount());
            assertEquals(TdsSessionRedeliveryResult.Outcome.ALL_IDENTICAL, result.redeliveryResult().outcome());
        }
        assertEquals(2, headers.totalsBySessionId(command.sessionId()).getReceptionCount());
        assertEquals(0, headers.totalsBySessionId(command.sessionId()).getIdenticalCount());
        assertEquals(0, headers.totalsBySessionId(command.sessionId()).getConflictCount());
        assertEquals(0, detailCount(command.sessionId()));
        assertIncomplete(command);
    }

    @Test
    void rejectsAddedDeletedOrReplacedSequencesWithoutChangingPriorDiagnostics() {
        var full = TdsRedeliveryFixtures.command();
        var command = withSamples(full, full.measurements().subList(0, 59));
        processing.process(command);
        processing.process(command);
        var before = incompleteSnapshot(command.sessionId());
        for (var list : List.of(full.measurements(), full.measurements().subList(0, 58),
                full.measurements().subList(1, 60))) {
            assertThrows(IllegalArgumentException.class, () -> processing.process(withSamples(command, list)));
        }
        assertEquals(before, incompleteSnapshot(command.sessionId()));
        assertEquals(1, headers.countBySessionId(command.sessionId()));
        assertEquals(59, detailCount(command.sessionId()));
    }

    @Test
    void validatesMetadataAndDatabaseTimestampPrecision() {
        var command = TdsRedeliveryFixtures.startedAt(incomplete(1), Instant.parse("2026-10-21T00:00:00.999999600Z"));
        incompleteSave.save(command);
        var before = incompleteSnapshot(command.sessionId());
        var sameTime = TdsRedeliveryFixtures.startedAt(command, Instant.parse("2026-10-21T00:00:01Z"));
        assertEquals(1, incompleteRedelivery.process(sameTime).identicalCount());
        for (var bad : List.of(
                new SaveNormalTdsSessionCommand(command.sessionId(), "other", command.startedAt(), 1000,
                        command.temperatureAssumedC(), command.calibrationVersion(), command.measurements()),
                new SaveNormalTdsSessionCommand(command.sessionId(), command.deviceId(), command.startedAt(), 1000,
                        new BigDecimal("26"), command.calibrationVersion(), command.measurements()),
                new SaveNormalTdsSessionCommand(command.sessionId(), command.deviceId(), command.startedAt(), 1000,
                        command.temperatureAssumedC(), "other", command.measurements()),
                TdsRedeliveryFixtures.startedAt(command, null),
                TdsRedeliveryFixtures.startedAt(command, Instant.parse("2026-10-21T00:00:01.000001Z")))) {
            assertThrows(IllegalArgumentException.class, () -> incompleteRedelivery.process(bad));
        }
        assertEquals(1, headers.countBySessionId(command.sessionId()));
        assertEquals(before, incompleteSnapshot(command.sessionId()));
    }

    @Test
    void rejectsInconsistentStoredCountsOriginalsMissingDiagnosticsAndStatistics() {
        for (String corruption : List.of("update tds_session set preserved_sample_count = 58 where session_id = ?",
                "delete from tds_measurement where session_id = ? and seq = 59",
                "delete from tds_session_missing_sample where session_id = ?",
                "insert into tds_session_missing_sample select session_id, 1 from tds_session where session_id = ?",
                "update tds_session set stats_sample_count = 1 where session_id = ?",
                "update tds_session set avg_tds_ppm = 10 where session_id = ?")) {
            var command = incomplete(59);
            incompleteSave.save(command);
            jdbc.update(corruption, command.sessionId());
            var before = incompleteSnapshot(command.sessionId());
            assertThrows(IllegalArgumentException.class, () -> processing.process(command));
            assertEquals(before, incompleteSnapshot(command.sessionId()));
            assertEquals(0, headers.countBySessionId(command.sessionId()));
        }
        assertThrows(IllegalArgumentException.class, () -> incompleteRedelivery.process(incomplete(1)));
        var normal = storedCommand();
        assertThrows(IllegalArgumentException.class, () -> incompleteRedelivery.process(normal));
    }

    @Test
    void recordsAllConflictsWithoutRecalculatingStatistics() {
        var command = incomplete(59);
        incompleteSave.save(command);
        var changed = TdsRedeliveryFixtures.map(command, m -> new SaveNormalTdsSessionCommand.Measurement(
                m.seq(), m.elapsedMs(), m.tdsPpm().add(BigDecimal.ONE), true, m.rawVoltageMv()));
        assertEquals(59, incompleteRedelivery.process(changed).conflictCount());
        assertIncomplete(command);
    }
}
