package com.juca.homewatermonitorbackend.session.application.automatic;

import com.juca.homewatermonitorbackend.cultivation.application.CultivationException;
import com.juca.homewatermonitorbackend.measurement.domain.HourlyMeasurementSlot;
import com.juca.homewatermonitorbackend.session.application.control.ControlException;
import com.juca.homewatermonitorbackend.session.application.control.ControlRuntime;
import com.juca.homewatermonitorbackend.session.infrastructure.persistence.AutomaticMeasurementRunStore;
import java.time.Instant;
import java.util.UUID;

/** No enclosing transaction: commit the hourly claim before entering the existing control path. */
public class AutomaticMeasurementService {
    private final AutomaticMeasurementProperties settings;
    private final AutomaticMeasurementRunStore runs;
    private final ControlRuntime control;
    private final Instant startedAt;
    public AutomaticMeasurementService(AutomaticMeasurementProperties settings, AutomaticMeasurementRunStore runs,
                                       ControlRuntime control, Instant startedAt) {
        this.settings=settings; this.runs=runs; this.control=control; this.startedAt=startedAt;
        control.requireDevice(settings.deviceId());
    }
    public void runAt(Instant now) {
        if (!settings.enabled() || !HourlyMeasurementSlot.due(now, startedAt)) return;
        var slot=HourlyMeasurementSlot.at(now);
        runs.reconcileBefore(slot);
        var key=UUID.randomUUID();
        if (!runs.claim(settings.deviceId(), settings.cultivationId(), slot, key)) return;
        try {
            control.submit(key, settings.deviceId(), "START", null, settings.cultivationId());
        } catch (ControlException e) {
            runs.finish(key, "SKIPPED", e.code()); return;
        } catch (CultivationException e) {
            runs.finish(key, "SKIPPED", e.code()); return;
        } catch (RuntimeException e) {
            runs.finish(key, "UNKNOWN", "SUBMISSION_UNCERTAIN"); return;
        }
        runs.finish(key, "SUBMITTED", null);
    }
}
