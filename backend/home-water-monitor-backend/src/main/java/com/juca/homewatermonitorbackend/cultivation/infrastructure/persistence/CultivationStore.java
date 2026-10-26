package com.juca.homewatermonitorbackend.cultivation.infrastructure.persistence;

import com.juca.homewatermonitorbackend.cultivation.domain.Cultivation;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Repository
@Transactional(propagation=Propagation.MANDATORY)
public class CultivationStore {
    @PersistenceContext private EntityManager em;
    // The client registration key is the resource UUID. A concurrent retry waits for the winner's commit.
    public boolean insert(Cultivation c) {
        return em.createNativeQuery("""
                insert into cultivation(cultivation_id,plant_name,name,started_at,memo,created_at,updated_at)
                values (:id,:plant,:name,:start,:memo,:created,:updated)
                on conflict (cultivation_id) do nothing
                """).setParameter("id",c.id()).setParameter("plant",c.plantName()).setParameter("name",c.name())
                .setParameter("start",c.startedAt()).setParameter("memo",c.memo())
                .setParameter("created",c.createdAt()).setParameter("updated",c.updatedAt()).executeUpdate() == 1;
    }
    public CultivationEntity find(UUID id, boolean lock) {
        return lock ? em.find(CultivationEntity.class,id,LockModeType.PESSIMISTIC_WRITE)
                : em.find(CultivationEntity.class,id);
    }
    public List<Cultivation> list(int limit,int offset) {
        return em.createQuery("from CultivationEntity order by createdAt desc, id",CultivationEntity.class)
                .setFirstResult(offset).setMaxResults(limit).getResultList().stream().map(CultivationEntity::snapshot).toList();
    }
}
