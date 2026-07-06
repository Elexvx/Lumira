package com.lumira.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

public final class AuthenticationTrustSupport {
    private static final String INTERNAL_PRINCIPAL_NAME = "internal-service";
    private static final String INTERNAL_SESSION_ID = "internal";
    private static final int MAX_SESSION_ID_LENGTH = 128;
    private static final Pattern SAFE_SESSION_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._:@/-]{1,128}$");

    private AuthenticationTrustSupport() {
    }

    public static boolean canReuse(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if (!(authentication.getPrincipal() instanceof CurrentUser currentUser)) {
            return false;
        }
        return isTrustedCurrentUser(currentUser) || isInternalServicePrincipal(currentUser);
    }

    public static boolean isTrustedCurrentUser(CurrentUser currentUser) {
        return currentUser != null
                && currentUser.isAuthenticated()
                && currentUser.getUserId() != null
                && currentUser.getUserId() > 0
                && StringUtils.hasText(currentUser.getUserUuid())
                && StringUtils.hasText(currentUser.getUsername())
                && isTrustedSessionId(currentUser.getSessionId())
                && currentUser.getSessionVersion() != null
                && currentUser.getSessionVersion() > 0
                && StringUtils.hasText(currentUser.getPermissionsVersion());
    }

    public static boolean isInternalServiceAuthentication(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof CurrentUser currentUser
                && isInternalServicePrincipal(currentUser);
    }

    private static boolean isInternalServicePrincipal(CurrentUser currentUser) {
        return currentUser != null
                && !currentUser.isAuthenticated()
                && currentUser.getUserId() != null
                && currentUser.getUserId() == 0L
                && INTERNAL_PRINCIPAL_NAME.equals(currentUser.getUsername())
                && INTERNAL_SESSION_ID.equals(currentUser.getSessionId())
                && currentUser.getSessionVersion() != null
                && currentUser.getSessionVersion() == 0
                && !StringUtils.hasText(currentUser.getUserUuid())
                && !StringUtils.hasText(currentUser.getPermissionsVersion())
                && currentUser.getPermissions() != null
                && currentUser.getPermissions().isEmpty()
                && currentUser.getRoleIds().isEmpty()
                && currentUser.getPrimaryDeptId() == null
                && currentUser.getDeptIds().isEmpty()
                && currentUser.getDescendantDeptIds().isEmpty()
                && currentUser.getDataScopes().isEmpty();
    }

    private static boolean isTrustedSessionId(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return false;
        }
        String normalized = sessionId.trim();
        return normalized.length() <= MAX_SESSION_ID_LENGTH
                && SAFE_SESSION_ID_PATTERN.matcher(normalized).matches()
                && !normalized.contains("..")
                && !normalized.contains("//");
    }
}
