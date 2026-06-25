package com.lumira.api.system;

public record VerificationVerificationDTO(Boolean verified, String message, Long userId, String factorCode) {
}
