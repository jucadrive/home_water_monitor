package com.juca.homewatermonitorbackend.session.application;

import com.juca.homewatermonitorbackend.measurement.domain.TdsMeasurement;
import com.juca.homewatermonitorbackend.measurement.domain.TdsMeasurementComparisonResult;
import com.juca.homewatermonitorbackend.measurement.infrastructure.persistence.TdsMeasurementEntity;
import com.juca.homewatermonitorbackend.measurement.infrastructure.persistence.TdsMeasurementJpaRepository;
import com.juca.homewatermonitorbackend.measurement.infrastructure.persistence.TdsMeasurementRedeliveryEntity;
import com.juca.homewatermonitorbackend.session.infrastructure.persistence.TdsRedeliveryPersistenceAdapter;
import com.juca.homewatermonitorbackend.session.infrastructure.persistence.TdsSessionEntity;
import com.juca.homewatermonitorbackend.session.infrastructure.persistence.TdsSessionLockAdapter;
import com.juca.homewatermonitorbackend.session.infrastructure.persistence.TdsSessionRedeliveryEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// 이미 정상 커밋된 세션만 비교한다. 신규 저장·불완전 보존·MQTT 발행은 수행하지 않는다.
@Service
public class ProcessNormalTdsSessionRedeliveryService {
    private final NormalTdsSessionInputValidator validator;
    private final TdsSessionLockAdapter lockAdapter;
    private final TdsMeasurementJpaRepository measurements;
    private final TdsRedeliveryPersistenceAdapter persistence;
    private final TdsSessionMetadataValidator metadata;

    public ProcessNormalTdsSessionRedeliveryService(NormalTdsSessionInputValidator validator,
            TdsSessionLockAdapter lockAdapter, TdsMeasurementJpaRepository measurements,
            TdsRedeliveryPersistenceAdapter persistence, TdsSessionMetadataValidator metadata) {
        this.validator = validator;
        this.lockAdapter = lockAdapter;
        this.measurements = measurements;
        this.persistence = persistence;
        this.metadata = metadata;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TdsSessionRedeliveryResult process(SaveNormalTdsSessionCommand command) {
        Instant receivedAt = Instant.now();
        validator.validate(command);
        UUID redeliveryId = UUID.randomUUID();
        TdsSessionEntity session = lockAdapter.lock(command.sessionId())
                .orElseThrow(() -> new IllegalArgumentException("기존 세션이 없습니다."));
        validateSession(session, command);
        List<TdsMeasurementEntity> originals = measurements.findBySessionIdOrderBySeqAsc(command.sessionId());
        // 건수뿐 아니라 보존된 순번 집합도 확인하여 불일치를 거절한다.
        if (originals.size() != 60) {
            throw new IllegalArgumentException("기존 원본 60개가 필요합니다.");
        }
        for (int index = 0; index < originals.size(); index++) {
            if (originals.get(index).getSeq() != index + 1) {
                throw new IllegalArgumentException("기존 원본 순번이 올바르지 않습니다.");
            }
        }
        var incomingBySeq = command.measurements().stream().collect(Collectors.toMap(
                SaveNormalTdsSessionCommand.Measurement::seq, Function.identity()));
        List<TdsMeasurementRedeliveryEntity> details = originals.stream().map(original -> {
            var incoming = incomingBySeq.get(original.getSeq());
            return new TdsMeasurementRedeliveryEntity(redeliveryId,
                    new TdsMeasurement(command.sessionId(), original.getSeq(), original.getElapsedMs(),
                            original.getTdsPpm(), original.isWarmup(), original.getRawVoltageMv()),
                    new TdsMeasurement(command.sessionId(), incoming.seq(), incoming.elapsedMs(),
                            incoming.tdsPpm(), incoming.isWarmup(), incoming.rawVoltageMv()));
        }).toList();
        int identical = (int) details.stream()
                .filter(detail -> detail.getComparisonResult() == TdsMeasurementComparisonResult.IDENTICAL).count();
        int conflicts = details.size() - identical;
        persistence.insert(new TdsSessionRedeliveryEntity(redeliveryId, command.sessionId(), receivedAt,
                identical, conflicts), details);
        // Spring 프록시의 실제 커밋 실패는 호출자에게 예외로 전달된다.
        return new TdsSessionRedeliveryResult(redeliveryId, command.sessionId(), "NORMAL", 60, identical, conflicts);
    }

    private void validateSession(TdsSessionEntity session, SaveNormalTdsSessionCommand command) {
        if (!"NORMAL".equals(session.getPreservationResult()) || session.getExpectedSampleCount() != 60
                || session.getPreservedSampleCount() != 60) {
            throw new IllegalArgumentException("정상 원본 60개를 보존한 세션만 재수신할 수 있습니다.");
        }
        metadata.validate(session, command);
    }
}
