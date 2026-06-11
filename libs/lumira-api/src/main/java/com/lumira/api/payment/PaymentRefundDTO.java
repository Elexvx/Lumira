package com.lumira.api.payment;

import java.time.LocalDateTime;
import java.util.Map;

public record PaymentRefundDTO(
        String refundNo,
        String orderNo,
        String providerCode,
        String providerRefundNo,
        Long amountMinor,
        String currency,
        String status,
        String reason,
        Map<String, Object> metadata,
        String failureCode,
        String failureMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime refundedAt
) {
}
