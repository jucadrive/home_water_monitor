package com.juca.homewatermonitorbackend.security.infrastructure;

import com.juca.homewatermonitorbackend.security.application.OwnerAuthentication;
import com.juca.homewatermonitorbackend.security.domain.TokenRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AuthenticationController {
    private final OwnerAuthentication owner;
    private final TokenRegistry tokens;
    public AuthenticationController(OwnerAuthentication owner, TokenRegistry tokens) { this.owner = owner; this.tokens = tokens; }
    public record Login(String username, String password) {
        @Override public String toString() { return "Login[redacted]"; }
    }
    @PostMapping("/login") public ResponseEntity<?> login(@RequestBody Login input) {
        var issued = owner.login(input.username(), input.password());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                Map.of("accessToken", issued.accessToken(), "tokenType", "Bearer", "expiresAt", issued.expiresAt()));
    }
    @GetMapping("/session") public ResponseEntity<?> session(BearerTokenAuthentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                Map.of("username", authentication.getName(), "expiresAt", authentication.getTokenAttributes().get("exp")));
    }
    @PostMapping("/logout") public ResponseEntity<?> logout(BearerTokenAuthentication authentication) {
        tokens.revoke(authentication.getToken().getTokenValue());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @ExceptionHandler(OwnerAuthentication.Rejected.class) public ResponseEntity<?> rejected(OwnerAuthentication.Rejected e) {
        var builder = ResponseEntity.status(e.status()).cacheControl(CacheControl.noStore());
        if (e.status() == 429) builder.header("Retry-After", "60");
        if (e.status() == 401) builder.header("WWW-Authenticate", "Bearer");
        return builder.body(Map.of("code", e.code(), "message", e.code()));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class) public ResponseEntity<?> malformed() {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(Map.of("code", "AUTH_INPUT_INVALID", "message", "AUTH_INPUT_INVALID"));
    }
}
