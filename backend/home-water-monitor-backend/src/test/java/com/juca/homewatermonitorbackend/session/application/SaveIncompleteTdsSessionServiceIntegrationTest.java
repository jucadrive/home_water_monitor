package com.juca.homewatermonitorbackend.session.application;

import org.junit.jupiter.api.Test;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SaveIncompleteTdsSessionServiceIntegrationTest extends IncompleteIntegrationSupport {
    @Test
    void preservesZeroOneAndFiftyNineSamplesIncludingAnInternalGapWithoutStatistics() {
        for (int count : new int[]{0, 1, 59}) {
            var command = incomplete(count);
            assertEquals(command.sessionId(), incompleteSave.save(command));
            assertIncomplete(command);
            assertEquals(0, headers.countBySessionId(command.sessionId()));
        }
        var full = TdsRedeliveryFixtures.command();
        var gap = withSamples(full, full.measurements().stream().filter(m -> m.seq() != 30).toList().reversed());
        incompleteSave.save(gap);
        assertIncomplete(gap);
        assertEquals(List.of(30), missingSequences(gap.sessionId()));
    }

    @Test
    void rejectsCompleteAndMalformedListsWithoutPartialPreservation() {
        var full = TdsRedeliveryFixtures.command();
        assertThrows(IllegalArgumentException.class, () -> incompleteSave.save(full));
        assertAbsent(full.sessionId());
        var repeated = withSamples(full, List.of(full.measurements().getFirst(), full.measurements().getFirst()));
        assertThrows(IllegalArgumentException.class, () -> incompleteSave.save(repeated));
        assertAbsent(full.sessionId());
        var invalid = TdsRedeliveryFixtures.map(incomplete(59), m -> m.seq() == 40
                ? new SaveNormalTdsSessionCommand.Measurement(40, -1, m.tdsPpm(), false, null) : m);
        assertThrows(IllegalArgumentException.class, () -> incompleteSave.save(invalid));
        assertAbsent(invalid.sessionId());
    }

    @Test
    void rollsBackOriginalAndMissingDiagnosticWriteFailuresAndKeepsEarlierData() {
        var previous = incomplete(1);
        incompleteSave.save(previous);
        var before = incompleteSnapshot(previous.sessionId());
        for (String table : List.of("tds_measurement", "tds_session_missing_sample")) {
            var command = incomplete(40);
            jdbc.execute("alter table " + table + " add constraint ck_test_incomplete_write check (session_id <> '"
                    + command.sessionId() + "'::uuid) not valid");
            try {
                assertThrows(DataIntegrityViolationException.class, () -> incompleteSave.save(command));
                assertAbsent(command.sessionId());
                assertEquals(before, incompleteSnapshot(previous.sessionId()));
            } finally {
                jdbc.execute("alter table " + table + " drop constraint ck_test_incomplete_write");
            }
        }
    }

    @Test
    void rollsBackTheRealCommitAfterOriginalsAndMissingDiagnosticsAreWritten() {
        var previous = incomplete(0);
        incompleteSave.save(previous);
        var before = incompleteSnapshot(previous.sessionId());
        var command = incomplete(59);
        jdbc.execute("""
                create function test_incomplete_commit() returns trigger language plpgsql as $$
                declare originals bigint; missing bigint;
                begin
                    select count(*) into originals from tds_measurement where session_id = NEW.session_id;
                    select count(*) into missing from tds_session_missing_sample where session_id = NEW.session_id;
                    raise exception 'incomplete_commit_after_%_originals_%_missing', originals, missing using errcode = '23514';
                end; $$;
                """);
        try {
            jdbc.execute("""
                    create constraint trigger ct_test_incomplete_commit after insert on tds_session
                    deferrable initially deferred for each row execute function test_incomplete_commit()
                    """);
            var error = assertThrows(RuntimeException.class, () -> incompleteSave.save(command));
            var sql = assertInstanceOf(SQLException.class, NestedExceptionUtils.getMostSpecificCause(error));
            assertEquals("23514", sql.getSQLState());
            assertTrue(sql.getMessage().contains("incomplete_commit_after_59_originals_1_missing"));
            assertAbsent(command.sessionId());
            assertEquals(before, incompleteSnapshot(previous.sessionId()));
        } finally {
            jdbc.execute("drop trigger if exists ct_test_incomplete_commit on tds_session");
            jdbc.execute("drop function if exists test_incomplete_commit()");
        }
    }

    @Test
    void enforcesMissingIdentityReferencesRangeAndVariableRedeliveryCounts() {
        var command = incomplete(59);
        incompleteSave.save(command);
        for (int seq : new int[]{0, 60, 61}) {
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                    "insert into tds_session_missing_sample values (?, ?)", command.sessionId(), seq));
        }
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into tds_session_missing_sample values (?, 1)", UUID.randomUUID()));
        for (int n : new int[]{0, 59, 60}) {
            jdbc.update("insert into tds_session_redelivery values (?, ?, now(), ?, ?, 0)",
                    UUID.randomUUID(), command.sessionId(), n, n);
        }
        assertEquals(3, headers.countBySessionId(command.sessionId()));
    }
}
