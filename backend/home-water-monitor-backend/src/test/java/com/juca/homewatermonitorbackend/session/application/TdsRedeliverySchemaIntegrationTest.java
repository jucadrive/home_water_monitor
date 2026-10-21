package com.juca.homewatermonitorbackend.session.application;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// V3/V4의 실제 PostgreSQL 제약을 우회 입력으로 검증한다.
class TdsRedeliverySchemaIntegrationTest extends RedeliveryIntegrationSupport {
    @Test
    void rejectsInvalidHeaderCountsAndMissingSession() {
        var c = storedCommand();
        for (int[] counts : new int[][]{{61, 61, 0}, {-1, 0, -1}, {0, 1, 0},
                {60, -1, 61}, {60, 61, -1}, {60, 59, 0}}) {
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                    insert into tds_session_redelivery values (?, ?, now(), ?, ?, ?)
                    """, UUID.randomUUID(), c.sessionId(), counts[0], counts[1], counts[2]));
        }
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                insert into tds_session_redelivery values (?, ?, now(), 60, 60, 0)
                """, UUID.randomUUID(), UUID.randomUUID()));
        assertEquals(0, headers.countBySessionId(c.sessionId()));
    }

    @Test
    void enforcesDetailIdentityReferencesAndValueConstraints() {
        var c = storedCommand();
        var result = redelivery.process(c);
        UUID id = result.redeliveryId();
        for (String assignment : new String[]{"seq = 0", "seq = 61", "comparison_result = 'UNKNOWN'",
                "original_elapsed_ms = -1", "incoming_elapsed_ms = -1", "incoming_tds_ppm = null"}) {
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                    "update tds_measurement_redelivery set " + assignment + " where redelivery_id = ? and seq = 1", id));
        }
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                update tds_measurement_redelivery set redelivery_id = ? where redelivery_id = ? and seq = 1
                """, UUID.randomUUID(), id));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                insert into tds_measurement_redelivery select * from tds_measurement_redelivery
                where redelivery_id = ? and seq = 1
                """, id));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("delete from tds_session_redelivery where redelivery_id = ?", id));
        assertEquals(60, details.findByIdRedeliveryIdOrderByIdSeqAsc(id).size());
    }

    @Test
    void exposesZeroTotalsBeforeAnySuccessfulRedelivery() {
        var totals = headers.totalsBySessionId(storedCommand().sessionId());
        assertAll(() -> assertEquals(0, totals.getReceptionCount()),
                () -> assertEquals(0, totals.getIdenticalCount()), () -> assertEquals(0, totals.getConflictCount()));
    }

    @Test
    void retainsNewSessionRejectionWhenNoStatisticsSamplesExist() {
        var c = TdsRedeliveryFixtures.map(TdsRedeliveryFixtures.command(), m ->
                new SaveNormalTdsSessionCommand.Measurement(m.seq(), m.elapsedMs(), m.tdsPpm(), true, m.rawVoltageMv()));
        assertThrows(IllegalArgumentException.class, () -> initialSave.save(c));
        assertEquals(0, jdbc.queryForObject("select count(*) from tds_session where session_id = ?", Long.class, c.sessionId()));
    }
}
