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
        String producer,
        String aggregateId,
        Long aggregateVersion,
        int schemaVersion,
        Instant occurredAt,
        String traceId,
        String releaseId,
        String payloadDigest,
        Map<String, Object> payload
) {

    public EventEnvelope {
        eventId = requireText(eventId, "eventId");
        eventType = requireText(eventType, "eventType");
        sourceModule = requireText(sourceModule, "sourceModule");
        producer = requireText(producer, "producer");
        aggregateId = requireText(aggregateId, "aggregateId");
        if (aggregateVersion != null && aggregateVersion <= 0L) {
            throw new IllegalArgumentException("aggregateVersion must be positive when present");
        }
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt is required");
        if (traceId != null && traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        releaseId = optionalText(releaseId, "releaseId");
        payloadDigest = optionalText(payloadDigest, "payloadDigest");
        if (payloadDigest != null && !payloadDigest.matches("(?:sha256:)?[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("payloadDigest must be a SHA-256 digest");
        }
        payload = payload == null
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(payload));
    }

    /**
     * Compatibility constructor for existing outbox producers that have not
     * yet populated aggregate/version/release metadata.
     */
    public EventEnvelope(
            String eventId,
            String eventType,
            String sourceModule,
            String aggregateId,
            int schemaVersion,
            Instant occurredAt,
            String traceId,
            Map<String, Object> payload
    ) {
        this(eventId, eventType, sourceModule, sourceModule, aggregateId, null, schemaVersion,
                occurredAt, traceId, null, null, payload);
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
                sourceModule,
                aggregateId,
                null,
                schemaVersion,
                occurredAt == null ? Instant.now() : occurredAt,
                traceId,
                null,
                null,
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

    private static String optionalText(String value, String name) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
