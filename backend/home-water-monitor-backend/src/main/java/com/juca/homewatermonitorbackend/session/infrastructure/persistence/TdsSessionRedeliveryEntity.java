package com.juca.homewatermonitorbackend.session.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

// 성공적으로 커밋할 재수신 목록 한 건의 판정 집계다.
@Entity
@Table(name = "tds_session_redelivery")
@Getter
public class TdsSessionRedeliveryEntity {
    @Id
    private UUID redeliveryId;
    @Column(nullable = false)
    private UUID sessionId;
    @Column(nullable = false)
    private Instant receivedAt;
    @Column(nullable = false)
    private int sampleCount;
    @Column(nullable = false)
    private int identicalCount;
    @Column(nullable = false)
    private int conflictCount;

    protected TdsSessionRedeliveryEntity() {
    }

    public TdsSessionRedeliveryEntity(UUID id, UUID sessionId, Instant receivedAt, int identicalCount, int conflictCount) {
        this.redeliveryId = id;
        this.sessionId = sessionId;
        this.receivedAt = receivedAt;
        this.sampleCount = identicalCount + conflictCount;
        this.identicalCount = identicalCount;
        this.conflictCount = conflictCount;
    }
}
