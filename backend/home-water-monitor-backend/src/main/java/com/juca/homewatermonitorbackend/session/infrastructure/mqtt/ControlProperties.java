package com.juca.homewatermonitorbackend.session.infrastructure.mqtt;

import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties("water-monitor.control")
public record ControlProperties(boolean enabled,String statusClientId,String publishClientId) {
    public ControlProperties {
        if(enabled && (statusClientId==null || publishClientId==null
                || !statusClientId.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")
                || !publishClientId.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}") || statusClientId.equals(publishClientId))) {
            throw new IllegalArgumentException("Distinct control client IDs are required");
        }
    }
}
