package com.lumira.api.system;

public record PasskeyCredentialUsageRequestDTO(
        Long credentialId,
        Long signCount,
        Boolean backupEligible,
        Boolean backupState
) {
}
