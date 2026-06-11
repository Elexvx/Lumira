package com.lumira.api.payment;

import java.util.Map;

public record PaymentWebhookRequestDTO(
        String eventId,
        String eventType,
        String payload,
        String signature,
        String timestamp,
        String nonce,
        Map<String, String> headers
) {
}
