package com.lumira.saas.modules.system.verification;

import com.lumira.api.system.WechatLoginSettingsDTO;
import com.lumira.common.security.FieldCryptoService;
import com.lumira.saas.modules.system.config.entity.SysConfigEntity;
import com.lumira.saas.modules.system.config.mapper.SysConfigMapper;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.vo.SystemVO;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Service
public class WechatLoginSettingsService {

    private static final long SETTINGS_CACHE_TTL_MS = 30_000L;
    private static final String ENABLED_KEY = "verification.wechat-login.enabled";
    private static final String APP_ID_KEY = "verification.wechat-login.app-id";
    private static final String APP_SECRET_KEY = "verification.wechat-login.app-secret";
    private static final String REDIRECT_URI_KEY = "verification.wechat-login.redirect-uri";
    private static final String STATE_EXPIRE_MINUTES_KEY = "verification.wechat-login.state-expire-minutes";

    private final SysConfigMapper sysConfigMapper;
    private final WechatLoginProperties properties;
    private final FieldCryptoService fieldCryptoService;
    private final Cache<String, WechatLoginSettingsRecord> settingsCache;
    private final Cache<String, CompletableFuture<WechatLoginSettingsRecord>> settingsLoadInFlight;

    public WechatLoginSettingsService(SysConfigMapper sysConfigMapper, WechatLoginProperties properties, FieldCryptoService fieldCryptoService) {
        this.sysConfigMapper = sysConfigMapper;
        this.properties = properties;
        this.fieldCryptoService = fieldCryptoService;
        this.settingsCache = CacheBuilder.newBuilder()
                .maximumSize(2048)
                .expireAfterWrite(SETTINGS_CACHE_TTL_MS, TimeUnit.MILLISECONDS)
                .build();
        this.settingsLoadInFlight = CacheBuilder.newBuilder()
                .maximumSize(2048)
                .expireAfterWrite(SETTINGS_CACHE_TTL_MS, TimeUnit.MILLISECONDS)
                .build();
    }

