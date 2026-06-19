package com.lumira.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SecondFactorCompleteRequest(
        @NotBlank
        @Pattern(regexp = LoginValidationPatterns.SYSTEM_TOKEN, message = "验证方式不合法")
        String factorCode,
        @NotBlank
        @Pattern(regexp = LoginValidationPatterns.SYSTEM_TOKEN, message = "验证码会话不合法")
        String challengeId,
        @NotBlank
        @Pattern(regexp = LoginValidationPatterns.VERIFICATION_CODE, message = "验证码只能包含字母和数字")
        String verificationCode
) {
}
