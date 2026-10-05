package com.example.htmldeploy.domain.deployment.model;

import java.time.Instant;

import com.example.htmldeploy.domain.artifact.model.ArtifactId;
import com.example.htmldeploy.domain.project.model.ProjectId;
import com.example.htmldeploy.domain.shared.DomainException;

public final class Deployment {

    private final DeploymentId id;
    private final ProjectId projectId;
    private final String environment;
    private final ArtifactId artifactId;
    private final long version;
    private DeploymentStatus status;
    private String releasePath;
    private String errorCode;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant finishedAt;

    private Deployment(
            DeploymentId id,
            ProjectId projectId,
            String environment,
            ArtifactId artifactId,
            long version,
            DeploymentStatus status,
            String releasePath,
            String errorCode,
            Instant createdAt,
            Instant updatedAt,
            Instant finishedAt
    ) {
        this.id = id;
        this.projectId = projectId;
        this.environment = environment;
        this.artifactId = artifactId;
        this.version = version;
        this.status = status;
        this.releasePath = releasePath;
        this.errorCode = errorCode;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.finishedAt = finishedAt;
    }

    public static Deployment create(ProjectId projectId, ArtifactId artifactId, String environment, long version) {
        if (environment == null || environment.isBlank()) {
            throw new IllegalArgumentException("environment must not be blank");
        }
        Instant now = Instant.now();
        return new Deployment(
                DeploymentId.newId(),
                projectId,
                environment.trim(),
                artifactId,
                version,
                DeploymentStatus.CREATED,
                null,
                null,
                now,
                now,
                null
        );
    }

    public static Deployment reconstitute(
            DeploymentId id,
            ProjectId projectId,
            String environment,
            ArtifactId artifactId,
            long version,
            DeploymentStatus status,
            String releasePath,
            String errorCode,
            Instant createdAt,
            Instant updatedAt,
            Instant finishedAt
    ) {
        return new Deployment(
                id,
                projectId,
                environment,
                artifactId,
                version,
                status,
                releasePath,
                errorCode,
                createdAt,
                updatedAt,
                finishedAt
        );
    }

    public void begin() {
        if (status == DeploymentStatus.ACTIVE || status == DeploymentStatus.SUPERSEDED) {
            return;
        }
        if (status == DeploymentStatus.DEPLOYING) {
            return;
        }
        if (status != DeploymentStatus.CREATED && status != DeploymentStatus.FAILED) {
            throw new DomainException("deployment.invalid_state", "deployment cannot start in state " + status);
        }
        this.status = DeploymentStatus.DEPLOYING;
        this.errorCode = null;
        this.updatedAt = Instant.now();
    }

    public void activate(String releasePath) {
        if (status == DeploymentStatus.ACTIVE) {
            return;
        }
        if (status != DeploymentStatus.DEPLOYING) {
            throw new DomainException("deployment.invalid_state", "deployment is not deploying");
        }
        this.releasePath = releasePath;
        this.status = DeploymentStatus.ACTIVE;
        this.finishedAt = Instant.now();
        this.updatedAt = this.finishedAt;
    }

    public void fail(String errorCode) {
        this.status = DeploymentStatus.FAILED;
        this.errorCode = errorCode;
        this.finishedAt = Instant.now();
        this.updatedAt = this.finishedAt;
    }

    public void supersede() {
        if (status == DeploymentStatus.SUPERSEDED) {
            return;
        }
        if (status != DeploymentStatus.ACTIVE) {
            throw new DomainException("deployment.invalid_state", "only active deployment can be superseded");
        }
        this.status = DeploymentStatus.SUPERSEDED;
        this.updatedAt = Instant.now();
    }

    public DeploymentId id() {
        return id;
    }

    public ProjectId projectId() {
        return projectId;
    }

    public String environment() {
        return environment;
    }

    public ArtifactId artifactId() {
        return artifactId;
    }

    public long version() {
        return version;
    }

    public DeploymentStatus status() {
        return status;
    }

    public String releasePath() {
        return releasePath;
    }

    public String errorCode() {
        return errorCode;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant finishedAt() {
        return finishedAt;
    }
}
