package com.juca.homewatermonitorbackend.cultivation.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CultivationTest {
    private final Instant now=Instant.parse("2026-10-26T00:00:00Z");
    private Cultivation create(String plant,String name,Instant start,Instant end) {
        return new Cultivation(UUID.randomUUID(),plant,name,start,end,null,now,now);
    }
    @Test void normalizesNamesAndDerivesLifecycle() {
        var c=create(" 바질 "," 첫 용기 ",now.minusSeconds(1),null);
        assertEquals("바질",c.plantName());assertEquals("첫 용기",c.name());assertEquals("",c.memo());
        assertEquals("ACTIVE",c.status());assertDoesNotThrow(c::requireActive);
        var ended=create("상추","용기",now.minusSeconds(1),now);
        assertEquals("ENDED",ended.status());assertThrows(IllegalStateException.class,ended::requireActive);
    }
    @Test void rejectsInvalidNamesAndTimes() {
        assertThrows(IllegalArgumentException.class,()->create(" ","name",now,null));
        assertThrows(IllegalArgumentException.class,()->create("x".repeat(101),"name",now,null));
        assertThrows(IllegalArgumentException.class,()->create("basil","",now,null));
        assertThrows(IllegalArgumentException.class,()->create("basil","name",now.plusSeconds(1),null));
        assertThrows(IllegalArgumentException.class,()->create("basil","name",now,now.minusSeconds(1)));
        assertThrows(NullPointerException.class,()->create("basil","name",null,null));
    }
}
