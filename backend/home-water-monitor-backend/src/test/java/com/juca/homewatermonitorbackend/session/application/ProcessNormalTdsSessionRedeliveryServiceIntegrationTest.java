package com.juca.homewatermonitorbackend.session.application;

import com.juca.homewatermonitorbackend.measurement.domain.TdsMeasurementComparisonResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// 프록시 반환 이후 진단·원본을 조회하여 정상 재수신과 거절 경계를 검증한다.
class ProcessNormalTdsSessionRedeliveryServiceIntegrationTest extends RedeliveryIntegrationSupport {
    @Test
    void recordsIdenticalRepeatedListsBySequenceWithoutChangingOriginals() {
        var c = storedCommand();
        var before = snapshot(c.sessionId());
        var reversed = new ArrayList<>(c.measurements());
        Collections.reverse(reversed);
        var reordered = new SaveNormalTdsSessionCommand(c.sessionId(), c.deviceId(), c.startedAt(), 1000,
                new BigDecimal("25.000"), c.calibrationVersion(), reversed);
        var first = redelivery.process(TdsRedeliveryFixtures.map(reordered, m ->
                new SaveNormalTdsSessionCommand.Measurement(m.seq(), m.elapsedMs(), m.tdsPpm().setScale(3),
                        m.isWarmup(), m.rawVoltageMv().setScale(3))));
        var second = redelivery.process(c);
        assertNotEquals(first.redeliveryId(), second.redeliveryId());
        assertEquals(TdsSessionRedeliveryResult.Outcome.ALL_IDENTICAL, first.outcome());
        assertEquals("NORMAL", first.preservationResult());
        assertEquals(60, first.preservedSampleCount());
        assertEquals(60, first.identicalCount());
        assertEquals(0, first.conflictCount());
        var recorded = details.findByIdRedeliveryIdOrderByIdSeqAsc(first.redeliveryId());
        assertEquals(60, recorded.size());
        for (int index = 0; index < recorded.size(); index++) {
            var detail = recorded.get(index);
            var expected = c.measurements().get(index);
            assertEquals(index + 1, detail.getId().getSeq());
            assertEquals(TdsMeasurementComparisonResult.IDENTICAL, detail.getComparisonResult());
            assertEquals(expected.elapsedMs(), detail.getOriginal().getElapsedMs());
            assertEquals(0, expected.tdsPpm().compareTo(detail.getIncoming().getTdsPpm()));
            assertEquals(expected.isWarmup(), detail.getIncoming().isWarmup());
            assertEquals(0, expected.rawVoltageMv().compareTo(detail.getOriginal().getRawVoltageMv()));
        }
        var totals = headers.totalsBySessionId(c.sessionId());
        assertEquals(2, totals.getReceptionCount());
        assertEquals(120, totals.getIdenticalCount());
        assertEquals(0, totals.getConflictCount());
        assertEquals(120, detailCount(c.sessionId()));
        assertUnchanged(c.sessionId(), before);
    }

