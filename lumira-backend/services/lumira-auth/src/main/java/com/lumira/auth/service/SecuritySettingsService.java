package com.lumira.auth.service;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SecuritySettingsDTO;
import com.lumira.auth.config.AuthSecurityProperties;
import com.lumira.common.runtime.ReadModelVersionCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service("authSecuritySettingsService")
public class SecuritySettingsService {

    private static final Logger log = LoggerFactory.getLogger(SecuritySettingsService.class);
    private static final long SETTINGS_CACHE_TTL_MS = 30_000L;
    private static final long READ_MODEL_VERSION_CACHE_TTL_MS = 2_000L;
    private static final String PUBLIC_BOOTSTRAP_CACHE_KEY = "auth:platform/public-bootstrap";
    private static final String READ_MODEL_CONTEXT_PLATFORM = "platform";
    private static final String READ_MODEL_SCOPE_PUBLIC_BOOTSTRAP = "public-bootstrap";

    private final AuthSecurityProperties securityProperties;
    private final SystemInternalApi systemInternalApi;
    private final ReadModelVersionCache readModelVersionCache;
    private volatile CachedSecuritySettings cachedSettings;

    public SecuritySettingsService(AuthSecurityProperties securityProperties, SystemInternalApi systemInternalApi) {
        this(securityProperties, systemInternalApi, new ReadModelVersionCache(READ_MODEL_VERSION_CACHE_TTL_MS));
    }

    @Autowired
    public SecuritySettingsService(
            AuthSecurityProperties securityProperties,
            SystemInternalApi systemInternalApi,
            ReadModelVersionCache readModelVersionCache
    ) {
        this.securityProperties = securityProperties;
        this.systemInternalApi = systemInternalApi;
        this.readModelVersionCache = readModelVersionCache;
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
        Long publicBootstrapVersion = currentPublicBootstrapVersion();
        CachedSecuritySettings cached = cachedSettings;
        if (isSettingsCacheCurrent(cached, publicBootstrapVersion)) {
            return cached.settings();
        }
        synchronized (this) {
            publicBootstrapVersion = currentPublicBootstrapVersion();
            cached = cachedSettings;
            if (isSettingsCacheCurrent(cached, publicBootstrapVersion)) {
                return cached.settings();
            }
            SecuritySettingsDTO loaded = loadSettingsFresh();
            cachedSettings = new CachedSecuritySettings(
                    loaded,
                    publicBootstrapVersion,
                    System.currentTimeMillis() + SETTINGS_CACHE_TTL_MS
            );
            return loaded;
        }
    }

    private boolean isSettingsCacheCurrent(CachedSecuritySettings cached, Long publicBootstrapVersion) {
        if (cached == null || cached.settings() == null) {
            return false;
        }
        if (publicBootstrapVersion == null) {
            return !cached.isExpired();
        }
        return Objects.equals(cached.publicBootstrapVersion(), publicBootstrapVersion);
    }

    private Long currentPublicBootstrapVersion() {
        try {
            return readModelVersionCache.readValue(
                    PUBLIC_BOOTSTRAP_CACHE_KEY,
                    READ_MODEL_VERSION_CACHE_TTL_MS,
                    () -> systemInternalApi.readModelVersion(
                            READ_MODEL_CONTEXT_PLATFORM,
                            READ_MODEL_SCOPE_PUBLIC_BOOTSTRAP
                    )
            );
        } catch (Exception ex) {
            log.debug("Failed to load platform public bootstrap version for auth security settings cache", ex);
            return null;
        }
    }

    private SecuritySettingsDTO loadSettingsFresh() {
        try {
            SecuritySettingsDTO settings = systemInternalApi.securitySettings();
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

    private record CachedSecuritySettings(
            SecuritySettingsDTO settings,
            Long publicBootstrapVersion,
            long expiresAtMillis
    ) {
        private boolean isExpired() {
            return System.currentTimeMillis() > expiresAtMillis;
        }
    }

}
