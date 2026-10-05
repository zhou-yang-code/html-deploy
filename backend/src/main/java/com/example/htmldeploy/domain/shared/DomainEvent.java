package com.example.htmldeploy.domain.shared;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public interface DomainEvent {

    UUID eventId();

    Instant occurredAt();

    String eventType();

    String aggregateType();

    UUID aggregateId();

    UUID tenantId();

    UUID projectId();

    Map<String, Object> payload();
}
