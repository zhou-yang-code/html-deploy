package com.example.htmldeploy.domain.project.repository;

import java.util.List;
import java.util.Optional;

import com.example.htmldeploy.domain.identity.model.TenantId;
import com.example.htmldeploy.domain.project.model.Project;
import com.example.htmldeploy.domain.project.model.ProjectId;
import com.example.htmldeploy.domain.project.model.ProjectSlug;

public interface ProjectRepository {

    Project save(Project project);

    Optional<Project> findById(ProjectId id);

    Optional<Project> findByTenantIdAndSlug(TenantId tenantId, ProjectSlug slug);

    List<Project> findByTenantId(TenantId tenantId);

    boolean existsByTenantIdAndSlug(TenantId tenantId, ProjectSlug slug);
}
