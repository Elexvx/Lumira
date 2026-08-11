package com.lumira.saas.modules.system.user.support;

/**
 * Persistence format for the deterministic avatar shown when a user has not
 * uploaded a custom image yet. The value is stored in sys_user.avatar_url so
 * every client receives the same identity-derived avatar after the first
 * bootstrap instead of generating a new value per page.
 */
public final class UserAvatarDefaults {
    public static final String GENERATED_AVATAR_URL_PREFIX = "/api/v1/profile/avatar/generated/";

    private UserAvatarDefaults() {
    }

    public static String generatedAvatarUrl(String userUuid) {
        String normalized = userUuid == null ? "" : userUuid.trim();
        return normalized.isEmpty() ? null : GENERATED_AVATAR_URL_PREFIX + normalized;
    }
}
