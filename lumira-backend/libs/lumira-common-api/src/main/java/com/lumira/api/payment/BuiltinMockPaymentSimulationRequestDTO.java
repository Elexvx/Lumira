package com.lumira.api.payment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record BuiltinMockPaymentSimulationRequestDTO(
        @NotBlank String outcome,
        @Min(0) @Max(300) Integer callbackDelaySeconds
) {
}
