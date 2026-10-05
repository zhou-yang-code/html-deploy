package com.example.htmldeploy.domain.project.model;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record ProjectSlug(String value) {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9](?:[a-z0-9-]{0,48}[a-z0-9])?$");

    public ProjectSlug {
        Objects.requireNonNull(value, "project slug must not be null");
        value = value.trim().toLowerCase(Locale.ROOT);
        if (!SLUG_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("project slug may contain lowercase letters, numbers and hyphens");
        }
    }
}
