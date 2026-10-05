package com.example.htmldeploy.infrastructure.persistence.project;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.example.htmldeploy.domain.identity.model.TenantId;
import com.example.htmldeploy.domain.project.model.Project;
import com.example.htmldeploy.domain.project.model.ProjectId;
import com.example.htmldeploy.domain.project.model.ProjectSlug;
import com.example.htmldeploy.domain.project.model.ProjectStatus;
import com.example.htmldeploy.domain.project.repository.ProjectRepository;
import com.example.htmldeploy.infrastructure.persistence.identity.JdbcUserAccountRepository;

@Repository
public class JdbcProjectRepository implements ProjectRepository {

    private final JdbcClient jdbc;

    public JdbcProjectRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Project save(Project project) {
        int updated = jdbc.sql("""
                        update project
                           set name = :name,
                               slug = :slug,
                               status = :status,
                               updated_at = :updatedAt
                         where id = :id
                        """)
                .param("id", project.id().value())
                .param("name", project.name())
                .param("slug", project.slug().value())
                .param("status", project.status().name())
                .param("updatedAt", project.updatedAt())
                .update();
        if (updated == 0) {
            jdbc.sql("""
                            insert into project (id, tenant_id, name, slug, status, created_at, updated_at)
                            values (:id, :tenantId, :name, :slug, :status, :createdAt, :updatedAt)
                            """)
                    .param("id", project.id().value())
                    .param("tenantId", project.tenantId().value())
                    .param("name", project.name())
                    .param("slug", project.slug().value())
                    .param("status", project.status().name())
                    .param("createdAt", project.createdAt())
                    .param("updatedAt", project.updatedAt())
                    .update();
        }
        return project;
    }

    @Override
    public Optional<Project> findById(ProjectId id) {
        return jdbc.sql("""
                        select id, tenant_id, name, slug, status, created_at, updated_at
                          from project
                         where id = :id
                        """)
                .param("id", id.value())
                .query(this::map)
                .optional();
    }

    @Override
    public Optional<Project> findByTenantIdAndSlug(TenantId tenantId, ProjectSlug slug) {
        return jdbc.sql("""
                        select id, tenant_id, name, slug, status, created_at, updated_at
                          from project
                         where tenant_id = :tenantId
                           and slug = :slug
                        """)
                .param("tenantId", tenantId.value())
                .param("slug", slug.value())
                .query(this::map)
                .optional();
    }

    @Override
    public List<Project> findByTenantId(TenantId tenantId) {
        return jdbc.sql("""
                        select id, tenant_id, name, slug, status, created_at, updated_at
                          from project
                         where tenant_id = :tenantId
                         order by created_at desc
                        """)
                .param("tenantId", tenantId.value())
                .query(this::map)
                .list();
    }

    @Override
    public boolean existsByTenantIdAndSlug(TenantId tenantId, ProjectSlug slug) {
        Long count = jdbc.sql("""
                        select count(*)
                          from project
                         where tenant_id = :tenantId
                           and slug = :slug
                        """)
                .param("tenantId", tenantId.value())
                .param("slug", slug.value())
                .query(Long.class)
                .single();
        return count != null && count > 0;
    }

    private Project map(ResultSet rs, int rowNum) throws SQLException {
        return Project.reconstitute(
                new ProjectId(JdbcUserAccountRepository.uuid(rs, "id")),
                new TenantId(JdbcUserAccountRepository.uuid(rs, "tenant_id")),
                rs.getString("name"),
                new ProjectSlug(rs.getString("slug")),
                ProjectStatus.valueOf(rs.getString("status")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant()
        );
    }
}
