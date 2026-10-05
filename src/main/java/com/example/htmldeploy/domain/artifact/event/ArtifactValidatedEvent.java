package com.example.htmldeploy.domain.artifact.event;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.example.htmldeploy.domain.artifact.model.Artifact;
import com.example.htmldeploy.domain.shared.DomainEvent;

public record ArtifactValidatedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID aggregateId,
        UUID tenantId,
        UUID projectId,
        String sha256,
        int fileCount,
        long totalBytes
) implements DomainEvent {

    public static ArtifactValidatedEvent from(Artifact artifact, UUID tenantId) {
        return new ArtifactValidatedEvent(
                UUID.randomUUID(),
                Instant.now(),
                artifact.id().value(),
                tenantId,
                artifact.projectId().value(),
                artifact.sha256().value(),
                artifact.manifest().fileCount(),
                artifact.manifest().totalBytes()
        );
    }

    @Override
    public String eventType() {
        return "ArtifactValidated";
    }

    @Override
    public String aggregateType() {
        return "Artifact";
    }

    @Override
    public Map<String, Object> payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sha256", sha256);
        payload.put("fileCount", fileCount);
        payload.put("totalBytes", totalBytes);
        return payload;
    }
}
