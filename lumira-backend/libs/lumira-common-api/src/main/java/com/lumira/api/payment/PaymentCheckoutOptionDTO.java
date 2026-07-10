package com.lumira.api.payment;

import java.util.List;

public record PaymentCheckoutOptionDTO(
        String providerCode,
        String displayName,
        Integer sortOrder,
        String currency,
        List<String> enabledScenes
) {
}
