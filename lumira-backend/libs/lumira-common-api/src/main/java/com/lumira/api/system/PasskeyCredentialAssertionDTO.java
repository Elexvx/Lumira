package com.lumira.api.system;

public record PasskeyCredentialAssertionDTO(
        Long id,
        Long userId,
        String userUuid,
        String userHandle,
        String credentialId,
        String publicKeyCose,
        Long signCount
) {
}
