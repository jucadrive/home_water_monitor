package com.juca.homewatermonitorbackend.session.infrastructure.persistence;

import com.juca.homewatermonitorbackend.measurement.infrastructure.persistence.TdsMeasurementRedeliveryEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 헤더와 상세를 바깥 서비스 트랜잭션에 참여시켜 원자적으로 신규 등록한다.
@Repository
public class TdsRedeliveryPersistenceAdapter {
    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(TdsSessionRedeliveryEntity header, List<TdsMeasurementRedeliveryEntity> details) {
        entityManager.persist(header);
        // FK 대상 헤더를 먼저 DB에 쓰되 실제 커밋은 서비스 트랜잭션에 맡긴다.
        entityManager.flush();
        details.forEach(entityManager::persist);
    }
}
