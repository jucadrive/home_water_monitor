package com.juca.homewatermonitorbackend.session.infrastructure.scheduling;

import com.juca.homewatermonitorbackend.session.application.automatic.AutomaticMeasurementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.Instant;

public class HourlyMeasurementJob {
    private static final Logger LOG=LoggerFactory.getLogger(HourlyMeasurementJob.class);
    private final AutomaticMeasurementService measurements;
    public HourlyMeasurementJob(AutomaticMeasurementService measurements) { this.measurements=measurements; }
    @Scheduled(cron="0 0 * * * *", zone="UTC")
    public void run() {
        try { measurements.runAt(Instant.now()); }
        catch (RuntimeException e) {
            // Do not leak DB connection details or credentials through exception messages.
            LOG.error("Hourly measurement callback failed: {}. Inspect the run ledger; no immediate retry.",
                    e.getClass().getSimpleName());
        }
    }
}
