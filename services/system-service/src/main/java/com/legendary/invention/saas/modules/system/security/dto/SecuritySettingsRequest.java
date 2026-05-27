package com.legendary.invention.saas.modules.system.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class SecuritySettingsRequest {

    @NotNull
    @Positive
    private Long idleTimeoutSeconds;
    @NotNull
    @Positive
    private Long accessTokenExpireSeconds;
    @NotNull
    @Positive
    private Long refreshTokenExpireSeconds;
    @NotNull
    private Boolean allowMultiDeviceLogin;
    @NotNull
    private Boolean captchaEnabled;
    @NotBlank
    private String captchaType;
    @NotNull
    @Positive
    private Long loginDefenseWindowMinutes;
    @NotNull
    @Positive
    private Long loginMaxValidationAttempts;
    @NotNull
    @Positive
    private Long loginMaxFailureCount;
    @NotNull
    @Positive
    private Long verificationCodeExpireSeconds;
    @NotNull
    @Positive
    private Long verificationCodeCooldownSeconds;

    public Long getIdleTimeoutSeconds() { return idleTimeoutSeconds; }
    public void setIdleTimeoutSeconds(Long idleTimeoutSeconds) { this.idleTimeoutSeconds = idleTimeoutSeconds; }
    public Long getAccessTokenExpireSeconds() { return accessTokenExpireSeconds; }
    public void setAccessTokenExpireSeconds(Long accessTokenExpireSeconds) { this.accessTokenExpireSeconds = accessTokenExpireSeconds; }
    public Long getRefreshTokenExpireSeconds() { return refreshTokenExpireSeconds; }
    public void setRefreshTokenExpireSeconds(Long refreshTokenExpireSeconds) { this.refreshTokenExpireSeconds = refreshTokenExpireSeconds; }
    public Boolean getAllowMultiDeviceLogin() { return allowMultiDeviceLogin; }
    public void setAllowMultiDeviceLogin(Boolean allowMultiDeviceLogin) { this.allowMultiDeviceLogin = allowMultiDeviceLogin; }
    public Boolean getCaptchaEnabled() { return captchaEnabled; }
    public void setCaptchaEnabled(Boolean captchaEnabled) { this.captchaEnabled = captchaEnabled; }
    public String getCaptchaType() { return captchaType; }
    public void setCaptchaType(String captchaType) { this.captchaType = captchaType; }
    public Long getLoginDefenseWindowMinutes() { return loginDefenseWindowMinutes; }
    public void setLoginDefenseWindowMinutes(Long loginDefenseWindowMinutes) { this.loginDefenseWindowMinutes = loginDefenseWindowMinutes; }
    public Long getLoginMaxValidationAttempts() { return loginMaxValidationAttempts; }
    public void setLoginMaxValidationAttempts(Long loginMaxValidationAttempts) { this.loginMaxValidationAttempts = loginMaxValidationAttempts; }
    public Long getLoginMaxFailureCount() { return loginMaxFailureCount; }
    public void setLoginMaxFailureCount(Long loginMaxFailureCount) { this.loginMaxFailureCount = loginMaxFailureCount; }
    public Long getVerificationCodeExpireSeconds() { return verificationCodeExpireSeconds; }
    public void setVerificationCodeExpireSeconds(Long verificationCodeExpireSeconds) { this.verificationCodeExpireSeconds = verificationCodeExpireSeconds; }
    public Long getVerificationCodeCooldownSeconds() { return verificationCodeCooldownSeconds; }
    public void setVerificationCodeCooldownSeconds(Long verificationCodeCooldownSeconds) { this.verificationCodeCooldownSeconds = verificationCodeCooldownSeconds; }
}
