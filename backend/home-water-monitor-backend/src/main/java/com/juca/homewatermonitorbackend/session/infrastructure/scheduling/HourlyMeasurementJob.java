package com.juca.homewatermonitorbackend.session.infrastructure.scheduling;

import com.juca.homewatermonitorbackend.session.application.automatic.AutomaticMeasurementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.Instant;
import java.util.concurrent.*;
import jakarta.annotation.PreDestroy;

public class HourlyMeasurementJob {
    private static final Logger LOG=LoggerFactory.getLogger(HourlyMeasurementJob.class);
    private final AutomaticMeasurementService measurements;
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,
        new SynchronousQueue<>(),r->{var t=new Thread(r,"automatic-measurement");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    public HourlyMeasurementJob(AutomaticMeasurementService measurements) { this.measurements=measurements; }
    @Scheduled(cron="0 0 * * * *", zone="UTC")
    public void run() {
        Instant now=Instant.now();
        try { worker.execute(()->execute(now)); }
        catch(RejectedExecutionException e){LOG.warn("Hourly worker busy or stopped; no queued catch-up");}
    }
    private void execute(Instant now) {
        try { measurements.runAt(now); }
        catch (RuntimeException e) {
            // Do not leak DB connection details or credentials through exception messages.
            LOG.error("Hourly measurement callback failed: {}. Inspect the run ledger; no immediate retry.",
                    e.getClass().getSimpleName());
        }
    }
    @PreDestroy public void stop() { worker.shutdownNow(); }
}
