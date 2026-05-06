package com.legendary.invention.api.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        String username,
        String mobile,
        @NotBlank(message = "密码不能为空") String password,
        String captchaId,
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
