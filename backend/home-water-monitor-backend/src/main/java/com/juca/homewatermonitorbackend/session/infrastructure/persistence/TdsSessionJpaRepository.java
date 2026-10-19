package com.juca.homewatermonitorbackend.session.infrastructure.persistence;

import com.juca.homewatermonitorbackend.session.infrastructure.persistence.TdsSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TdsSessionJpaRepository extends JpaRepository<TdsSessionEntity, UUID> {
}
