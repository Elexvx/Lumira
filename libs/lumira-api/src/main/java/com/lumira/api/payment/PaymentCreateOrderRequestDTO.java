package com.lumira.api.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record PaymentCreateOrderRequestDTO(
        @NotBlank String providerCode,
        @NotBlank String orderNo,
        @NotBlank String subject,
        @NotNull Long amountMinor,
        @NotBlank String currency,
        String clientIp,
        String notifyUrl,
        String returnUrl,
        Map<String, Object> metadata,
        String idempotencyKey
) {
}
