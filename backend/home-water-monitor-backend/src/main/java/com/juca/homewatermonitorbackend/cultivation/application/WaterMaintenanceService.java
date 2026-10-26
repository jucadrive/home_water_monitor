package com.juca.homewatermonitorbackend.cultivation.application;

import com.juca.homewatermonitorbackend.cultivation.domain.WaterMaintenance;
import com.juca.homewatermonitorbackend.cultivation.infrastructure.persistence.WaterMaintenanceStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service @Transactional(timeout=5)
public class WaterMaintenanceService {
    private final CultivationService cultivations;
    private final WaterMaintenanceStore store;
    public WaterMaintenanceService(CultivationService cultivations,WaterMaintenanceStore store) {
        this.cultivations=cultivations;this.store=store;
    }
    public record Create(WaterMaintenance.Type type,WaterMaintenance.Scope replacementScope,Instant occurredAt,BigDecimal amountMl,String memo) { }
    public record Registration(WaterMaintenance maintenance,boolean created) { }
    public Registration create(UUID cultivation,UUID key,Create body) {
        CultivationService.key(key);
        var candidate=new WaterMaintenance(key,cultivation,body.type(),body.replacementScope(),body.occurredAt(),body.amountMl(),body.memo(),Instant.now());
        var existing=store.find(key);
        // A successfully committed retry remains readable after the cultivation has ended.
        if(existing!=null)return match(existing,candidate,false);
        candidate.requireWithin(cultivations.requireActive(cultivation));
        boolean inserted=store.insert(candidate);
        return match(store.find(key),candidate,inserted);
    }
    private Registration match(WaterMaintenance actual,WaterMaintenance candidate,boolean created) {
        if(!actual.same(candidate))throw new CultivationException(409,"IDEMPOTENCY_CONFLICT");
        return new Registration(actual,created);
    }
    @Transactional(readOnly=true)
    public List<WaterMaintenance> list(UUID cultivation,int limit,int offset) {
        CultivationService.page(limit,offset);cultivations.get(cultivation);return store.list(cultivation,limit,offset);
    }
    @Transactional(readOnly=true)
    public Instant lastFullReplacement(UUID cultivation) { cultivations.get(cultivation);return store.lastFullReplacement(cultivation); }
}
