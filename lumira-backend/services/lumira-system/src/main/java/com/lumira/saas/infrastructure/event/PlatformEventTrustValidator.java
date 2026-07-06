package com.lumira.saas.infrastructure.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.regex.Pattern;

final class PlatformEventTrustValidator {

    static final int MAX_EVENT_TYPE_LENGTH = 96;
    static final int MAX_EVENT_KEY_LENGTH = 256;
    static final int MAX_PAYLOAD_JSON_LENGTH = 64 * 1024;
    static final int MAX_STREAM_KEY_LENGTH = 128;

    private static final Pattern EVENT_TYPE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,95}$");
    private static final Pattern EVENT_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9._:@/-]{1,256}$");
    private static final Pattern REDIS_STREAM_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9:_-]{1,128}$");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private PlatformEventTrustValidator() {
    }

    static void requireTrustedSystemEvent(PlatformEventOutboxEntity event) {
        if (event == null || event.getId() == null || event.getId() <= 0) {
            throw new IllegalArgumentException("platform event id is required");
        }
        if (!PlatformEventTypes.SOURCE_SYSTEM.equals(event.getSourceType())) {
            throw new IllegalArgumentException("platform event sourceType must be SYSTEM");
        }
        if (event.getUserId() != null) {
            if (event.getUserId() <= 0) {
                throw new IllegalArgumentException("platform event userId must be positive when present");
            }
            if (event.getUserUuid() == null || event.getUserUuid().isBlank()) {
                throw new IllegalArgumentException("platform event userUuid is required when userId is present");
            }
            requireMatchingPayloadUserUuid(event.getPayloadJson(), event.getUserUuid());
        }
        requireTrustedEventType(event.getEventType());
        requireTrustedEventKey(event.getEventKey());
        requireTrustedPayload(event.getPayloadJson());
    }

    private static void requireMatchingPayloadUserUuid(String payloadJson, String eventUserUuid) {
        String payloadUserUuid = extractPayloadUserUuid(payloadJson);
        if (payloadUserUuid == null) {
            throw new IllegalArgumentException("platform event payload userUuid is required when userId is present");
        }
        if (!payloadUserUuid.equals(eventUserUuid.trim())) {
            throw new IllegalArgumentException("platform event payload userUuid must match row userUuid");
        }
    }

    private static String extractPayloadUserUuid(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(payloadJson);
            String topLevel = textOrNull(root.path("userUuid"));
            if (topLevel != null) {
                return topLevel;
            }
            JsonNode attributes = root.path("attributes");
            if (attributes.isObject()) {
                return textOrNull(attributes.path("userUuid"));
            }
            return null;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("platform event payload is invalid json", exception);
        }
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String text = node.asText(null);
        return text == null || text.isBlank() ? null : text.trim();
    }

    static String requireTrustedEventType(String eventType) {
        if (eventType == null) {
            throw new IllegalArgumentException("platform eventType is required");
        }
        String normalized = eventType.trim();
        if (normalized.length() > MAX_EVENT_TYPE_LENGTH || !EVENT_TYPE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("platform eventType is invalid");
        }
        return normalized;
    }

    static String requireTrustedEventKey(String eventKey) {
        if (eventKey == null) {
            return null;
        }
        String normalized = eventKey.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > MAX_EVENT_KEY_LENGTH
                || !EVENT_KEY_PATTERN.matcher(normalized).matches()
                || normalized.contains("..")
                || normalized.contains("//")) {
            throw new IllegalArgumentException("platform eventKey is invalid");
        }
        return normalized;
    }

    static void requireTrustedPayload(String payloadJson) {
        if (payloadJson != null && payloadJson.length() > MAX_PAYLOAD_JSON_LENGTH) {
            throw new IllegalArgumentException("platform event payload is too large");
        }
    }

    static String requireTrustedRedisStreamKey(String streamKey) {
        if (streamKey == null || !REDIS_STREAM_KEY_PATTERN.matcher(streamKey).matches()) {
            throw new IllegalArgumentException("redis stream key is invalid");
        }
        return streamKey;
    }
}
