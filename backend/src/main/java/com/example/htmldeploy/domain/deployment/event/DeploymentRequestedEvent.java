package com.example.htmldeploy.domain.deployment.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.example.htmldeploy.domain.deployment.model.Deployment;
import com.example.htmldeploy.domain.shared.DomainEvent;

public record DeploymentRequestedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID aggregateId,
        UUID tenantId,
        UUID projectId,
        UUID artifactId,
        String environment,
        long version
) implements DomainEvent {

    public static DeploymentRequestedEvent from(Deployment deployment, UUID tenantId) {
        return new DeploymentRequestedEvent(
                UUID.randomUUID(),
                Instant.now(),
                deployment.id().value(),
                tenantId,
                deployment.projectId().value(),
                deployment.artifactId().value(),
                deployment.environment(),
                deployment.version()
        );
    }

    @Override
    public String eventType() {
        return "DeploymentRequested";
    }

    @Override
    public String aggregateType() {
        return "Deployment";
    }

    @Override
    public Map<String, Object> payload() {
        return Map.of(
                "artifactId", artifactId,
                "environment", environment,
                "version", version
        );
    }
}
