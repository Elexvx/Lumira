package com.lumira.saas.modules.system.verification;

import com.lumira.api.system.WechatLoginSettingsDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.FieldCryptoService;
import com.lumira.saas.infrastructure.readmodel.ReadModelVersionService;
import com.lumira.saas.infrastructure.readmodel.ReadModelEventKey;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.system.config.entity.SysConfigEntity;
import com.lumira.saas.modules.system.config.mapper.SysConfigMapper;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.vo.SystemVO;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnLumiraControlPlaneEnabled
public class WechatLoginSettingsService {
    private static final Logger log = LoggerFactory.getLogger(WechatLoginSettingsService.class);

    private static final long SETTINGS_CACHE_TTL_MS = 30_000L;
    private static final long READ_MODEL_VERSION_CACHE_TTL_MS = 2_000L;
    private static final String ENABLED_KEY = "verification.wechat-login.enabled";
    private static final String APP_ID_KEY = "verification.wechat-login.app-id";
    private static final String APP_SECRET_KEY = "verification.wechat-login.app-secret";
    private static final String REDIRECT_URI_KEY = "verification.wechat-login.redirect-uri";
    private static final String STATE_EXPIRE_MINUTES_KEY = "verification.wechat-login.state-expire-minutes";
    private static final String GLOBAL_SETTINGS_CACHE_KEY = "global";
    private static final String READ_MODEL_CONTEXT_PLATFORM = "platform";
    private static final String READ_MODEL_SCOPE_PUBLIC_BOOTSTRAP = "public-bootstrap";
    private static final String PERMISSION_VERIFICATION_MANAGE = "system:verification:manage";
    private static final String PERMISSION_CONFIG_UPDATE = "system:config:update";

    private final SysConfigMapper sysConfigMapper;
    private final WechatLoginProperties properties;
    private final FieldCryptoService fieldCryptoService;
    private final ReadModelVersionService readModelVersionService;
    private final SessionAuthenticationService sessionAuthenticationService;
    private final boolean enforceTrustedUserResolution;
    private final Cache<String, WechatLoginSettingsRecord> settingsCache;
    private final Cache<String, CompletableFuture<WechatLoginSettingsRecord>> settingsLoadInFlight;
    private final ThreadLocal<CurrentUser> currentUpdateOperator = new ThreadLocal<>();
    private volatile CachedReadModelVersion cachedPublicBootstrapVersion;

    @Autowired
    public WechatLoginSettingsService(
            SysConfigMapper sysConfigMapper,
            WechatLoginProperties properties,
            FieldCryptoService fieldCryptoService,
            ReadModelVersionService readModelVersionService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(
                sysConfigMapper,
                properties,
                fieldCryptoService,
                readModelVersionService,
                sessionAuthenticationService,
                true
        );
    }

    private WechatLoginSettingsService(
            SysConfigMapper sysConfigMapper,
            WechatLoginProperties properties,
            FieldCryptoService fieldCryptoService,
            ReadModelVersionService readModelVersionService,
            SessionAuthenticationService sessionAuthenticationService,
            boolean enforceTrustedUserResolution
    ) {
        this.sysConfigMapper = sysConfigMapper;
        this.properties = properties;
        this.fieldCryptoService = fieldCryptoService;
        this.readModelVersionService = readModelVersionService;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
        this.settingsCache = CacheBuilder.newBuilder()
                .maximumSize(2048)
                .expireAfterWrite(SETTINGS_CACHE_TTL_MS, TimeUnit.MILLISECONDS)
                .build();
        this.settingsLoadInFlight = CacheBuilder.newBuilder()
                .maximumSize(2048)
                .expireAfterWrite(SETTINGS_CACHE_TTL_MS, TimeUnit.MILLISECONDS)
                .build();
    }

    public WechatLoginSettingsService(
            SysConfigMapper sysConfigMapper,
            WechatLoginProperties properties,
            FieldCryptoService fieldCryptoService,
            ReadModelVersionService readModelVersionService
    ) {
        this(sysConfigMapper, properties, fieldCryptoService, readModelVersionService, null, false);
    }

