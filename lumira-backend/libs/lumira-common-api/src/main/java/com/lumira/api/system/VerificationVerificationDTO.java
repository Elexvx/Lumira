package com.lumira.api.system;

import java.util.List;

public record VerificationVerificationDTO(
        Boolean verified,
        String message,
        Long userId,
        String userUuid,
        String factorCode,
        List<String> recoveryCodes
) {
}
