package com.example.htmldeploy.domain.artifact.port;

import java.time.Instant;
import java.util.Map;

public record UploadTarget(
        String method,
        String url,
        Instant expiresAt,
        Map<String, String> headers
) {
    public UploadTarget {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }
}
