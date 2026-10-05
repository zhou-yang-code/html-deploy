package com.example.htmldeploy.domain.project.model;

import java.time.Instant;

import com.example.htmldeploy.domain.identity.model.TenantId;

public final class Project {

    private final ProjectId id;
    private final TenantId tenantId;
    private final String name;
    private final ProjectSlug slug;
    private ProjectStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private Project(
            ProjectId id,
            TenantId tenantId,
            String name,
            ProjectSlug slug,
            ProjectStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.slug = slug;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Project create(TenantId tenantId, String name, ProjectSlug slug) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("project name must not be blank");
        }
        Instant now = Instant.now();
        return new Project(ProjectId.newId(), tenantId, name.trim(), slug, ProjectStatus.ACTIVE, now, now);
    }

    public static Project reconstitute(
            ProjectId id,
            TenantId tenantId,
            String name,
            ProjectSlug slug,
            ProjectStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new Project(id, tenantId, name, slug, status, createdAt, updatedAt);
    }

    public void archive() {
        this.status = ProjectStatus.ARCHIVED;
        this.updatedAt = Instant.now();
    }

    public void restore() {
        this.status = ProjectStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public ProjectId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public String name() {
        return name;
    }

    public ProjectSlug slug() {
        return slug;
    }

    public ProjectStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
