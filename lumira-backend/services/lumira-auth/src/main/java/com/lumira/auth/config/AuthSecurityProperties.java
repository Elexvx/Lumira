package com.lumira.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "saas.security")
public class AuthSecurityProperties {

    private String jwtSecret = "replace_me";
    private String issuer = "saas-auth";
    private long idleTimeoutSeconds = 1800;
    private long accessTokenExpireSeconds = 1800;
    private long refreshTokenExpireSeconds = 604800;
    private boolean allowMultiDeviceLogin = true;
    private boolean captchaEnabled = false;
    private String captchaType = "IMAGE";
    private long loginDefenseWindowMinutes = 5;
    private long loginMaxValidationAttempts = 100;
    private long loginMaxFailureCount = 10;
    private boolean allowUnsafeDefaultAdminLogin = false;
    private long permissionSnapshotVersionCacheTtlSeconds = 30;
    private long permissionSnapshotVersionCacheMaxEntries = 100_000;
    private long authBootstrapCacheTtlSeconds = 5;
    private long loginCapabilitiesCacheTtlSeconds = 5;
    private List<String> permitPaths = new ArrayList<>();

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public long getIdleTimeoutSeconds() {
        return idleTimeoutSeconds;
    }

    public void setIdleTimeoutSeconds(long idleTimeoutSeconds) {
        this.idleTimeoutSeconds = idleTimeoutSeconds;
    }

    public long getAccessTokenExpireSeconds() {
        return accessTokenExpireSeconds;
    }

    public void setAccessTokenExpireSeconds(long accessTokenExpireSeconds) {
        this.accessTokenExpireSeconds = accessTokenExpireSeconds;
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

    public String getCaptchaType() {
        return captchaType;
    }

    public void setCaptchaType(String captchaType) {
        this.captchaType = captchaType;
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

    public boolean isAllowUnsafeDefaultAdminLogin() {
        return allowUnsafeDefaultAdminLogin;
    }

    public void setAllowUnsafeDefaultAdminLogin(boolean allowUnsafeDefaultAdminLogin) {
        this.allowUnsafeDefaultAdminLogin = allowUnsafeDefaultAdminLogin;
    }

    public long getPermissionSnapshotVersionCacheTtlSeconds() {
        return permissionSnapshotVersionCacheTtlSeconds;
    }

    public void setPermissionSnapshotVersionCacheTtlSeconds(long permissionSnapshotVersionCacheTtlSeconds) {
        this.permissionSnapshotVersionCacheTtlSeconds = permissionSnapshotVersionCacheTtlSeconds;
    }

    public long getPermissionSnapshotVersionCacheMaxEntries() {
        return permissionSnapshotVersionCacheMaxEntries;
    }

    public void setPermissionSnapshotVersionCacheMaxEntries(long permissionSnapshotVersionCacheMaxEntries) {
        this.permissionSnapshotVersionCacheMaxEntries = permissionSnapshotVersionCacheMaxEntries;
    }

    public long getAuthBootstrapCacheTtlSeconds() {
        return authBootstrapCacheTtlSeconds;
    }

    public void setAuthBootstrapCacheTtlSeconds(long authBootstrapCacheTtlSeconds) {
        this.authBootstrapCacheTtlSeconds = authBootstrapCacheTtlSeconds;
    }

    public long getLoginCapabilitiesCacheTtlSeconds() {
        return loginCapabilitiesCacheTtlSeconds;
    }

    public void setLoginCapabilitiesCacheTtlSeconds(long loginCapabilitiesCacheTtlSeconds) {
        this.loginCapabilitiesCacheTtlSeconds = loginCapabilitiesCacheTtlSeconds;
    }

    public List<String> getPermitPaths() {
        return permitPaths;
    }

    public void setPermitPaths(List<String> permitPaths) {
        this.permitPaths = permitPaths;
    }
}