    @Test
    void snapshotsEachChangedFieldAndBothVoltageNullDirections() {
        var c = TdsRedeliveryFixtures.map(TdsRedeliveryFixtures.command(), m -> m.seq() == 5
                ? new SaveNormalTdsSessionCommand.Measurement(m.seq(), m.elapsedMs(), m.tdsPpm(), m.isWarmup(), null) : m);
        initialSave.save(c);
        var before = snapshot(c.sessionId());
        var changed = TdsRedeliveryFixtures.map(c, m -> switch (m.seq()) {
            case 1 -> new SaveNormalTdsSessionCommand.Measurement(1, m.elapsedMs(), new BigDecimal("301"), m.isWarmup(), m.rawVoltageMv());
            case 2 -> new SaveNormalTdsSessionCommand.Measurement(2, 2001, m.tdsPpm(), m.isWarmup(), m.rawVoltageMv());
            case 3 -> new SaveNormalTdsSessionCommand.Measurement(3, m.elapsedMs(), m.tdsPpm(), !m.isWarmup(), m.rawVoltageMv());
            case 4 -> new SaveNormalTdsSessionCommand.Measurement(4, m.elapsedMs(), m.tdsPpm(), m.isWarmup(), null);
            case 5 -> new SaveNormalTdsSessionCommand.Measurement(5, m.elapsedMs(), m.tdsPpm(), m.isWarmup(), BigDecimal.ONE);
            case 6 -> new SaveNormalTdsSessionCommand.Measurement(6, m.elapsedMs(), m.tdsPpm(), m.isWarmup(), new BigDecimal("751"));
            default -> m;
        });
        var result = redelivery.process(changed);
        assertEquals(54, result.identicalCount());
        assertEquals(6, result.conflictCount());
        assertEquals(TdsSessionRedeliveryResult.Outcome.HAS_CONFLICT, result.outcome());
        var rows = details.findByIdRedeliveryIdOrderByIdSeqAsc(result.redeliveryId());
        for (int i = 0; i < 60; i++) {
            assertEquals(i < 6 ? TdsMeasurementComparisonResult.CONFLICT : TdsMeasurementComparisonResult.IDENTICAL,
                    rows.get(i).getComparisonResult());
        }
        assertEquals(new BigDecimal("300.00"), rows.get(0).getOriginal().getTdsPpm());
        assertEquals(new BigDecimal("301.00"), rows.get(0).getIncoming().getTdsPpm());
        assertEquals(2000, rows.get(1).getOriginal().getElapsedMs());
        assertEquals(2001, rows.get(1).getIncoming().getElapsedMs());
        assertTrue(rows.get(2).getOriginal().isWarmup());
        assertFalse(rows.get(2).getIncoming().isWarmup());
        assertNull(rows.get(3).getIncoming().getRawVoltageMv());
        assertNull(rows.get(4).getOriginal().getRawVoltageMv());
        assertEquals(new BigDecimal("1.00"), rows.get(4).getIncoming().getRawVoltageMv());
        assertEquals(new BigDecimal("751.00"), rows.get(5).getIncoming().getRawVoltageMv());
        assertEquals(54, headers.totalsBySessionId(c.sessionId()).getIdenticalCount());
        assertEquals(6, headers.totalsBySessionId(c.sessionId()).getConflictCount());
        assertUnchanged(c.sessionId(), before);
    }

    @Test
    void acceptsAllWarmupAsConflictsWithoutRecalculatingStatistics() {
        var c = storedCommand();
        var before = snapshot(c.sessionId());
        var result = redelivery.process(TdsRedeliveryFixtures.map(c, m ->
                new SaveNormalTdsSessionCommand.Measurement(m.seq(), m.elapsedMs(), BigDecimal.ONE, true, null)));
        assertEquals(0, result.identicalCount());
        assertEquals(60, result.conflictCount());
        assertUnchanged(c.sessionId(), before);
    }

    @Test
    void rejectsChangedMetadataIncludingNullTimeWithoutDiagnostics() {
        var c = storedCommand();
        var before = snapshot(c.sessionId());
        var invalid = List.of(
                new SaveNormalTdsSessionCommand(c.sessionId(), "other", c.startedAt(), 1000, c.temperatureAssumedC(), c.calibrationVersion(), c.measurements()),
                new SaveNormalTdsSessionCommand(c.sessionId(), c.deviceId(), c.startedAt(), 1000, BigDecimal.ONE, c.calibrationVersion(), c.measurements()),
                new SaveNormalTdsSessionCommand(c.sessionId(), c.deviceId(), c.startedAt(), 1000, c.temperatureAssumedC(), "other", c.measurements()),
                TdsRedeliveryFixtures.startedAt(c, c.startedAt().plusSeconds(1)), TdsRedeliveryFixtures.startedAt(c, null));
        for (var input : invalid) {
            assertThrows(IllegalArgumentException.class, () -> redelivery.process(input));
        }
        jdbc.update("update tds_session set sampling_interval_ms = 2000 where session_id = ?", c.sessionId());
        assertThrows(IllegalArgumentException.class, () -> redelivery.process(c));
        jdbc.update("update tds_session set sampling_interval_ms = 1000 where session_id = ?", c.sessionId());
        assertEquals(0, headers.countBySessionId(c.sessionId()));
        assertUnchanged(c.sessionId(), before);
    }

