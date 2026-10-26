package com.juca.homewatermonitorbackend.session.application;

import com.juca.homewatermonitorbackend.session.infrastructure.persistence.TdsSessionEntity;
import com.juca.homewatermonitorbackend.session.infrastructure.persistence.TdsSessionLockAdapter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TdsSessionProcessingTransactionService {
    private final TdsSessionLockAdapter locks;
    private final com.juca.homewatermonitorbackend.session.infrastructure.persistence.DeviceControlStore control;
    private final SaveNormalTdsSessionService normalSave;
    private final SaveIncompleteTdsSessionService incompleteSave;
    private final ProcessNormalTdsSessionRedeliveryService normalRedelivery;
    private final ProcessIncompleteTdsSessionRedeliveryService incompleteRedelivery;

    public TdsSessionProcessingTransactionService(TdsSessionLockAdapter locks, SaveNormalTdsSessionService normalSave,
            SaveIncompleteTdsSessionService incompleteSave, ProcessNormalTdsSessionRedeliveryService normalRedelivery,
            ProcessIncompleteTdsSessionRedeliveryService incompleteRedelivery,
            com.juca.homewatermonitorbackend.session.infrastructure.persistence.DeviceControlStore control) {
        this.control=control;
        this.locks = locks;
        this.normalSave = normalSave;
        this.incompleteSave = incompleteSave;
        this.normalRedelivery = normalRedelivery;
        this.incompleteRedelivery = incompleteRedelivery;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TdsSessionProcessingResult process(SaveNormalTdsSessionCommand command) {
        if(!control.matchesSource(command.sessionId(),command.deviceId()))throw new IllegalArgumentException("SESSION_DEVICE_MISMATCH");
        var existing = locks.lock(command.sessionId());
        if (existing.isPresent()) return redeliver(existing.get(), command);
        int count = command.measurements().size();
        var id = count == 60 ? normalSave.save(command) : incompleteSave.save(command);
        return TdsSessionProcessingResult.saved(id, count);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TdsSessionProcessingResult processExisting(SaveNormalTdsSessionCommand command) {
        if(!control.matchesSource(command.sessionId(),command.deviceId()))throw new IllegalArgumentException("SESSION_DEVICE_MISMATCH");
        var session = locks.lock(command.sessionId())
                .orElseThrow(() -> new IllegalArgumentException("기존 세션이 없습니다."));
        return redeliver(session, command);
    }

    private TdsSessionProcessingResult redeliver(TdsSessionEntity session, SaveNormalTdsSessionCommand command) {
        return switch (session.getPreservationResult()) {
            case "NORMAL" -> TdsSessionProcessingResult.redelivered(normalRedelivery.process(command));
            case "INCOMPLETE" -> TdsSessionProcessingResult.redelivered(incompleteRedelivery.process(command));
            default -> throw new IllegalArgumentException("알 수 없는 보존 결과입니다.");
        };
    }
}
