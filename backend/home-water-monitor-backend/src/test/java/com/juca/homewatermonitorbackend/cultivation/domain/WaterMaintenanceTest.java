package com.juca.homewatermonitorbackend.cultivation.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class WaterMaintenanceTest {
    final UUID id=UUID.randomUUID();final Instant now=Instant.parse("2026-10-26T00:00:00Z");
    WaterMaintenance create(WaterMaintenance.Type type,WaterMaintenance.Scope scope,String amount) {
        return new WaterMaintenance(UUID.randomUUID(),id,type,scope,now,amount==null?null:new BigDecimal(amount),null,now);
    }
    @Test void validatesReplacementScopeAndLiquidVolumeWithoutGuessingUnknown() {
        assertNull(create(WaterMaintenance.Type.REPLACE,WaterMaintenance.Scope.FULL,null).amountMl());
        assertEquals(new BigDecimal("1.20"),create(WaterMaintenance.Type.TOP_UP,null,"1.200").amountMl());
        assertDoesNotThrow(()->create(WaterMaintenance.Type.NUTRIENT_ADD,null,"0.01"));
        assertThrows(IllegalArgumentException.class,()->create(WaterMaintenance.Type.REPLACE,null,"1"));
        assertThrows(IllegalArgumentException.class,()->create(WaterMaintenance.Type.TOP_UP,WaterMaintenance.Scope.FULL,"1"));
        for(String value:new String[]{"0","-1","0.001","10000000000"})
            assertThrows(IllegalArgumentException.class,()->create(WaterMaintenance.Type.TOP_UP,null,value));
    }
    @Test void rejectsFutureAndOutsideCultivationPeriod() {
        assertThrows(IllegalArgumentException.class,()->new WaterMaintenance(UUID.randomUUID(),id,WaterMaintenance.Type.TOP_UP,null,now.plusSeconds(1),null,"",now));
        var m=create(WaterMaintenance.Type.TOP_UP,null,null);
        var c=new Cultivation(id,"a","b",now,null,"",now,now);
        assertDoesNotThrow(()->m.requireWithin(c));
        var ended=new Cultivation(id,"a","b",now,now,"",now,now);
        assertThrows(IllegalStateException.class,()->m.requireWithin(ended));
        var wrong=new Cultivation(UUID.randomUUID(),"a","b",now,null,"",now,now);
        assertThrows(IllegalArgumentException.class,()->m.requireWithin(wrong));
    }
}