    @Test
    void comparesDatabaseTimestampRepresentationAtRoundingAndSecondBoundaries() {
        // 동일 입력의 DB 왕복과 서로 다른 DB 표현을 모두 확인한다.
        Instant[] times = {null, Instant.parse("2026-10-21T00:00:00.123456499Z"),
                Instant.parse("2026-10-21T00:00:00.123456500Z"), Instant.parse("2026-10-21T00:00:59.999999600Z")};
        for (Instant time : times) {
            var c = TdsRedeliveryFixtures.startedAt(TdsRedeliveryFixtures.command(), time);
            initialSave.save(c);
            assertEquals(60, redelivery.process(c).identicalCount());
            var storedTime = jdbc.queryForObject("select started_at from tds_session where session_id = ?",
                    java.time.OffsetDateTime.class, c.sessionId());
            if (storedTime != null) {
                assertEquals(60, redelivery.process(TdsRedeliveryFixtures.startedAt(c, storedTime.toInstant())).identicalCount());
            }
            assertThrows(IllegalArgumentException.class, () -> redelivery.process(TdsRedeliveryFixtures.startedAt(c,
                    time == null ? Instant.EPOCH : time.plusSeconds(1))));
        }
    }

    @Test
    void rejectsMissingIncompleteAndInconsistentSessionsWithoutFallback() {
        var absent = TdsRedeliveryFixtures.command();
        assertThrows(IllegalArgumentException.class, () -> redelivery.process(absent));
        assertEquals(0, headers.countBySessionId(absent.sessionId()));
        for (String change : List.of("update tds_session set preservation_result = 'INCOMPLETE' where session_id = ?",
                "update tds_session set preserved_sample_count = 59 where session_id = ?",
                "delete from tds_measurement where session_id = ? and seq = 60",
                "update tds_measurement set seq = 61 where session_id = ? and seq = 60")) {
            var c = storedCommand();
            jdbc.update(change, c.sessionId());
            var before = snapshot(c.sessionId());
            assertThrows(IllegalArgumentException.class, () -> redelivery.process(c));
            assertEquals(0, headers.countBySessionId(c.sessionId()));
            assertUnchanged(c.sessionId(), before);
        }
    }

    @Test
    void rejectsMalformedListsWithoutWritingDiagnostics() {
        var c = storedCommand();
        var before = snapshot(c.sessionId());
        var longer = new ArrayList<>(c.measurements());
        longer.add(c.measurements().getFirst());
        for (var list : List.of(c.measurements().subList(0, 59), longer)) {
            var invalid = new SaveNormalTdsSessionCommand(c.sessionId(), c.deviceId(), c.startedAt(), 1000,
                    c.temperatureAssumedC(), c.calibrationVersion(), list);
            assertThrows(IllegalArgumentException.class, () -> redelivery.process(invalid));
        }
        for (int seq : new int[]{1, 61}) {
            var invalid = TdsRedeliveryFixtures.map(c, m -> new SaveNormalTdsSessionCommand.Measurement(
                    seq, m.elapsedMs(), m.tdsPpm(), m.isWarmup(), m.rawVoltageMv()));
            assertThrows(IllegalArgumentException.class, () -> redelivery.process(invalid));
        }
        var invalid = TdsRedeliveryFixtures.map(c, m -> new SaveNormalTdsSessionCommand.Measurement(
                m.seq(), -1, m.tdsPpm(), m.isWarmup(), m.rawVoltageMv()));
        assertThrows(IllegalArgumentException.class, () -> redelivery.process(invalid));
        assertEquals(0, headers.countBySessionId(c.sessionId()));
        assertEquals(0, detailCount(c.sessionId()));
        assertUnchanged(c.sessionId(), before);
    }
}
