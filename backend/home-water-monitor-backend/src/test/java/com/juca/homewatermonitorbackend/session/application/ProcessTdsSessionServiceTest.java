package com.juca.homewatermonitorbackend.session.application;

import com.juca.homewatermonitorbackend.session.infrastructure.persistence.TdsSessionInsertConflictClassifier;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import java.sql.SQLException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProcessTdsSessionServiceTest {
    private final TdsSessionProcessingTransactionService worker = mock(TdsSessionProcessingTransactionService.class);
    private final ProcessTdsSessionService service = new ProcessTdsSessionService(new TdsSessionInputValidator(),
            worker, new TdsSessionInsertConflictClassifier());

    @Test
    void recoversOnlyASessionPrimaryKeyConflictOnceAndPropagatesRecoveryFailure() {
        var command = IncompleteIntegrationSupport.incomplete(59);
        var conflict = new ConstraintViolationException("test", new SQLException("test", "23505"), "tds_session_pkey");
        var result = TdsSessionProcessingResult.redelivered(new TdsSessionRedeliveryResult(
                UUID.randomUUID(), command.sessionId(), "INCOMPLETE", 59, 59, 0));
        when(worker.process(command)).thenThrow(conflict);
        when(worker.processExisting(command)).thenReturn(result).thenThrow(conflict);
        assertSame(result, service.process(command));
        assertSame(conflict, assertThrows(RuntimeException.class, () -> service.process(command)));
        verify(worker, times(2)).process(command);
        verify(worker, times(2)).processExisting(command);
        verifyNoMoreInteractions(worker);
    }

    @Test
    void propagatesOtherConstraintAndUnknownCommitFailuresWithoutRecovery() {
        var command = IncompleteIntegrationSupport.incomplete(1);
        for (RuntimeException failure : new RuntimeException[]{new IllegalStateException("unknown commit outcome"),
                new ConstraintViolationException("test", new SQLException("test", "23505"), "other_pkey"),
                new ConstraintViolationException("test", new SQLException("test", "23514"), "tds_session_pkey")}) {
            doThrow(failure).when(worker).process(command);
            assertSame(failure, assertThrows(RuntimeException.class, () -> service.process(command)));
        }
        verify(worker, times(3)).process(command);
        verifyNoMoreInteractions(worker);
    }
}
