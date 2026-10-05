package com.example.htmldeploy.domain.identity.model;

import java.util.Objects;
import java.util.UUID;

public record UserId(UUID value) {

    public UserId {
        Objects.requireNonNull(value, "userId must not be null");
    }

    public static UserId newId() {
        return new UserId(UUID.randomUUID());
    }
}
