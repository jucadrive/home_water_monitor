package com.juca.homewatermonitorbackend.session.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class TdsSessionMissingSampleAdapter {
    @PersistenceContext
    private EntityManager entityManager;

    public void insert(UUID sessionId, List<Integer> sequences) {
        // 원본 0개일 때도 FK 대상 세션을 먼저 쓴다. 실제 커밋은 바깥 서비스 경계다.
        entityManager.flush();
        sequences.forEach(seq -> entityManager.persist(new TdsSessionMissingSampleEntity(sessionId, seq)));
    }

    public List<Integer> findSequences(UUID sessionId) {
        return entityManager.createQuery("""
                select m.id.seq from TdsSessionMissingSampleEntity m
                where m.id.sessionId = :sessionId order by m.id.seq
                """, Integer.class).setParameter("sessionId", sessionId).getResultList();
    }
}
