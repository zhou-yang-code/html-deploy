package com.example.htmldeploy.application.artifact;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.htmldeploy.application.artifact.port.UploadTokenPort;
import com.example.htmldeploy.application.port.DomainEventPublisher;
import com.example.htmldeploy.application.project.ProjectApplicationService;
import com.example.htmldeploy.application.project.ProjectApplicationService.ProjectContext;
import com.example.htmldeploy.domain.artifact.event.ArtifactRejectedEvent;
import com.example.htmldeploy.domain.artifact.event.ArtifactUploadedEvent;
import com.example.htmldeploy.domain.artifact.event.ArtifactValidatedEvent;
import com.example.htmldeploy.domain.artifact.model.Artifact;
import com.example.htmldeploy.domain.artifact.model.ArtifactId;
import com.example.htmldeploy.domain.artifact.model.ArtifactManifest;
import com.example.htmldeploy.domain.artifact.model.ArtifactStatus;
import com.example.htmldeploy.domain.artifact.model.Sha256;
import com.example.htmldeploy.domain.artifact.port.ArchiveInspector;
import com.example.htmldeploy.domain.artifact.port.ArtifactContentStore;
import com.example.htmldeploy.domain.artifact.port.ArtifactStorage;
import com.example.htmldeploy.domain.artifact.port.UploadTarget;
import com.example.htmldeploy.domain.artifact.repository.ArtifactRepository;
import com.example.htmldeploy.domain.artifact.service.ArchivePolicy;
import com.example.htmldeploy.domain.artifact.service.ArchiveValidationService;
import com.example.htmldeploy.domain.identity.model.Role;
import com.example.htmldeploy.domain.identity.model.UserId;
import com.example.htmldeploy.domain.project.model.ProjectId;
import com.example.htmldeploy.domain.shared.DomainException;

@Service
public class ArtifactApplicationService {

    private final ArtifactRepository artifacts;
    private final ArtifactStorage storage;
    private final ArtifactContentStore contentStore;
    private final ArchiveInspector archiveInspector;
    private final ArchiveValidationService validationService;
    private final ArchivePolicy archivePolicy;
    private final ProjectApplicationService projects;
    private final DomainEventPublisher events;
    private final UploadTokenPort uploadTokens;

    public ArtifactApplicationService(
            ArtifactRepository artifacts,
            ArtifactStorage storage,
            ArtifactContentStore contentStore,
            ArchiveInspector archiveInspector,
            ArchiveValidationService validationService,
            ArchivePolicy archivePolicy,
            ProjectApplicationService projects,
            DomainEventPublisher events,
            UploadTokenPort uploadTokens
    ) {
        this.artifacts = artifacts;
        this.storage = storage;
        this.contentStore = contentStore;
        this.archiveInspector = archiveInspector;
        this.validationService = validationService;
        this.archivePolicy = archivePolicy;
        this.projects = projects;
        this.events = events;
        this.uploadTokens = uploadTokens;
    }

    @Transactional
    public ArtifactUploadView createUpload(CreateUploadCommand command) {
        ProjectContext project = projects.requireProject(
                command.actorId(),
                command.projectId(),
                Role.DEVELOPER,
                Role.MAINTAINER
        );
        String objectKey = "projects/"
                + project.project().id().value()
                + "/artifacts/"
                + UUID.randomUUID()
                + ".zip";
        Artifact artifact = artifacts.save(Artifact.create(
                project.project().id(),
                command.originalFilename(),
                objectKey
        ));
        UploadTarget target = storage.createUploadTarget(artifact, java.time.Duration.ofMinutes(15));
        return new ArtifactUploadView(
                artifact.id().value(),
                artifact.status(),
                target.method(),
                target.url(),
                target.expiresAt()
        );
    }

    @Transactional
    public void storeUploadedContent(String token, InputStream inputStream) {
        UUID artifactId = uploadTokens.verify(token);
        Artifact artifact = findArtifact(new ArtifactId(artifactId));
        if (artifact.status() == ArtifactStatus.READY) {
            return;
        }
        if (artifact.status() != ArtifactStatus.CREATED && artifact.status() != ArtifactStatus.UPLOADED) {
            throw new DomainException("artifact.invalid_state", "artifact cannot accept content in state " + artifact.status());
        }
        storage.store(artifact.objectKey(), inputStream, archivePolicy.maxZipBytes());
    }

    @Transactional
    public ArtifactDetails completeUpload(UserId actorId, ArtifactId artifactId) {
        Artifact artifact = findArtifact(artifactId);
        projects.requireProject(actorId, artifact.projectId(), Role.DEVELOPER, Role.MAINTAINER);
        var object = storage.stat(artifact.objectKey());
        artifact.markUploaded(object.sizeBytes());
        artifacts.save(artifact);
        ProjectContext project = projects.requireProject(
                actorId,
                artifact.projectId(),
                Role.VIEWER,
                Role.DEVELOPER,
                Role.MAINTAINER
        );
        events.publish(ArtifactUploadedEvent.from(artifact, project.project().tenantId().value()));
        return details(artifact);
    }

