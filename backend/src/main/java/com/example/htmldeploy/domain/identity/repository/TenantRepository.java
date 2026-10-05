package com.example.htmldeploy.domain.identity.repository;

import java.util.Optional;

import com.example.htmldeploy.domain.identity.model.Tenant;
import com.example.htmldeploy.domain.identity.model.TenantId;
import com.example.htmldeploy.domain.identity.model.TenantSlug;

public interface TenantRepository {

    Tenant save(Tenant tenant);

    Optional<Tenant> findById(TenantId id);

    boolean existsBySlug(TenantSlug slug);
}
