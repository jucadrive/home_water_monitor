package com.juca.homewatermonitorbackend.cultivation.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

public record WaterMaintenance(UUID id,UUID cultivationId,Type type,Scope replacementScope,
                               Instant occurredAt,BigDecimal amountMl,String memo,Instant createdAt) {
    public enum Type { REPLACE, TOP_UP, NUTRIENT_ADD }
    public enum Scope { FULL, PARTIAL }
    public WaterMaintenance {
        Objects.requireNonNull(id);Objects.requireNonNull(cultivationId);Objects.requireNonNull(type);
        Objects.requireNonNull(createdAt);
        occurredAt=Objects.requireNonNull(occurredAt).truncatedTo(ChronoUnit.MICROS);
        memo=Cultivation.text(memo,2000,false);
        if ((type==Type.REPLACE)!=(replacementScope!=null) || occurredAt.isAfter(createdAt))
            throw new IllegalArgumentException("Invalid maintenance");
        if(amountMl!=null) {
            if(amountMl.signum()<=0 || amountMl.stripTrailingZeros().scale()>2
                    || amountMl.compareTo(new BigDecimal("9999999999.99"))>0)
                throw new IllegalArgumentException("Invalid liquid volume");
            amountMl=amountMl.setScale(2);
        }
    }
    public void requireWithin(Cultivation c) {
        c.requireActive();
        if(!cultivationId.equals(c.id()) || occurredAt.isBefore(c.startedAt()))
            throw new IllegalArgumentException("Outside cultivation period");
    }
    public boolean same(WaterMaintenance other) {
        return cultivationId.equals(other.cultivationId) && type==other.type && replacementScope==other.replacementScope
                && occurredAt.equals(other.occurredAt) && Objects.equals(amountMl,other.amountMl) && memo.equals(other.memo);
    }
}
