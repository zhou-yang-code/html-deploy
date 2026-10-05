package com.example.htmldeploy.infrastructure.messaging;

import java.time.Instant;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.example.htmldeploy.application.port.DomainEventPublisher;
import com.example.htmldeploy.domain.shared.DomainEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class JdbcOutboxEventPublisher implements DomainEventPublisher {

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public JdbcOutboxEventPublisher(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(DomainEvent event) {
        jdbc.sql("""
                        insert into outbox_event (
                            id, event_type, aggregate_type, aggregate_id, tenant_id, project_id,
                            payload, status, attempts, next_attempt_at, created_at
                        )
                        values (
                            :id, :eventType, :aggregateType, :aggregateId, :tenantId, :projectId,
                            :payload, 'PENDING', 0, :nextAttemptAt, :createdAt
                        )
                        """)
                .param("id", event.eventId())
                .param("eventType", event.eventType())
                .param("aggregateType", event.aggregateType())
                .param("aggregateId", event.aggregateId())
                .param("tenantId", event.tenantId())
                .param("projectId", event.projectId())
                .param("payload", serialize(event))
                .param("nextAttemptAt", event.occurredAt())
                .param("createdAt", Instant.now())
                .update();
    }

    private String serialize(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event.payload());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize domain event", exception);
        }
    }
}
