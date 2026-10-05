package com.example.htmldeploy.domain.artifact.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.example.htmldeploy.domain.artifact.model.Artifact;
import com.example.htmldeploy.domain.shared.DomainEvent;

public record ArtifactRejectedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID aggregateId,
        UUID tenantId,
        UUID projectId,
        String errorCode
) implements DomainEvent {

    public static ArtifactRejectedEvent from(Artifact artifact, UUID tenantId) {
        return new ArtifactRejectedEvent(
                UUID.randomUUID(),
                Instant.now(),
                artifact.id().value(),
                tenantId,
                artifact.projectId().value(),
                artifact.errorCode()
        );
    }

    @Override
    public String eventType() {
        return "ArtifactRejected";
    }

    @Override
    public String aggregateType() {
        return "Artifact";
    }

    @Override
    public Map<String, Object> payload() {
        return Map.of("errorCode", errorCode);
    }
}
