package com.lumira.api.auth;

import jakarta.validation.constraints.NotBlank;

public record WechatLoginRequest(
        @NotBlank(message = "微信授权 code 不能为空") String code,
        @NotBlank(message = "微信授权 state 不能为空") String state
) {
}
