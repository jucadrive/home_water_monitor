package com.juca.homewatermonitorbackend.session.application.control;

import com.juca.homewatermonitorbackend.session.infrastructure.persistence.DeviceControlStore;
import com.juca.homewatermonitorbackend.session.infrastructure.mqtt.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CultivationControlRuntimeTest {
    ControlCommand command(UUID key,UUID c,ControlModel.State state) {
        return new ControlCommand(UUID.randomUUID(),key,"a","START",UUID.randomUUID(),state,null,null,null,Instant.now(),null,null,null,null,c);
    }
    ControlRuntime runtime(CommandTransactionService tx,DeviceControlStore store) {
        var r=new ControlRuntime(tx,store,mock(ControlMqttPublisher.class),new SessionMqttProperties(true,"tcp://localhost:1883","rx","tx",Set.of("a"),"",""));
        ReflectionTestUtils.setField(r,"running",true);return r;
    }
    @Test void pendingAndStoredIdempotencyBothCompareCultivation() throws Exception {
        var tx=mock(CommandTransactionService.class);var store=mock(DeviceControlStore.class);var runtime=runtime(tx,store);
        UUID key=UUID.randomUUID(),cultivation=UUID.randomUUID();var accepted=command(key,cultivation,ControlModel.State.REQUESTED);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(tx.reserve(eq(key),eq("a"),eq("START"),isNull(),eq(cultivation))).thenAnswer(inv->{entered.countDown();assertTrue(release.await(2,TimeUnit.SECONDS));return accepted;});
        when(tx.probing(any())).thenReturn(command(key,cultivation,ControlModel.State.NOT_DISPATCHED));
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->runtime.submit(key,"a","START",null,cultivation));assertTrue(entered.await(2,TimeUnit.SECONDS));
            var different=pool.submit(()->runtime.submit(key,"a","START",null,UUID.randomUUID()));
            release.countDown();assertTrue(first.get(5,TimeUnit.SECONDS).created());
            assertInstanceOf(ControlException.class,assertThrows(ExecutionException.class,()->different.get(5,TimeUnit.SECONDS)).getCause());
            when(store.byKey(key)).thenReturn(accepted);
            assertFalse(runtime.submit(key,"a","START",null,cultivation).created());
            assertThrows(ControlException.class,()->runtime.submit(key,"a","START",null));
            assertThrows(ControlException.class,()->runtime.submit(key,"a","START",null,UUID.randomUUID()));
        } finally {release.countDown();runtime.stop();}
    }
    @Test void databaseConflictRecoveryChecksWinningCultivation() {
        for(boolean same:List.of(true,false)) {
            var tx=mock(CommandTransactionService.class);var store=mock(DeviceControlStore.class);var runtime=runtime(tx,store);
            UUID key=UUID.randomUUID(),c=UUID.randomUUID();var winner=command(key,same?c:UUID.randomUUID(),ControlModel.State.REQUESTED);
            var conflict=new RuntimeException("test unique conflict");
            when(store.byKey(key)).thenReturn(null,winner);when(store.keyConflict(conflict)).thenReturn(true);
            when(tx.reserve(eq(key),eq("a"),eq("START"),isNull(),eq(c))).thenThrow(conflict);
            try {
                if(same)assertFalse(runtime.submit(key,"a","START",null,c).created());
                else assertThrows(ControlException.class,()->runtime.submit(key,"a","START",null,c));
            } finally {runtime.stop();}
        }
    }
}
