package com.lumira.api.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record DomainEventEnvelopeDTO(
        UUID eventId,
        String eventType,
        String aggregateType,
        String aggregateId,
        Long tenantId,
        int schemaVersion,
        String eventKey,
        Instant occurredAt,
        String traceId,
        Map<String, Object> payload
) {
}
