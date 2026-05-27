package com.legendary.invention.auth.service;

import com.legendary.invention.api.client.SystemInternalApi;
import com.legendary.invention.api.system.SecuritySettingsDTO;
import com.legendary.invention.auth.config.SecurityProperties;
import com.legendary.invention.common.constant.PlatformConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SecuritySettingsService {

    private static final Logger log = LoggerFactory.getLogger(SecuritySettingsService.class);

    private final SecurityProperties securityProperties;
    private final SystemInternalApi systemInternalApi;

    public SecuritySettingsService(SecurityProperties securityProperties, SystemInternalApi systemInternalApi) {
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

    private SecuritySettingsDTO loadSettings() {
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
