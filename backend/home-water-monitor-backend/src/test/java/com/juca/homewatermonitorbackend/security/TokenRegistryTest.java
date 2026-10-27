package com.juca.homewatermonitorbackend.security;

import com.juca.homewatermonitorbackend.security.domain.TokenRegistry;
import java.time.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TokenRegistryTest {
    static class Time extends Clock {
        Instant now = Instant.parse("2026-10-26T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
    @Test void expiresAtTheBoundaryAndRestartDoesNotRestoreSessions() {
        var time = new Time(); var store = new TokenRegistry(time, Duration.ofHours(8), 5);
        var issued = store.issue("owner");
        assertEquals("owner", store.find(issued.accessToken()).username());
        assertNull(new TokenRegistry(time, Duration.ofHours(8), 5).find(issued.accessToken()));
        time.now = issued.expiresAt(); assertNull(store.find(issued.accessToken()));
    }
    @Test void revokeAndCapacityNeverAffectUnrelatedCurrentTokens() {
        var store = new TokenRegistry(new Time(), Duration.ofHours(8), 2);
        var first = store.issue("owner"); var second = store.issue("owner"); var third = store.issue("owner");
        assertNull(store.find(first.accessToken())); assertNotNull(store.find(second.accessToken()));
        store.revoke(second.accessToken()); assertNull(store.find(second.accessToken()));
        assertNotNull(store.find(third.accessToken()));
    }
    @Test void malformedTokensAreRejectedAndConcurrentIssuanceStaysBounded() throws Exception {
        var store = new TokenRegistry(new Time(), Duration.ofHours(8), 5);
        assertNull(store.find(null)); assertNull(store.find("bad")); assertNull(store.find("x".repeat(10000)));
        try (var executor = Executors.newFixedThreadPool(8)) {
            var jobs = new java.util.ArrayList<Future<TokenRegistry.Issued>>();
            for (int i = 0; i < 30; i++) jobs.add(executor.submit(() -> store.issue("owner")));
            var issued = new java.util.ArrayList<TokenRegistry.Issued>();
            for (var job : jobs) issued.add(job.get());
            assertEquals(30, issued.stream().map(TokenRegistry.Issued::accessToken).distinct().count());
            assertEquals(5, issued.stream().filter(i -> store.find(i.accessToken()) != null).count());
        }
    }
}
