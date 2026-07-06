package com.lumira.api.system;

public record PasskeyCredentialSaveRequestDTO(
        Long userId,
        String userUuid,
        String userHandle,
        String credentialId,
        String publicKeyCose,
        Long signCount,
        String transports,
        Boolean backupEligible,
        Boolean backupState,
        String label
) {
}
