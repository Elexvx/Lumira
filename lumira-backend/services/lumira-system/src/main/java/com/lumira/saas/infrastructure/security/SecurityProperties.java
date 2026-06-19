package com.lumira.saas.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "saas.security")
public class SecurityProperties {

    private String jwtSecret = "";
    private String fieldSecret = "";
    private String issuer = "saas-foundation";
    private long idleTimeoutSeconds = 1800;
    private long accessTokenExpireSeconds = 1800;
    private long refreshTokenExpireSeconds = 604800;
    private boolean allowMultiDeviceLogin = true;
    private boolean captchaEnabled = false;
    private boolean loginCaptchaEnabled = false;
    private String captchaType = "IMAGE";
    private long loginCaptchaExpireSeconds = 300;
    private long loginDefenseWindowMinutes = 5;
    private long loginMaxValidationAttempts = 100;
    private long loginMaxFailureCount = 10;
    private long verificationCodeExpireSeconds = 300;
    private long verificationCodeCooldownSeconds = 60;
    private long passwordMinLength = 6;
    private boolean passwordRequireUppercase = false;
    private boolean passwordRequireLowercase = false;
    private boolean passwordRequireSpecialCharacter = false;
    private boolean passwordAllowConsecutiveCharacters = true;
    private List<String> permitPaths = new ArrayList<>(List.of("/api/v1/public/**"));

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public String getFieldSecret() {
        return fieldSecret;
    }

    public void setFieldSecret(String fieldSecret) {
        this.fieldSecret = fieldSecret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public long getAccessTokenExpireSeconds() {
        return accessTokenExpireSeconds;
    }

    public void setAccessTokenExpireSeconds(long accessTokenExpireSeconds) {
        this.accessTokenExpireSeconds = accessTokenExpireSeconds;
    }

    public long getIdleTimeoutSeconds() {
        return idleTimeoutSeconds;
    }

    public void setIdleTimeoutSeconds(long idleTimeoutSeconds) {
        this.idleTimeoutSeconds = idleTimeoutSeconds;
    }

    public long getRefreshTokenExpireSeconds() {
        return refreshTokenExpireSeconds;
    }

    public void setRefreshTokenExpireSeconds(long refreshTokenExpireSeconds) {
        this.refreshTokenExpireSeconds = refreshTokenExpireSeconds;
    }

    public boolean isAllowMultiDeviceLogin() {
        return allowMultiDeviceLogin;
    }

    public void setAllowMultiDeviceLogin(boolean allowMultiDeviceLogin) {
        this.allowMultiDeviceLogin = allowMultiDeviceLogin;
    }

    public boolean isCaptchaEnabled() {
        return captchaEnabled;
    }

    public void setCaptchaEnabled(boolean captchaEnabled) {
        this.captchaEnabled = captchaEnabled;
    }

    public boolean isLoginCaptchaEnabled() {
        return loginCaptchaEnabled;
    }

    public void setLoginCaptchaEnabled(boolean loginCaptchaEnabled) {
        this.loginCaptchaEnabled = loginCaptchaEnabled;
    }

    public long getLoginCaptchaExpireSeconds() {
        return loginCaptchaExpireSeconds;
    }

    public void setLoginCaptchaExpireSeconds(long loginCaptchaExpireSeconds) {
        this.loginCaptchaExpireSeconds = loginCaptchaExpireSeconds;
    }

    public long getLoginDefenseWindowMinutes() {
        return loginDefenseWindowMinutes;
    }

    public void setLoginDefenseWindowMinutes(long loginDefenseWindowMinutes) {
        this.loginDefenseWindowMinutes = loginDefenseWindowMinutes;
    }

    public long getLoginMaxValidationAttempts() {
        return loginMaxValidationAttempts;
    }

    public void setLoginMaxValidationAttempts(long loginMaxValidationAttempts) {
        this.loginMaxValidationAttempts = loginMaxValidationAttempts;
    }

    public long getLoginMaxFailureCount() {
        return loginMaxFailureCount;
    }

    public void setLoginMaxFailureCount(long loginMaxFailureCount) {
        this.loginMaxFailureCount = loginMaxFailureCount;
    }

    public long getVerificationCodeExpireSeconds() {
        return verificationCodeExpireSeconds;
    }

    public void setVerificationCodeExpireSeconds(long verificationCodeExpireSeconds) {
        this.verificationCodeExpireSeconds = verificationCodeExpireSeconds;
    }

    public long getVerificationCodeCooldownSeconds() {
        return verificationCodeCooldownSeconds;
    }

    public void setVerificationCodeCooldownSeconds(long verificationCodeCooldownSeconds) {
        this.verificationCodeCooldownSeconds = verificationCodeCooldownSeconds;
    }

    public long getPasswordMinLength() {
        return passwordMinLength;
    }

    public void setPasswordMinLength(long passwordMinLength) {
        this.passwordMinLength = passwordMinLength;
    }

    public boolean isPasswordRequireUppercase() {
        return passwordRequireUppercase;
    }

    public void setPasswordRequireUppercase(boolean passwordRequireUppercase) {
        this.passwordRequireUppercase = passwordRequireUppercase;
    }

    public boolean isPasswordRequireLowercase() {
        return passwordRequireLowercase;
    }

    public void setPasswordRequireLowercase(boolean passwordRequireLowercase) {
        this.passwordRequireLowercase = passwordRequireLowercase;
    }

    public boolean isPasswordRequireSpecialCharacter() {
        return passwordRequireSpecialCharacter;
    }

    public void setPasswordRequireSpecialCharacter(boolean passwordRequireSpecialCharacter) {
        this.passwordRequireSpecialCharacter = passwordRequireSpecialCharacter;
    }

    public boolean isPasswordAllowConsecutiveCharacters() {
        return passwordAllowConsecutiveCharacters;
    }

    public void setPasswordAllowConsecutiveCharacters(boolean passwordAllowConsecutiveCharacters) {
        this.passwordAllowConsecutiveCharacters = passwordAllowConsecutiveCharacters;
    }

    public List<String> getPermitPaths() {
        return permitPaths;
    }

    public void setPermitPaths(List<String> permitPaths) {
        this.permitPaths = permitPaths;
    }

    public String getCaptchaType() {
        return captchaType;
    }

    public void setCaptchaType(String captchaType) {
        this.captchaType = captchaType;
    }
}
