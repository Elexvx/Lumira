package com.lumira.api.system;

public record PasskeyCredentialUsageRequestDTO(
        Long credentialId,
        Long userId,
        String userUuid,
        Long signCount,
        Boolean backupEligible,
        Boolean backupState
) {
}
