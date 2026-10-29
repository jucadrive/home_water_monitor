package com.juca.homewatermonitorbackend.session.application;

import com.juca.homewatermonitorbackend.measurement.domain.TdsMeasurement;
import com.juca.homewatermonitorbackend.measurement.domain.TdsMeasurementComparisonResult;
import com.juca.homewatermonitorbackend.measurement.infrastructure.persistence.TdsMeasurementEntity;
import com.juca.homewatermonitorbackend.measurement.infrastructure.persistence.TdsMeasurementJpaRepository;
import com.juca.homewatermonitorbackend.measurement.infrastructure.persistence.TdsMeasurementRedeliveryEntity;
import com.juca.homewatermonitorbackend.session.infrastructure.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

// 최초 결과를 고정하고 같은 seq 집합만 비교한다. 원본/누락 진단을 추가·삭제하지 않는다.
@Service
public class ProcessIncompleteTdsSessionRedeliveryService {
    private final TdsSessionInputValidator validator;
    private final TdsSessionMetadataValidator metadata;
    private final TdsSessionLockAdapter locks;
    private final TdsMeasurementJpaRepository measurements;
    private final TdsSessionMissingSampleAdapter missing;
    private final TdsRedeliveryPersistenceAdapter persistence;

    public ProcessIncompleteTdsSessionRedeliveryService(TdsSessionInputValidator validator,
            TdsSessionMetadataValidator metadata, TdsSessionLockAdapter locks,
            TdsMeasurementJpaRepository measurements, TdsSessionMissingSampleAdapter missing,
            TdsRedeliveryPersistenceAdapter persistence) {
        this.validator = validator;
        this.metadata = metadata;
        this.locks = locks;
        this.measurements = measurements;
        this.missing = missing;
        this.persistence = persistence;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TdsSessionRedeliveryResult process(SaveNormalTdsSessionCommand command) {
        Instant receivedAt = Instant.now();
        validator.validate(command);
        var session = locks.lock(command.sessionId())
                .orElseThrow(() -> new IllegalArgumentException("기존 세션이 없습니다."));
        var originals = measurements.findBySessionIdOrderBySeqAsc(command.sessionId());
        validateStored(session, originals);
        metadata.validate(session, command);
        var incomingBySeq = command.measurements().stream().collect(Collectors.toMap(
                SaveNormalTdsSessionCommand.Measurement::seq, Function.identity()));
        var originalSequences = originals.stream().map(TdsMeasurementEntity::getSeq).collect(Collectors.toSet());
        if (!originalSequences.equals(incomingBySeq.keySet())) {
            throw new IllegalArgumentException("확정된 불완전 세션에 원본을 추가하거나 삭제할 수 없습니다.");
        }
        UUID redeliveryId = UUID.randomUUID();
        var details = originals.stream().map(original -> {
            var incoming = incomingBySeq.get(original.getSeq());
            return new TdsMeasurementRedeliveryEntity(redeliveryId,
                    new TdsMeasurement(command.sessionId(), original.getSeq(), original.getElapsedMs(),
                            original.getTdsPpm(), original.isWarmup(), original.getRawVoltageMv(), original.getTemperatureC(), original.getTemperatureElapsedMs()),
                    new TdsMeasurement(command.sessionId(), incoming.seq(), incoming.elapsedMs(),
                            incoming.tdsPpm(), incoming.isWarmup(), incoming.rawVoltageMv(), incoming.temperatureC(), incoming.temperatureElapsedMs()));
        }).toList();
        int identical = (int) details.stream()
                .filter(d -> d.getComparisonResult() == TdsMeasurementComparisonResult.IDENTICAL).count();
        int conflicts = details.size() - identical;
        persistence.insert(new TdsSessionRedeliveryEntity(redeliveryId, command.sessionId(), receivedAt,
                identical, conflicts), details);
        return new TdsSessionRedeliveryResult(redeliveryId, command.sessionId(), "INCOMPLETE",
                originals.size(), identical, conflicts);
    }

    private void validateStored(TdsSessionEntity session, List<TdsMeasurementEntity> originals) {
        if (!"INCOMPLETE".equals(session.getPreservationResult()) || session.getExpectedSampleCount() != 60
                || originals.size() >= 60 || session.getPreservedSampleCount() != originals.size()
                || session.getStatsSampleCount() != 0 || session.getMinTdsPpm() != null || session.getMaxTdsPpm() != null
                || session.getAvgTdsPpm() != null || session.getMedianTdsPpm() != null
                || originals.stream().anyMatch(m -> m.getSeq() < 1 || m.getSeq() > 60)) {
            throw new IllegalArgumentException("기존 불완전 원본/보존 결과가 일치하지 않습니다.");
        }
        var sequences = originals.stream().map(TdsMeasurementEntity::getSeq).collect(Collectors.toSet());
        var expectedMissing = IntStream.rangeClosed(1, 60).filter(seq -> !sequences.contains(seq)).boxed().toList();
        if (!expectedMissing.equals(missing.findSequences(session.getSessionId()))) {
            throw new IllegalArgumentException("기존 누락 진단이 원본과 일치하지 않습니다.");
        }
    }
}
