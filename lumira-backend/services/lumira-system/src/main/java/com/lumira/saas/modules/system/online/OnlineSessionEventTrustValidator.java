package com.lumira.saas.modules.system.online;

import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.regex.Pattern;

final class OnlineSessionEventTrustValidator {

    private static final int MAX_SESSION_ID_LENGTH = 128;
    private static final int MAX_USER_UUID_LENGTH = 64;
    private static final int MAX_OPERATOR_USERNAME_LENGTH = 64;
    private static final int MAX_EVENT_JSON_LENGTH = 4096;
    private static final Pattern SAFE_TEXT_PATTERN = Pattern.compile("^[A-Za-z0-9._:@/-]+$");
    private static final Set<String> ALLOWED_ACTIONS = Set.of(
            OnlineSessionEvent.ACTION_UPSERT,
            OnlineSessionEvent.ACTION_REMOVED,
            OnlineSessionEvent.ACTION_HEARTBEAT
    );

    private OnlineSessionEventTrustValidator() {
    }

    static String requireTrustedSessionId(String sessionId) {
        String normalized = boundedSafeText(sessionId, MAX_SESSION_ID_LENGTH, "sessionId", true);
        if (normalized.contains("..") || normalized.contains("//")) {
            throw new IllegalArgumentException("sessionId is invalid");
        }
        return normalized;
    }

    static void requireTrustedEvent(OnlineSessionEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("online session event is required");
        }
        if (!StringUtils.hasText(event.getAction()) || !ALLOWED_ACTIONS.contains(event.getAction().trim())) {
            throw new IllegalArgumentException("online session event action is invalid");
        }
        event.setAction(event.getAction().trim());
        boolean identityRequired = !OnlineSessionEvent.ACTION_HEARTBEAT.equals(event.getAction());
        if (identityRequired && event.getUserId() == null) {
            throw new IllegalArgumentException("online session event userId is required");
        }
        if (event.getUserId() != null && event.getUserId() <= 0) {
            throw new IllegalArgumentException("online session event userId is invalid");
        }
        if (identityRequired || StringUtils.hasText(event.getSessionId())) {
            event.setSessionId(requireTrustedSessionId(event.getSessionId()));
        }
        event.setUserUuid(boundedSafeText(event.getUserUuid(), MAX_USER_UUID_LENGTH, "userUuid", identityRequired));
        event.setOperatorUsername(boundedSafeText(event.getOperatorUsername(), MAX_OPERATOR_USERNAME_LENGTH, "operatorUsername", false));
    }

    static void requireTrustedSerializedEvent(String payload) {
        if (!StringUtils.hasText(payload) || payload.length() > MAX_EVENT_JSON_LENGTH) {
            throw new IllegalArgumentException("online session event payload is invalid");
        }
    }

    private static String boundedSafeText(String value, int maxLength, String fieldName, boolean required) {
        if (!StringUtils.hasText(value)) {
            if (required) {
                throw new IllegalArgumentException(fieldName + " is required");
            }
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength || !SAFE_TEXT_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(fieldName + " is invalid");
        }
        return normalized;
    }
}
