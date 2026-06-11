package com.lumira.api.payment;

import java.time.LocalDateTime;

public record PaymentProviderTestResultDTO(
        String providerCode,
        String providerName,
        boolean success,
        String message,
        LocalDateTime checkedAt
) {
}
