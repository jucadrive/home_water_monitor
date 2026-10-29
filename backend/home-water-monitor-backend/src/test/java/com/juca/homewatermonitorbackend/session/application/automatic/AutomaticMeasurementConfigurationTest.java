package com.juca.homewatermonitorbackend.session.application.automatic;

import com.juca.homewatermonitorbackend.cultivation.application.*;
import com.juca.homewatermonitorbackend.session.application.control.*;
import com.juca.homewatermonitorbackend.session.infrastructure.persistence.AutomaticMeasurementRunStore;
import com.juca.homewatermonitorbackend.session.infrastructure.scheduling.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.Scheduled;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AutomaticMeasurementConfigurationTest {
    final UUID target=UUID.randomUUID();
    final ControlRuntime control=mock(ControlRuntime.class);
    final CultivationService cultivations=mock(CultivationService.class);
    ApplicationContextRunner enabled() {
        return new ApplicationContextRunner().withUserConfiguration(AutomaticMeasurementConfiguration.class)
            .withBean(ControlRuntime.class,()->control).withBean(CultivationService.class,()->cultivations)
            .withBean(AutomaticMeasurementRunStore.class,()->mock(AutomaticMeasurementRunStore.class))
            .withPropertyValues("water-monitor.control.enabled=true", "water-monitor.automatic-measurement.enabled=true",
                "water-monitor.automatic-measurement.device-id=sensor",
                "water-monitor.automatic-measurement.cultivation-id="+target);
    }
    @Test void omittedFlagMeansNoAutomaticConfiguration() {
        new ApplicationContextRunner().withUserConfiguration(AutomaticMeasurementConfiguration.class).run(c->{
            assertNull(c.getStartupFailure());assertFalse(c.containsBean("hourlyMeasurementJob"));
        });
    }
    @Test void explicitEnabledTargetsRegisterOnlyTheHourlyUtcTrigger() throws Exception {
        enabled().run(c->{
            assertNull(c.getStartupFailure());assertNotNull(c.getBean(HourlyMeasurementJob.class));
            verify(control).requireDevice("sensor");verify(cultivations).get(target);
        });
        var schedule=HourlyMeasurementJob.class.getMethod("run").getAnnotation(Scheduled.class);
        assertEquals("0 0 * * * *",schedule.cron());assertEquals("UTC",schedule.zone());
    }
    @Test void missingCultivationOrControlCannotSilentlyEnableAutomation() {
        enabled().withPropertyValues("water-monitor.automatic-measurement.cultivation-id=").run(c->assertNotNull(c.getStartupFailure()));
        new ApplicationContextRunner().withUserConfiguration(AutomaticMeasurementConfiguration.class)
            .withPropertyValues("water-monitor.automatic-measurement.enabled=true",
                "water-monitor.automatic-measurement.device-id=sensor",
                "water-monitor.automatic-measurement.cultivation-id="+target)
            .run(c->assertNotNull(c.getStartupFailure()));
    }
    @Test void nonexistentCultivationFailsStartupRatherThanRunningWithoutATarget() {
        when(cultivations.get(target)).thenThrow(new CultivationException(404,"CULTIVATION_NOT_FOUND"));
        enabled().run(c->assertNotNull(c.getStartupFailure()));
    }
}
