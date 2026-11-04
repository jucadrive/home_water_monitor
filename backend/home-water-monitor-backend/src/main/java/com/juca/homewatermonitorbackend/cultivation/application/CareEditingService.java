package com.juca.homewatermonitorbackend.cultivation.application;

import com.juca.homewatermonitorbackend.cultivation.domain.*;
import com.juca.homewatermonitorbackend.cultivation.infrastructure.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service @Transactional(timeout=5)
public class CareEditingService {
    private final CultivationStore cultivations;
    private final WaterMaintenanceStore maintenance;
    private final CareEditStore receipts;
    private final JsonMapper json=JsonMapper.builder().build();
    public CareEditingService(CultivationStore cultivations,WaterMaintenanceStore maintenance,CareEditStore receipts) {
        this.cultivations=cultivations;this.maintenance=maintenance;this.receipts=receipts;
    }
    public record Profile(long expectedVersion,String plantName,String name,String memo) {
        public Profile {
            if(expectedVersion<0)throw new IllegalArgumentException("version");
            plantName=Cultivation.text(Objects.requireNonNull(plantName),100,true);
            name=Cultivation.text(Objects.requireNonNull(name),100,true);
            memo=Cultivation.text(Objects.requireNonNull(memo),2000,false);
        }
    }
    public record Correction(long expectedVersion,WaterMaintenance.Type type,WaterMaintenance.Scope replacementScope,
                             Instant occurredAt,BigDecimal amountMl,String memo) {
        public Correction {
            if(expectedVersion<0)throw new IllegalArgumentException("version");
            Objects.requireNonNull(type);occurredAt=Objects.requireNonNull(occurredAt).truncatedTo(ChronoUnit.MICROS);
            memo=Cultivation.text(Objects.requireNonNull(memo),2000,false);
            if(amountMl!=null) {
                if(amountMl.signum()<=0 || amountMl.stripTrailingZeros().scale()>2 || amountMl.compareTo(new BigDecimal("9999999999.99"))>0)
                    throw new IllegalArgumentException("amount");
                amountMl=amountMl.setScale(2);
            }
            if((type==WaterMaintenance.Type.REPLACE)!=(replacementScope!=null))throw new IllegalArgumentException("scope");
        }
    }
    public String profile(UUID id,UUID key,Profile input) {
        String path="PUT /api/cultivations/"+id+"/profile", body=json.writeValueAsString(input);
        String replay=begin(key,path,body);if(replay!=null)return replay;
        var entity=required(id);var before=entity.snapshot();version(before.version(),input.expectedVersion());
        boolean changed=!before.plantName().equals(input.plantName()) || !before.name().equals(input.name()) || !before.memo().equals(input.memo());
        Instant now=Instant.now().truncatedTo(ChronoUnit.MICROS);
        if(changed)entity.profile(input.plantName(),input.name(),input.memo(),now);
        return finish(key,path,body,before,entity.snapshot(),changed,now);
    }
    public String correct(UUID cultivation,UUID id,UUID key,Correction input) {
        String path="PUT /api/cultivations/"+cultivation+"/water-maintenances/"+id,body=json.writeValueAsString(input);
        String replay=begin(key,path,body);if(replay!=null)return replay;
        var c=required(cultivation).snapshot(); // Shared order with registration/end: parent before child.
        var entity=maintenance.lock(id);
        if(entity==null || !entity.snapshot().cultivationId().equals(cultivation))throw missing();
        var before=entity.snapshot();version(before.version(),input.expectedVersion());
        Instant now=Instant.now().truncatedTo(ChronoUnit.MICROS);
        var after=new WaterMaintenance(id,cultivation,input.type(),input.replacementScope(),input.occurredAt(),input.amountMl(),input.memo(),
                before.createdAt(),now,before.version()+1);
        after.requireCorrectionWithin(c);
        boolean changed=!before.same(after);
        if(changed)entity.correct(after);
        return finish(key,path,body,before,entity.snapshot(),changed,now);
    }
    @Transactional(readOnly=true)
    public WaterMaintenance get(UUID cultivation,UUID id) {
        if(cultivations.find(cultivation,false)==null)throw new CultivationException(404,"CULTIVATION_NOT_FOUND");
        var value=maintenance.find(id);if(value==null || !value.cultivationId().equals(cultivation))throw missing();return value;
    }
    private String begin(UUID key,String path,String body) {
        CultivationService.key(key);receipts.lockKey(key);return receipts.replay(key,path,body);
    }
    private String finish(UUID key,String path,String body,Object before,Object after,boolean changed,Instant now) {
        String response=json.writeValueAsString(after);
        receipts.save(key,path,body,response,changed?json.writeValueAsString(before):null,changed?response:null,now);
        return response;
    }
    private CultivationEntity required(UUID id) {
        var entity=cultivations.find(id,true);if(entity==null)throw new CultivationException(404,"CULTIVATION_NOT_FOUND");return entity;
    }
    private static void version(long actual,long expected) {
        if(actual!=expected)throw new CultivationException(409,"VERSION_CONFLICT");
    }
    private static CultivationException missing() { return new CultivationException(404,"WATER_MAINTENANCE_NOT_FOUND"); }
}
