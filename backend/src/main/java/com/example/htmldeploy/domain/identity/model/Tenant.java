package com.example.htmldeploy.domain.identity.model;

import java.time.Instant;

public final class Tenant {

    private final TenantId id;
    private final String name;
    private final TenantSlug slug;
    private final Instant createdAt;

    private Tenant(TenantId id, String name, TenantSlug slug, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.createdAt = createdAt;
    }

    public static Tenant create(String name, TenantSlug slug) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("tenant name must not be blank");
        }
        return new Tenant(TenantId.newId(), name.trim(), slug, Instant.now());
    }

    public static Tenant reconstitute(TenantId id, String name, TenantSlug slug, Instant createdAt) {
        return new Tenant(id, name, slug, createdAt);
    }

    public TenantId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public TenantSlug slug() {
        return slug;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
