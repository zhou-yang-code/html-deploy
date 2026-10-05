package com.example.htmldeploy.infrastructure.config;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String publicBaseUrl,
        String contentScheme,
        String contentDomain,
        Path storageRoot,
        Path nginxRoot,
        String uploadTokenSecret,
        Duration uploadUrlTtl,
        ArchiveLimits archiveLimits
) {
    public AppProperties {
        publicBaseUrl = defaultIfBlank(publicBaseUrl, "http://localhost:8080");
        contentScheme = defaultIfBlank(contentScheme, "http");
        contentDomain = defaultIfBlank(contentDomain, "apps.localhost:8081");
        storageRoot = storageRoot == null ? Path.of("./data") : storageRoot;
        nginxRoot = nginxRoot == null ? Path.of("./data/www") : nginxRoot;
        uploadTokenSecret = defaultIfBlank(uploadTokenSecret, "change-me-in-production-change-me");
        uploadUrlTtl = uploadUrlTtl == null ? Duration.ofMinutes(15) : uploadUrlTtl;
        archiveLimits = archiveLimits == null
                ? new ArchiveLimits(100L * 1024 * 1024, 500L * 1024 * 1024, 10_000, 512)
                : archiveLimits;
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record ArchiveLimits(
            long maxZipBytes,
            long maxExpandedBytes,
            int maxFiles,
            int maxPathLength
    ) {
    }
}
