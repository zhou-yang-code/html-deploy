package com.example.htmldeploy.interfaces.rest.api;

import java.time.Instant;
import java.util.Map;

public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        Map<String, Object> details
) {
    public static ApiError of(int status, String code, String message) {
        return new ApiError(Instant.now(), status, code, message, Map.of());
    }
}