    public WechatLoginSettingsRecord loadSettings() {
        String cacheKey = cacheKey(currentPublicBootstrapVersion(false));
        WechatLoginSettingsRecord cached = settingsCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }
        return loadSettingsWithSingleFlight(cacheKey);
    }

    public WechatLoginSettingsRecord loadSettingsFresh() {
        return loadSettingsFresh(cacheKey(currentPublicBootstrapVersion(true)), false);
    }

    private WechatLoginSettingsRecord loadSettingsWithSingleFlight(String cacheKey) {
        try {
            CompletableFuture<WechatLoginSettingsRecord> future = settingsLoadInFlight.get(
                    cacheKey,
                    () -> CompletableFuture.completedFuture(loadSettingsFresh(cacheKey, true))
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

    private WechatLoginSettingsRecord loadSettingsFresh(String cacheKey, boolean useCache) {
        if (useCache) {
            WechatLoginSettingsRecord cached = settingsCache.getIfPresent(cacheKey);
            if (cached != null) {
                return cached;
            }
        }
        Map<String, String> values = loadConfigValuesByKeys(keys());
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
        if (useCache) {
            settingsCache.put(cacheKey, record);
        }
        return record;
    }

    public boolean isAvailable() {
        return loadSettings().available();
    }

    public SystemVO.WechatLoginSettingsVO getSettings() {
        WechatLoginSettingsRecord record = loadSettings();
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

    public WechatLoginSettingsDTO getInternalSettings() {
        WechatLoginSettingsRecord record = loadSettings();
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

    @Transactional
    public SystemVO.WechatLoginSettingsVO updateSettings(CurrentUser operator, SystemDTO.WechatLoginSettingsRequest request) {
        CurrentUser trustedOperator = requireTrustedVerificationManager(operator);
        Long operatorId = trustedOperator.getUserId();
        currentUpdateOperator.set(trustedOperator);
        try {
            requireRequest(request, "Wechat login settings request");
            WechatLoginSettingsRecord current = loadSettings();
            boolean enabled = request.getEnabled() == null ? current.enabled() : Boolean.TRUE.equals(request.getEnabled());
            String appId = sanitizeText(request.getAppId(), current.appId());
            String existingSecret = defaultIfBlank(current.appSecret(), "");
            String appSecret = StringUtils.hasText(request.getAppSecret()) ? request.getAppSecret().trim() : existingSecret;
            String redirectUri = sanitizeText(request.getRedirectUri(), current.redirectUri());
            int stateExpireMinutes = request.getStateExpireMinutes() == null
                    ? current.stateExpireMinutes()
                    : Math.max(1, request.getStateExpireMinutes());

            if (enabled && (!StringUtils.hasText(appId) || !StringUtils.hasText(appSecret) || !StringUtils.hasText(redirectUri))) {
                throw new BizException(ErrorCode.BAD_REQUEST, "微信登录启用前必须完整配置 AppID、AppSecret 和回调地址");
            }

            upsertConfigValue(ENABLED_KEY, "微信登录启用", String.valueOf(enabled), "是否启用微信扫码登录", operatorId);
            upsertConfigValue(APP_ID_KEY, "微信 AppID", appId, "微信开放平台网站应用 AppID", operatorId);
            upsertConfigValue(APP_SECRET_KEY, "微信 AppSecret", appSecret, "微信开放平台网站应用 AppSecret", operatorId);
            upsertConfigValue(REDIRECT_URI_KEY, "微信登录回调地址", redirectUri, "微信开放平台授权回调地址", operatorId);
            upsertConfigValue(STATE_EXPIRE_MINUTES_KEY, "微信登录状态有效期", String.valueOf(stateExpireMinutes), "微信登录 state 缓存有效期，单位分钟", operatorId);
            invalidateSettingsCache();
            markPublicBootstrapChanged("wechat-settings-update");
            return getSettings();
        } finally {
            currentUpdateOperator.remove();
        }
    }

    @Transactional
    public SystemVO.WechatLoginSettingsVO resetSettings(CurrentUser operator) {
        CurrentUser trustedOperator = requireTrustedVerificationManager(operator);
        Long operatorId = trustedOperator.getUserId();
        currentUpdateOperator.set(trustedOperator);
        try {
            upsertConfigValue(ENABLED_KEY, "微信登录启用", "false", "是否启用微信扫码登录", operatorId);
            upsertConfigValue(APP_ID_KEY, "微信 AppID", "", "微信开放平台网站应用 AppID", operatorId);
            upsertConfigValue(APP_SECRET_KEY, "微信 AppSecret", "", "微信开放平台网站应用 AppSecret", operatorId);
            upsertConfigValue(REDIRECT_URI_KEY, "微信登录回调地址", "", "微信开放平台授权回调地址", operatorId);
            upsertConfigValue(STATE_EXPIRE_MINUTES_KEY, "微信登录状态有效期", String.valueOf(Math.max(1, properties.getStateExpireMinutes())), "微信登录 state 缓存有效期，单位分钟", operatorId);
            invalidateSettingsCache();
            markPublicBootstrapChanged("wechat-settings-reset");
            return getSettings();
        } finally {
            currentUpdateOperator.remove();
        }
    }

    private void upsertConfigValue(String configKey, String configName, String configValue, String remark, Long operatorId) {
        SysConfigEntity entity = new SysConfigEntity();
        entity.setConfigKey(configKey);
        entity.setConfigName(configName);
        entity.setConfigValue(encryptConfigValue(configKey, normalizeConfigText(configValue)));
        entity.setIsSystem(0);
        entity.setRemark(remark);
        entity.setCreatedBy(operatorId);
        entity.setCreatedByUuid(requireCurrentUpdateOperatorUuid());
        entity.setUpdatedBy(operatorId);
        entity.setUpdatedByUuid(requireCurrentUpdateOperatorUuid());
        sysConfigMapper.upsertPlatformConfig(entity);
    }

    private Map<String, String> loadConfigValuesByKeys(List<String> keys) {
        List<SysConfigEntity> rows = sysConfigMapper.listEffectiveValues("PLATFORM", keys);
        Map<String, String> valueByKey = new LinkedHashMap<>();
        for (SysConfigEntity row : rows) {
            String configKey = row.getConfigKey();
            if (!valueByKey.containsKey(configKey)) {
                valueByKey.put(configKey, normalizeConfigText(decryptConfigValue(configKey, normalizeConfigTextRaw(row.getConfigValue()))));
            }
        }
        return valueByKey;
    }

    private void invalidateSettingsCache() {
        settingsCache.invalidateAll();
        settingsLoadInFlight.invalidateAll();
        cachedPublicBootstrapVersion = null;
    }

    private void markPublicBootstrapChanged(String eventKey) {
        if (readModelVersionService != null) {
            readModelVersionService.bump(
                    READ_MODEL_CONTEXT_PLATFORM,
                    READ_MODEL_SCOPE_PUBLIC_BOOTSTRAP,
                    ReadModelEventKey.unique(eventKey)
            );
        }
    }

    private String cacheKey(Long publicBootstrapVersion) {
        String keysSignature = String.join(",", keys());
        if (publicBootstrapVersion == null) {
            return GLOBAL_SETTINGS_CACHE_KEY + ":" + keysSignature;
        }
        return GLOBAL_SETTINGS_CACHE_KEY + ":v" + publicBootstrapVersion + ":" + keysSignature;
    }

    private Long currentPublicBootstrapVersion(boolean forceRefresh) {
        if (readModelVersionService == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        CachedReadModelVersion cached = cachedPublicBootstrapVersion;
        if (!forceRefresh && cached != null && cached.expiresAtMillis() > now) {
            return cached.version();
        }
        Long version = null;
        try {
            version = readModelVersionService.currentVersion(
                    READ_MODEL_CONTEXT_PLATFORM,
                    READ_MODEL_SCOPE_PUBLIC_BOOTSTRAP
            );
        } catch (Throwable ignored) {
            version = null;
        }
        cachedPublicBootstrapVersion = new CachedReadModelVersion(version, now + READ_MODEL_VERSION_CACHE_TTL_MS);
        return version;
    }

    private String encryptConfigValue(String configKey, String configValue) {
        return APP_SECRET_KEY.equals(configKey) ? fieldCryptoService.encrypt(configValue) : configValue;
    }

    private String decryptConfigValue(String configKey, String configValue) {
        if (!APP_SECRET_KEY.equals(configKey) || !StringUtils.hasText(configValue)) {
            return configValue;
        }
        try {
            return fieldCryptoService.decrypt(configValue);
        } catch (RuntimeException exception) {
            log.warn("Failed to decrypt Wechat login config key {}, treating it as blank", configKey, exception);
            return "";
        }
    }

    private List<String> keys() {
        return List.of(ENABLED_KEY, APP_ID_KEY, APP_SECRET_KEY, REDIRECT_URI_KEY, STATE_EXPIRE_MINUTES_KEY);
    }

    private String sanitizeText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private void requirePositiveOperatorId(Long operatorId) {
        if (operatorId == null || operatorId <= 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Operator id must be a positive number");
        }
    }

    private CurrentUser requireTrustedVerificationManager(CurrentUser operator) {
        CurrentUser trustedOperator = requireTrustedOperator(operator);
        if (!hasConfigManagePermission(trustedOperator)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Missing permission: " + PERMISSION_VERIFICATION_MANAGE);
        }
        return trustedOperator;
    }

    private boolean hasConfigManagePermission(CurrentUser operator) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(operator)) {
            return false;
        }
        if (operator.getPermissions() == null || operator.getPermissions().isEmpty()) {
            return false;
        }
        return operator.getPermissions().contains("*")
                || operator.getPermissions().contains(PERMISSION_VERIFICATION_MANAGE)
                || operator.getPermissions().contains(PERMISSION_CONFIG_UPDATE);
    }

    private CurrentUser requireTrustedOperator(CurrentUser operator) {
        Long simulatedRoleId = normalizeSimulatedRoleId(operator == null ? null : operator.getSimulatedRoleId());
        if (operator != null) {
            operator.setSimulatedRoleId(simulatedRoleId);
        }
        if (sessionAuthenticationService != null) {
            CurrentUser refreshedOperator = requireTrustedAuthenticatedCurrentUser(
                    sessionAuthenticationService.authenticateSessionTicket(
                            operator == null ? null : operator.getSessionId(),
                            operator == null ? null : operator.getUserId(),
                            operator == null ? null : operator.getUserUuid(),
                            simulatedRoleId,
                            operator == null ? null : operator.getSessionVersion(),
                            operator == null ? null : operator.getPermissionsVersion()
                    )
            );
            if (operator != null) {
                copyTrustedCurrentUser(operator, refreshedOperator);
                return operator;
            }
            return refreshedOperator;
        }
        if (enforceTrustedUserResolution && AuthenticationTrustSupport.isTrustedCurrentUser(operator)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user resolver is unavailable");
        }
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(operator)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "A trusted operator identity is required");
        }
        return operator;
    }

    private Long normalizeSimulatedRoleId(Long simulatedRoleId) {
        return simulatedRoleId == null || simulatedRoleId <= 0 ? null : simulatedRoleId;
    }

    private CurrentUser requireTrustedAuthenticatedCurrentUser(SessionAuthenticationService.AuthenticatedAccess authenticatedAccess) {
        CurrentUser refreshedOperator = authenticatedAccess == null ? null : authenticatedAccess.currentUser();
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(refreshedOperator)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "A trusted operator identity is required");
        }
        return refreshedOperator;
    }

    private void copyTrustedCurrentUser(CurrentUser target, CurrentUser source) {
        target.setUserId(source.getUserId());
        target.setUserUuid(source.getUserUuid());
        target.setUsername(source.getUsername());
        target.setSessionId(source.getSessionId());
        target.setSessionVersion(source.getSessionVersion());
        target.setAuthenticated(source.isAuthenticated());
        target.setPermissions(source.getPermissions());
        target.setRoleIds(source.getRoleIds());
        target.setPrimaryDeptId(source.getPrimaryDeptId());
        target.setDeptIds(source.getDeptIds());
        target.setDescendantDeptIds(source.getDescendantDeptIds());
        target.setDataScopes(source.getDataScopes());
        target.setPermissionsVersion(source.getPermissionsVersion());
        target.setRequiresPasswordChange(source.getRequiresPasswordChange());
        target.setDefaultHomePath(source.getDefaultHomePath());
        target.setSimulatedRoleId(normalizeSimulatedRoleId(source.getSimulatedRoleId()));
        target.setLoginType(source.getLoginType());
    }

    private String requireCurrentUpdateOperatorUuid() {
        CurrentUser operator = currentUpdateOperator.get();
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(operator)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "A trusted operator identity is required");
        }
        return operator.getUserUuid();
    }

    private void requireRequest(Object request, String name) {
        if (request == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, name + " is required");
        }
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
        public boolean available() {
            return enabled && configured;
        }
    }

    private record CachedReadModelVersion(Long version, long expiresAtMillis) {
    }
}
