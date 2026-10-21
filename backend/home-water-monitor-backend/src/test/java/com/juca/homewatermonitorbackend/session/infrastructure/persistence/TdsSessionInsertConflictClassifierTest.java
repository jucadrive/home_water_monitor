package com.juca.homewatermonitorbackend.session.infrastructure.persistence;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class TdsSessionInsertConflictClassifierTest {
    private final TdsSessionInsertConflictClassifier classifier = new TdsSessionInsertConflictClassifier();

    @Test
    void recognizesOnlyTheStructuredSessionPrimaryKeyViolationThroughWrappers() {
        var violation = violation("23505", "tds_session_pkey");
        assertTrue(classifier.isSessionPrimaryKeyConflict(new DataIntegrityViolationException("wrapped", violation)));
        assertFalse(classifier.isSessionPrimaryKeyConflict(violation("23505", "uk_tds_measurement_session_seq")));
        assertFalse(classifier.isSessionPrimaryKeyConflict(violation("23505", "tds_session_redelivery_pkey")));
        assertFalse(classifier.isSessionPrimaryKeyConflict(violation("23514", "tds_session_pkey")));
    }

    @Test
    void doesNotGuessFromMessagesOrIncompleteMetadata() {
        assertFalse(classifier.isSessionPrimaryKeyConflict(new SQLException("tds_session_pkey", "23505")));
        assertFalse(classifier.isSessionPrimaryKeyConflict(new RuntimeException("23505 tds_session_pkey")));
        assertFalse(classifier.isSessionPrimaryKeyConflict(violation("23505", null)));
        assertFalse(classifier.isSessionPrimaryKeyConflict(violation(null, "tds_session_pkey")));
        assertFalse(classifier.isSessionPrimaryKeyConflict(null));
    }

    @Test
    void terminatesWhenAnUnknownExceptionHasACyclicCauseChain() {
        var first = new RuntimeException("first");
        var second = new RuntimeException("second", first);
        first.initCause(second);
        assertFalse(classifier.isSessionPrimaryKeyConflict(first));
    }

    private ConstraintViolationException violation(String state, String constraint) {
        return new ConstraintViolationException("test", new SQLException("test", state), constraint);
    }
}
