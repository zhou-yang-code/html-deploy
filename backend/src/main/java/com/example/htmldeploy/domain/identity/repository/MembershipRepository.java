package com.example.htmldeploy.domain.identity.repository;

import java.util.List;
import java.util.Optional;

import com.example.htmldeploy.domain.identity.model.Role;
import com.example.htmldeploy.domain.identity.model.TenantId;
import com.example.htmldeploy.domain.identity.model.TenantMember;
import com.example.htmldeploy.domain.identity.model.UserId;

public interface MembershipRepository {

    TenantMember save(TenantMember member);

    Optional<TenantMember> findByTenantIdAndUserId(TenantId tenantId, UserId userId);

    List<TenantMember> findByTenantId(TenantId tenantId);

    List<TenantMember> findByUserId(UserId userId);

    long countOwners(TenantId tenantId);

    boolean hasAnyRole(TenantId tenantId, UserId userId, Role... roles);
}
