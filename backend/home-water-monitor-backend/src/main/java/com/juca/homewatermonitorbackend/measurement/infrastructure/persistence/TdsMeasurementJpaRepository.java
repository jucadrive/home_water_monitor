package com.juca.homewatermonitorbackend.measurement.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TdsMeasurementJpaRepository extends JpaRepository<TdsMeasurementEntity, Long> {

    List<TdsMeasurementEntity> findBySessionIdOrderBySeqAsc(UUID sessionId);

    long countBySessionId(UUID sessionId);
}
