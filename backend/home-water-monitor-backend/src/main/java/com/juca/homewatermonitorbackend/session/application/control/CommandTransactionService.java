package com.juca.homewatermonitorbackend.session.application.control;

import com.juca.homewatermonitorbackend.session.infrastructure.persistence.DeviceControlStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;
import static com.juca.homewatermonitorbackend.session.application.control.ControlModel.*;

@Service
@Transactional(isolation=Isolation.READ_COMMITTED,timeout=3)
public class CommandTransactionService {
    private final DeviceControlStore store;
    private final com.juca.homewatermonitorbackend.cultivation.application.CultivationService cultivations;
    public CommandTransactionService(DeviceControlStore store,com.juca.homewatermonitorbackend.cultivation.application.CultivationService cultivations) { this.store=store;this.cultivations=cultivations; }
    public ControlCommand reserve(UUID key,String device,String type,UUID requestedSession) {
        return reserve(key,device,type,requestedSession,null);
    }
    public ControlCommand reserve(UUID key,String device,String type,UUID requestedSession,UUID cultivation) {
        var previous=store.byKey(key);
        if(previous!=null)return match(previous,device,type,requestedSession,cultivation);
        if(cultivation!=null) {
            if(!"START".equals(type))throw new IllegalArgumentException();
            cultivations.requireActive(cultivation);
        }
        var slot=store.lock(device);
        var existing=store.byKey(key);
        if(existing!=null) return match(existing,device,type,requestedSession,cultivation);
        var active=store.command(slot.active());
        if("START".equals(type)) {
            if(slot.held()!=null || active!=null) throw new ControlException(409,"CONTROL_BUSY");
        } else if(slot.held()==null || !slot.held().equals(requestedSession)
                || active!=null && active.commandState()!=State.ACCEPTED && active.commandState()!=State.REJECTED) {
            throw new ControlException(409,"CONTROL_BUSY");
        }
        var command=store.create(key,device,type,"START".equals(type)?UUID.randomUUID():requestedSession,cultivation);
        store.slot(device,slot.boot(),command.commandId(),command.sessionId());
        return command;
    }
    public static ControlCommand match(ControlCommand c,String device,String type,UUID session) {
        return match(c,device,type,session,null);
    }
    public static ControlCommand match(ControlCommand c,String device,String type,UUID session,UUID cultivation) {
        if(!c.same(device,type,session,cultivation)) throw new ControlException(409,"IDEMPOTENCY_CONFLICT");
        return c;
    }
    public ControlCommand probing(UUID id) {
        var c=store.command(id); store.lock(c.deviceId());
        if(c.commandState()==State.REQUESTED) store.state(id,State.PROBING,null);
        return store.command(id);
    }
    public ControlCommand dispatch(UUID id,Status status,String payload) {
        var c=store.command(id); store.lock(c.deviceId());
        if(c.commandState()!=State.PROBING || !ControlPolicy.executable(status,c.commandType(),c.sessionId())) {
            finish(id,State.NOT_DISPATCHED,"NOT_READY"); return store.command(id);
        }
        store.dispatch(id,status.bootId(),status.controlWindow().windowId(),payload);
        return store.command(id);
    }
    public void finish(UUID id,State state,String reason) {
        var c=store.command(id); if(c==null)return;
        var slot=store.lock(c.deviceId());
        store.state(id,state,reason);
        var actual=store.command(id);
        if((actual.commandState()==State.NOT_DISPATCHED || actual.commandState()==State.NOT_EXECUTED
                || actual.commandState()==State.REJECTED) && id.equals(slot.active())) {
            store.slot(c.deviceId(),slot.boot(),null,"START".equals(c.commandType()) && c.sessionId().equals(slot.held())?null:slot.held());
        }
    }
    public boolean observe(Status s,boolean probe,UUID epoch,Instant received) {
        var slot=store.lock(s.deviceId());
        var old=store.observation(s.deviceId());
        if(store.retired(s.deviceId(),s.bootId())) return false;
        boolean newBoot=!s.bootId().equals(slot.boot());
        if(newBoot && !probe) return false;
        if(!newBoot && old!=null && s.statusSeq()<=old.status().statusSeq())return false;
        var active=store.command(slot.active());
        UUID activeId=slot.active(), held=slot.held();
        if(newBoot) {
            store.boot(s.deviceId(),s.bootId());
            if(active!=null && active.targetBootId()!=null) {
                store.state(active.commandId(),State.INTERRUPTED_REBOOT,"BOOT_CHANGED");
                activeId=null; held=s.sessionId(); active=null;
            } else if(active==null) { held=s.sessionId(); }
        }
        if(active!=null && s.bootId().equals(active.targetBootId())) {
            var e=s.lastExecution(); var r=s.commandResult();
            if(e!=null && e.commandId().equals(active.commandId()) && e.sessionId().equals(active.sessionId())
                    && e.commandType().equals(active.commandType()) && e.windowId().equals(active.windowId())) {
                store.state(active.commandId(),State.ACCEPTED,null);
            } else if(r!=null && r.commandId().equals(active.commandId()) && r.sessionId().equals(active.sessionId())
                    && r.commandType().equals(active.commandType()) && "REJECTED".equals(r.outcome())) {
                store.state(active.commandId(),State.REJECTED,r.reason()); activeId=null;
                if("START".equals(active.commandType()))held=s.sessionId();
            } else if(probe && active.commandState()==State.UNKNOWN && "MQTT".equals(s.controlMode())
                    && s.fault()==null && ("IDLE".equals(s.state()) || "MANUAL_WAIT".equals(s.state()))) {
                store.state(active.commandId(),State.NOT_EXECUTED,"FRESH_PROBE_NO_EXECUTION");
                activeId=null; held=s.sessionId();
            }
        }
        if(s.sessionId()!=null) held=s.sessionId();
        var release=s.lastRelease();
        if(release!=null)store.released(s.deviceId(),release.sessionId(),s.bootId());
        if(held!=null && release!=null && held.equals(release.sessionId()) && "IDLE".equals(s.state())
                && store.preserved(held,release.preservationResult(),release.preservedSampleCount())) { held=null; activeId=null; }
        store.slot(s.deviceId(),s.bootId(),activeId,held);
        store.observation(s,probe,epoch,received);
        return true;
    }
    public void recover() {
        for(var c:store.unfinished()) {
            if(c.commandState()==State.REQUESTED || c.commandState()==State.PROBING) finish(c.commandId(),State.NOT_DISPATCHED,"SERVER_RESTART");
            else if(c.commandState()==State.DISPATCHING) finish(c.commandId(),State.UNKNOWN,"SERVER_RESTART");
        }
    }
}
