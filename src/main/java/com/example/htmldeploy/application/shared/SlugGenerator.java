package com.example.htmldeploy.application.shared;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

public final class SlugGenerator {

    private SlugGenerator() {
    }

    public static String from(String value, String fallbackPrefix) {
        if (value == null || value.isBlank()) {
            return fallbackPrefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "")
                .replaceAll("-{2,}", "-");
        if (normalized.length() < 3) {
            return fallbackPrefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        }
        return normalized.length() > 40 ? normalized.substring(0, 40).replaceAll("-+$", "") : normalized;
    }
}
