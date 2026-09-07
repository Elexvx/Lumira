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

    public void record(
            String sourceType,
            String eventType,
            Long userId,
            String aggregateType,
            Long aggregateId,
            Map<String, Object> attributes
    ) {
        platformEventOutboxService.record(
                normalize(sourceType, PlatformEventTypes.SOURCE_SYSTEM),
                normalize(eventType, "UNKNOWN"),
                userId,
                buildEventKey(eventType, aggregateType, aggregateId),
                buildPayload(userId, aggregateType, aggregateId, attributes)
        );
    }

    /**
     * Persists a platform outbox row inside the caller's active transaction.
     * The method name intentionally distinguishes durable recording from the
     * later asynchronous relay/delivery phase.
     */
    public void recordInCurrentTransaction(
            String eventType,
            Long userId,
            String aggregateType,
            Long aggregateId,
            Map<String, Object> attributes
    ) {
        platformEventOutboxService.record(
                PlatformEventTypes.SOURCE_SYSTEM,
                normalize(eventType, "UNKNOWN"),
                userId,
                buildEventKey(eventType, aggregateType, aggregateId),
                buildPayload(userId, aggregateType, aggregateId, attributes)
        );
    }

    String buildEventKey(String eventType, String aggregateType, Long aggregateId) {
        return normalize(eventType, "UNKNOWN")
                + ":" + normalize(aggregateType, "aggregate")
                + ":" + (aggregateId == null ? "none" : aggregateId);
    }

    private Map<String, Object> buildPayload(
            Long userId,
            String aggregateType,
            Long aggregateId,
            Map<String, Object> attributes
    ) {
        String userUuid = resolveUserUuid(attributes);
        if (userId != null && userId > 0 && userUuid == null) {
            throw new IllegalArgumentException("platform event userUuid is required when userId is present");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", SCHEMA_VERSION);
        payload.put("occurredAt", LocalDateTime.now());
        payload.put("userId", userId);
        if (userUuid != null) {
            payload.put("userUuid", userUuid);
        }
        payload.put("aggregateType", normalize(aggregateType, "aggregate"));
        payload.put("aggregateId", aggregateId);
        Map<String, Object> normalizedAttributes = attributes == null ? Map.of() : new LinkedHashMap<>(attributes);
        putTextIfPresent(payload, "sourceModule", normalizedAttributes.get("sourceModule"));
        putTextIfPresent(payload, "producer", normalizedAttributes.get("producer"));
        putTextIfPresent(payload, "owner", normalizedAttributes.get("owner"));
        payload.put("attributes", normalizedAttributes);
        return payload;
    }

    private void putTextIfPresent(Map<String, Object> payload, String key, Object value) {
        if (value instanceof String text && StringUtils.hasText(text)) {
            payload.put(key, text.trim());
        }
    }

    private String resolveUserUuid(Map<String, Object> attributes) {
        Object value = attributes == null ? null : attributes.get("userUuid");
        if (value instanceof String text && StringUtils.hasText(text)) {
            return text.trim();
        }
        return null;
    }

    private String normalize(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
