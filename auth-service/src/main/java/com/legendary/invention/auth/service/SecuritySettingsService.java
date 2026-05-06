package com.legendary.invention.auth.service;

import com.legendary.invention.auth.config.SecurityProperties;
import org.springframework.stereotype.Service;

@Service
public class SecuritySettingsService {

    private final SecurityProperties securityProperties;

    public SecuritySettingsService(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    public long getIdleTimeoutSeconds() {
        return securityProperties.getIdleTimeoutSeconds();
    }

    public long getAccessTokenExpireSeconds() {
        return securityProperties.getAccessTokenExpireSeconds();
    }

    public long getRefreshTokenExpireSeconds() {
        return securityProperties.getRefreshTokenExpireSeconds();
    }

    public boolean isAllowMultiDeviceLogin() {
        return securityProperties.isAllowMultiDeviceLogin();
    }

    public boolean isCaptchaEnabled() {
        return securityProperties.isCaptchaEnabled();
    }

    public String getCaptchaType() {
        return securityProperties.getCaptchaType();
    }

    public long getLoginDefenseWindowMinutes() {
        return securityProperties.getLoginDefenseWindowMinutes();
    }

    public long getLoginMaxValidationAttempts() {
        return securityProperties.getLoginMaxValidationAttempts();
    }

    public long getLoginMaxFailureCount() {
        return securityProperties.getLoginMaxFailureCount();
    }
}
