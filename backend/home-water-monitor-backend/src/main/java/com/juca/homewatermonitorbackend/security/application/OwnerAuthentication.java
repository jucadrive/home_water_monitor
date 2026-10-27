package com.juca.homewatermonitorbackend.security.application;

import com.juca.homewatermonitorbackend.security.domain.TokenRegistry;
import java.time.*;
import java.util.ArrayDeque;
import org.springframework.security.crypto.password.PasswordEncoder;

public final class OwnerAuthentication {
    public static final class Rejected extends RuntimeException {
        private final int status;
        private final String code;
        public Rejected(int status, String code) { super(code); this.status = status; this.code = code; }
        public int status() { return status; }
        public String code() { return code; }
    }
    private final AuthSettings settings;
    private final PasswordEncoder passwords;
    private final TokenRegistry tokens;
    private final Clock clock;
    private final ArrayDeque<Instant> attempts = new ArrayDeque<>();
    public OwnerAuthentication(AuthSettings settings, PasswordEncoder passwords, TokenRegistry tokens, Clock clock) {
        this.settings = settings; this.passwords = passwords; this.tokens = tokens; this.clock = clock;
    }
    public TokenRegistry.Issued login(String username, String password) {
        if (username == null || username.length() > 64 || password == null || password.isEmpty()
                || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new Rejected(400, "AUTH_INPUT_INVALID");
        admit();
        // Always verify the hash, including when the name differs; errors never reveal account existence.
        boolean matches = passwords.matches(password, settings.passwordHash());
        if (!settings.username().equals(username) || !matches) throw new Rejected(401, "AUTH_INVALID_CREDENTIALS");
        return tokens.issue(settings.username());
    }
    private synchronized void admit() {
        Instant cutoff = clock.instant().minusSeconds(60);
        while (!attempts.isEmpty() && !attempts.peekFirst().isAfter(cutoff)) attempts.removeFirst();
        if (attempts.size() >= settings.loginAttempts()) throw new Rejected(429, "AUTH_RATE_LIMITED");
        attempts.addLast(clock.instant());
    }
}
