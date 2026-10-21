package com.juca.homewatermonitorbackend.session.infrastructure.persistence;

import com.juca.homewatermonitorbackend.session.infrastructure.persistence.TdsSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface TdsSessionJpaRepository extends JpaRepository<TdsSessionEntity, UUID> {
    // 같은 기존 세션을 처리하는 재수신 호출은 이 잠금 조회를 먼저 통과한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TdsSessionEntity s where s.sessionId = :sessionId")
    Optional<TdsSessionEntity> findBySessionIdForUpdate(@Param("sessionId") UUID sessionId);
}
