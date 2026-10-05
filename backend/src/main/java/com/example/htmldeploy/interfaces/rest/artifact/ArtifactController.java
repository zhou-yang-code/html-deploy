package com.example.htmldeploy.interfaces.rest.artifact;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.htmldeploy.application.artifact.ArtifactApplicationService;
import com.example.htmldeploy.application.artifact.ArtifactApplicationService.ArtifactDetails;
import com.example.htmldeploy.application.artifact.ArtifactApplicationService.ArtifactUploadView;
import com.example.htmldeploy.application.artifact.ArtifactApplicationService.CreateUploadCommand;
import com.example.htmldeploy.domain.artifact.model.ArtifactId;
import com.example.htmldeploy.domain.project.model.ProjectId;
import com.example.htmldeploy.interfaces.rest.security.CurrentUser;

@RestController
@RequestMapping("/api/v1")
public class ArtifactController {

    private final ArtifactApplicationService artifacts;

    public ArtifactController(ArtifactApplicationService artifacts) {
        this.artifacts = artifacts;
    }

    @PostMapping("/projects/{projectId}/artifacts")
    public ArtifactUploadView createUpload(
            Authentication authentication,
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateArtifactRequest request
    ) {
        return artifacts.createUpload(new CreateUploadCommand(
                CurrentUser.id(authentication),
                new ProjectId(projectId),
                request.originalFilename()
        ));
    }

    @PutMapping(
            path = "/artifacts/{artifactId}/content",
            consumes = {
                    MediaType.APPLICATION_OCTET_STREAM_VALUE,
                    "application/zip",
                    "application/x-zip-compressed"
            }
    )
    public ResponseEntity<Void> uploadContent(
            @PathVariable UUID artifactId,
            @RequestParam String token,
            HttpServletRequest request
    ) throws java.io.IOException {
        artifacts.storeUploadedContent(token, request.getInputStream());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/artifacts/{artifactId}/complete")
    public ArtifactDetails complete(Authentication authentication, @PathVariable UUID artifactId) {
        return artifacts.completeUpload(CurrentUser.id(authentication), new ArtifactId(artifactId));
    }

    @GetMapping("/artifacts/{artifactId}")
    public ArtifactDetails get(Authentication authentication, @PathVariable UUID artifactId) {
        return artifacts.get(CurrentUser.id(authentication), new ArtifactId(artifactId));
    }

    public record CreateArtifactRequest(
            @NotBlank @Size(max = 255) String originalFilename
    ) {
    }
}
