package com.juca.homewatermonitorbackend.cultivation.infrastructure.persistence;

import com.juca.homewatermonitorbackend.cultivation.domain.Cultivation;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "cultivation")
public class CultivationEntity {
    @Id @Column(name="cultivation_id") private UUID id;
    @Column(name="plant_name", nullable=false, length=100) private String plantName;
    @Column(nullable=false, length=100) private String name;
    @Column(name="started_at", nullable=false) private Instant startedAt;
    @Column(name="ended_at") private Instant endedAt;
    @Column(nullable=false, length=2000) private String memo;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;
    @Column(nullable=false) private long version;
    protected CultivationEntity() { }
    public Cultivation snapshot() {
        return new Cultivation(id,plantName,name,startedAt,endedAt,memo,createdAt,updatedAt,version);
    }
    public void profile(String plant,String label,String note,Instant now) {
        plantName=plant;name=label;memo=note;updatedAt=now;version++;
    }
    public void end(Instant now) {
        // PostgreSQL stores microseconds; the first response and a later replay must agree exactly.
        if (endedAt == null) { endedAt=now.truncatedTo(java.time.temporal.ChronoUnit.MICROS); updatedAt=endedAt;version++; }
    }
}
