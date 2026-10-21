package com.juca.homewatermonitorbackend.measurement.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

// 재수신 한 건에 대응하는 진단 상세를 순번으로 조회한다.
public interface TdsMeasurementRedeliveryJpaRepository
        extends JpaRepository<TdsMeasurementRedeliveryEntity, TdsMeasurementRedeliveryId> {
    List<TdsMeasurementRedeliveryEntity> findByIdRedeliveryIdOrderByIdSeqAsc(UUID redeliveryId);
}
