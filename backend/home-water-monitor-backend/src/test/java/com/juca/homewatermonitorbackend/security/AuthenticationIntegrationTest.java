package com.juca.homewatermonitorbackend.security;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import com.juca.homewatermonitorbackend.security.domain.TokenRegistry;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.profiles.active=personal")
@Testcontainers @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthenticationIntegrationTest {
    @Container static final PostgreSQLContainer pg = new PostgreSQLContainer("postgres:18-alpine");
    @DynamicPropertySource static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl); r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
    }
    @LocalServerPort int port;
    @Autowired TokenRegistry tokens;
    @Autowired org.springframework.core.env.Environment environment;
    @org.springframework.test.context.bean.override.mockito.MockitoBean Clock authClock;
    final java.util.concurrent.atomic.AtomicReference<Instant> now = new java.util.concurrent.atomic.AtomicReference<>(Instant.parse("2026-10-26T00:00:00Z"));
    @org.junit.jupiter.api.BeforeEach void time() { when(authClock.instant()).thenAnswer(invocation -> now.get()); }
    String origin() { return "http://127.0.0.1:" + port; }
    HttpResponse<String> request(String method, String path, String body, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(origin() + path)).timeout(Duration.ofSeconds(5));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        try (var client = HttpClient.newHttpClient()) {
            return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
    }
    @Test void allBusinessRoutesRejectAnonymousRequestsWithJsonAndNoRedirectOrCookie() throws Exception {
        for (var path : List.of("/api/cultivations", "/api/measurement-sessions", "/api/devices/test/status", "/api/auth/session")) {
            var response = request("GET", path, null, null);
            assertEquals(401, response.statusCode()); assertTrue(response.body().contains("AUTH_REQUIRED"));
            assertTrue(response.headers().firstValue("Location").isEmpty());
            assertTrue(response.headers().firstValue("Set-Cookie").isEmpty());
        }
        assertEquals(401, request("POST", "/api/cultivations", "{}", null).statusCode());
        assertEquals(401, request("GET", "/api/cultivations", null, "invalid").statusCode());
    }
    @Test void loginSessionBusinessAccessAndLogoutUseRealBearerAuthentication() throws Exception {
        String token = AuthHttpFixture.token(origin());
        var session = request("GET", "/api/auth/session", null, token);
        assertEquals(200, session.statusCode()); assertTrue(session.body().contains(AuthHttpFixture.USER));
        assertEquals("no-store", session.headers().firstValue("Cache-Control").orElse(""));
        assertEquals(200, request("GET", "/api/cultivations", null, token).statusCode());
        assertEquals(403, request("GET", "/unapproved", null, token).statusCode());
        assertEquals(204, request("POST", "/api/auth/logout", null, token).statusCode());
        assertEquals(401, request("GET", "/api/cultivations", null, token).statusCode());
        assertNull(tokens.find(token));
    }
    @Test void loginErrorsAreJsonWithoutCredentialReflection() throws Exception {
        var invalid = request("POST", "/api/auth/login", "{\"username\":\"unknown\",\"password\":\"sentinel-do-not-echo\"}", null);
        assertEquals(401, invalid.statusCode()); assertFalse(invalid.body().contains("sentinel"));
        assertEquals(400, request("POST", "/api/auth/login", "{bad", null).statusCode());
        assertEquals(400, request("POST", "/api/auth/login", "{}", null).statusCode());
    }
    @Test void expiryRejectsHttpRequestsAtTheExactBoundary() throws Exception {
        String token = AuthHttpFixture.token(origin());
        now.set(now.get().plus(Duration.ofHours(8)));
        var expired = request("GET", "/api/cultivations", null, token);
        assertEquals(401, expired.statusCode()); assertTrue(expired.body().contains("AUTH_REQUIRED"));
    }
    @Test void personalProfileBindsLoopbackAndKeepsPhysicalDeviceControlOff() {
        assertEquals("127.0.0.1", environment.getProperty("server.address"));
        assertEquals("false", environment.getProperty("water-monitor.mqtt.enabled"));
        assertEquals("false", environment.getProperty("water-monitor.control.enabled"));
    }
}
