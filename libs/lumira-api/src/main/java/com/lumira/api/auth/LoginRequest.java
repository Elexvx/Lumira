package com.lumira.api.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Size(max = 128, message = "账号长度不能超过128个字符")
        @Pattern(regexp = LoginValidationPatterns.SAFE_ACCOUNT, message = "账号包含不允许的字符")
        String username,
        @Pattern(regexp = LoginValidationPatterns.CHINA_MOBILE, message = "请输入有效手机号")
        String mobile,
        @NotBlank(message = "密码不能为空") String password,
        String captchaId,
        @Pattern(regexp = LoginValidationPatterns.CAPTCHA_CODE, message = "验证码只能包含字母和数字")
        String captchaCode,
        String captchaProof
) {
    @AssertTrue(message = "用户名和手机号至少填写一项")
    public boolean isAccountProvided() {
        return (username != null && !username.isBlank()) || (mobile != null && !mobile.isBlank());
    }

    public String account() {
        if (username != null && !username.isBlank()) {
            return username.trim();
        }
        return mobile == null ? "" : mobile.trim();
    }
}
