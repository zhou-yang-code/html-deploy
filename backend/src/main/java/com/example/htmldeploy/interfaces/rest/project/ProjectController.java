package com.example.htmldeploy.interfaces.rest.project;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.htmldeploy.application.project.ProjectApplicationService;
import com.example.htmldeploy.application.project.ProjectApplicationService.CreateProjectCommand;
import com.example.htmldeploy.application.project.ProjectApplicationService.ProjectSummary;
import com.example.htmldeploy.domain.identity.model.TenantId;
import com.example.htmldeploy.domain.project.model.ProjectId;
import com.example.htmldeploy.interfaces.rest.security.CurrentUser;

@RestController
public class ProjectController {

    private final ProjectApplicationService projects;

    public ProjectController(ProjectApplicationService projects) {
        this.projects = projects;
    }

    @GetMapping("/api/v1/tenants/{tenantId}/projects")
    public List<ProjectSummary> list(Authentication authentication, @PathVariable UUID tenantId) {
        return projects.list(CurrentUser.id(authentication), new TenantId(tenantId));
    }

    @PostMapping("/api/v1/tenants/{tenantId}/projects")
    public ProjectSummary create(
            Authentication authentication,
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateProjectRequest request
    ) {
        return projects.create(new CreateProjectCommand(
                CurrentUser.id(authentication),
                new TenantId(tenantId),
                request.name(),
                request.slug()
        ));
    }

    @GetMapping("/api/v1/projects/{projectId}")
    public ProjectSummary get(Authentication authentication, @PathVariable UUID projectId) {
        return projects.get(CurrentUser.id(authentication), new ProjectId(projectId));
    }

    @PostMapping("/api/v1/projects/{projectId}/archive")
    public ProjectSummary archive(Authentication authentication, @PathVariable UUID projectId) {
        return projects.archive(CurrentUser.id(authentication), new ProjectId(projectId));
    }

    @DeleteMapping("/api/v1/projects/{projectId}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable UUID projectId) {
        projects.delete(CurrentUser.id(authentication), new ProjectId(projectId));
        return ResponseEntity.noContent().build();
    }

    public record CreateProjectRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 50) String slug
    ) {
    }
}
