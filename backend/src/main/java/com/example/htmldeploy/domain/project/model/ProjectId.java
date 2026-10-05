package com.example.htmldeploy.domain.project.model;

import java.util.Objects;
import java.util.UUID;

public record ProjectId(UUID value) {

    public ProjectId {
        Objects.requireNonNull(value, "projectId must not be null");
    }

    public static ProjectId newId() {
        return new ProjectId(UUID.randomUUID());
    }
}
