package com.legendary.invention.api.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginCodeChallengeRequest(
        @NotBlank(message = "登录方式不能为空") String loginType,
        @NotBlank(message = "账号不能为空") String account
) {
}
