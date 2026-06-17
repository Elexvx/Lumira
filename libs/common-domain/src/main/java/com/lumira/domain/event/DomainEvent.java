package com.lumira.domain.event;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public interface DomainEvent extends Serializable {

    UUID eventId();

    String eventType();

    String aggregateType();

    String aggregateId();

    Long tenantId();

    int schemaVersion();

    String eventKey();

    Instant occurredAt();

    Map<String, Object> attributes();
}