    @Transactional
    public void validate(ArtifactId artifactId) {
        Artifact artifact = findArtifact(artifactId);
        if (artifact.status() == ArtifactStatus.READY || artifact.status() == ArtifactStatus.REJECTED) {
            return;
        }
        ProjectContext project = projects.requireProjectForWorker(artifact.projectId());
        Path tempDirectory = null;
        try {
            artifact.startValidation();
            artifacts.save(artifact);
            tempDirectory = Files.createTempDirectory("html-deploy-artifact-");
            Path zipFile = storage.downloadToTemp(artifact.objectKey(), tempDirectory);
            Sha256 sha256 = sha256(zipFile);
            Path extractionDirectory = tempDirectory.resolve("extracted");
            var inspection = archiveInspector.inspectAndExtract(zipFile, extractionDirectory, archivePolicy);
            ArtifactManifest manifest = inspection.manifest();
            validationService.validate(manifest, archivePolicy);
            contentStore.storeExtracted(artifact.id(), extractionDirectory);
            artifact.markValidated(sha256, manifest);
            artifacts.save(artifact);
            events.publish(ArtifactValidatedEvent.from(artifact, project.project().tenantId().value()));
        } catch (Exception exception) {
            String errorCode = exception instanceof DomainException domainException
                    ? domainException.code()
                    : "artifact.validation_failed";
            artifact.reject(errorCode);
            artifacts.save(artifact);
            events.publish(ArtifactRejectedEvent.from(artifact, project.project().tenantId().value()));
        } finally {
            deleteRecursively(tempDirectory);
        }
    }

    @Transactional(readOnly = true)
    public ArtifactDetails get(UserId actorId, ArtifactId artifactId) {
        Artifact artifact = findArtifact(artifactId);
        projects.requireProject(
                actorId,
                artifact.projectId(),
                Role.VIEWER,
                Role.DEVELOPER,
                Role.MAINTAINER
        );
        return details(artifact);
    }

    public Artifact requireReady(UserId actorId, ArtifactId artifactId, ProjectId projectId) {
        Artifact artifact = findArtifact(artifactId);
        if (!artifact.projectId().equals(projectId)) {
            throw new DomainException("artifact.project_mismatch", "artifact does not belong to project");
        }
        projects.requireProject(actorId, projectId, Role.DEVELOPER, Role.MAINTAINER);
        if (artifact.status() != ArtifactStatus.READY) {
            throw new DomainException("artifact.not_ready", "artifact is not validated");
        }
        return artifact;
    }

    public Artifact requireReadyForWorker(ArtifactId artifactId) {
        Artifact artifact = findArtifact(artifactId);
        if (artifact.status() != ArtifactStatus.READY) {
            throw new DomainException("artifact.not_ready", "artifact is not validated");
        }
        return artifact;
    }

    private Artifact findArtifact(ArtifactId artifactId) {
        return artifacts.findById(artifactId)
                .orElseThrow(() -> new DomainException("artifact.not_found", "artifact not found"));
    }

    private ArtifactDetails details(Artifact artifact) {
        return new ArtifactDetails(
                artifact.id().value(),
                artifact.projectId().value(),
                artifact.originalFilename(),
                artifact.status(),
                artifact.sizeBytes(),
                artifact.sha256() == null ? null : artifact.sha256().value(),
                artifact.manifest() == null ? null : artifact.manifest().fileCount(),
                artifact.manifest() == null ? null : artifact.manifest().totalBytes(),
                artifact.errorCode(),
                artifact.createdAt(),
                artifact.updatedAt()
        );
    }

    private Sha256 sha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return new Sha256(HexFormat.of().formatHex(digest.digest()));
        } catch (NoSuchAlgorithmException | IOException exception) {
            throw new IllegalStateException("failed to calculate artifact sha256", exception);
        }
    }

    private void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var paths = Files.walk(path)) {
            for (Path item : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(item);
            }
        } catch (IOException ignored) {
            // Temporary cleanup failure should not hide the validation result.
        }
    }

    public record CreateUploadCommand(
            UserId actorId,
            ProjectId projectId,
            String originalFilename
    ) {
    }

    public record ArtifactUploadView(
            UUID artifactId,
            ArtifactStatus status,
            String method,
            String uploadUrl,
            Instant expiresAt
    ) {
    }

    public record ArtifactDetails(
            UUID id,
            UUID projectId,
            String originalFilename,
            ArtifactStatus status,
            long sizeBytes,
            String sha256,
            Integer fileCount,
            Long totalBytes,
            String errorCode,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
