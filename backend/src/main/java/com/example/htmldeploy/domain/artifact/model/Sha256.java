package com.example.htmldeploy.domain.artifact.model;

import java.util.Objects;
import java.util.regex.Pattern;

public record Sha256(String value) {

    private static final Pattern HEX_PATTERN = Pattern.compile("^[0-9a-f]{64}$");

    public Sha256 {
        Objects.requireNonNull(value, "sha256 must not be null");
        if (!HEX_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("sha256 must be 64 lowercase hex characters");
        }
    }
}
