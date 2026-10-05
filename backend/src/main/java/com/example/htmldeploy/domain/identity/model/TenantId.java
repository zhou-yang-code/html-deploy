package com.example.htmldeploy.domain.identity.model;

import java.util.Objects;
import java.util.UUID;

public record TenantId(UUID value) {

    public TenantId {
        Objects.requireNonNull(value, "tenantId must not be null");
    }

    public static TenantId newId() {
        return new TenantId(UUID.randomUUID());
    }
}
