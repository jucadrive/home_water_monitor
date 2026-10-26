package com.juca.homewatermonitorbackend.cultivation.infrastructure.persistence;

import com.juca.homewatermonitorbackend.cultivation.domain.WaterMaintenance;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository @Transactional(propagation=Propagation.MANDATORY)
public class WaterMaintenanceStore {
    @PersistenceContext private EntityManager em;
    public WaterMaintenance find(UUID id) {
        var e=em.find(WaterMaintenanceEntity.class,id);return e==null?null:e.snapshot();
    }
    public boolean insert(WaterMaintenance m) {
        return em.createNativeQuery("""
                insert into water_maintenance(maintenance_id,cultivation_id,type,replacement_scope,occurred_at,amount_ml,memo,created_at)
                values (:id,:cultivation,:type,:scope,:at,:amount,:memo,:created)
                on conflict (maintenance_id) do nothing
                """).setParameter("id",m.id()).setParameter("cultivation",m.cultivationId()).setParameter("type",m.type().name())
                .setParameter("scope",m.replacementScope()==null?null:m.replacementScope().name())
                .setParameter("at",m.occurredAt()).setParameter("amount",m.amountMl()).setParameter("memo",m.memo())
                .setParameter("created",m.createdAt()).executeUpdate()==1;
    }
    public List<WaterMaintenance> list(UUID cultivation,int limit,int offset) {
        return em.createQuery("from WaterMaintenanceEntity where cultivationId=:id order by occurredAt desc,id",WaterMaintenanceEntity.class)
                .setParameter("id",cultivation).setFirstResult(offset).setMaxResults(limit).getResultList().stream()
                .map(WaterMaintenanceEntity::snapshot).toList();
    }
    public Instant lastFullReplacement(UUID cultivation) {
        return em.createQuery("select max(occurredAt) from WaterMaintenanceEntity where cultivationId=:id and type='REPLACE' and replacementScope='FULL'",Instant.class)
                .setParameter("id",cultivation).getSingleResult();
    }
}
