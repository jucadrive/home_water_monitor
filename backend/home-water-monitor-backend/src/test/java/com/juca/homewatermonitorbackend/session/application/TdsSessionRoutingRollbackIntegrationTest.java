package com.juca.homewatermonitorbackend.session.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.core.NestedExceptionUtils;

import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

// 새 진입점이 쓰기/실제 커밋 실패를 성공으로 바꾸지 않는지 DB에서 확인한다.
@Timeout(25)
class TdsSessionRoutingRollbackIntegrationTest extends RoutingConcurrencySupport {
    @Test
    void rollsBackNewMeasurementsOnWriteFailureAndPreservesEarlierData() {
        var earlier = storedCommand();
        redelivery.process(earlier);
        var before = snapshot(earlier.sessionId());
        var command = TdsRedeliveryFixtures.command();
        jdbc.execute("""
                alter table tds_measurement add constraint ck_test_routing_measurement
                check (session_id <> '%s'::uuid or seq <> 40) not valid
                """.formatted(command.sessionId()));
        try {
            assertSqlState("23514", () -> routing.process(command));
            assertNoNewData(command.sessionId());
            assertPriorData(earlier, before);
        } finally {
            jdbc.execute("alter table tds_measurement drop constraint ck_test_routing_measurement");
        }
    }

    @Test
    void rollsBackNewSessionWhenTheRealCommitFailsAfterSixtyMeasurements() {
        var earlier = storedCommand();
        redelivery.process(earlier);
        var before = snapshot(earlier.sessionId());
        var command = TdsRedeliveryFixtures.command();
        installCommitFailure(true);
        try {
            var sql = assertSqlState("23514", () -> routing.process(command));
            assertTrue(sql.getMessage().contains("routing_commit_rejected_after_60_samples"));
            assertNoNewData(command.sessionId());
            assertPriorData(earlier, before);
        } finally {
            removeCommitFailure(true);
        }
    }

    @Test
    void rollsBackExistingSessionDiagnosticsOnWriteFailure() {
        var command = storedCommand();
        redelivery.process(command);
        var before = snapshot(command.sessionId());
        jdbc.execute("""
                alter table tds_measurement_redelivery add constraint ck_test_routing_detail
                check (seq <> 40) not valid
                """);
        try {
            assertSqlState("23514", () -> routing.process(command));
            assertPriorData(command, before);
        } finally {
            jdbc.execute("alter table tds_measurement_redelivery drop constraint ck_test_routing_detail");
        }
    }

    @Test
    void rollsBackExistingDiagnosticsWhenTheRealCommitFailsAfterSixtyDetails() {
        var command = storedCommand();
        redelivery.process(command);
        var before = snapshot(command.sessionId());
        installCommitFailure(false);
        try {
            var sql = assertSqlState("23514", () -> routing.process(command));
            assertTrue(sql.getMessage().contains("routing_commit_rejected_after_60_samples"));
            assertPriorData(command, before);
        } finally {
            removeCommitFailure(false);
        }
    }

    @Test
    void rollsBackRecoveryDiagnosticsWithoutRemovingTheWinningOriginal() throws Exception {
        var command = TdsRedeliveryFixtures.command();
        installCommitFailure(false);
        try (var workers = Executors.newFixedThreadPool(2);
             var held = holdFirstSave(workers, command, false)) {
            var waiting = workers.submit(() -> routing.process(command));
            awaitInsertWait(held);
            held.close();
            held.future().get(8, TimeUnit.SECONDS);
            var before = snapshot(command.sessionId());
            var error = assertThrows(ExecutionException.class, () -> waiting.get(8, TimeUnit.SECONDS));
            var sql = assertInstanceOf(SQLException.class, NestedExceptionUtils.getMostSpecificCause(error));
            assertEquals("23514", sql.getSQLState());
            assertTrue(sql.getMessage().contains("routing_commit_rejected_after_60_samples"));
            assertEquals(1, sessionCount(command.sessionId()));
            assertEquals(60, before.measurements().size());
            assertEquals(0, headers.countBySessionId(command.sessionId()));
            assertEquals(0, detailCount(command.sessionId()));
            assertUnchanged(command.sessionId(), before);
        } finally {
            removeCommitFailure(false);
        }
    }

    private void assertPriorData(SaveNormalTdsSessionCommand command, Snapshot before) {
        assertUnchanged(command.sessionId(), before);
        assertEquals(1, headers.countBySessionId(command.sessionId()));
        assertEquals(60, detailCount(command.sessionId()));
    }

    private SQLException assertSqlState(String state, org.junit.jupiter.api.function.Executable action) {
        var error = assertThrows(RuntimeException.class, action);
        var sql = assertInstanceOf(SQLException.class, NestedExceptionUtils.getMostSpecificCause(error));
        assertEquals(state, sql.getSQLState());
        return sql;
    }

    private void installCommitFailure(boolean initial) {
        String countQuery = initial
                ? "select count(*) into sample_count from tds_measurement where session_id = NEW.session_id"
                : "select count(*) into sample_count from tds_measurement_redelivery where redelivery_id = NEW.redelivery_id";
        jdbc.execute("""
                create function test_reject_routing_commit() returns trigger language plpgsql as $$
                declare sample_count bigint;
                begin
                    %s;
                    raise exception 'routing_commit_rejected_after_%%_samples', sample_count using errcode = '23514';
                end;
                $$;
                """.formatted(countQuery));
        jdbc.execute("""
                create constraint trigger ct_test_reject_routing_commit after insert on %s
                deferrable initially deferred for each row execute function test_reject_routing_commit()
                """.formatted(initial ? "tds_session" : "tds_session_redelivery"));
    }

    private void removeCommitFailure(boolean initial) {
        jdbc.execute("drop trigger if exists ct_test_reject_routing_commit on "
                + (initial ? "tds_session" : "tds_session_redelivery"));
        jdbc.execute("drop function if exists test_reject_routing_commit()");
    }
}
