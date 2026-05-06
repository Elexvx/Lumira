package com.legendary.invention.api.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginCodeCompleteRequest(
        @NotBlank(message = "验证码会话不能为空") String challengeId,
        @NotBlank(message = "验证码不能为空") String verificationCode
) {
}
