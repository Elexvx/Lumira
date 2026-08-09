package com.lumira.api.event;

/**
 * Durable event envelope passed to the public catalog projection. Its sequence
 * is the positive, monotonically increasing {@code platform_event_outbox.id},
 * never a DomainEvent UUID. The payload is intentionally opaque to the
 * transport adapter so the catalog can validate and persist it without
 * importing a source implementation.
 */
public record EventCatalogProjectionEvent(
        long outboxSequence,
        String eventType,
        String payloadJson
) {
    public EventCatalogProjectionEvent {
        if (outboxSequence <= 0L) {
            throw new IllegalArgumentException("outboxSequence must be positive");
        }
        eventType = requireText(eventType, "eventType");
        payloadJson = requireText(payloadJson, "payloadJson");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
