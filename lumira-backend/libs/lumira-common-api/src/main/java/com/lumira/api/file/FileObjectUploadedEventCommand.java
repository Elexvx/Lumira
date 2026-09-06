package com.lumira.api.file;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Owner command for the FILE_OBJECT_UPLOADED integration event.
 *
 * <p>The async runtime transports this contract but never persists the File
 * domain projection. The file owner validates and commits the command.</p>
 */
public record FileObjectUploadedEventCommand(
        String eventId,
        String eventType,
        String sourceModule,
        String producer,
        String owner,
        String aggregateId,
        Long aggregateVersion,
        int schemaVersion,
        Instant occurredAt,
        String traceId,
        String releaseId,
        String payloadDigest,
        Map<String, Object> payload
) {

    public FileObjectUploadedEventCommand {
        eventId = requiredText(eventId, "eventId");
        eventType = requiredText(eventType, "eventType");
        sourceModule = requiredText(sourceModule, "sourceModule");
        producer = requiredText(producer, "producer");
        owner = requiredText(owner, "owner");
        aggregateId = requiredText(aggregateId, "aggregateId");
        if (aggregateVersion == null || aggregateVersion <= 0L) {
            throw new IllegalArgumentException("aggregateVersion must be positive");
        }
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt is required");
        releaseId = requiredText(releaseId, "releaseId");
        payloadDigest = requiredText(payloadDigest, "payloadDigest");
        if (!payloadDigest.matches("(?:sha256:)?[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("payloadDigest must be a SHA-256 digest");
        }
        payload = payload == null
                ? Map.of()
                : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }

    public long fileId() {
        try {
            long value = Long.parseLong(aggregateId);
            if (value <= 0L) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("aggregateId must be a positive file id", exception);
        }
    }

    private static String requiredText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
