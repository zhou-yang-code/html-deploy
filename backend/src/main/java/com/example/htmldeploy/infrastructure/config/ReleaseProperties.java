package com.example.htmldeploy.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.release")
public record ReleaseProperties(
        String provider,
        Duration pollInterval,
        Duration publishTimeout
) {
    public ReleaseProperties {
        provider = provider == null || provider.isBlank() ? "local" : provider.toLowerCase();
        pollInterval = pollInterval == null ? Duration.ofSeconds(2) : pollInterval;
        publishTimeout = publishTimeout == null ? Duration.ofMinutes(5) : publishTimeout;
    }
}
