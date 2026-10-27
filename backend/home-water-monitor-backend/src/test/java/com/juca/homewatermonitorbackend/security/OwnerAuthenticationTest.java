package com.juca.homewatermonitorbackend.security;

import com.juca.homewatermonitorbackend.security.application.*;
import com.juca.homewatermonitorbackend.security.domain.TokenRegistry;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;

class OwnerAuthenticationTest {
    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.boot.context.properties.EnableConfigurationProperties(AuthSettings.class)
    static class Binding { }
    static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
    static final String hash = encoder.encode(AuthHttpFixture.PASSWORD);
    @Test void missingWebAuthenticationConfigurationCannotBind() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner().withUserConfiguration(Binding.class)
                .run(context -> assertNotNull(context.getStartupFailure()));
    }
    @Test void rejectsMissingOrUnsafeSettingsWithoutEchoingThem() {
        assertThrows(IllegalArgumentException.class, () -> new AuthSettings(null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new AuthSettings("owner", "plaintext", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new AuthSettings("owner", hash, Duration.ZERO, null, null));
        assertEquals("AuthSettings[redacted]", new AuthSettings("owner", hash, null, null, null).toString());
    }
    @Test void rateLimitIsBoundedAndResetsWithoutDisclosingTheUsername() {
        var time = new TokenRegistryTest.Time(); var settings = new AuthSettings("owner", hash, null, 5, 2);
        var tokens = new TokenRegistry(time, settings.tokenTtl(), 5);
        var service = new OwnerAuthentication(settings, encoder, tokens, time);
        assertEquals(401, assertThrows(OwnerAuthentication.Rejected.class, () -> service.login("owner", "wrong")).status());
        assertEquals(401, assertThrows(OwnerAuthentication.Rejected.class, () -> service.login("other", AuthHttpFixture.PASSWORD)).status());
        assertEquals(429, assertThrows(OwnerAuthentication.Rejected.class, () -> service.login("owner", AuthHttpFixture.PASSWORD)).status());
        time.now = time.now.plusSeconds(60);
        assertNotNull(tokens.find(service.login("owner", AuthHttpFixture.PASSWORD).accessToken()));
    }
    @Test void inputValidationHappensBeforePasswordVerification() {
        var settings = new AuthSettings("owner", hash, null, null, null);
        var service = new OwnerAuthentication(settings, encoder,
                new TokenRegistry(Clock.systemUTC(), settings.tokenTtl(), 5), Clock.systemUTC());
        assertEquals(400, assertThrows(OwnerAuthentication.Rejected.class, () -> service.login("owner", "x".repeat(73))).status());
        assertEquals(400, assertThrows(OwnerAuthentication.Rejected.class, () -> service.login(null, "password")).status());
    }
}
