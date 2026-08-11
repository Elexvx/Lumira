package com.lumira.api.payment;

import java.time.LocalDateTime;
import java.util.List;

public record BuiltinMockPaymentCheckoutDTO(
        String orderNo,
        String providerOrderNo,
        String subject,
        Long amountMinor,
        String currency,
        String status,
        String tradeStatus,
        LocalDateTime expiresAt,
        String returnUrl,
        String callbackStatus,
        String scheduledOutcome,
        LocalDateTime callbackScheduledAt,
        List<String> allowedOutcomes,
        List<Integer> delayOptions,
        Integer maxDelaySeconds,
        String environmentNotice
) {
}
