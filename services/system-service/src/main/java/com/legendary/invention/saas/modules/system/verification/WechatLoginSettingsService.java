package com.legendary.invention.saas.modules.system.verification;

import com.legendary.invention.api.system.WechatLoginSettingsDTO;
import com.legendary.invention.saas.modules.auth.config.WechatLoginProperties;
import com.legendary.invention.saas.modules.system.config.entity.SysConfigEntity;
import com.legendary.invention.saas.modules.system.config.mapper.SysConfigMapper;
import com.legendary.invention.saas.modules.system.dto.SystemDTO;
import com.legendary.invention.saas.modules.system.vo.SystemVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WechatLoginSettingsService {

    private static final String ENABLED_KEY = "verification.wechat-login.enabled";
    private static final String APP_ID_KEY = "verification.wechat-login.app-id";
    private static final String APP_SECRET_KEY = "verification.wechat-login.app-secret";
    private static final String REDIRECT_URI_KEY = "verification.wechat-login.redirect-uri";
    private static final String STATE_EXPIRE_MINUTES_KEY = "verification.wechat-login.state-expire-minutes";

    private final SysConfigMapper sysConfigMapper;
    private final WechatLoginProperties properties;

    public WechatLoginSettingsService(SysConfigMapper sysConfigMapper, WechatLoginProperties properties) {
        this.sysConfigMapper = sysConfigMapper;
        this.properties = properties;
    }

    public WechatLoginSettingsRecord loadSettings(Long tenantId) {
        Map<String, String> values = loadConfigValuesByKeys(tenantId, keys());
        boolean enabled = Boolean.parseBoolean(defaultIfBlank(values.get(ENABLED_KEY), String.valueOf(properties.isEnabled())));
        String appId = defaultIfBlank(values.get(APP_ID_KEY), properties.getAppId());
        String appSecret = defaultIfBlank(values.get(APP_SECRET_KEY), properties.getAppSecret());
        String redirectUri = defaultIfBlank(values.get(REDIRECT_URI_KEY), properties.getRedirectUri());
        int stateExpireMinutes = parseInt(defaultIfBlank(values.get(STATE_EXPIRE_MINUTES_KEY), String.valueOf(properties.getStateExpireMinutes())), 10);
        stateExpireMinutes = Math.max(1, stateExpireMinutes);
        boolean configured = enabled
                && StringUtils.hasText(appId)
                && StringUtils.hasText(appSecret)
                && StringUtils.hasText(redirectUri);
        return new WechatLoginSettingsRecord(enabled, appId, appSecret, redirectUri, stateExpireMinutes, configured);
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
        return getSettings(tenantId);
    }

    private void upsertConfigValue(Long tenantId, String configKey, String configName, String configValue, String remark, Long operatorId) {
        SysConfigEntity entity = new SysConfigEntity();
        entity.setTenantId(effectiveTenantId(tenantId));
        entity.setConfigKey(configKey);
        entity.setConfigName(configName);
        entity.setConfigValue(normalizeConfigText(configValue));
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
                valueByKey.put(configKey, normalizeConfigText(row.getConfigValue()));
            }
        }
        return valueByKey;
    }

    private List<String> keys() {
        return List.of(ENABLED_KEY, APP_ID_KEY, APP_SECRET_KEY, REDIRECT_URI_KEY, STATE_EXPIRE_MINUTES_KEY);
    }

    private Long effectiveTenantId(Long tenantId) {
        return tenantId == null ? com.legendary.invention.common.constant.PlatformConstants.PLATFORM_TENANT_ID : tenantId;
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
