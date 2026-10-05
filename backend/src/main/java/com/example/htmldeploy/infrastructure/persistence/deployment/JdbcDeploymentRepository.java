package com.example.htmldeploy.infrastructure.persistence.deployment;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.example.htmldeploy.domain.artifact.model.ArtifactId;
import com.example.htmldeploy.domain.deployment.model.Deployment;
import com.example.htmldeploy.domain.deployment.model.DeploymentId;
import com.example.htmldeploy.domain.deployment.model.DeploymentStatus;
import com.example.htmldeploy.domain.deployment.repository.DeploymentRepository;
import com.example.htmldeploy.domain.project.model.ProjectId;
import com.example.htmldeploy.infrastructure.persistence.identity.JdbcUserAccountRepository;
import com.example.htmldeploy.infrastructure.persistence.JdbcTime;

@Repository
public class JdbcDeploymentRepository implements DeploymentRepository {

    private final JdbcClient jdbc;

    public JdbcDeploymentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Deployment save(Deployment deployment) {
        int updated = jdbc.sql("""
                        update deployment
                           set status = :status,
                               release_path = :releasePath,
                               error_code = :errorCode,
                               updated_at = :updatedAt,
                               finished_at = :finishedAt
                         where id = :id
                        """)
                .param("id", deployment.id().value())
                .param("status", deployment.status().name())
                .param("releasePath", deployment.releasePath())
                .param("errorCode", deployment.errorCode())
                .param("updatedAt", JdbcTime.toOffsetDateTime(deployment.updatedAt()))
                .param("finishedAt", JdbcTime.toOffsetDateTime(deployment.finishedAt()))
                .update();
        if (updated == 0) {
            jdbc.sql("""
                            insert into deployment (
                                id, project_id, environment, provider, artifact_id, version, status,
                                release_path, error_code, created_at, updated_at, finished_at
                            )
                            values (
                                :id, :projectId, :environment, :provider, :artifactId, :version, :status,
                                :releasePath, :errorCode, :createdAt, :updatedAt, :finishedAt
                            )
                            """)
                    .param("id", deployment.id().value())
                    .param("projectId", deployment.projectId().value())
                    .param("environment", deployment.environment())
                    .param("provider", deployment.provider())
                    .param("artifactId", deployment.artifactId().value())
                    .param("version", deployment.version())
                    .param("status", deployment.status().name())
                    .param("releasePath", deployment.releasePath())
                    .param("errorCode", deployment.errorCode())
                    .param("createdAt", JdbcTime.toOffsetDateTime(deployment.createdAt()))
                    .param("updatedAt", JdbcTime.toOffsetDateTime(deployment.updatedAt()))
                    .param("finishedAt", JdbcTime.toOffsetDateTime(deployment.finishedAt()))
                    .update();
        }
        return deployment;
    }

    @Override
    public Optional<Deployment> findById(DeploymentId id) {
        return jdbc.sql(baseSelect() + " where id = :id")
                .param("id", id.value())
                .query(this::map)
                .optional();
    }

    @Override
    public List<Deployment> findByProjectId(ProjectId projectId) {
        return jdbc.sql(baseSelect() + " where project_id = :projectId order by version desc")
                .param("projectId", projectId.value())
                .query(this::map)
                .list();
    }

    @Override
    public Optional<Deployment> findActive(ProjectId projectId, String environment) {
        return jdbc.sql(baseSelect() + """
                         where project_id = :projectId
                           and environment = :environment
                           and status = 'ACTIVE'
                         order by version desc
                         limit 1
                        """)
                .param("projectId", projectId.value())
                .param("environment", environment)
                .query(this::map)
                .optional();
    }

    @Override
    public long nextVersion(ProjectId projectId) {
        Long value = jdbc.sql("""
                        select coalesce(max(version), 0) + 1
                          from deployment
                         where project_id = :projectId
                        """)
                .param("projectId", projectId.value())
                .query(Long.class)
                .single();
        return value == null ? 1 : value;
    }

    @Override
    public List<Deployment> findByProjectIdAndStatus(ProjectId projectId, DeploymentStatus status) {
        return jdbc.sql(baseSelect() + """
                         where project_id = :projectId
                           and status = :status
                         order by version desc
                        """)
                .param("projectId", projectId.value())
                .param("status", status.name())
                .query(this::map)
                .list();
    }

    private String baseSelect() {
        return """
                select id, project_id, environment, provider, artifact_id, version, status,
                       release_path, error_code, created_at, updated_at, finished_at
                  from deployment
                """;
    }

    private Deployment map(ResultSet rs, int rowNum) throws SQLException {
        return Deployment.reconstitute(
                new DeploymentId(JdbcUserAccountRepository.uuid(rs, "id")),
                new ProjectId(JdbcUserAccountRepository.uuid(rs, "project_id")),
                rs.getString("environment"),
                rs.getString("provider"),
                new ArtifactId(JdbcUserAccountRepository.uuid(rs, "artifact_id")),
                rs.getLong("version"),
                DeploymentStatus.valueOf(rs.getString("status")),
                rs.getString("release_path"),
                rs.getString("error_code"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant(),
                nullableInstant(rs, "finished_at")
        );
    }

    private Instant nullableInstant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
