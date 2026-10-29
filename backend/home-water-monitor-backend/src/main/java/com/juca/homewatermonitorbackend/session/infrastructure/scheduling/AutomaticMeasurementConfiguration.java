package com.juca.homewatermonitorbackend.session.infrastructure.scheduling;

import com.juca.homewatermonitorbackend.session.application.automatic.*;
import com.juca.homewatermonitorbackend.session.application.control.ControlRuntime;
import com.juca.homewatermonitorbackend.session.infrastructure.persistence.AutomaticMeasurementRunStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.time.Instant;
import com.juca.homewatermonitorbackend.cultivation.application.CultivationService;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(prefix="water-monitor.automatic-measurement", name="enabled", havingValue="true")
@EnableConfigurationProperties(AutomaticMeasurementProperties.class)
@EnableScheduling
public class AutomaticMeasurementConfiguration {
    @Bean AutomaticMeasurementService automaticMeasurementService(AutomaticMeasurementProperties settings,
            AutomaticMeasurementRunStore runs, ControlRuntime control, CultivationService cultivations) {
        cultivations.get(settings.cultivationId());
        return new AutomaticMeasurementService(settings,runs,control,Instant.now());
    }
    @Bean HourlyMeasurementJob hourlyMeasurementJob(AutomaticMeasurementService service) {
        return new HourlyMeasurementJob(service);
    }
}
