package com.lumira.auth.service;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SecuritySettingsDTO;
import com.lumira.auth.config.AuthSecurityProperties;
import com.lumira.common.constant.PlatformConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service("authSecuritySettingsService")
public class SecuritySettingsService {

    private static final Logger log = LoggerFactory.getLogger(SecuritySettingsService.class);
    private static final long SETTINGS_CACHE_TTL_MS = 30_000L;

    private final AuthSecurityProperties securityProperties;
    private final SystemInternalApi systemInternalApi;
    private volatile SecuritySettingsDTO cachedSettings;
    private volatile long cachedSettingsUntilMillis;

    public SecuritySettingsService(AuthSecurityProperties securityProperties, SystemInternalApi systemInternalApi) {
        this.securityProperties = securityProperties;
        this.systemInternalApi = systemInternalApi;
    }

    public long getIdleTimeoutSeconds() {
        return longValue(loadSettings().idleTimeoutSeconds(), securityProperties.getIdleTimeoutSeconds());
    }

    public long getAccessTokenExpireSeconds() {
        return longValue(loadSettings().accessTokenExpireSeconds(), securityProperties.getAccessTokenExpireSeconds());
    }

    public long getRefreshTokenExpireSeconds() {
        return longValue(loadSettings().refreshTokenExpireSeconds(), securityProperties.getRefreshTokenExpireSeconds());
    }

    public boolean isAllowMultiDeviceLogin() {
        return booleanValue(loadSettings().allowMultiDeviceLogin(), securityProperties.isAllowMultiDeviceLogin());
    }

    public boolean isCaptchaEnabled() {
        return booleanValue(loadSettings().captchaEnabled(), securityProperties.isCaptchaEnabled());
    }

    public String getCaptchaType() {
        String captchaType = loadSettings().captchaType();
        return captchaType == null || captchaType.isBlank() ? securityProperties.getCaptchaType() : captchaType;
    }

    public long getLoginDefenseWindowMinutes() {
        return longValue(loadSettings().loginDefenseWindowMinutes(), securityProperties.getLoginDefenseWindowMinutes());
    }

    public long getLoginMaxValidationAttempts() {
        return longValue(loadSettings().loginMaxValidationAttempts(), securityProperties.getLoginMaxValidationAttempts());
    }

    public long getLoginMaxFailureCount() {
        return longValue(loadSettings().loginMaxFailureCount(), securityProperties.getLoginMaxFailureCount());
    }

    public long getVerificationCodeExpireSeconds() {
        return longValue(loadSettings().verificationCodeExpireSeconds(), 300);
    }

    public long getVerificationCodeCooldownSeconds() {
        return longValue(loadSettings().verificationCodeCooldownSeconds(), 60);
    }

    public com.lumira.api.system.SecuritySettingsDTO snapshot() {
        return loadSettings();
    }

    private SecuritySettingsDTO loadSettings() {
        long now = System.currentTimeMillis();
        SecuritySettingsDTO cached = cachedSettings;
        if (cached != null && now < cachedSettingsUntilMillis) {
            return cached;
        }
        synchronized (this) {
            now = System.currentTimeMillis();
            cached = cachedSettings;
            if (cached != null && now < cachedSettingsUntilMillis) {
                return cached;
            }
            SecuritySettingsDTO loaded = loadSettingsFresh();
            cachedSettings = loaded;
            cachedSettingsUntilMillis = now + SETTINGS_CACHE_TTL_MS;
            return loaded;
        }
    }

    private SecuritySettingsDTO loadSettingsFresh() {
        try {
            SecuritySettingsDTO settings = systemInternalApi.securitySettings(PlatformConstants.PLATFORM_TENANT_ID);
            if (settings != null) {
                return settings;
            }
        } catch (Exception ex) {
            log.warn("Failed to load security settings from system-service, falling back to auth local properties", ex);
        }
        return new SecuritySettingsDTO(
                securityProperties.getIdleTimeoutSeconds(),
                securityProperties.getAccessTokenExpireSeconds(),
                securityProperties.getRefreshTokenExpireSeconds(),
                securityProperties.isAllowMultiDeviceLogin(),
                securityProperties.isCaptchaEnabled(),
                securityProperties.getCaptchaType(),
                securityProperties.getLoginDefenseWindowMinutes(),
                securityProperties.getLoginMaxValidationAttempts(),
                securityProperties.getLoginMaxFailureCount(),
                300L,
                60L
        );
    }

    private long longValue(Long value, long fallback) {
        return value == null ? fallback : value;
    }

    private boolean booleanValue(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }
}
