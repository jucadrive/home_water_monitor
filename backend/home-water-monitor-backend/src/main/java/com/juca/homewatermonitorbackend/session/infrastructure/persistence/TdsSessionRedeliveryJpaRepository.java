package com.juca.homewatermonitorbackend.session.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

// 누적 건수는 별도 카운터가 아닌 커밋된 진단 헤더에서 계산한다.
public interface TdsSessionRedeliveryJpaRepository extends JpaRepository<TdsSessionRedeliveryEntity, UUID> {
    long countBySessionId(UUID sessionId);

    @Query("""
            select count(r) as receptionCount, coalesce(sum(r.identicalCount), 0) as identicalCount,
                   coalesce(sum(r.conflictCount), 0) as conflictCount
            from TdsSessionRedeliveryEntity r where r.sessionId = :sessionId
            """)
    Totals totalsBySessionId(@Param("sessionId") UUID sessionId);

    interface Totals {
        long getReceptionCount();
        long getIdenticalCount();
        long getConflictCount();
    }
}
