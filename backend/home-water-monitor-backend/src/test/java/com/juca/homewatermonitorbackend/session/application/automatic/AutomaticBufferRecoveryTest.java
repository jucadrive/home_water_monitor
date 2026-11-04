package com.juca.homewatermonitorbackend.session.application.automatic;

import com.juca.homewatermonitorbackend.session.application.control.*;
import com.juca.homewatermonitorbackend.session.infrastructure.persistence.*;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.juca.homewatermonitorbackend.session.application.control.ControlModel.*;

class AutomaticBufferRecoveryTest {
    final DeviceControlStore devices=mock(DeviceControlStore.class);
    final AutomaticRecoveryStore ledger=mock(AutomaticRecoveryStore.class);
    final ControlRuntime control=mock(ControlRuntime.class);
    final UUID plant=UUID.randomUUID(),session=UUID.randomUUID(),boot=UUID.randomUUID(),original=UUID.randomUUID(),retry=UUID.randomUUID();
    final Instant hour=Instant.parse("2026-11-04T06:00:00Z");
    final AtomicLong nano=new AtomicLong();
    Runnable onPause=()->{};
    AutomaticBufferRecovery recovery;
    Status waiting;
    @BeforeEach void setup() {
        recovery=new AutomaticBufferRecovery(devices,ledger,control,nano::get,()->{nano.addAndGet(1_000_000_000L);onPause.run();});
        waiting=status("MANUAL_WAIT",session,boot,original,1,5,null);
        when(control.liveness("sensor")).thenReturn("FRESH");
        observe(waiting,original,session);
        when(devices.command(original)).thenReturn(command(original,State.ACCEPTED,null,plant));
        when(devices.sourceStart("sensor",session)).thenReturn(command(original,State.ACCEPTED,null,plant));
        when(ledger.prepare(anyString(),any(),any(),any(),any())).thenReturn(true);
        when(control.submitRecovery(any(),eq("sensor"),eq(session),any())).thenAnswer(i->{
            observe(waiting,retry,session);return new ControlRuntime.Submission(command(retry,State.REQUESTED,null,null),true);
        });
        when(devices.command(retry)).thenReturn(command(retry,State.ACCEPTED,null,null));
    }
    Status status(String state,UUID held,UUID b,UUID execution,int round,int attempt,Release release) {
        return new Status(1,"DEVICE_STATUS","sensor",b,1,1,null,null,state,held,held==null?0:60,held!=null,round,attempt,
            "MQTT",true,null,new Result(execution,"RETRY",session,"ACCEPTED",null),
            new Execution(execution,execution.equals(original)?"START":"RETRY",session,UUID.randomUUID()),release);
    }
    void observe(Status s,UUID active,UUID held) {
        when(devices.observation("sensor")).thenReturn(new DeviceControlStore.Observation(s,hour,hour,UUID.randomUUID()));
        when(devices.slot("sensor")).thenReturn(new DeviceControlStore.Slot("sensor",s.bootId(),active,held));
    }
    ControlCommand command(UUID id,State state,Instant released,UUID cultivation) {
        return new ControlCommand(id,UUID.randomUUID(),"sensor",id.equals(original)?"START":"RETRY",session,state,boot,
            UUID.randomUUID(),null,hour,null,null,hour,released,cultivation);
    }
    void release(boolean preserved) {
        observe(status("IDLE",null,boot,retry,0,0,new Release(session,"NORMAL",60,"PRESERVATION_CONFIRMED")),null,null);
        when(devices.command(retry)).thenReturn(command(retry,State.ACCEPTED,hour,null));
        when(devices.preserved(session,"NORMAL",60)).thenReturn(preserved);
    }
    String run() {return recovery.beforeStart("sensor",plant,hour);}
    @Test void idleNeedsNoRecoveryAndPermitsStart() {
        observe(status("IDLE",null,boot,original,0,0,null),null,null);
        assertNull(run());verify(control,never()).submitRecovery(any(),any(),any(),any());
    }
    @Test void successRequiresDatabasePreservationAndReleaseBeforePermittingStart() {
        onPause=()->release(true);assertNull(run());
        var order=inOrder(ledger,control);
        order.verify(ledger).prepare(eq("sensor"),eq(hour),eq(session),eq(boot),any());
        order.verify(control).submitRecovery(any(),eq("sensor"),eq(session),any());
        verify(ledger).finish(any(),eq("RECOVERED"),isNull());
    }
    @Test void stalePreflightSendsNothing() {
        when(control.liveness("sensor")).thenReturn("STALE");assertEquals("RECOVERY_STALE",run());
        verify(ledger,never()).prepare(any(),any(),any(),any(),any());
    }
    @Test void anotherPlantOrUnknownSourceIsNeverRecovered() {
        when(devices.sourceStart("sensor",session)).thenReturn(null);assertEquals("RECOVERY_INELIGIBLE",run());
        when(devices.sourceStart("sensor",session)).thenReturn(command(original,State.ACCEPTED,null,UUID.randomUUID()));
        assertEquals("RECOVERY_INELIGIBLE",run());verify(control,never()).submitRecovery(any(),any(),any(),any());
    }
    @Test void changedManualCommandStopsBeforeSubmission() {
        observe(waiting,UUID.randomUUID(),session);assertEquals("RECOVERY_INELIGIBLE",run());
    }
    @Test void duplicateLedgerClaimNeverSubmits() {
        when(ledger.prepare(any(),any(),any(),any(),any())).thenReturn(false);
        assertEquals("RECOVERY_ALREADY_CLAIMED",run());verify(control,never()).submitRecovery(any(),any(),any(),any());
    }
    @Test void failedIntentCommitPreventsSubmission() {
        when(ledger.prepare(any(),any(),any(),any(),any())).thenThrow(new IllegalStateException());
        assertThrows(IllegalStateException.class,this::run);verify(control,never()).submitRecovery(any(),any(),any(),any());
    }
    @Test void uncertainSubmitIsRecordedWithoutAnotherRequest() {
        when(control.submitRecovery(any(),any(),any(),any())).thenThrow(new IllegalStateException("private"));
        assertEquals("RECOVERY_UNCERTAIN",run());verify(control,times(1)).submitRecovery(any(),any(),any(),any());
        verify(ledger).finish(any(),eq("UNKNOWN"),eq("RECOVERY_UNCERTAIN"));
    }
    @Test void manualRaceAtReservationIsRecordedAsStopped() {
        when(control.submitRecovery(any(),any(),any(),any())).thenThrow(new ControlException(409,"RECOVERY_STATE_CHANGED"));
        assertEquals("RECOVERY_STATE_CHANGED",run());verify(ledger).finish(any(),eq("STOPPED"),eq("RECOVERY_STATE_CHANGED"));
    }
    @Test void timeoutIsBoundedAndNeverClearsBuffer() {
        assertEquals("RECOVERY_TIMEOUT",run());assertEquals(360_000_000_000L,nano.get());
        verify(control,times(1)).submitRecovery(any(),any(),any(),any());
        verify(devices,never()).slot(anyString(),any(),any(),any());
    }
    @Test void rebootAndStaleDuringRecoveryStop() {
        onPause=()->observe(status("MANUAL_WAIT",session,UUID.randomUUID(),retry,2,1,null),retry,session);
        assertEquals("RECOVERY_BOOT_CHANGED",run());
    }
    @Test void lossOfFreshnessStopsWithoutNewMeasurement() {
        onPause=()->when(control.liveness("sensor")).thenReturn("STALE");assertEquals("RECOVERY_STALE",run());
    }
    @Test void exhaustedSecondRoundStops() {
        onPause=()->observe(status("MANUAL_WAIT",session,boot,retry,2,5,null),retry,session);
        assertEquals("RECOVERY_EXHAUSTED",run());
    }
    @Test void mqttReceiptWithoutDatabasePreservationDoesNotPermitStart() {
        onPause=()->release(false);assertEquals("RECOVERY_STATE_CHANGED",run());
    }
    @Test void shutdownInterruptPreservesUncertainty() {
        onPause=()->Thread.currentThread().interrupt();
        try { assertEquals("WORKER_INTERRUPTED",run());assertTrue(Thread.currentThread().isInterrupted()); }
        finally {Thread.interrupted();}
    }
}
