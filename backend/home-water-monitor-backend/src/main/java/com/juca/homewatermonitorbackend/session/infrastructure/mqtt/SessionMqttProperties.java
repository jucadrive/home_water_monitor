package com.juca.homewatermonitorbackend.session.infrastructure.mqtt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URI;
import java.util.Set;

@ConfigurationProperties("water-monitor.mqtt")
public record SessionMqttProperties(boolean enabled, String brokerUri, String inboundClientId,
        String outboundClientId, Set<String> allowedDeviceIds, String username, String password) {
    public SessionMqttProperties {
        if (enabled) {
            try {
                URI uri = URI.create(brokerUri);
                if (!("tcp".equals(uri.getScheme()) || "ssl".equals(uri.getScheme())) || uri.getHost() == null
                        || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                        || uri.getPath() != null && !uri.getPath().isEmpty()
                        || uri.getPort() < 1 || uri.getPort() > 65535) throw new IllegalArgumentException();
                if (!validId(inboundClientId) || !validId(outboundClientId) || inboundClientId.equals(outboundClientId)
                        || allowedDeviceIds == null || allowedDeviceIds.isEmpty()
                        || allowedDeviceIds.stream().anyMatch(id -> !validId(id))) throw new IllegalArgumentException();
                allowedDeviceIds = Set.copyOf(allowedDeviceIds);
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("MQTT configuration requires broker URI, distinct client IDs and allowed devices");
            }
        }
    }

    private static boolean validId(String id) {
        return id != null && id.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");
    }

    @Override public String toString() { return "SessionMqttProperties[redacted]"; }
}
