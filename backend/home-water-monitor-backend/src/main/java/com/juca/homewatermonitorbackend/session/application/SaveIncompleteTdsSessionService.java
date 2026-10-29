package com.juca.homewatermonitorbackend.session.application;

import com.juca.homewatermonitorbackend.measurement.infrastructure.persistence.TdsMeasurementEntity;
import com.juca.homewatermonitorbackend.measurement.infrastructure.persistence.TdsMeasurementJpaRepository;
import com.juca.homewatermonitorbackend.session.infrastructure.persistence.TdsSessionEntity;
import com.juca.homewatermonitorbackend.session.infrastructure.persistence.TdsSessionMissingSampleAdapter;
import com.juca.homewatermonitorbackend.session.infrastructure.persistence.TdsSessionPersistenceAdapter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

// 원본·INCOMPLETE 결과·최초 누락 진단을 하나의 트랜잭션으로 확정한다.
@Service
public class SaveIncompleteTdsSessionService {
    private final TdsSessionInputValidator validator;
    private final TdsSessionPersistenceAdapter sessions;
    private final TdsMeasurementJpaRepository measurements;
    private final TdsSessionMissingSampleAdapter missing;

    public SaveIncompleteTdsSessionService(TdsSessionInputValidator validator, TdsSessionPersistenceAdapter sessions,
            TdsMeasurementJpaRepository measurements, TdsSessionMissingSampleAdapter missing) {
        this.validator = validator;
        this.sessions = sessions;
        this.measurements = measurements;
        this.missing = missing;
    }

    @Transactional
    public UUID save(SaveNormalTdsSessionCommand command) {
        validator.validate(command);
        if (command.measurements().size() == 60) {
            throw new IllegalArgumentException("불완전 최초 목록에는 원본 0~59개가 필요합니다.");
        }
        sessions.insert(new TdsSessionEntity(command.sessionId(), command.deviceId(), command.startedAt(), Instant.now(),
                60, command.samplingIntervalMs(), command.measurements().size(), 0, "INCOMPLETE",
                command.temperatureAssumedC(), command.calibrationVersion(), null, null, null, null).temperatureContext(command.temperatureSource(), command.compensationVersion()));
        measurements.saveAll(command.measurements().stream().map(m -> new TdsMeasurementEntity(command.sessionId(),
                m.seq(), m.elapsedMs(), m.tdsPpm(), m.isWarmup(), m.rawVoltageMv(), m.temperatureC(), m.temperatureElapsedMs())).toList());
        var present = command.measurements().stream().map(SaveNormalTdsSessionCommand.Measurement::seq)
                .collect(Collectors.toSet());
        missing.insert(command.sessionId(), IntStream.rangeClosed(1, 60).filter(seq -> !present.contains(seq))
                .boxed().toList());
        return command.sessionId();
    }
}
