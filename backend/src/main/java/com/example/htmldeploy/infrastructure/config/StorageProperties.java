package com.example.htmldeploy.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        String type,
        String endpoint,
        String publicEndpoint,
        String accessKey,
        String secretKey,
        String bucket,
        String region
) {
    public StorageProperties {
        type = type == null || type.isBlank() ? "local" : type.toLowerCase();
        bucket = bucket == null || bucket.isBlank() ? "html-deploy" : bucket;
        region = region == null || region.isBlank() ? "us-east-1" : region;
    }
}
