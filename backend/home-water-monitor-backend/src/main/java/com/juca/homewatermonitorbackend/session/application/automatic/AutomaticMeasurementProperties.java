package com.juca.homewatermonitorbackend.session.application.automatic;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.UUID;

@ConfigurationProperties("water-monitor.automatic-measurement")
public record AutomaticMeasurementProperties(boolean enabled, String deviceId, UUID cultivationId, boolean recoveryEnabled) {
    public AutomaticMeasurementProperties(boolean enabled,String deviceId,UUID cultivationId) {
        this(enabled,deviceId,cultivationId,false);
    }
    @org.springframework.boot.context.properties.bind.ConstructorBinding
    public AutomaticMeasurementProperties {
        if (enabled && (deviceId == null || !deviceId.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")
                || cultivationId == null || cultivationId.version() != 4 || cultivationId.variant() != 2))
            throw new IllegalArgumentException("Automatic measurement requires an explicit device and cultivation UUID v4");
    }
}
