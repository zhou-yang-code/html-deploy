package com.example.htmldeploy.infrastructure.persistence.identity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.example.htmldeploy.domain.identity.model.Tenant;
import com.example.htmldeploy.domain.identity.model.TenantId;
import com.example.htmldeploy.domain.identity.model.TenantSlug;
import com.example.htmldeploy.domain.identity.repository.TenantRepository;
import com.example.htmldeploy.infrastructure.persistence.JdbcTime;

@Repository
public class JdbcTenantRepository implements TenantRepository {

    private final JdbcClient jdbc;

    public JdbcTenantRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Tenant save(Tenant tenant) {
        int updated = jdbc.sql("""
                        update tenant
                           set name = :name,
                               slug = :slug
                         where id = :id
                        """)
                .param("id", tenant.id().value())
                .param("name", tenant.name())
                .param("slug", tenant.slug().value())
                .update();
        if (updated == 0) {
            jdbc.sql("""
                            insert into tenant (id, name, slug, created_at)
                            values (:id, :name, :slug, :createdAt)
                            """)
                    .param("id", tenant.id().value())
                    .param("name", tenant.name())
                    .param("slug", tenant.slug().value())
                    .param("createdAt", JdbcTime.toOffsetDateTime(tenant.createdAt()))
                    .update();
        }
        return tenant;
    }

    @Override
    public Optional<Tenant> findById(TenantId id) {
        return jdbc.sql("""
                        select id, name, slug, created_at
                          from tenant
                         where id = :id
                        """)
                .param("id", id.value())
                .query(this::map)
                .optional();
    }

    @Override
    public boolean existsBySlug(TenantSlug slug) {
        Long count = jdbc.sql("select count(*) from tenant where slug = :slug")
                .param("slug", slug.value())
                .query(Long.class)
                .single();
        return count != null && count > 0;
    }

    private Tenant map(ResultSet rs, int rowNum) throws SQLException {
        return Tenant.reconstitute(
                new TenantId(JdbcUserAccountRepository.uuid(rs, "id")),
                rs.getString("name"),
                new TenantSlug(rs.getString("slug")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant()
        );
    }
}
