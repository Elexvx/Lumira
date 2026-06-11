package com.lumira.api.payment;

import java.time.LocalDateTime;
import java.util.Map;

public record PaymentOrderDTO(
        String orderNo,
        String providerCode,
        String providerOrderNo,
        String subject,
        Long amountMinor,
        String currency,
        String status,
        String paymentUrl,
        String clientIp,
        String notifyUrl,
        String returnUrl,
        Map<String, Object> metadata,
        String failureCode,
        String failureMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime paidAt
) {
}
