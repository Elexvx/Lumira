package com.lumira.api.payment;

import java.time.LocalDateTime;

public record PaymentWebhookEventDTO(
        String providerCode,
        String eventId,
        String eventType,
        boolean signatureValid,
        boolean processed,
        String processMessage,
        LocalDateTime receivedAt,
        LocalDateTime processedAt
) {
}
