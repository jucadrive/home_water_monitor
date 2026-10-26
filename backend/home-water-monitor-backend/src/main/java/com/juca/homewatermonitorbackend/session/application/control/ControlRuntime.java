package com.juca.homewatermonitorbackend.session.application.control;

import com.juca.homewatermonitorbackend.session.infrastructure.mqtt.ControlMessageCodec;
import com.juca.homewatermonitorbackend.session.infrastructure.mqtt.ControlMqttPublisher;
import com.juca.homewatermonitorbackend.session.infrastructure.mqtt.SessionMqttProperties;
import com.juca.homewatermonitorbackend.session.infrastructure.persistence.DeviceControlStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import static com.juca.homewatermonitorbackend.session.application.control.ControlModel.*;

@Service
@ConditionalOnProperty(prefix="water-monitor.control",name="enabled",havingValue="true")
public class ControlRuntime implements SmartLifecycle {
    private final CommandTransactionService transactions;
    private final DeviceControlStore store;
    private final ControlMqttPublisher publisher;
    private final Set<String> allowed;
    private final java.util.List<String> devices;
    private final ConcurrentHashMap<String,ReentrantLock> probeLocks=new ConcurrentHashMap<>();
    private final AtomicInteger probeCursor=new AtomicInteger();
    private final ControlMessageCodec codec=new ControlMessageCodec();
    private final ControlProbeExchange exchange=new ControlProbeExchange();
    private final Set<String> commanding=ConcurrentHashMap.newKeySet(), probing=ConcurrentHashMap.newKeySet();
    private final Semaphore admission=new Semaphore(2);
    private final ConcurrentHashMap<UUID,CompletableFuture<ControlCommand>> accepting=new ConcurrentHashMap<>();
    private final ThreadPoolExecutor commands=pool(2,0,"control-command"), probes=pool(2,0,"control-probe"), statuses=pool(1,32,"control-status");
    private final ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor(factory("control-timer"));
    private final UUID epoch=UUID.randomUUID();
    private volatile boolean running;
    public ControlRuntime(CommandTransactionService transactions,DeviceControlStore store,ControlMqttPublisher publisher,SessionMqttProperties mqtt) {
        this.transactions=transactions;this.store=store;this.publisher=publisher;allowed=Set.copyOf(mqtt.allowedDeviceIds());
        devices=allowed.stream().sorted().toList();for(String device:devices)probeLocks.put(device,new ReentrantLock());
    }
    private static ThreadFactory factory(String name) {
        var count=new AtomicInteger();return r->{var t=new Thread(r,name+"-"+count.incrementAndGet());t.setDaemon(true);return t;};
    }
    private static ThreadPoolExecutor pool(int n,int queue,String name) {
        return new ThreadPoolExecutor(n,n,0,TimeUnit.MILLISECONDS,queue==0?new SynchronousQueue<>():new ArrayBlockingQueue<>(queue),factory(name),new ThreadPoolExecutor.AbortPolicy());
    }
    public void requireDevice(String device) { if(!allowed.contains(device))throw new ControlException(404,"DEVICE_NOT_FOUND"); }
    public record Submission(ControlCommand command,boolean created) { }
    public Submission submit(UUID key,String device,String type,UUID session) {
        return submit(key,device,type,session,null);
    }
    public Submission submit(UUID key,String device,String type,UUID session,UUID cultivation) {
        ControlPolicy.uuid(key);
        if(cultivation!=null && !"START".equals(type))throw new IllegalArgumentException();
        var promise=new CompletableFuture<ControlCommand>();var previous=accepting.putIfAbsent(key,promise);
        if(previous!=null) {
            try{return new Submission(CommandTransactionService.match(previous.get(4,TimeUnit.SECONDS),device,type,session,cultivation),false);}
            catch(ControlException e){throw e;}
            catch(Exception e){throw new ControlException(503,"CONTROL_ACCEPTANCE_PENDING");}
        }
        try{var result=doSubmit(key,device,type,session,cultivation);promise.complete(result.command());return result;}
        catch(RuntimeException e){promise.completeExceptionally(e);throw e;}
        finally{accepting.remove(key,promise);}
    }
    private Submission doSubmit(UUID key,String device,String type,UUID session,UUID cultivation) {
        requireDevice(device);ControlPolicy.uuid(key);ControlPolicy.type(type);
        if("RETRY".equals(type))ControlPolicy.uuid(session);
        var previous=store.byKey(key);
        if(previous!=null)return new Submission(CommandTransactionService.match(previous,device,type,session,cultivation),false);
        if(!running || !admission.tryAcquire())throw new ControlException(503,"CONTROL_CAPACITY");
        boolean owned=false,transferred=false;
        try {
            if(!commanding.add(device))throw new ControlException(409,"CONTROL_BUSY");
            owned=true;exchange.cancel(device);
            ControlCommand c;
            try { c=cultivation==null ? transactions.reserve(key,device,type,session) : transactions.reserve(key,device,type,session,cultivation); }
            catch(RuntimeException error) {
                if(!store.keyConflict(error))throw error;
                var winner=store.byKey(key);
                if(winner==null)throw error;
                return new Submission(CommandTransactionService.match(winner,device,type,session,cultivation),false);
            }
            if(c.commandState()!=State.REQUESTED)return new Submission(c,false);
            UUID id=c.commandId();
            try { commands.execute(()->{try{dispatch(id);}finally{commanding.remove(device);admission.release();}}); }
            catch(RejectedExecutionException error){transactions.finish(id,State.NOT_DISPATCHED,"WORKER_STOPPED");return new Submission(c,true);}
            transferred=true;return new Submission(c,true);
        } finally { if(!transferred){if(owned)commanding.remove(device);admission.release();} }
    }
    private Status probe(String device,long millis,boolean command) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(millis);
        var lock=probeLocks.get(device);
        if(!lock.tryLock(millis,TimeUnit.MILLISECONDS))throw new TimeoutException();
        try {
            // A cancelled background publish may still be in flight. Serialize its completion before a new window request.
            if(!command && commanding.contains(device))throw new CancellationException();
            long remaining=deadline-System.nanoTime();if(remaining<=0)throw new TimeoutException();
            var p=exchange.begin(device,TimeUnit.NANOSECONDS.toMillis(remaining));
            try {
                publisher.publish(device,codec.encode(new Probe(1,"STATE_REQUEST",device,p.id())),p.deadlineNano());
                remaining=p.deadlineNano()-System.nanoTime();
                if(remaining<=0)throw new TimeoutException();
                return p.answer().get(remaining,TimeUnit.NANOSECONDS);
            } finally { exchange.end(device,p); }
        } finally { lock.unlock(); }
    }
    private void dispatch(UUID id) {
        boolean dispatched=false;
        try {
            var c=transactions.probing(id);if(c.commandState()!=State.PROBING)return;
            var s=probe(c.deviceId(),5000,true);
            if(!ControlPolicy.executable(s,c.commandType(),c.sessionId())) {transactions.finish(id,State.NOT_DISPATCHED,"NOT_READY");return;}
            Object wire="START".equals(c.commandType())?new Start(1,"START",c.deviceId(),id,c.sessionId(),s.bootId(),s.controlWindow().windowId(),60,1000)
                    :new Retry(1,"RETRY",c.deviceId(),id,c.sessionId(),s.bootId(),s.controlWindow().windowId());
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            c=transactions.dispatch(id,s,codec.json(wire));
            if(c.commandState()!=State.DISPATCHING)return;
            dispatched=true;publisher.publish(c.deviceId(),codec.encode(wire),deadline);
            while(running && System.nanoTime()<deadline) {
                var current=store.command(id);
                if(current.commandState()!=State.DISPATCHING)return;
                Thread.sleep(25);
            }
            transactions.finish(id,State.UNKNOWN,"OBSERVATION_TIMEOUT");
        } catch(Exception e) {
            if(e instanceof InterruptedException)Thread.currentThread().interrupt();
            try {var current=store.command(id);transactions.finish(id,
                    dispatched || current!=null && current.commandState()==State.DISPATCHING?State.UNKNOWN:State.NOT_DISPATCHED,"CONTROL_FAILURE");}
            catch(RuntimeException ignored){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Control recovery required");}
        }
    }
    public void status(Status s,long receivedNano,Instant receivedAt,Runnable acknowledge) {
        try { statuses.execute(()->{
            try {
                var p=exchange.claim(s,receivedNano);
                boolean stored=transactions.observe(s,p!=null,epoch,receivedAt);
                if(p!=null && stored)exchange.complete(s,p);
            } catch(RuntimeException e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Control status storage failed");}
            finally{acknowledge.run();}
        }); } catch(RejectedExecutionException e){acknowledge.run();org.slf4j.LoggerFactory.getLogger(getClass()).warn("Control status capacity exhausted");}
    }
    public String liveness(String device){return exchange.liveness(device);}
    private void tick() {
        int first=Math.floorMod(probeCursor.getAndAdd(2),devices.size());
        for(int i=0;i<devices.size();i++) {
            String device=devices.get((first+i)%devices.size());
            if(commanding.contains(device)||!probing.add(device))continue;
            try { probes.execute(()->{try{probe(device,10000,false);}catch(Exception ignored){}finally{probing.remove(device);}}); }
            catch(RejectedExecutionException e){probing.remove(device);}
        }
    }
    @Override public void start(){transactions.recover();running=true;scheduler.scheduleWithFixedDelay(this::tick,0,15,TimeUnit.SECONDS);}
    @Override public void stop(){running=false;for(String d:allowed)exchange.cancel(d);scheduler.shutdownNow();commands.shutdownNow();probes.shutdownNow();statuses.shutdown();}
    @Override public boolean isRunning(){return running;}
    @Override public int getPhase(){return 200;}
}
