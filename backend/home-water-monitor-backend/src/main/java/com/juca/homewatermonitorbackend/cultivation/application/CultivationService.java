package com.juca.homewatermonitorbackend.cultivation.application;

import com.juca.homewatermonitorbackend.cultivation.domain.Cultivation;
import com.juca.homewatermonitorbackend.cultivation.infrastructure.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service @Transactional(timeout=5)
public class CultivationService {
    private final CultivationStore store;
    public CultivationService(CultivationStore store) { this.store=store; }
    public record Create(String plantName,String name,Instant startedAt,String memo) { }
    public record Registration(Cultivation cultivation,boolean created) { }
    public Registration create(UUID key,Create input) {
        key(key);
        Instant now=Instant.now();
        var candidate=new Cultivation(key,input.plantName(),input.name(),input.startedAt(),null,input.memo(),now,now);
        boolean inserted=store.insert(candidate);
        var actual=required(key,false).snapshot();
        if (!actual.plantName().equals(candidate.plantName()) || !actual.name().equals(candidate.name())
                || !actual.startedAt().equals(candidate.startedAt()) || !actual.memo().equals(candidate.memo()))
            throw new CultivationException(409,"IDEMPOTENCY_CONFLICT");
        return new Registration(actual,inserted);
    }
    @Transactional(readOnly=true)
    public Cultivation get(UUID id) { return required(id,false).snapshot(); }
    @Transactional(readOnly=true)
    public List<Cultivation> list(int limit,int offset) { page(limit,offset);return store.list(limit,offset); }
    public Cultivation end(UUID id) {
        var entity=required(id,true);entity.end(Instant.now());return entity.snapshot();
    }
    // Called from the command reservation/maintenance transaction, never as a separate preflight check.
    public Cultivation requireActive(UUID id) {
        var c=required(id,true).snapshot();
        if (c.endedAt()!=null) throw new CultivationException(409,"CULTIVATION_ENDED");
        return c;
    }
    private CultivationEntity required(UUID id,boolean lock) {
        var c=store.find(id,lock);
        if(c==null)throw new CultivationException(404,"CULTIVATION_NOT_FOUND");
        return c;
    }
    public static void key(UUID id) {
        if(id==null || id.version()!=4 || id.variant()!=2)throw new IllegalArgumentException("Invalid key");
    }
    public static void page(int limit,int offset) {
        if(limit<1 || limit>200 || offset<0)throw new IllegalArgumentException("Invalid page");
    }
}
