package com.example.htmldeploy.domain.artifact.model;

import java.time.Instant;

import com.example.htmldeploy.domain.project.model.ProjectId;
import com.example.htmldeploy.domain.shared.DomainException;

public final class Artifact {

    private final ArtifactId id;
    private final ProjectId projectId;
    private final String originalFilename;
    private final String objectKey;
    private ArtifactStatus status;
    private long sizeBytes;
    private Sha256 sha256;
    private ArtifactManifest manifest;
    private String errorCode;
    private String errorMessage;
    private final Instant createdAt;
    private Instant updatedAt;

    private Artifact(
            ArtifactId id,
            ProjectId projectId,
            String originalFilename,
            String objectKey,
            ArtifactStatus status,
            long sizeBytes,
            Sha256 sha256,
            ArtifactManifest manifest,
            String errorCode,
            String errorMessage,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.projectId = projectId;
        this.originalFilename = originalFilename;
        this.objectKey = objectKey;
        this.status = status;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.manifest = manifest;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Artifact create(ProjectId projectId, String originalFilename, String objectKey) {
        Instant now = Instant.now();
        return new Artifact(
                ArtifactId.newId(),
                projectId,
                originalFilename,
                objectKey,
                ArtifactStatus.CREATED,
                0,
                null,
                null,
                null,
                null,
                now,
                now
        );
    }

    public static Artifact reconstitute(
            ArtifactId id,
            ProjectId projectId,
            String originalFilename,
            String objectKey,
            ArtifactStatus status,
            long sizeBytes,
            Sha256 sha256,
            ArtifactManifest manifest,
            String errorCode,
            String errorMessage,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new Artifact(
                id,
                projectId,
                originalFilename,
                objectKey,
                status,
                sizeBytes,
                sha256,
                manifest,
                errorCode,
                errorMessage,
                createdAt,
                updatedAt
        );
    }

    public void markUploaded(long sizeBytes) {
        if (status != ArtifactStatus.CREATED && status != ArtifactStatus.UPLOADED) {
            throw new DomainException("artifact.invalid_state", "artifact cannot accept an upload in state " + status);
        }
        if (sizeBytes <= 0) {
            throw new DomainException("artifact.empty", "uploaded artifact is empty");
        }
        this.sizeBytes = sizeBytes;
        this.status = ArtifactStatus.UPLOADED;
        this.updatedAt = Instant.now();
    }

    public void startValidation() {
        if (status == ArtifactStatus.VALIDATING || status == ArtifactStatus.READY) {
            return;
        }
        if (status != ArtifactStatus.UPLOADED) {
            throw new DomainException("artifact.invalid_state", "artifact is not ready for validation");
        }
        this.status = ArtifactStatus.VALIDATING;
        this.updatedAt = Instant.now();
    }

    public void markValidated(Sha256 sha256, ArtifactManifest manifest) {
        if (status == ArtifactStatus.READY) {
            return;
        }
        if (status != ArtifactStatus.VALIDATING) {
            throw new DomainException("artifact.invalid_state", "artifact is not validating");
        }
        this.sha256 = sha256;
        this.manifest = manifest;
        this.status = ArtifactStatus.READY;
        this.errorCode = null;
        this.errorMessage = null;
        this.updatedAt = Instant.now();
    }

    public void reject(String errorCode, String errorMessage) {
        this.status = ArtifactStatus.REJECTED;
        this.errorCode = errorCode;
        this.errorMessage = truncate(errorMessage);
        this.updatedAt = Instant.now();
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    public ArtifactId id() {
        return id;
    }

    public ProjectId projectId() {
        return projectId;
    }

    public String originalFilename() {
        return originalFilename;
    }

    public String objectKey() {
        return objectKey;
    }

    public ArtifactStatus status() {
        return status;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public Sha256 sha256() {
        return sha256;
    }

    public ArtifactManifest manifest() {
        return manifest;
    }

    public String errorCode() {
        return errorCode;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
