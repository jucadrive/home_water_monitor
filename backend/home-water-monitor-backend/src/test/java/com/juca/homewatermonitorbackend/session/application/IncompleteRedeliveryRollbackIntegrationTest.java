package com.juca.homewatermonitorbackend.session.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.core.NestedExceptionUtils;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(30)
class IncompleteRedeliveryRollbackIntegrationTest extends IncompleteConcurrencySupport {
    @Test
    void rollsBackHeaderAndDetailWriteFailuresWithoutChangingEarlierDiagnostics() {
        for (String table : List.of("tds_session_redelivery", "tds_measurement_redelivery")) {
            var command = incomplete(59);
            processing.process(command);
            processing.process(command);
            var before = incompleteSnapshot(command.sessionId());
            String condition = table.equals("tds_session_redelivery")
                    ? "session_id <> '" + command.sessionId() + "'::uuid" : "seq <> 40";
            jdbc.execute("alter table " + table + " add constraint ck_test_incomplete_redelivery check ("
                    + condition + ") not valid");
            try {
                var error = assertThrows(RuntimeException.class, () -> processing.process(command));
                assertEquals("23514", sqlCause(error).getSQLState());
                assertEquals(before, incompleteSnapshot(command.sessionId()));
                assertEquals(1, headers.countBySessionId(command.sessionId()));
                assertEquals(59, detailCount(command.sessionId()));
            } finally {
                jdbc.execute("alter table " + table + " drop constraint ck_test_incomplete_redelivery");
            }
        }
    }

    @Test
    void rollsBackRealRedeliveryCommitsIncludingEmptyEventsAndPreservesEarlierData() {
        for (int count : new int[]{0, 59}) {
            var command = incomplete(count);
            processing.process(command);
            processing.process(command);
            var before = incompleteSnapshot(command.sessionId());
            installCommitFailure();
            try {
                var error = assertThrows(RuntimeException.class, () -> processing.process(command));
                assertCommitFailure(error, count);
                assertEquals(before, incompleteSnapshot(command.sessionId()));
                assertEquals(1, headers.countBySessionId(command.sessionId()));
                assertEquals(count, detailCount(command.sessionId()));
            } finally { removeCommitFailure(); }
        }
    }

    @Test
    void rollsBackRecoveryCommitWithoutDeletingTheWinningOriginalAndMissingDiagnostics() throws Exception {
        var command = incomplete(59);
        installCommitFailure();
        try (var workers = Executors.newFixedThreadPool(2); var held = hold(workers, command, false, false)) {
            var waiting = workers.submit(() -> processing.process(command));
            awaitDatabaseWait(held, true);
            held.close();
            held.future().get(8, TimeUnit.SECONDS);
            var before = incompleteSnapshot(command.sessionId());
            var error = assertThrows(ExecutionException.class, () -> waiting.get(8, TimeUnit.SECONDS));
            assertCommitFailure(error, 59);
            assertEquals(before, incompleteSnapshot(command.sessionId()));
            assertIncomplete(command);
            assertEquals(0, headers.countBySessionId(command.sessionId()));
            assertEquals(0, detailCount(command.sessionId()));
        } finally { removeCommitFailure(); }
        assertEquals(59, processing.process(command).redeliveryResult().identicalCount());
    }

    @Test
    void propagatesNewSessionCommitFailureThroughTheUnifiedEntry() {
        var earlier = incomplete(1);
        processing.process(earlier);
        var before = incompleteSnapshot(earlier.sessionId());
        var command = incomplete(0);
        jdbc.execute("""
                create function test_unified_incomplete_commit() returns trigger language plpgsql as $$
                declare missing bigint;
                begin
                    select count(*) into missing from tds_session_missing_sample where session_id = NEW.session_id;
                    raise exception 'unified_incomplete_commit_%_missing', missing using errcode = '23514';
                end; $$;
                """);
        try {
            jdbc.execute("""
                    create constraint trigger ct_test_unified_incomplete_commit after insert on tds_session
                    deferrable initially deferred for each row execute function test_unified_incomplete_commit()
                    """);
            var error = assertThrows(RuntimeException.class, () -> processing.process(command));
            assertEquals("23514", sqlCause(error).getSQLState());
            assertTrue(sqlCause(error).getMessage().contains("unified_incomplete_commit_60_missing"));
            assertAbsent(command.sessionId());
            assertEquals(before, incompleteSnapshot(earlier.sessionId()));
        } finally {
            jdbc.execute("drop trigger if exists ct_test_unified_incomplete_commit on tds_session");
            jdbc.execute("drop function if exists test_unified_incomplete_commit()");
        }
    }

    private SQLException sqlCause(Throwable error) {
        return assertInstanceOf(SQLException.class, NestedExceptionUtils.getMostSpecificCause(error));
    }

    private void assertCommitFailure(Throwable error, int details) {
        var sql = sqlCause(error);
        assertEquals("23514", sql.getSQLState());
        assertTrue(sql.getMessage().contains("incomplete_redelivery_commit_" + details + "_details"));
    }

    private void installCommitFailure() {
        jdbc.execute("""
                create function test_incomplete_redelivery_commit() returns trigger language plpgsql as $$
                declare details bigint;
                begin
                    select count(*) into details from tds_measurement_redelivery where redelivery_id = NEW.redelivery_id;
                    raise exception 'incomplete_redelivery_commit_%_details', details using errcode = '23514';
                end; $$;
                """);
        jdbc.execute("""
                create constraint trigger ct_test_incomplete_redelivery_commit after insert on tds_session_redelivery
                deferrable initially deferred for each row execute function test_incomplete_redelivery_commit()
                """);
    }

    private void removeCommitFailure() {
        jdbc.execute("drop trigger if exists ct_test_incomplete_redelivery_commit on tds_session_redelivery");
        jdbc.execute("drop function if exists test_incomplete_redelivery_commit()");
    }
}
