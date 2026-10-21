package com.juca.homewatermonitorbackend.session.infrastructure.persistence;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

// DB/ORM 예외의 구조화된 정보만 사용하며 메시지 문자열이나 다른 제약으로 복구를 확장하지 않는다.
@Component
public class TdsSessionInsertConflictClassifier {
    public boolean isSessionPrimaryKeyConflict(Throwable error) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = error; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && "23505".equals(violation.getSQLState())
                    && "tds_session_pkey".equals(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
