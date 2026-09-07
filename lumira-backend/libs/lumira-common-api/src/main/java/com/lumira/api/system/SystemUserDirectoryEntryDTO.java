package com.lumira.api.system;

/**
 * Minimal user-directory projection exposed to modules that need to match a
 * local user with an external directory. It deliberately excludes credentials
 * and unrelated profile fields.
 */
public record SystemUserDirectoryEntryDTO(
        Long userId,
        String userUuid,
        String displayName,
        String email,
        String mobile
) {
}
