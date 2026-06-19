package com.lumira.saas.infrastructure.event;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PlatformEventPublisher {

    private static final int SCHEMA_VERSION = 1;

    private final PlatformEventOutboxService platformEventOutboxService;

    public PlatformEventPublisher(PlatformEventOutboxService platformEventOutboxService) {
        this.platformEventOutboxService = platformEventOutboxService;
    }

    public void publishAfterCommit(
            String sourceType,
            String eventType,
            Long tenantId,
            Long userId,
            String aggregateType,
            Long aggregateId,
            Map<String, Object> attributes
    ) {
        platformEventOutboxService.recordAfterCommit(
                normalize(sourceType, PlatformEventTypes.SOURCE_SYSTEM),
                normalize(eventType, "UNKNOWN"),
                tenantId,
                userId,
                buildEventKey(eventType, tenantId, aggregateType, aggregateId),
                buildPayload(tenantId, userId, aggregateType, aggregateId, attributes)
        );
    }

    String buildEventKey(String eventType, Long tenantId, String aggregateType, Long aggregateId) {
        return normalize(eventType, "UNKNOWN")
                + ":" + (tenantId == null ? "unknown" : tenantId)
                + ":" + normalize(aggregateType, "aggregate")
                + ":" + (aggregateId == null ? "none" : aggregateId);
    }

    private Map<String, Object> buildPayload(
            Long tenantId,
            Long userId,
            String aggregateType,
            Long aggregateId,
            Map<String, Object> attributes
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", SCHEMA_VERSION);
        payload.put("occurredAt", LocalDateTime.now());
        payload.put("tenantId", tenantId);
        payload.put("userId", userId);
        payload.put("aggregateType", normalize(aggregateType, "aggregate"));
        payload.put("aggregateId", aggregateId);
        payload.put("attributes", attributes == null ? Map.of() : new LinkedHashMap<>(attributes));
        return payload;
    }

    private String normalize(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
