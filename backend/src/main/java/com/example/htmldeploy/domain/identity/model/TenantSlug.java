package com.example.htmldeploy.domain.identity.model;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record TenantSlug(String value) {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9](?:[a-z0-9-]{1,38}[a-z0-9])?$");

    public TenantSlug {
        Objects.requireNonNull(value, "tenant slug must not be null");
        value = value.trim().toLowerCase(Locale.ROOT);
        if (!SLUG_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("tenant slug must contain 3-40 lowercase letters, numbers or hyphens");
        }
    }
}
