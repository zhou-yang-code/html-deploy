package com.example.htmldeploy.domain.artifact.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.example.htmldeploy.domain.artifact.model.Artifact;
import com.example.htmldeploy.domain.shared.DomainEvent;

public record ArtifactUploadedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID aggregateId,
        UUID tenantId,
        UUID projectId,
        String objectKey
) implements DomainEvent {

    public static ArtifactUploadedEvent from(Artifact artifact, UUID tenantId) {
        return new ArtifactUploadedEvent(
                UUID.randomUUID(),
                Instant.now(),
                artifact.id().value(),
                tenantId,
                artifact.projectId().value(),
                artifact.objectKey()
        );
    }

    @Override
    public String eventType() {
        return "ArtifactUploaded";
    }

    @Override
    public String aggregateType() {
        return "Artifact";
    }

    @Override
    public Map<String, Object> payload() {
        return Map.of("objectKey", objectKey);
    }
}
