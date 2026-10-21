package com.juca.homewatermonitorbackend.session.application;

import com.juca.homewatermonitorbackend.session.infrastructure.persistence.TdsSessionInsertConflictClassifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 커밋/롤백 경계 밖에서 최초 저장의 세션 PK 충돌만 한 번 복구한다.
@Service
public class ProcessTdsSessionService {
    private final TdsSessionInputValidator validator;
    private final TdsSessionProcessingTransactionService processing;
    private final TdsSessionInsertConflictClassifier conflicts;

    public ProcessTdsSessionService(TdsSessionInputValidator validator,
            TdsSessionProcessingTransactionService processing, TdsSessionInsertConflictClassifier conflicts) {
        this.validator = validator;
        this.processing = processing;
        this.conflicts = conflicts;
    }

    @Transactional(propagation = Propagation.NEVER)
    public TdsSessionProcessingResult process(SaveNormalTdsSessionCommand command) {
        validator.validate(command);
        try {
            return processing.process(command);
        } catch (RuntimeException error) {
            if (!conflicts.isSessionPrimaryKeyConflict(error)) throw error;
        }
        return processing.processExisting(command);
    }
}
