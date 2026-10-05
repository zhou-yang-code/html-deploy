package com.example.htmldeploy.domain.artifact.model;

import java.util.Objects;
import java.util.UUID;

public record ArtifactId(UUID value) {

    public ArtifactId {
        Objects.requireNonNull(value, "artifactId must not be null");
    }

    public static ArtifactId newId() {
        return new ArtifactId(UUID.randomUUID());
    }
}
