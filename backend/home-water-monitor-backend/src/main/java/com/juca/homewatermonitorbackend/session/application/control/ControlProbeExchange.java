package com.juca.homewatermonitorbackend.session.application.control;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import com.juca.homewatermonitorbackend.session.application.control.ControlModel.Status;

// 허용 장치별 최대 하나. 서버 재시작에서 이전 probe/단조 시각을 재사용하지 않는다.
public class ControlProbeExchange {
    public record Pending(UUID id,long sentNano,long deadlineNano,CompletableFuture<Status> answer,AtomicBoolean claimed) { }
    private final ConcurrentHashMap<String,Pending> pending=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String,Long> confirmed=new ConcurrentHashMap<>();
    public Pending begin(String device,long limitMs) {
        cancel(device);
        long now=System.nanoTime();
        var p=new Pending(UUID.randomUUID(),now,now+limitMs*1000000,new CompletableFuture<>(),new AtomicBoolean());
        pending.put(device,p); return p;
    }
    public Pending claim(Status s,long receivedNano) {
        var p=pending.get(s.deviceId());
        return p!=null && p.id().equals(s.replyToProbeId()) && receivedNano>=p.sentNano()
                && receivedNano<p.deadlineNano() && System.nanoTime()<p.deadlineNano()
                && p.claimed().compareAndSet(false,true)?p:null;
    }
    public void complete(Status s,Pending p) {
        if(pending.remove(s.deviceId(),p)) { confirmed.put(s.deviceId(),System.nanoTime());p.answer().complete(s); }
    }
    public void end(String device,Pending p) { pending.remove(device,p); }
    public void cancel(String device) {
        var old=pending.remove(device);if(old!=null)old.answer().completeExceptionally(new IllegalStateException("PROBE_SUPERSEDED"));
    }
    public String liveness(String device) {
        Long at=confirmed.get(device);
        return ControlPolicy.liveness(at!=null,at==null?-1:(System.nanoTime()-at)/1000000);
    }
}
