package com.lumira.api.notification;

import java.util.Objects;

/**
 * A validated command sent by an event consumer to the message owner.
 *
 * <p>The command carries the original event identity so the owner can commit
 * its durable consumption receipt together with the notification write.</p>
 */
public record NotificationCommand(
        String eventId,
        String eventType,
        String sourceModule,
        String aggregateId,
        Long targetUserId,
        String targetUserUuid,
        String title,
        String content
) {

    public NotificationCommand {
        eventId = requiredText(eventId, "eventId", 128);
        eventType = requiredText(eventType, "eventType", 128);
        sourceModule = requiredText(sourceModule, "sourceModule", 64);
        aggregateId = requiredText(aggregateId, "aggregateId", 191);
        if (targetUserId == null || targetUserId <= 0L) {
            throw new IllegalArgumentException("targetUserId must be positive");
        }
        targetUserUuid = requiredText(targetUserUuid, "targetUserUuid", 128);
        title = requiredText(title, "title", 128);
        content = requiredText(content, "content", 8_000);
    }

    private static String requiredText(String value, String field, int maxLength) {
        String normalized = Objects.requireNonNull(value, field + " is required").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }
}
