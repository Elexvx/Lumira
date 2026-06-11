package com.lumira.api.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record PaymentCreateRefundRequestDTO(
        @NotBlank String refundNo,
        @NotNull Long amountMinor,
        @NotBlank String currency,
        @NotBlank String reason,
        Map<String, Object> metadata,
        String idempotencyKey
) {
}
