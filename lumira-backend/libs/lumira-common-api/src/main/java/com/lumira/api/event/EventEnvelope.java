package com.lumira.api.event;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Minimal cross-module integration-event envelope.
 *
 * <p>The envelope standardizes event identity and tracing metadata without
 * coupling producers to a transport or replacing the existing transactional
 * outbox and relay contracts.</p>
 */
public record EventEnvelope(
        String eventId,
        String eventType,
        String sourceModule,
        String aggregateId,
        int schemaVersion,
        Instant occurredAt,
        String traceId,
        Map<String, Object> payload
) {

    public EventEnvelope {
        eventId = requireText(eventId, "eventId");
        eventType = requireText(eventType, "eventType");
        sourceModule = requireText(sourceModule, "sourceModule");
        aggregateId = requireText(aggregateId, "aggregateId");
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt is required");
        if (traceId != null && traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        payload = payload == null
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(payload));
    }

    public static EventEnvelope of(
            String eventId,
            String eventType,
            String sourceModule,
            String aggregateId,
            int schemaVersion,
            Instant occurredAt,
            String traceId,
            Map<String, Object> payload
    ) {
        return new EventEnvelope(
                eventId,
                eventType,
                sourceModule,
                aggregateId,
                schemaVersion,
                occurredAt == null ? Instant.now() : occurredAt,
                traceId,
                payload
        );
    }

    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNull(value, name + " is required").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return normalized;
    }
}
