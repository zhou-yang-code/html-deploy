package com.example.htmldeploy.infrastructure.persistence.artifact;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.example.htmldeploy.domain.artifact.model.Artifact;
import com.example.htmldeploy.domain.artifact.model.ArtifactId;
import com.example.htmldeploy.domain.artifact.model.ArtifactManifest;
import com.example.htmldeploy.domain.artifact.model.ArtifactStatus;
import com.example.htmldeploy.domain.artifact.model.Sha256;
import com.example.htmldeploy.domain.artifact.repository.ArtifactRepository;
import com.example.htmldeploy.domain.project.model.ProjectId;
import com.example.htmldeploy.infrastructure.persistence.identity.JdbcUserAccountRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
public class JdbcArtifactRepository implements ArtifactRepository {

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public JdbcArtifactRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public Artifact save(Artifact artifact) {
        String manifestJson = writeManifest(artifact.manifest());
        int updated = jdbc.sql("""
                        update artifact
                           set status = :status,
                               size_bytes = :sizeBytes,
                               sha256 = :sha256,
                               manifest_json = :manifestJson,
                               error_code = :errorCode,
                               updated_at = :updatedAt
                         where id = :id
                        """)
                .param("id", artifact.id().value())
                .param("status", artifact.status().name())
                .param("sizeBytes", artifact.sizeBytes())
                .param("sha256", artifact.sha256() == null ? null : artifact.sha256().value())
                .param("manifestJson", manifestJson)
                .param("errorCode", artifact.errorCode())
                .param("updatedAt", artifact.updatedAt())
                .update();
        if (updated == 0) {
            jdbc.sql("""
                            insert into artifact (
                                id, project_id, original_filename, object_key, status,
                                size_bytes, sha256, manifest_json, error_code, created_at, updated_at
                            )
                            values (
                                :id, :projectId, :originalFilename, :objectKey, :status,
                                :sizeBytes, :sha256, :manifestJson, :errorCode, :createdAt, :updatedAt
                            )
                            """)
                    .param("id", artifact.id().value())
                    .param("projectId", artifact.projectId().value())
                    .param("originalFilename", artifact.originalFilename())
                    .param("objectKey", artifact.objectKey())
                    .param("status", artifact.status().name())
                    .param("sizeBytes", artifact.sizeBytes())
                    .param("sha256", artifact.sha256() == null ? null : artifact.sha256().value())
                    .param("manifestJson", manifestJson)
                    .param("errorCode", artifact.errorCode())
                    .param("createdAt", artifact.createdAt())
                    .param("updatedAt", artifact.updatedAt())
                    .update();
        }
        return artifact;
    }

    @Override
    public Optional<Artifact> findById(ArtifactId id) {
        return jdbc.sql("""
                        select id, project_id, original_filename, object_key, status,
                               size_bytes, sha256, manifest_json, error_code, created_at, updated_at
                          from artifact
                         where id = :id
                        """)
                .param("id", id.value())
                .query(this::map)
                .optional();
    }

    @Override
    public Optional<Artifact> findByProjectIdAndSha256(ProjectId projectId, Sha256 sha256) {
        return jdbc.sql("""
                        select id, project_id, original_filename, object_key, status,
                               size_bytes, sha256, manifest_json, error_code, created_at, updated_at
                          from artifact
                         where project_id = :projectId
                           and sha256 = :sha256
                        """)
                .param("projectId", projectId.value())
                .param("sha256", sha256.value())
                .query(this::map)
                .optional();
    }

    private Artifact map(ResultSet rs, int rowNum) throws SQLException {
        String sha = rs.getString("sha256");
        String manifestJson = rs.getString("manifest_json");
        return Artifact.reconstitute(
                new ArtifactId(JdbcUserAccountRepository.uuid(rs, "id")),
                new ProjectId(JdbcUserAccountRepository.uuid(rs, "project_id")),
                rs.getString("original_filename"),
                rs.getString("object_key"),
                ArtifactStatus.valueOf(rs.getString("status")),
                rs.getLong("size_bytes"),
                sha == null ? null : new Sha256(sha),
                readManifest(manifestJson),
                rs.getString("error_code"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant()
        );
    }

    private String writeManifest(ArtifactManifest manifest) {
        if (manifest == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(manifest);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize artifact manifest", exception);
        }
    }

    private ArtifactManifest readManifest(String manifestJson) {
        if (manifestJson == null || manifestJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(manifestJson, ArtifactManifest.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to deserialize artifact manifest", exception);
        }
    }
}
