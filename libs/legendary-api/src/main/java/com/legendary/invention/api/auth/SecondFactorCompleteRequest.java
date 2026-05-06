package com.legendary.invention.api.auth;

import jakarta.validation.constraints.NotBlank;

public record SecondFactorCompleteRequest(
        @NotBlank String factorCode,
        @NotBlank String challengeId,
        @NotBlank String verificationCode
) {
}
