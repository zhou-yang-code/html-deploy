package com.example.htmldeploy.interfaces.rest.deployment;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.htmldeploy.application.deployment.DeploymentApplicationService;
import com.example.htmldeploy.application.deployment.DeploymentApplicationService.CreateDeploymentCommand;
import com.example.htmldeploy.application.deployment.DeploymentApplicationService.DeploymentDetails;
import com.example.htmldeploy.application.deployment.DeploymentApplicationService.RollbackCommand;
import com.example.htmldeploy.domain.artifact.model.ArtifactId;
import com.example.htmldeploy.domain.deployment.model.DeploymentId;
import com.example.htmldeploy.domain.project.model.ProjectId;
import com.example.htmldeploy.interfaces.rest.security.CurrentUser;

@RestController
@RequestMapping("/api/v1")
public class DeploymentController {

    private final DeploymentApplicationService deployments;

    public DeploymentController(DeploymentApplicationService deployments) {
        this.deployments = deployments;
    }

    @PostMapping("/projects/{projectId}/deployments")
    DeploymentDetails create(
            Authentication authentication,
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateDeploymentRequest request
    ) {
        return deployments.create(new CreateDeploymentCommand(
                CurrentUser.id(authentication),
                new ProjectId(projectId),
                new ArtifactId(request.artifactId()),
                request.environment()
        ));
    }

    @GetMapping("/projects/{projectId}/deployments")
    List<DeploymentDetails> list(Authentication authentication, @PathVariable UUID projectId) {
        return deployments.list(CurrentUser.id(authentication), new ProjectId(projectId));
    }

    @GetMapping("/deployments/{deploymentId}")
    DeploymentDetails get(Authentication authentication, @PathVariable UUID deploymentId) {
        return deployments.get(CurrentUser.id(authentication), new DeploymentId(deploymentId));
    }

    @PostMapping("/deployments/{deploymentId}/rollback")
    DeploymentDetails rollback(Authentication authentication, @PathVariable UUID deploymentId) {
        return deployments.rollback(new RollbackCommand(
                CurrentUser.id(authentication),
                new DeploymentId(deploymentId)
        ));
    }

    public record CreateDeploymentRequest(
            @NotNull UUID artifactId,
            @NotBlank @Size(max = 64) String environment
    ) {
    }
}
