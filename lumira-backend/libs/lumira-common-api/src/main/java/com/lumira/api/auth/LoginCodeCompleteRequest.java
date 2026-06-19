package com.lumira.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LoginCodeCompleteRequest(
        @NotBlank(message = "验证码会话不能为空")
        @Pattern(regexp = LoginValidationPatterns.SYSTEM_TOKEN, message = "验证码会话不合法")
        String challengeId,
        @NotBlank(message = "验证码不能为空")
        @Pattern(regexp = LoginValidationPatterns.VERIFICATION_CODE, message = "验证码只能包含字母和数字")
        String verificationCode
) {
}
