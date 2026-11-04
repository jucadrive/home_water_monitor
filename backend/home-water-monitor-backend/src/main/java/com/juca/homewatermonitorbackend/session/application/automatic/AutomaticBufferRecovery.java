package com.juca.homewatermonitorbackend.session.application.automatic;

import com.juca.homewatermonitorbackend.session.application.control.*;
import com.juca.homewatermonitorbackend.session.infrastructure.persistence.*;
import java.time.Instant;
import java.util.*;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.juca.homewatermonitorbackend.session.application.control.ControlModel.*;

/** Bounded observation on the hourly worker, never inside a long DB transaction. */
public class AutomaticBufferRecovery {
    private static final Logger LOG=LoggerFactory.getLogger(AutomaticBufferRecovery.class);
    private final DeviceControlStore devices;
    private final AutomaticRecoveryStore ledger;
    private final ControlRuntime control;
    private final LongSupplier nano;
    private final Pause pause;
    @FunctionalInterface interface Pause { void sleep() throws InterruptedException; }
    public AutomaticBufferRecovery(DeviceControlStore devices,AutomaticRecoveryStore ledger,ControlRuntime control) {
        this(devices,ledger,control,System::nanoTime,()->Thread.sleep(1000));
    }
    AutomaticBufferRecovery(DeviceControlStore devices,AutomaticRecoveryStore ledger,ControlRuntime control,LongSupplier nano,Pause pause) {
        this.devices=devices;this.ledger=ledger;this.control=control;this.nano=nano;this.pause=pause;
    }
    /** Null permits START; any code skips this hourly START while preserving the device buffer. */
    public String beforeStart(String device,UUID cultivation,Instant slot) {
        ledger.reconcileBefore(slot);
        if(!"FRESH".equals(control.liveness(device)))return "RECOVERY_STALE";
        var observed=devices.observation(device);
        var current=devices.slot(device);
        if(observed==null || current==null)return "RECOVERY_STATE_MISSING";
        var s=observed.status();
        if(!s.controlReady() || !"MQTT".equals(s.controlMode()) || s.fault()!=null)return "RECOVERY_NOT_READY";
        if("IDLE".equals(s.state()) && s.sessionId()==null && current.held()==null && current.active()==null)return null;
        var active=devices.command(current.active());
        var source=s.sessionId()==null?null:devices.sourceStart(device,s.sessionId());
        if(!"MANUAL_WAIT".equals(s.state()) || !s.finalized() || s.sampleCount()!=60 || s.attempt()!=5
                || !Objects.equals(current.held(),s.sessionId()) || !s.bootId().equals(current.boot())
                || s.lastExecution()==null || !Objects.equals(s.lastExecution().sessionId(),s.sessionId())
                || active==null || active.commandState()!=State.ACCEPTED
                || !active.commandId().equals(s.lastExecution().commandId())
                || !s.bootId().equals(active.targetBootId()) || !s.sessionId().equals(active.sessionId())
                || source==null || !cultivation.equals(source.cultivationId()))return "RECOVERY_INELIGIBLE";
        UUID key=UUID.randomUUID();
        if(!ledger.prepare(device,slot,s.sessionId(),s.bootId(),key))return "RECOVERY_ALREADY_CLAIMED";
        long deadline=nano.getAsLong()+360_000_000_000L;
        try {
            var expected=new RecoveryExpectation(current.active(),s.bootId(),s.lastExecution().commandId(),s.round(),cultivation);
            var command=control.submitRecovery(key,device,s.sessionId(),expected).command();
            ledger.finish(key,"SUBMITTED",null);
            LOG.info("Automatic recovery submitted device={} session={} command={} slot={}",device,s.sessionId(),command.commandId(),slot);
            String result=observe(device,s,command.commandId(),deadline);
            ledger.finish(key,result==null?"RECOVERED":"STOPPED",result);
            LOG.info("Automatic recovery finished device={} session={} command={} result={}",device,s.sessionId(),command.commandId(),result==null?"RECOVERED":result);
            return result;
        } catch(ControlException e) {
            ledger.finish(key,"STOPPED",e.code());return e.code();
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();ledger.finish(key,"UNKNOWN","WORKER_INTERRUPTED");return "WORKER_INTERRUPTED";
        } catch(RuntimeException e) {
            // Submission may have committed: never generate a replacement key in this slot.
            ledger.finish(key,"UNKNOWN","RECOVERY_UNCERTAIN");return "RECOVERY_UNCERTAIN";
        }
    }
    private String observe(String device,Status before,UUID commandId,long deadline) throws InterruptedException {
        while(nano.getAsLong()<deadline) {
            if(Thread.currentThread().isInterrupted())throw new InterruptedException();
            if(!"FRESH".equals(control.liveness(device)))return "RECOVERY_STALE";
            var o=devices.observation(device);var slot=devices.slot(device);var c=devices.command(commandId);
            if(o==null || slot==null || c==null)return "RECOVERY_STATE_MISSING";
            var s=o.status();
            if(!before.bootId().equals(s.bootId()) || !before.bootId().equals(slot.boot()))return "RECOVERY_BOOT_CHANGED";
            if(s.fault()!=null || !s.controlReady() || !"MQTT".equals(s.controlMode()))return "RECOVERY_NOT_READY";
            var released=s.lastRelease();
            if("IDLE".equals(s.state()) && s.sessionId()==null && s.sampleCount()==0
                    && slot.held()==null && slot.active()==null && c.releaseConfirmedAt()!=null
                    && released!=null && before.sessionId().equals(released.sessionId())
                    && "NORMAL".equals(released.preservationResult()) && released.preservedSampleCount()==60
                    && devices.preserved(before.sessionId(),"NORMAL",60))return null;
            if(!before.sessionId().equals(slot.held()) || !commandId.equals(slot.active())
                    || !before.sessionId().equals(s.sessionId()))return "RECOVERY_STATE_CHANGED";
            if(Set.of(State.REJECTED,State.NOT_DISPATCHED,State.NOT_EXECUTED,State.INTERRUPTED_REBOOT).contains(c.commandState()))
                return "RECOVERY_COMMAND_FAILED";
            if(s.lastExecution()!=null && !s.lastExecution().commandId().equals(before.lastExecution().commandId())
                    && !s.lastExecution().commandId().equals(commandId))return "RECOVERY_STATE_CHANGED";
            if("MANUAL_WAIT".equals(s.state()) && s.attempt()==5 && s.lastExecution()!=null
                    && commandId.equals(s.lastExecution().commandId()))return "RECOVERY_EXHAUSTED";
            pause.sleep();
        }
        return "RECOVERY_TIMEOUT";
    }
}
