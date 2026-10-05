package com.example.htmldeploy.domain.deployment.model;

import java.util.Objects;
import java.util.UUID;

public record DeploymentId(UUID value) {

    public DeploymentId {
        Objects.requireNonNull(value, "deploymentId must not be null");
    }

    public static DeploymentId newId() {
        return new DeploymentId(UUID.randomUUID());
    }
}
