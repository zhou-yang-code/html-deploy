package com.example.htmldeploy.domain.deployment.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.example.htmldeploy.domain.deployment.model.Deployment;
import com.example.htmldeploy.domain.shared.DomainEvent;

public record DeploymentFailedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID aggregateId,
        UUID tenantId,
        UUID projectId,
        String errorCode
) implements DomainEvent {

    public static DeploymentFailedEvent from(Deployment deployment, UUID tenantId) {
        return new DeploymentFailedEvent(
                UUID.randomUUID(),
                Instant.now(),
                deployment.id().value(),
                tenantId,
                deployment.projectId().value(),
                deployment.errorCode()
        );
    }

    @Override
    public String eventType() {
        return "DeploymentFailed";
    }

    @Override
    public String aggregateType() {
        return "Deployment";
    }

    @Override
    public Map<String, Object> payload() {
        return Map.of("errorCode", errorCode);
    }
}
