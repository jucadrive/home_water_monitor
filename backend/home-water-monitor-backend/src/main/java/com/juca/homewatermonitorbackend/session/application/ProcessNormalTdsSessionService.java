package com.juca.homewatermonitorbackend.session.application;

import com.juca.homewatermonitorbackend.session.infrastructure.persistence.TdsSessionInsertConflictClassifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// DB 트랜잭션 밖에서 실제 커밋/롤백 결과를 받아 최초 저장 경합만 한 번 복구한다.
@Service
public class ProcessNormalTdsSessionService {
    private final NormalTdsSessionInputValidator validator;
    private final NormalTdsSessionProcessingTransactionService processing;
    private final TdsSessionInsertConflictClassifier conflicts;

    public ProcessNormalTdsSessionService(NormalTdsSessionInputValidator validator,
            NormalTdsSessionProcessingTransactionService processing, TdsSessionInsertConflictClassifier conflicts) {
        this.validator = validator;
        this.processing = processing;
        this.conflicts = conflicts;
    }

    @Transactional(propagation = Propagation.NEVER)
    public NormalTdsSessionProcessingResult process(SaveNormalTdsSessionCommand command) {
        validator.validate(command);
        try {
            return processing.process(command);
        } catch (RuntimeException error) {
            if (!conflicts.isSessionPrimaryKeyConflict(error)) {
                throw error;
            }
        }
        // 실패한 프록시 호출의 트랜잭션은 이미 종료됐다. 복구 실패는 재시도하지 않는다.
        return processing.processExisting(command);
    }
}
