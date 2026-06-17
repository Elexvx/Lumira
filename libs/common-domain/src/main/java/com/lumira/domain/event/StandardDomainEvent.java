package com.lumira.domain.event;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record StandardDomainEvent(
        UUID eventId,
        String eventType,
        String aggregateType,
        String aggregateId,
        Long tenantId,
        int schemaVersion,
        String eventKey,
        Instant occurredAt,
        Map<String, Object> attributes
) implements DomainEvent {

    public StandardDomainEvent {
        eventId = eventId == null ? UUID.randomUUID() : eventId;
        eventType = requireText(eventType, "eventType");
        aggregateType = requireText(aggregateType, "aggregateType");
        aggregateId = requireText(aggregateId, "aggregateId");
        schemaVersion = schemaVersion <= 0 ? 1 : schemaVersion;
        eventKey = requireText(eventKey, "eventKey");
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static StandardDomainEvent of(
            String eventType,
            String aggregateType,
            String aggregateId,
            Long tenantId,
            Map<String, Object> attributes
    ) {
        return new StandardDomainEvent(
                UUID.randomUUID(),
                eventType,
                aggregateType,
                aggregateId,
                tenantId,
                1,
                eventType + ":" + tenantId + ":" + aggregateType + ":" + aggregateId,
                Instant.now(),
                attributes
        );
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return trimmed;
    }
}
