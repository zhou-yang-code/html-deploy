package com.example.htmldeploy.domain.identity.model;

import java.time.Instant;

public record TenantMember(
        TenantId tenantId,
        UserId userId,
        Role role,
        Instant createdAt
) {
    public static TenantMember create(TenantId tenantId, UserId userId, Role role) {
        return new TenantMember(tenantId, userId, role, Instant.now());
    }
}
