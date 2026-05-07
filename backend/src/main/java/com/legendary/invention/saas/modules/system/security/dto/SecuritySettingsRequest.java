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
}
