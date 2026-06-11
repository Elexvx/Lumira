package com.lumira.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PasskeyAuthenticationCompleteRequest(
        @NotBlank String challengeId,
        @NotBlank String id,
        @NotBlank String rawId,
        @NotBlank String type,
        @NotNull Response response,
        String authenticatorAttachment
) {
    public record Response(
            @NotBlank String clientDataJSON,
            @NotBlank String authenticatorData,
            @NotBlank String signature,
            String userHandle
    ) {
    }
}
