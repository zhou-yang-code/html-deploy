package com.example.htmldeploy.infrastructure.persistence.identity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.example.htmldeploy.domain.identity.model.Role;
import com.example.htmldeploy.domain.identity.model.TenantId;
import com.example.htmldeploy.domain.identity.model.TenantMember;
import com.example.htmldeploy.domain.identity.model.UserId;
import com.example.htmldeploy.domain.identity.repository.MembershipRepository;
import com.example.htmldeploy.infrastructure.persistence.JdbcTime;

@Repository
public class JdbcMembershipRepository implements MembershipRepository {

    private final JdbcClient jdbc;

    public JdbcMembershipRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public TenantMember save(TenantMember member) {
        int updated = jdbc.sql("""
                        update tenant_member
                           set role = :role
                         where tenant_id = :tenantId
                           and user_id = :userId
                        """)
                .param("tenantId", member.tenantId().value())
                .param("userId", member.userId().value())
                .param("role", member.role().name())
                .update();
        if (updated == 0) {
            jdbc.sql("""
                            insert into tenant_member (tenant_id, user_id, role, created_at)
                            values (:tenantId, :userId, :role, :createdAt)
                            """)
                    .param("tenantId", member.tenantId().value())
                    .param("userId", member.userId().value())
                    .param("role", member.role().name())
                    .param("createdAt", JdbcTime.toOffsetDateTime(member.createdAt()))
                    .update();
        }
        return member;
    }

    @Override
    public Optional<TenantMember> findByTenantIdAndUserId(TenantId tenantId, UserId userId) {
        return jdbc.sql("""
                        select tenant_id, user_id, role, created_at
                          from tenant_member
                         where tenant_id = :tenantId
                           and user_id = :userId
                        """)
                .param("tenantId", tenantId.value())
                .param("userId", userId.value())
                .query(this::map)
                .optional();
    }

    @Override
    public List<TenantMember> findByTenantId(TenantId tenantId) {
        return jdbc.sql("""
                        select tenant_id, user_id, role, created_at
                          from tenant_member
                         where tenant_id = :tenantId
                         order by created_at
                        """)
                .param("tenantId", tenantId.value())
                .query(this::map)
                .list();
    }

    @Override
    public List<TenantMember> findByUserId(UserId userId) {
        return jdbc.sql("""
                        select tenant_id, user_id, role, created_at
                          from tenant_member
                         where user_id = :userId
                         order by created_at
                        """)
                .param("userId", userId.value())
                .query(this::map)
                .list();
    }

    @Override
    public long countOwners(TenantId tenantId) {
        Long count = jdbc.sql("""
                        select count(*)
                          from tenant_member
                         where tenant_id = :tenantId
                           and role = 'OWNER'
                        """)
                .param("tenantId", tenantId.value())
                .query(Long.class)
                .single();
        return count == null ? 0 : count;
    }

    @Override
    public boolean hasAnyRole(TenantId tenantId, UserId userId, Role... roles) {
        if (roles == null || roles.length == 0) {
            return false;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(roles.length, "?"));
        List<Object> params = new java.util.ArrayList<>();
        params.add(tenantId.value());
        params.add(userId.value());
        Arrays.stream(roles).map(Enum::name).forEach(params::add);
        Long count = jdbc.sql("""
                        select count(*)
                          from tenant_member
                         where tenant_id = ?
                           and user_id = ?
                           and role in (%s)
                        """.formatted(placeholders))
                .params(params)
                .query(Long.class)
                .single();
        return count != null && count > 0;
    }

    private TenantMember map(ResultSet rs, int rowNum) throws SQLException {
        return new TenantMember(
                new TenantId(JdbcUserAccountRepository.uuid(rs, "tenant_id")),
                new UserId(JdbcUserAccountRepository.uuid(rs, "user_id")),
                Role.valueOf(rs.getString("role")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant()
        );
    }
}
