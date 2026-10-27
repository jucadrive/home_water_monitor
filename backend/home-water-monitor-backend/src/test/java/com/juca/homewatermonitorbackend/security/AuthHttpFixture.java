package com.juca.homewatermonitorbackend.security;

import java.net.URI;
import java.net.http.*;
import tools.jackson.databind.json.JsonMapper;

// Public fixture credentials for isolated test databases only, never a production default.
public final class AuthHttpFixture {
    public static final String USER = "fixture-owner";
    public static final String PASSWORD = "fixture-password-only";
    private AuthHttpFixture() { }
    public static String token(String origin) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create(origin + "/api/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"" + USER + "\",\"password\":\"" + PASSWORD + "\"}")).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("Fixture authentication failed: " + response.statusCode());
            return JsonMapper.builder().build().readTree(response.body()).get("accessToken").asString();
        }
    }
}
