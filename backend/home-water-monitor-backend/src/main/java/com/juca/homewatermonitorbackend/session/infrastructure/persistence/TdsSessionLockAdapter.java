package com.juca.homewatermonitorbackend.session.infrastructure.persistence;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.Optional;

// PostgreSQL 고유 timeout과 시각 저장 표현을 영속성 계층 안에 둔다.
@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class TdsSessionLockAdapter {
    private final JdbcTemplate jdbc;
    private final TdsSessionJpaRepository sessions;
    private final int lockTimeoutMs;

    public TdsSessionLockAdapter(JdbcTemplate jdbc, TdsSessionJpaRepository sessions,
                                @Value("${water-monitor.redelivery.lock-timeout-ms:5000}") int lockTimeoutMs) {
        if (lockTimeoutMs <= 0) {
            throw new IllegalArgumentException("잠금 대기 제한은 양수여야 합니다.");
        }
        this.jdbc = jdbc;
        this.sessions = sessions;
        this.lockTimeoutMs = lockTimeoutMs;
    }

    public Optional<TdsSessionEntity> lock(UUID sessionId) {
        // true는 이 트랜잭션에만 적용한다. 연결 풀에 설정이 남지 않아야 한다.
        jdbc.queryForObject("select set_config('lock_timeout', ?, true)", String.class, lockTimeoutMs + "ms");
        return sessions.findBySessionIdForUpdate(sessionId);
    }

    public boolean matchesStartedAt(UUID sessionId, Instant incoming) {
        // JDBC의 timestamptz 바인딩으로 최초 저장과 동일한 마이크로초 표현을 비교한다.
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select started_at is not distinct from cast(? as timestamptz)
                from tds_session where session_id = ?
                """, Boolean.class, incoming == null ? null : incoming.atOffset(ZoneOffset.UTC), sessionId));
    }
}
