package com.juca.homewatermonitorbackend.session.application;

import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

abstract class IncompleteIntegrationSupport extends RedeliveryIntegrationSupport {
    @Autowired SaveIncompleteTdsSessionService incompleteSave;
    @Autowired ProcessIncompleteTdsSessionRedeliveryService incompleteRedelivery;
    @Autowired ProcessTdsSessionService processing;

    static SaveNormalTdsSessionCommand incomplete(int count) {
        var full = TdsRedeliveryFixtures.command();
        return withSamples(full, full.measurements().subList(0, count));
    }

    static SaveNormalTdsSessionCommand withSamples(SaveNormalTdsSessionCommand source,
            List<SaveNormalTdsSessionCommand.Measurement> samples) {
        return new SaveNormalTdsSessionCommand(source.sessionId(), source.deviceId(), source.startedAt(),
                source.samplingIntervalMs(), source.temperatureAssumedC(), source.calibrationVersion(), samples);
    }

    List<Integer> missingSequences(UUID id) {
        return jdbc.queryForList("select seq from tds_session_missing_sample where session_id = ? order by seq",
                Integer.class, id);
    }

    IncompleteSnapshot incompleteSnapshot(UUID id) {
        return new IncompleteSnapshot(snapshot(id), missingSequences(id));
    }

    void assertIncomplete(SaveNormalTdsSessionCommand command) {
        var stored = snapshot(command.sessionId());
        assertEquals("INCOMPLETE", stored.session().get("preservation_result"));
        assertEquals(60, stored.session().get("expected_sample_count"));
        assertEquals(command.measurements().size(), stored.session().get("preserved_sample_count"));
        assertEquals(0, stored.session().get("stats_sample_count"));
        for (String column : List.of("min_tds_ppm", "max_tds_ppm", "avg_tds_ppm", "median_tds_ppm")) {
            assertNull(stored.session().get(column));
        }
        var sequences = command.measurements().stream().map(SaveNormalTdsSessionCommand.Measurement::seq).sorted().toList();
        assertEquals(sequences, stored.measurements().stream().map(row -> row.get("seq")).toList());
        assertEquals(IntStream.rangeClosed(1, 60).filter(seq -> !sequences.contains(seq)).boxed().toList(),
                missingSequences(command.sessionId()));
        for (var incoming : command.measurements()) {
            var row = stored.measurements().stream().filter(m -> m.get("seq").equals(incoming.seq())).findFirst().orElseThrow();
            assertEquals(0, incoming.tdsPpm().compareTo((java.math.BigDecimal) row.get("tds_ppm")));
            assertEquals(incoming.elapsedMs(), row.get("elapsed_ms"));
            assertEquals(incoming.isWarmup(), row.get("is_warmup"));
            if (incoming.rawVoltageMv() == null) assertNull(row.get("raw_voltage_mv"));
            else assertEquals(0, incoming.rawVoltageMv().compareTo((java.math.BigDecimal) row.get("raw_voltage_mv")));
        }
    }

    void assertAbsent(UUID id) {
        for (String table : List.of("tds_session", "tds_measurement", "tds_session_missing_sample", "tds_session_redelivery")) {
            assertEquals(0L, jdbc.queryForObject("select count(*) from " + table + " where session_id = ?", Long.class, id));
        }
        assertEquals(0, detailCount(id));
    }

    record IncompleteSnapshot(Snapshot originals, List<Integer> missing) { }
}
