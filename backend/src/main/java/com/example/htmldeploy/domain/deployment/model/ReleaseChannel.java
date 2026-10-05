package com.example.htmldeploy.domain.deployment.model;

import java.time.Instant;

import com.example.htmldeploy.domain.project.model.ProjectId;

public final class ReleaseChannel {

    private final ChannelId id;
    private final ProjectId projectId;
    private final String environment;
    private DeploymentId activeDeploymentId;
    private Instant updatedAt;

    private ReleaseChannel(
            ChannelId id,
            ProjectId projectId,
            String environment,
            DeploymentId activeDeploymentId,
            Instant updatedAt
    ) {
        this.id = id;
        this.projectId = projectId;
        this.environment = environment;
        this.activeDeploymentId = activeDeploymentId;
        this.updatedAt = updatedAt;
    }

    public static ReleaseChannel create(ProjectId projectId, String environment) {
        return new ReleaseChannel(ChannelId.newId(), projectId, environment, null, Instant.now());
    }

    public static ReleaseChannel reconstitute(
            ChannelId id,
            ProjectId projectId,
            String environment,
            DeploymentId activeDeploymentId,
            Instant updatedAt
    ) {
        return new ReleaseChannel(id, projectId, environment, activeDeploymentId, updatedAt);
    }

    public void activate(DeploymentId deploymentId) {
        this.activeDeploymentId = deploymentId;
        this.updatedAt = Instant.now();
    }

    public ChannelId id() {
        return id;
    }

    public ProjectId projectId() {
        return projectId;
    }

    public String environment() {
        return environment;
    }

    public DeploymentId activeDeploymentId() {
        return activeDeploymentId;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
