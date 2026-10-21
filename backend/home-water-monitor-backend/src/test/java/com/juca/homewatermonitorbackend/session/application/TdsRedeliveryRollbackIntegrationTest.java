package com.juca.homewatermonitorbackend.session.application;

import org.junit.jupiter.api.Test;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// 헤더·상세 쓰기와 실제 커밋에서 각각 실패를 주입해 진단의 원자성을 검증한다.
class TdsRedeliveryRollbackIntegrationTest extends RedeliveryIntegrationSupport {
    @Test
    void rollsBackHeaderInsertFailureAndKeepsPreviouslyCommittedDiagnostics() {
        var c = storedCommand();
        redelivery.process(c);
        var before = snapshot(c.sessionId());
        jdbc.execute("""
                alter table tds_session_redelivery add constraint ck_test_reject_header
                check (session_id <> '%s'::uuid) not valid
                """.formatted(c.sessionId()));
        try {
            var error = assertThrows(DataIntegrityViolationException.class, () -> redelivery.process(c));
            assertTrue(error.getMostSpecificCause().getMessage().contains("ck_test_reject_header"));
            assertOnlyPreviousRemains(c.sessionId(), before);
        } finally {
            jdbc.execute("alter table tds_session_redelivery drop constraint ck_test_reject_header");
        }
    }

    @Test
    void rollsBackHeaderAndAllDetailsWhenFortiethDetailFails() {
        var c = storedCommand();
        redelivery.process(c);
        var before = snapshot(c.sessionId());
        // 이전 행은 유지하되 이번 상세 쓰기를 거절하도록 NOT VALID 제약을 추가한다.
        jdbc.execute("""
                alter table tds_measurement_redelivery add constraint ck_test_reject_detail
                check (seq <> 40) not valid
                """);
        try {
            var error = assertThrows(DataIntegrityViolationException.class, () -> redelivery.process(c));
            assertTrue(error.getMostSpecificCause().getMessage().contains("ck_test_reject_detail"));
            assertOnlyPreviousRemains(c.sessionId(), before);
        } finally {
            jdbc.execute("alter table tds_measurement_redelivery drop constraint ck_test_reject_detail");
        }
    }

    @Test
    void rollsBackAllDiagnosticsWhenCommitFailsAfterSixtyDetails() {
        var c = storedCommand();
        redelivery.process(c);
        var before = snapshot(c.sessionId());
        jdbc.execute("""
                create function test_reject_redelivery_commit() returns trigger language plpgsql as $$
                declare detail_count bigint;
                begin
                    select count(*) into detail_count from tds_measurement_redelivery
                    where redelivery_id = NEW.redelivery_id;
                    raise exception 'redelivery_commit_rejected_after_%_details', detail_count using errcode = '23514';
                end;
                $$;
                """);
        try {
            jdbc.execute("""
                    create constraint trigger ct_test_reject_redelivery_commit
                    after insert on tds_session_redelivery deferrable initially deferred
                    for each row execute function test_reject_redelivery_commit()
                    """);
            var error = assertThrows(RuntimeException.class, () -> redelivery.process(c));
            var sql = assertInstanceOf(SQLException.class, NestedExceptionUtils.getMostSpecificCause(error));
            assertEquals("23514", sql.getSQLState());
            assertTrue(sql.getMessage().contains("redelivery_commit_rejected_after_60_details"));
            assertOnlyPreviousRemains(c.sessionId(), before);
        } finally {
            jdbc.execute("drop trigger if exists ct_test_reject_redelivery_commit on tds_session_redelivery");
            jdbc.execute("drop function if exists test_reject_redelivery_commit()");
        }
    }

    private void assertOnlyPreviousRemains(UUID id, Snapshot before) {
        assertEquals(1, headers.countBySessionId(id));
        assertEquals(60, detailCount(id));
        assertEquals(60, headers.totalsBySessionId(id).getIdenticalCount());
        assertEquals(0, headers.totalsBySessionId(id).getConflictCount());
        assertUnchanged(id, before);
    }
}
