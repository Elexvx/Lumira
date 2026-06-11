package com.lumira.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasskeyCredentialLabelRequest(
        @NotBlank @Size(max = 128) String label
) {
}
