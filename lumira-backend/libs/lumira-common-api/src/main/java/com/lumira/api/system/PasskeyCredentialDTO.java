package com.lumira.api.system;

import java.time.LocalDateTime;

public record PasskeyCredentialDTO(
        Long id,
        Long tenantId,
        Long userId,
        String username,
        String userHandle,
        String credentialId,
        String publicKeyCose,
        Long signCount,
        String transports,
        Boolean backupEligible,
        Boolean backupState,
        String label,
        LocalDateTime createdAt,
        LocalDateTime lastUsedAt
) {
}
