package com.legendary.invention.api.system;

public record PasskeyCredentialUsageRequestDTO(
        Long credentialId,
        Long signCount,
        Boolean backupEligible,
        Boolean backupState
) {
}
