package com.example.htmldeploy.infrastructure.messaging;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.example.htmldeploy.infrastructure.persistence.JdbcTime;

@Repository
public class JdbcOutboxEventStore {

    private final JdbcClient jdbc;

    public JdbcOutboxEventStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<OutboxMessage> findPending(int limit) {
        return jdbc.sql("""
                        select id, event_type, aggregate_id, payload, attempts
                          from outbox_event
                         where status = 'PENDING'
                           and next_attempt_at <= now()
                         order by created_at
                         limit :limit
                        """)
                .param("limit", limit)
                .query(this::map)
                .list();
    }

    public void markPublished(UUID eventId) {
        jdbc.sql("""
                        update outbox_event
                           set status = 'PUBLISHED',
                               published_at = :publishedAt,
                               last_error = null
                         where id = :id
                        """)
                .param("id", eventId)
                .param("publishedAt", JdbcTime.toOffsetDateTime(Instant.now()))
                .update();
    }

    public void markForRetry(OutboxMessage message, String error) {
        int nextAttempt = message.attempts() + 1;
        String status = nextAttempt >= 5 ? "FAILED" : "PENDING";
        long delaySeconds = Math.min(60, 1L << Math.min(nextAttempt, 6));
        jdbc.sql("""
                        update outbox_event
                           set status = :status,
                               attempts = :attempts,
                               next_attempt_at = :nextAttemptAt,
                               last_error = :lastError
                         where id = :id
                        """)
                .param("id", message.id())
                .param("status", status)
                .param("attempts", nextAttempt)
                .param("nextAttemptAt", JdbcTime.toOffsetDateTime(Instant.now().plusSeconds(delaySeconds)))
                .param("lastError", abbreviate(error))
                .update();
    }

    private OutboxMessage map(ResultSet rs, int rowNum) throws SQLException {
        Object aggregateId = rs.getObject("aggregate_id");
        return new OutboxMessage(
                toUuid(rs.getObject("id")),
                rs.getString("event_type"),
                toUuid(aggregateId),
                rs.getString("payload"),
                rs.getInt("attempts")
        );
    }

    private UUID toUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(value.toString());
    }

    private String abbreviate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }
}
