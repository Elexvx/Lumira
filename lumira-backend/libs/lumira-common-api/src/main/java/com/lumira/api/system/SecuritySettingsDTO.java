package com.lumira.api.system;

public record SecuritySettingsDTO(
        Long idleTimeoutSeconds,
        Long accessTokenExpireSeconds,
        Long refreshTokenExpireSeconds,
        Boolean allowMultiDeviceLogin,
        Boolean captchaEnabled,
        String captchaType,
        Long loginDefenseWindowMinutes,
        Long loginMaxValidationAttempts,
        Long loginMaxFailureCount,
        Long verificationCodeExpireSeconds,
        Long verificationCodeCooldownSeconds
) {
}
