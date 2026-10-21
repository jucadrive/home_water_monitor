package com.juca.homewatermonitorbackend.session.application;

import com.juca.homewatermonitorbackend.session.infrastructure.persistence.TdsSessionInsertConflictClassifier;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// 제한된 복구 횟수와 원래 예외 전달은 DB 동시성 검증과 별도로 확인한다.
class ProcessNormalTdsSessionServiceTest {
    private final NormalTdsSessionProcessingTransactionService processing =
            mock(NormalTdsSessionProcessingTransactionService.class);
    private final ProcessNormalTdsSessionService service = new ProcessNormalTdsSessionService(
            new NormalTdsSessionInputValidator(new TdsSessionInputValidator()), processing, new TdsSessionInsertConflictClassifier());

    @Test
    void recoversOnlyOnceAfterTheFirstCallHasThrown() {
        var command = TdsRedeliveryFixtures.command();
        var expected = NormalTdsSessionProcessingResult.redelivered(new TdsSessionRedeliveryResult(
                UUID.randomUUID(), command.sessionId(), "NORMAL", 60, 60, 0));
        when(processing.process(command)).thenThrow(primaryKeyConflict());
        when(processing.processExisting(command)).thenReturn(expected);
        assertSame(expected, service.process(command));
        var order = inOrder(processing);
        order.verify(processing).process(command);
        order.verify(processing).processExisting(command);
        verifyNoMoreInteractions(processing);
    }

    @Test
    void doesNotRetryARecoveryFailureEvenIfItIsAnotherPrimaryKeyViolation() {
        var command = TdsRedeliveryFixtures.command();
        var recoveryFailure = primaryKeyConflict();
        when(processing.process(command)).thenThrow(primaryKeyConflict());
        when(processing.processExisting(command)).thenThrow(recoveryFailure);
        assertSame(recoveryFailure, assertThrows(RuntimeException.class, () -> service.process(command)));
        verify(processing).process(command);
        verify(processing).processExisting(command);
        verifyNoMoreInteractions(processing);
    }

    @Test
    void propagatesOtherFailuresAndRejectsMalformedInputWithoutAttemptingProcessing() {
        var command = TdsRedeliveryFixtures.command();
        var failure = new IllegalStateException("unknown commit outcome");
        when(processing.process(command)).thenThrow(failure);
        assertSame(failure, assertThrows(RuntimeException.class, () -> service.process(command)));
        verify(processing).process(command);
        verifyNoMoreInteractions(processing);
        clearInvocations(processing);
        assertThrows(IllegalArgumentException.class, () -> service.process(null));
        verifyNoInteractions(processing);
    }

    private RuntimeException primaryKeyConflict() {
        return new ConstraintViolationException("test", new SQLException("test", "23505"), "tds_session_pkey");
    }
}
