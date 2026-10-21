package com.juca.homewatermonitorbackend.session.application;

import com.juca.homewatermonitorbackend.session.infrastructure.persistence.TdsSessionEntity;
import com.juca.homewatermonitorbackend.session.infrastructure.persistence.TdsSessionLockAdapter;
import org.springframework.stereotype.Component;
import java.util.Objects;

// 정상/불완전 모두 같은 메타데이터와 DB 시각 저장 표현으로 재수신 대상을 확인한다.
@Component
public class TdsSessionMetadataValidator {
    private final TdsSessionLockAdapter locks;

    public TdsSessionMetadataValidator(TdsSessionLockAdapter locks) { this.locks = locks; }

    public void validate(TdsSessionEntity session, SaveNormalTdsSessionCommand command) {
        if (!Objects.equals(session.getDeviceId(), command.deviceId())
                || !Objects.equals(session.getCalibrationVersion(), command.calibrationVersion())
                || session.getSamplingIntervalMs() != command.samplingIntervalMs()
                || session.getTemperatureAssumedC().compareTo(command.temperatureAssumedC()) != 0
                || !locks.matchesStartedAt(command.sessionId(), command.startedAt())) {
            throw new IllegalArgumentException("세션 메타데이터가 다릅니다.");
        }
    }
}
