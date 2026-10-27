package com.juca.homewatermonitorbackend.security.infrastructure;

import com.juca.homewatermonitorbackend.security.application.*;
import com.juca.homewatermonitorbackend.security.domain.TokenRegistry;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(AuthSettings.class)
public class ApiSecurity {
    @Bean Clock authClock() { return Clock.systemUTC(); }
    @Bean TokenRegistry tokens(AuthSettings settings, Clock authClock) { return new TokenRegistry(authClock, settings.tokenTtl(), settings.maxSessions()); }
    @Bean OwnerAuthentication owner(AuthSettings settings, TokenRegistry tokens, Clock authClock) {
        return new OwnerAuthentication(settings, new BCryptPasswordEncoder(12), tokens, authClock);
    }
    @Bean OpaqueTokenIntrospector introspector(TokenRegistry tokens) {
        return token -> {
            var session = tokens.find(token);
            if (session == null) throw new BadOpaqueTokenException("Invalid authentication");
            return new DefaultOAuth2AuthenticatedPrincipal(session.username(),
                    Map.of("sub", session.username(), "exp", session.expiresAt()), List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
        };
    }
    @Bean SecurityFilterChain api(HttpSecurity http, OpaqueTokenIntrospector introspector) throws Exception {
        return http
                // This HTTP surface accepts explicit Bearer credentials only; no ambient cookie authentication.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(c -> c.disable()).formLogin(c -> c.disable()).httpBasic(c -> c.disable()).logout(c -> c.disable())
                .authorizeHttpRequests(a -> a.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/api/**").hasRole("OWNER").anyRequest().denyAll())
                .exceptionHandling(e -> e.authenticationEntryPoint((r, s, x) -> error(s, 401, "AUTH_REQUIRED"))
                        .accessDeniedHandler((r, s, x) -> error(s, 403, "AUTH_FORBIDDEN")))
                .oauth2ResourceServer(o -> o.opaqueToken(t -> t.introspector(introspector))
                        .authenticationEntryPoint((r, s, x) -> error(s, 401, "AUTH_REQUIRED"))
                        .accessDeniedHandler((r, s, x) -> error(s, 403, "AUTH_FORBIDDEN")))
                .build();
    }
    static void error(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status); response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + code + "\"}");
    }
}
