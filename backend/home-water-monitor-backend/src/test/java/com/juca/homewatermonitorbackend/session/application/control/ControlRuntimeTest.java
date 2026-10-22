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

class ControlRuntimeTest {
    @Test void cancellingBackgroundProbeCannotPublishAfterNewCommandProbe()throws Exception {
        var tx=mock(CommandTransactionService.class);var store=mock(DeviceControlStore.class);var pub=mock(ControlMqttPublisher.class);
        var runtime=new ControlRuntime(tx,store,pub,new SessionMqttProperties(true,"tcp://localhost:1883","rx","tx",Set.of("a"),"",""));
        var codec=new ControlMessageCodec();var firstPublishing=new CountDownLatch(1);var release=new CountDownLatch(1);
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        when(tx.observe(any(),anyBoolean(),any(),any())).thenReturn(true);
        doAnswer(inv->{
            int number=calls.incrementAndGet();
            if(number==1){firstPublishing.countDown();assertTrue(release.await(2,TimeUnit.SECONDS));}
            var request=codec.tree(inv.getArgument(1));
            var json=(tools.jackson.databind.node.ObjectNode)codec.tree(java.nio.file.Files.readAllBytes(
                    java.nio.file.Path.of("../../docs/mqtt/v1/examples/device-status-idle.json")));
            json.put("deviceId","a");json.put("statusSeq",number);json.put("replyToProbeId",request.get("probeId").asString());
            runtime.status(codec.decodeStatus(codec.encode(json),"a"),System.nanoTime(),Instant.now(),()->{});
            return null;
        }).when(pub).publish(eq("a"),any(),anyLong());
        try(var callers=Executors.newFixedThreadPool(2)) {
            var first=callers.submit(()->ReflectionTestUtils.invokeMethod(runtime,"probe","a",3000L,false));
            assertTrue(firstPublishing.await(1,TimeUnit.SECONDS));
            ((ControlProbeExchange)ReflectionTestUtils.getField(runtime,"exchange")).cancel("a");
            var second=callers.submit(()->ReflectionTestUtils.invokeMethod(runtime,"probe","a",3000L,true));
            Thread.sleep(75);assertEquals(1,calls.get());release.countDown();
            assertThrows(ExecutionException.class,()->first.get(2,TimeUnit.SECONDS));
            assertNotNull(second.get(2,TimeUnit.SECONDS));assertEquals(2,calls.get());
        } finally{release.countDown();runtime.stop();}
    }
    @Test void admissionIsBoundedAndConcurrentSameKeyWaitsForOriginalReservation()throws Exception {
        var tx=mock(CommandTransactionService.class);var store=mock(DeviceControlStore.class);var pub=mock(ControlMqttPublisher.class);
        var settings=new SessionMqttProperties(true,"tcp://localhost:1883","rx","tx",Set.of("a","b","c"),"","");
        var runtime=new ControlRuntime(tx,store,pub,settings);ReflectionTestUtils.setField(runtime,"running",true);
        var entered=new CountDownLatch(2);var release=new CountDownLatch(1);
        when(tx.reserve(any(),anyString(),anyString(),isNull())).thenAnswer(inv->{
            entered.countDown();assertTrue(release.await(3,TimeUnit.SECONDS));
            return new ControlCommand(UUID.randomUUID(),inv.getArgument(0),inv.getArgument(1),"START",UUID.randomUUID(),
                    ControlModel.State.REQUESTED,null,null,null,Instant.now(),null,null,null,null);
        });
        // Worker execution is irrelevant to this admission test; it immediately closes.
        when(tx.probing(any())).thenAnswer(inv->new ControlCommand(inv.getArgument(0),UUID.randomUUID(),"a","START",UUID.randomUUID(),
                ControlModel.State.NOT_DISPATCHED,null,null,null,Instant.now(),null,null,null,null));
        UUID key=UUID.randomUUID();
        try(var callers=Executors.newFixedThreadPool(3)) {
            var a=callers.submit(()->runtime.submit(key,"a","START",null));
            var b=callers.submit(()->runtime.submit(UUID.randomUUID(),"b","START",null));
            assertTrue(entered.await(2,TimeUnit.SECONDS));
            var capacity=assertThrows(ControlException.class,()->runtime.submit(UUID.randomUUID(),"c","START",null));
            assertEquals(503,capacity.status());
            var duplicate=callers.submit(()->runtime.submit(key,"a","START",null));
            Thread.sleep(50);release.countDown();
            var first=a.get(3,TimeUnit.SECONDS);assertTrue(first.created());assertTrue(b.get(3,TimeUnit.SECONDS).created());
            var same=duplicate.get(3,TimeUnit.SECONDS);assertFalse(same.created());assertEquals(first.command().commandId(),same.command().commandId());
            verify(tx,times(2)).reserve(any(),anyString(),anyString(),isNull());
        } finally{release.countDown();runtime.stop();}
    }
    @Test void oneProbeResponseCanBeConsumedAndOnlyFreshProbeExtendsLiveness()throws Exception {
        var codec=new ControlMessageCodec();var json=(tools.jackson.databind.node.ObjectNode)codec.tree(
                java.nio.file.Files.readAllBytes(java.nio.file.Path.of("../../docs/mqtt/v1/examples/device-status-idle.json")));
        var exchange=new ControlProbeExchange();assertEquals("UNKNOWN",exchange.liveness("esp32-bench"));
        var p=exchange.begin("esp32-bench",5000);json.put("replyToProbeId",p.id().toString());
        var s=codec.decodeStatus(codec.encode(json),"esp32-bench");
        assertNull(exchange.claim(s,p.deadlineNano()));
        assertSame(p,exchange.claim(s,System.nanoTime()));assertNull(exchange.claim(s,System.nanoTime()));
        exchange.complete(s,p);assertEquals("FRESH",exchange.liveness("esp32-bench"));
        assertEquals(s,p.answer().get(1,TimeUnit.SECONDS));
        var expired=exchange.begin("esp32-bench",0);json.put("replyToProbeId",expired.id().toString());
        assertNull(exchange.claim(codec.decodeStatus(codec.encode(json),"esp32-bench"),System.nanoTime()));
    }
}
