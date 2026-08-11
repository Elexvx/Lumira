package com.lumira.api.payment;

import java.time.LocalDateTime;

public record BuiltinMockPaymentSimulationResultDTO(
        PaymentOrderDTO order,
        String outcome,
        String callbackStatus,
        String notifyId,
        LocalDateTime scheduledAt,
        String redirectUrl
) {
}
