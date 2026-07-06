package com.lumira.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordResetChallengeRequest(
        @NotBlank(message = "账号不能为空")
        @Size(max = 128, message = "账号长度不能超过128个字符")
        @Pattern(regexp = LoginValidationPatterns.SAFE_ACCOUNT, message = "账号包含不允许的字符")
        String account,
        @NotBlank(message = "验证方式不能为空")
        @Pattern(regexp = LoginValidationPatterns.LOGIN_TYPE, message = "验证方式不合法")
        String contactType,
        @NotBlank(message = "绑定邮箱或手机号不能为空")
        @Size(max = 128, message = "绑定邮箱或手机号长度不能超过128个字符")
        @Pattern(regexp = LoginValidationPatterns.SAFE_ACCOUNT, message = "绑定邮箱或手机号包含不允许的字符")
        String contact
) {
}
