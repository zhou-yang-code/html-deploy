package com.example.htmldeploy.application.project;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.htmldeploy.application.identity.IdentityApplicationService;
import com.example.htmldeploy.application.identity.IdentityApplicationService.TenantSummary;
import com.example.htmldeploy.application.port.SiteUrlResolver;
import com.example.htmldeploy.application.project.port.ProjectDeletionPort;
import com.example.htmldeploy.application.project.port.ProjectResourceCleaner;
import com.example.htmldeploy.application.shared.SlugGenerator;
import com.example.htmldeploy.domain.identity.model.Role;
import com.example.htmldeploy.domain.identity.model.TenantId;
import com.example.htmldeploy.domain.identity.model.UserId;
import com.example.htmldeploy.domain.project.model.Project;
import com.example.htmldeploy.domain.project.model.ProjectId;
import com.example.htmldeploy.domain.project.model.ProjectSlug;
import com.example.htmldeploy.domain.project.model.ProjectStatus;
import com.example.htmldeploy.domain.project.repository.ProjectRepository;
import com.example.htmldeploy.domain.shared.DomainException;

@Service
public class ProjectApplicationService {

    private final ProjectRepository projects;
    private final IdentityApplicationService identity;
    private final SiteUrlResolver siteUrls;
    private final ProjectDeletionPort projectDeletion;
    private final ProjectResourceCleaner resourceCleaner;

    public ProjectApplicationService(
            ProjectRepository projects,
            IdentityApplicationService identity,
            SiteUrlResolver siteUrls,
            ProjectDeletionPort projectDeletion,
            ProjectResourceCleaner resourceCleaner
    ) {
        this.projects = projects;
        this.identity = identity;
        this.siteUrls = siteUrls;
        this.projectDeletion = projectDeletion;
        this.resourceCleaner = resourceCleaner;
    }

    @Transactional
    public ProjectSummary create(CreateProjectCommand command) {
        identity.requireTenantRole(command.actorId(), command.tenantId(), Role.OWNER, Role.MAINTAINER);
        String rawSlug = command.slug() == null || command.slug().isBlank()
                ? SlugGenerator.from(command.name(), "project")
                : command.slug();
        ProjectSlug slug = new ProjectSlug(trimTo(rawSlug, 50));
        if (projects.existsByTenantIdAndSlug(command.tenantId(), slug)) {
            throw new DomainException("project.slug_exists", "project slug is already in use");
        }
        Project project = projects.save(Project.create(command.tenantId(), command.name(), slug));
        return summary(command.actorId(), project);
    }

    @Transactional(readOnly = true)
    public List<ProjectSummary> list(UserId actorId, TenantId tenantId) {
        identity.requireTenantRole(actorId, tenantId, Role.VIEWER, Role.DEVELOPER, Role.MAINTAINER);
        return projects.findByTenantId(tenantId).stream()
                .filter(project -> project.status() == com.example.htmldeploy.domain.project.model.ProjectStatus.ACTIVE)
                .map(project -> summary(actorId, project))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectSummary get(UserId actorId, ProjectId projectId) {
        return summary(
                actorId,
                requireProject(actorId, projectId, Role.VIEWER, Role.DEVELOPER, Role.MAINTAINER).project()
        );
    }

    @Transactional
    public ProjectSummary archive(UserId actorId, ProjectId projectId) {
        Project project = projects.findById(projectId)
                .orElseThrow(() -> new DomainException("project.not_found", "project not found"));
        identity.requireTenantRole(actorId, project.tenantId(), Role.OWNER);
        project.archive();
        projects.save(project);
        return summary(actorId, project);
    }

    @Transactional
    public void delete(UserId actorId, ProjectId projectId) {
        Project project = projects.findById(projectId)
                .orElseThrow(() -> new DomainException("project.not_found", "project not found"));
        identity.requireTenantRole(actorId, project.tenantId(), Role.OWNER);
        TenantSummary tenant = identity.tenantSummary(actorId, project.tenantId());
        resourceCleaner.deleteExternalResources(
                tenant.slug(),
                project.slug().value(),
                project.id().value()
        );
        projectDeletion.deletePlatformData(projectId);
    }

    @Transactional(readOnly = true)
    public ProjectContext requireProject(UserId actorId, ProjectId projectId, Role... roles) {
        Project project = projects.findById(projectId)
                .orElseThrow(() -> new DomainException("project.not_found", "project not found"));
        identity.requireTenantRole(actorId, project.tenantId(), roles);
        TenantSummary tenant = identity.tenantSummary(actorId, project.tenantId());
        return new ProjectContext(project, tenant);
    }

    @Transactional(readOnly = true)
    public ProjectContext requireProjectForWorker(ProjectId projectId) {
        Project project = projects.findById(projectId)
                .orElseThrow(() -> new DomainException("project.not_found", "project not found"));
        TenantSummary tenant = identity.tenantSummaryForWorker(project.tenantId());
        return new ProjectContext(project, tenant);
    }

    private ProjectSummary summary(UserId actorId, Project project) {
        TenantSummary tenant = identity.tenantSummary(actorId, project.tenantId());
        return new ProjectSummary(
                project.id().value(),
                project.tenantId().value(),
                tenant.slug(),
                project.name(),
                project.slug().value(),
                project.status(),
                siteUrls.resolve(tenant.slug(), project.slug().value()),
                project.createdAt(),
                project.updatedAt()
        );
    }

    private String trimTo(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length).replaceAll("-+$", "");
    }

    public record CreateProjectCommand(
            UserId actorId,
            TenantId tenantId,
            String name,
            String slug
    ) {
    }

    public record ProjectSummary(
            java.util.UUID id,
            java.util.UUID tenantId,
            String tenantSlug,
            String name,
            String slug,
            ProjectStatus status,
            String deploymentUrl,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record ProjectContext(Project project, TenantSummary tenant) {
    }
}
