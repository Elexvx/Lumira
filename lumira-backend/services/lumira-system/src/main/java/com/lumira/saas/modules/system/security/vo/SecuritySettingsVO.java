package com.lumira.saas.modules.system.security.vo;

public class SecuritySettingsVO {

    private Long idleTimeoutSeconds;
    private Long accessTokenExpireSeconds;
    private Long refreshTokenExpireSeconds;
    private Boolean allowMultiDeviceLogin;
    private Boolean captchaEnabled;
    private String captchaType;
    private Long loginDefenseWindowMinutes;
    private Long loginMaxValidationAttempts;
    private Long loginMaxFailureCount;
    private Long verificationCodeExpireSeconds;
    private Long verificationCodeCooldownSeconds;
    private Long passwordMinLength;
    private Boolean passwordRequireUppercase;
    private Boolean passwordRequireLowercase;
    private Boolean passwordRequireSpecialCharacter;
    private Boolean passwordAllowConsecutiveCharacters;

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
    public Long getPasswordMinLength() { return passwordMinLength; }
    public void setPasswordMinLength(Long passwordMinLength) { this.passwordMinLength = passwordMinLength; }
    public Boolean getPasswordRequireUppercase() { return passwordRequireUppercase; }
    public void setPasswordRequireUppercase(Boolean passwordRequireUppercase) { this.passwordRequireUppercase = passwordRequireUppercase; }
    public Boolean getPasswordRequireLowercase() { return passwordRequireLowercase; }
    public void setPasswordRequireLowercase(Boolean passwordRequireLowercase) { this.passwordRequireLowercase = passwordRequireLowercase; }
    public Boolean getPasswordRequireSpecialCharacter() { return passwordRequireSpecialCharacter; }
    public void setPasswordRequireSpecialCharacter(Boolean passwordRequireSpecialCharacter) { this.passwordRequireSpecialCharacter = passwordRequireSpecialCharacter; }
    public Boolean getPasswordAllowConsecutiveCharacters() { return passwordAllowConsecutiveCharacters; }
    public void setPasswordAllowConsecutiveCharacters(Boolean passwordAllowConsecutiveCharacters) { this.passwordAllowConsecutiveCharacters = passwordAllowConsecutiveCharacters; }
}
