package com.lumira.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordResetCompleteRequest(
        @NotBlank(message = "验证码挑战不能为空")
        @Size(max = 128, message = "验证码挑战长度不能超过128个字符")
        String challengeId,
        @NotBlank(message = "验证码不能为空")
        @Size(max = 12, message = "验证码长度不能超过12个字符")
        @Pattern(regexp = LoginValidationPatterns.VERIFICATION_CODE, message = "验证码格式不合法")
        String verificationCode,
        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 128, message = "新密码长度必须在6到128个字符之间")
        String newPassword
) {
}
