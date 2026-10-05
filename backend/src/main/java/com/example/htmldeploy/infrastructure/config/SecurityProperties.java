package com.example.htmldeploy.infrastructure.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        String jwtSecret,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        String issuer,
        List<String> allowedOrigins
) {
    public SecurityProperties {
        jwtSecret = jwtSecret == null
                ? "local-development-secret-key-must-be-at-least-32-bytes"
                : jwtSecret;
        accessTokenTtl = accessTokenTtl == null ? Duration.ofHours(12) : accessTokenTtl;
        refreshTokenTtl = refreshTokenTtl == null ? Duration.ofDays(7) : refreshTokenTtl;
        issuer = issuer == null || issuer.isBlank() ? "html-deploy" : issuer;
        allowedOrigins = allowedOrigins == null || allowedOrigins.isEmpty()
                ? List.of("http://localhost:5173", "http://127.0.0.1:5173")
                : List.copyOf(allowedOrigins);
    }
}
