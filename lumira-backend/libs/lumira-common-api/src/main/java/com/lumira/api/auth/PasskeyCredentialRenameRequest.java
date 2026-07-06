package com.lumira.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasskeyCredentialRenameRequest(
        @NotBlank @Size(max = 128) String label,
        @Size(max = 256) String currentPassword,
        @Size(max = 16) String currentFactorCode,
        @Size(max = 64) String currentChallengeId,
        @Size(max = 64) String currentVerificationCode
) {
}
