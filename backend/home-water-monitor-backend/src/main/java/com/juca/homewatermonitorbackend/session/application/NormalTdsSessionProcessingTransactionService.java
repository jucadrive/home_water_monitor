package com.juca.homewatermonitorbackend.session.application;

import com.juca.homewatermonitorbackend.session.infrastructure.persistence.TdsSessionLockAdapter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

// 조율 서비스가 별도 Bean의 프록시로 호출하여 반환 전에 실제 트랜잭션을 종료한다.
@Service
public class NormalTdsSessionProcessingTransactionService {
    private final TdsSessionLockAdapter locks;
    private final SaveNormalTdsSessionService initialSave;
    private final ProcessNormalTdsSessionRedeliveryService redelivery;

    public NormalTdsSessionProcessingTransactionService(TdsSessionLockAdapter locks,
            SaveNormalTdsSessionService initialSave, ProcessNormalTdsSessionRedeliveryService redelivery) {
        this.locks = locks;
        this.initialSave = initialSave;
        this.redelivery = redelivery;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public NormalTdsSessionProcessingResult process(SaveNormalTdsSessionCommand command) {
        // 미존재 시에도 트랜잭션 한정 lock_timeout이 이후 INSERT의 대기에 적용된다.
        if (locks.lock(command.sessionId()).isPresent()) {
            return NormalTdsSessionProcessingResult.redelivered(redelivery.process(command));
        }
        return NormalTdsSessionProcessingResult.saved(initialSave.save(command));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public NormalTdsSessionProcessingResult processExisting(SaveNormalTdsSessionCommand command) {
        // 복구에서는 반드시 기존 세션을 요구하며 다시 신규 저장으로 분기하지 않는다.
        return NormalTdsSessionProcessingResult.redelivered(redelivery.process(command));
    }
}
