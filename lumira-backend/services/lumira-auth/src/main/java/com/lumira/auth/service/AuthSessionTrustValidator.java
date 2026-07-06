package com.lumira.auth.service;

import com.lumira.auth.model.AuthSession;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

final class AuthSessionTrustValidator {

    private static final int MAX_SESSION_ID_LENGTH = 128;
    private static final int MAX_USER_UUID_LENGTH = 64;
    private static final int MAX_USERNAME_LENGTH = 64;
    private static final int MAX_PERMISSIONS_VERSION_LENGTH = 128;
    private static final int MAX_PAYLOAD_LENGTH = 64 * 1024;
    private static final Pattern SAFE_SESSION_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._:@/-]{1,128}$");

    private AuthSessionTrustValidator() {
    }

    static String requireTrustedSessionId(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new IllegalArgumentException("sessionId is required");
        }
        String normalized = sessionId.trim();
        if (normalized.length() > MAX_SESSION_ID_LENGTH
                || !SAFE_SESSION_ID_PATTERN.matcher(normalized).matches()
                || normalized.contains("..")
                || normalized.contains("//")) {
            throw new IllegalArgumentException("sessionId is invalid");
        }
        return normalized;
    }

    static String trustedSessionIdOrNull(String sessionId) {
        try {
            return requireTrustedSessionId(sessionId);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    static void requireTrustedSession(AuthSession session) {
        if (session == null) {
            throw new IllegalArgumentException("session is required");
        }
        session.setSessionId(requireTrustedSessionId(session.getSessionId()));
        if (session.getUserId() == null || session.getUserId() <= 0) {
            throw new IllegalArgumentException("session userId is invalid");
        }
        if (!StringUtils.hasText(session.getUserUuid()) || session.getUserUuid().trim().length() > MAX_USER_UUID_LENGTH) {
            throw new IllegalArgumentException("session userUuid is invalid");
        }
        session.setUserUuid(session.getUserUuid().trim());
        if (!StringUtils.hasText(session.getUsername()) || session.getUsername().trim().length() > MAX_USERNAME_LENGTH) {
            throw new IllegalArgumentException("session username is invalid");
        }
        session.setUsername(session.getUsername().trim());
        if (session.getSessionVersion() == null || session.getSessionVersion() <= 0) {
            throw new IllegalArgumentException("sessionVersion is invalid");
        }
        if (!StringUtils.hasText(session.getPermissionsVersion())
                || session.getPermissionsVersion().trim().length() > MAX_PERMISSIONS_VERSION_LENGTH) {
            throw new IllegalArgumentException("permissionsVersion is invalid");
        }
        session.setPermissionsVersion(session.getPermissionsVersion().trim());
    }

    static void requireTrustedPayload(String payload) {
        if (payload != null && payload.length() > MAX_PAYLOAD_LENGTH) {
            throw new IllegalArgumentException("session payload is too large");
        }
    }
}
