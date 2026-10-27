package com.juca.homewatermonitorbackend.security.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("water-monitor.auth")
public final class AuthSettings {
    private final String username;
    private final String passwordHash;
    private final Duration tokenTtl;
    private final int maxSessions;
    private final int loginAttempts;
    public AuthSettings(String username, String passwordHash, Duration tokenTtl, Integer maxSessions, Integer loginAttempts) {
        if (username == null || !username.matches("[A-Za-z0-9_-]{1,64}") || passwordHash == null
                || !passwordHash.matches("\\$2[aby]\\$(10|11|12|13|14)\\$[./A-Za-z0-9]{53}"))
            throw new IllegalArgumentException("Single-user authentication configuration is required");
        this.username = username;
        this.passwordHash = passwordHash;
        this.tokenTtl = tokenTtl == null ? Duration.ofHours(8) : tokenTtl;
        this.maxSessions = maxSessions == null ? 5 : maxSessions;
        this.loginAttempts = loginAttempts == null ? 5 : loginAttempts;
        if (this.tokenTtl.compareTo(Duration.ofSeconds(1)) < 0 || this.tokenTtl.compareTo(Duration.ofHours(24)) > 0
                || this.maxSessions < 1 || this.maxSessions > 20 || this.loginAttempts < 1 || this.loginAttempts > 1000)
            throw new IllegalArgumentException("Invalid authentication limits");
    }
    public String username() { return username; }
    public String passwordHash() { return passwordHash; }
    public Duration tokenTtl() { return tokenTtl; }
    public int maxSessions() { return maxSessions; }
    public int loginAttempts() { return loginAttempts; }
    @Override public String toString() { return "AuthSettings[redacted]"; }
}
