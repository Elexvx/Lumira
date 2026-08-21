package com.lumira.domain.event;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Durable, versioned contract used at module boundaries. An envelope must be
 * persisted in an owner outbox in the same transaction as the aggregate change
 * before it can be forwarded to a transport.
 */
public record IntegrationEventEnvelope(
        UUID eventId,
        String eventType,
        int schemaVersion,
        String sourceModule,
        String aggregateType,
        String aggregateId,
        Instant occurredAt,
        String traceId,
        String correlationId,
        String causationId,
        Map<String, Object> payload
) {
    public IntegrationEventEnvelope {
        eventId = eventId == null ? UUID.randomUUID() : eventId;
        eventType = requireText(eventType, "eventType");
        schemaVersion = schemaVersion < 1 ? 1 : schemaVersion;
        sourceModule = requireText(sourceModule, "sourceModule");
        aggregateType = requireText(aggregateType, "aggregateType");
        aggregateId = requireText(aggregateId, "aggregateId");
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
        traceId = trimToNull(traceId);
        correlationId = trimToNull(correlationId);
        causationId = trimToNull(causationId);
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    public String orderingKey() {
        return aggregateId;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
