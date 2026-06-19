package com.lumira.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoginCodeChallengeRequest(
        @NotBlank(message = "登录方式不能为空")
        @Pattern(regexp = LoginValidationPatterns.LOGIN_TYPE, message = "登录方式不合法")
        String loginType,
        @NotBlank(message = "账号不能为空")
        @Size(max = 128, message = "账号长度不能超过128个字符")
        @Pattern(regexp = LoginValidationPatterns.SAFE_ACCOUNT, message = "账号包含不允许的字符")
        String account
) {
}
