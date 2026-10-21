package com.juca.homewatermonitorbackend.session.infrastructure.persistence;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

// 누락 사실만 저장하며 원본 테이블에 값을 만들어 채우지 않는다.
@Entity
@Table(name = "tds_session_missing_sample")
public class TdsSessionMissingSampleEntity {
    @EmbeddedId
    private TdsSessionMissingSampleId id;

    protected TdsSessionMissingSampleEntity() { }

    public TdsSessionMissingSampleEntity(UUID sessionId, int seq) {
        this.id = new TdsSessionMissingSampleId(sessionId, seq);
    }
}