    public WechatLoginSettingsRecord loadSettings(Long tenantId) {
        String cacheKey = cacheKey(tenantId);
        WechatLoginSettingsRecord cached = settingsCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }
        return loadSettingsWithSingleFlight(tenantId, cacheKey);
    }

    private WechatLoginSettingsRecord loadSettingsWithSingleFlight(Long tenantId, String cacheKey) {
        try {
            CompletableFuture<WechatLoginSettingsRecord> future = settingsLoadInFlight.get(
                    cacheKey,
                    () -> CompletableFuture.completedFuture(loadSettingsFresh(tenantId, cacheKey))
            );
            WechatLoginSettingsRecord loaded = future.join();
            settingsLoadInFlight.invalidate(cacheKey);
            return loaded;
        } catch (ExecutionException ex) {
            settingsLoadInFlight.invalidate(cacheKey);
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Failed to load Wechat login settings", cause);
        } catch (RuntimeException ex) {
            settingsLoadInFlight.invalidate(cacheKey);
            throw ex;
        }
    }

    private WechatLoginSettingsRecord loadSettingsFresh(Long tenantId, String cacheKey) {
        WechatLoginSettingsRecord cached = settingsCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }
        Map<String, String> values = loadConfigValuesByKeys(tenantId, keys());
        boolean enabled = Boolean.parseBoolean(defaultIfBlank(values.get(ENABLED_KEY), String.valueOf(properties.isEnabled())));
        String appId = defaultIfBlank(values.get(APP_ID_KEY), properties.getAppId());
        String appSecret = defaultIfBlank(values.get(APP_SECRET_KEY), properties.getAppSecret());
        String redirectUri = defaultIfBlank(values.get(REDIRECT_URI_KEY), properties.getRedirectUri());
        int stateExpireMinutes = parseInt(defaultIfBlank(values.get(STATE_EXPIRE_MINUTES_KEY), String.valueOf(properties.getStateExpireMinutes())), 10);
        stateExpireMinutes = Math.max(1, stateExpireMinutes);
        boolean configured = StringUtils.hasText(appId)
                && StringUtils.hasText(appSecret)
                && StringUtils.hasText(redirectUri);
        WechatLoginSettingsRecord record = new WechatLoginSettingsRecord(enabled, appId, appSecret, redirectUri, stateExpireMinutes, configured);
        settingsCache.put(cacheKey, record);
        return record;
    }

    public boolean isAvailable(Long tenantId) {
        return loadSettings(tenantId).configured();
    }

    public SystemVO.WechatLoginSettingsVO getSettings(Long tenantId) {
        WechatLoginSettingsRecord record = loadSettings(tenantId);
        SystemVO.WechatLoginSettingsVO vo = new SystemVO.WechatLoginSettingsVO();
        vo.setEnabled(record.enabled());
        vo.setAppId(record.appId());
        vo.setAppSecret("");
        vo.setRedirectUri(record.redirectUri());
        vo.setStateExpireMinutes(record.stateExpireMinutes());
        vo.setConfigured(record.configured());
        vo.setAppSecretConfigured(StringUtils.hasText(record.appSecret()));
        return vo;
    }

    public WechatLoginSettingsDTO getInternalSettings(Long tenantId) {
        WechatLoginSettingsRecord record = loadSettings(tenantId);
        return new WechatLoginSettingsDTO(
                record.enabled(),
                record.appId(),
                record.appSecret(),
                record.redirectUri(),
                record.stateExpireMinutes(),
                record.configured(),
                StringUtils.hasText(record.appSecret())
        );
    }

    public SystemVO.WechatLoginSettingsVO updateSettings(Long tenantId, Long operatorId, SystemDTO.WechatLoginSettingsRequest request) {
        WechatLoginSettingsRecord current = loadSettings(tenantId);
        boolean enabled = request.getEnabled() == null ? current.enabled() : Boolean.TRUE.equals(request.getEnabled());
        String appId = sanitizeText(request.getAppId(), current.appId());
        String existingSecret = defaultIfBlank(current.appSecret(), "");
        String appSecret = StringUtils.hasText(request.getAppSecret()) ? request.getAppSecret().trim() : existingSecret;
        String redirectUri = sanitizeText(request.getRedirectUri(), current.redirectUri());
        int stateExpireMinutes = request.getStateExpireMinutes() == null
                ? current.stateExpireMinutes()
                : Math.max(1, request.getStateExpireMinutes());

        upsertConfigValue(tenantId, ENABLED_KEY, "微信登录启用", String.valueOf(enabled), "是否启用微信扫码登录", operatorId);
        upsertConfigValue(tenantId, APP_ID_KEY, "微信 AppID", appId, "微信开放平台网站应用 AppID", operatorId);
        upsertConfigValue(tenantId, APP_SECRET_KEY, "微信 AppSecret", appSecret, "微信开放平台网站应用 AppSecret", operatorId);
        upsertConfigValue(tenantId, REDIRECT_URI_KEY, "微信登录回调地址", redirectUri, "微信开放平台授权回调地址", operatorId);
        upsertConfigValue(tenantId, STATE_EXPIRE_MINUTES_KEY, "微信登录状态有效期", String.valueOf(stateExpireMinutes), "微信登录 state 缓存有效期，单位分钟", operatorId);
        invalidateTenantCache(tenantId);
        return getSettings(tenantId);
    }

    public SystemVO.WechatLoginSettingsVO resetSettings(Long tenantId, Long operatorId) {
        upsertConfigValue(tenantId, ENABLED_KEY, "微信登录启用", "false", "是否启用微信扫码登录", operatorId);
        upsertConfigValue(tenantId, APP_ID_KEY, "微信 AppID", "", "微信开放平台网站应用 AppID", operatorId);
        upsertConfigValue(tenantId, APP_SECRET_KEY, "微信 AppSecret", "", "微信开放平台网站应用 AppSecret", operatorId);
        upsertConfigValue(tenantId, REDIRECT_URI_KEY, "微信登录回调地址", "", "微信开放平台授权回调地址", operatorId);
        upsertConfigValue(tenantId, STATE_EXPIRE_MINUTES_KEY, "微信登录状态有效期", String.valueOf(Math.max(1, properties.getStateExpireMinutes())), "微信登录 state 缓存有效期，单位分钟", operatorId);
        invalidateTenantCache(tenantId);
        return getSettings(tenantId);
    }

    private void upsertConfigValue(Long tenantId, String configKey, String configName, String configValue, String remark, Long operatorId) {
        SysConfigEntity entity = new SysConfigEntity();
        entity.setTenantId(effectiveTenantId(tenantId));
        entity.setConfigKey(configKey);
        entity.setConfigName(configName);
        entity.setConfigValue(encryptConfigValue(configKey, normalizeConfigText(configValue)));
        entity.setIsSystem(0);
        entity.setRemark(remark);
        entity.setCreatedBy(operatorId);
        entity.setUpdatedBy(operatorId);
        sysConfigMapper.upsertPlatformConfig(entity);
    }

    private Map<String, String> loadConfigValuesByKeys(Long tenantId, List<String> keys) {
        Long effectiveTenantId = effectiveTenantId(tenantId);
        List<SysConfigEntity> rows = sysConfigMapper.listEffectiveValues(effectiveTenantId, "PLATFORM", keys);
        Map<String, String> valueByKey = new LinkedHashMap<>();
        for (SysConfigEntity row : rows) {
            String configKey = row.getConfigKey();
            if (!valueByKey.containsKey(configKey)) {
                valueByKey.put(configKey, normalizeConfigText(decryptConfigValue(configKey, normalizeConfigTextRaw(row.getConfigValue()))));
            }
        }
        return valueByKey;
    }

    private void invalidateTenantCache(Long tenantId) {
        String cacheKey = cacheKey(tenantId);
        settingsCache.invalidate(cacheKey);
        settingsLoadInFlight.invalidate(cacheKey);
    }

    private String cacheKey(Long tenantId) {
        return effectiveTenantId(tenantId) + ":" + String.join(",", keys());
    }

    private String encryptConfigValue(String configKey, String configValue) {
        return APP_SECRET_KEY.equals(configKey) ? fieldCryptoService.encrypt(configValue) : configValue;
    }

    private String decryptConfigValue(String configKey, String configValue) {
        return APP_SECRET_KEY.equals(configKey) ? fieldCryptoService.decrypt(configValue) : configValue;
    }

    private List<String> keys() {
        return List.of(ENABLED_KEY, APP_ID_KEY, APP_SECRET_KEY, REDIRECT_URI_KEY, STATE_EXPIRE_MINUTES_KEY);
    }

    private Long effectiveTenantId(Long tenantId) {
        return tenantId == null ? com.lumira.common.constant.PlatformConstants.PLATFORM_TENANT_ID : tenantId;
    }

    private String sanitizeText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String normalizeConfigText(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String normalizeConfigTextRaw(Object value) {
        return value == null ? "" : value.toString();
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public record WechatLoginSettingsRecord(
            boolean enabled,
            String appId,
            String appSecret,
            String redirectUri,
            int stateExpireMinutes,
            boolean configured
    ) {
    }
}
