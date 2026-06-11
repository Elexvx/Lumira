package com.lumira.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PasskeyRegistrationCompleteRequest(
        @NotBlank String challengeId,
        @NotBlank String id,
        @NotBlank String rawId,
        @NotBlank String type,
        @NotNull Response response,
        String authenticatorAttachment,
        List<String> transports,
        String label
) {
    public record Response(
            @NotBlank String clientDataJSON,
            @NotBlank String attestationObject
    ) {
    }
}
