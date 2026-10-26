package com.juca.homewatermonitorbackend.cultivation.infrastructure.persistence;

import com.juca.homewatermonitorbackend.cultivation.domain.WaterMaintenance;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="water_maintenance")
public class WaterMaintenanceEntity {
    @Id @Column(name="maintenance_id") private UUID id;
    @Column(name="cultivation_id",nullable=false) private UUID cultivationId;
    @Column(nullable=false,length=16) private String type;
    @Column(name="replacement_scope",length=8) private String replacementScope;
    @Column(name="occurred_at",nullable=false) private Instant occurredAt;
    @Column(name="amount_ml",precision=12,scale=2) private BigDecimal amountMl;
    @Column(nullable=false,length=2000) private String memo;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    protected WaterMaintenanceEntity() { }
    public WaterMaintenance snapshot() {
        return new WaterMaintenance(id,cultivationId,WaterMaintenance.Type.valueOf(type),
                replacementScope==null?null:WaterMaintenance.Scope.valueOf(replacementScope),occurredAt,amountMl,memo,createdAt);
    }
}
