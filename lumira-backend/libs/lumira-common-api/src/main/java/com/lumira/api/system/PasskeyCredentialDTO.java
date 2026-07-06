package com.lumira.api.system;

import java.time.LocalDateTime;

public record PasskeyCredentialDTO(
        Long id,
        String label,
        LocalDateTime createdAt,
        LocalDateTime lastUsedAt
) {
}
