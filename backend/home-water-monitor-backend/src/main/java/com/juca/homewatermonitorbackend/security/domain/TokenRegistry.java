package com.juca.homewatermonitorbackend.security.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;

// Process-local sessions are deliberately revoked by restart. Only token digests are retained.
public final class TokenRegistry {
    public record Session(String username, Instant expiresAt) { }
    public record Issued(String accessToken, Instant expiresAt) {
        @Override public String toString() { return "Issued[redacted]"; }
    }
    private final LinkedHashMap<String, Session> sessions = new LinkedHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;
    private final Duration ttl;
    private final int capacity;
    public TokenRegistry(Clock clock, Duration ttl, int capacity) {
        if (ttl.isNegative() || ttl.isZero() || capacity < 1) throw new IllegalArgumentException();
        this.clock = clock; this.ttl = ttl; this.capacity = capacity;
    }
    public synchronized Issued issue(String username) {
        prune();
        while (sessions.size() >= capacity) sessions.remove(sessions.keySet().iterator().next());
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiry = clock.instant().plus(ttl);
        sessions.put(digest(token), new Session(username, expiry));
        return new Issued(token, expiry);
    }
    public synchronized Session find(String token) {
        prune();
        return token == null || !token.matches("[A-Za-z0-9_-]{43}") ? null : sessions.get(digest(token));
    }
    public synchronized void revoke(String token) { if (token != null) sessions.remove(digest(token)); }
    private void prune() { sessions.values().removeIf(s -> !s.expiresAt().isAfter(clock.instant())); }
    private static String digest(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
}
