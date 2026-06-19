package com.lumira.api.system;

public record PasskeyCredentialSaveRequestDTO(
        Long tenantId,
        Long userId,
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
