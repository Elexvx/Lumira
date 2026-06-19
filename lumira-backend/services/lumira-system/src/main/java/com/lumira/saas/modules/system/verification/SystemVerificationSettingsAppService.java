package com.lumira.saas.modules.system.verification;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.FieldCryptoService;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.vo.SystemVO;
import com.lumira.saas.modules.system.support.SmtpMailService;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class SystemVerificationSettingsAppService {

    private static final Duration CONFIG_SNAPSHOT_TTL = Duration.ofSeconds(30);
    private static final int CONFIG_SNAPSHOT_MAX_ENTRIES = 2048;

    private static final String TOTP_CONFIG_ENABLED_KEY = "verification.totp.enabled";
    private static final String PASSWORD_LOGIN_ENABLED_KEY = "verification.password-login.enabled";
    private static final String EMAIL_LOGIN_ENABLED_KEY = "verification.email-login.enabled";
    private static final String LOGIN_MODE_ORDER_KEY = "verification.login-mode.order";
    private static final List<String> DEFAULT_LOGIN_MODE_ORDER = List.of("password", "sms", "email", "wechat", "passkey");
    private static final String SMS_CONFIG_ENABLED_KEY = "verification.sms.enabled";
    private static final String SMS_CONFIG_PROVIDER_KEY = "verification.sms.provider";
    private static final String SMS_CONFIG_SIGN_NAME_KEY = "verification.sms.sign-name";
    private static final String SMS_CONFIG_TEMPLATE_CODE_KEY = "verification.sms.template-code";
    private static final String SMS_CONFIG_ACCESS_KEY_ID_KEY = "verification.sms.access-key-id";
    private static final String SMS_CONFIG_ACCESS_KEY_SECRET_KEY = "verification.sms.access-key-secret";
    private static final String SMS_CONFIG_ENDPOINT_KEY = "verification.sms.endpoint";
    private static final String SMS_CONFIG_REGION_KEY = "verification.sms.region";
    private static final String PASSKEY_ENABLED_KEY = "verification.passkey.enabled";
    private static final String PASSKEY_PASSWORDLESS_ENABLED_KEY = "verification.passkey.passwordless-enabled";
    private static final String PASSKEY_SELF_BINDING_ENABLED_KEY = "verification.passkey.self-binding-enabled";
    private static final String PASSKEY_RP_ID_KEY = "verification.passkey.rp-id";
    private static final String PASSKEY_RP_NAME_KEY = "verification.passkey.rp-name";
    private static final String PASSKEY_ALLOWED_ORIGINS_KEY = "verification.passkey.allowed-origins";
    private static final String PASSKEY_CHALLENGE_TTL_KEY = "verification.passkey.challenge-ttl-seconds";

    private final MyBatisQueryOperations jdbcTemplate;
    private final SystemVerificationProperties properties;
    private final SmtpMailService smtpMailService;
    private final WechatLoginSettingsService wechatLoginSettingsService;
    private final FieldCryptoService fieldCryptoService;
    private final Cache<String, Map<String, String>> configSnapshotCache;
    private final Cache<String, CompletableFuture<Map<String, String>>> configLoadInFlight;

    public SystemVerificationSettingsAppService(
            MyBatisQueryOperations jdbcTemplate,
            SystemVerificationProperties properties,
            SmtpMailService smtpMailService,
            WechatLoginSettingsService wechatLoginSettingsService,
            FieldCryptoService fieldCryptoService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.smtpMailService = smtpMailService;
        this.wechatLoginSettingsService = wechatLoginSettingsService;
        this.fieldCryptoService = fieldCryptoService;
        this.configSnapshotCache = CacheBuilder.newBuilder()
                .maximumSize(CONFIG_SNAPSHOT_MAX_ENTRIES)
                .expireAfterWrite(CONFIG_SNAPSHOT_TTL.toMillis(), TimeUnit.MILLISECONDS)
                .build();
        this.configLoadInFlight = CacheBuilder.newBuilder()
                .maximumSize(CONFIG_SNAPSHOT_MAX_ENTRIES)
                .expireAfterWrite(CONFIG_SNAPSHOT_TTL.toMillis(), TimeUnit.MILLISECONDS)
                .build();
    }

    public SystemVO.SmsVerificationSettingsVO getSmsSettings(Long tenantId) {
        SmsVerificationSettingsRecord record = loadSmsSettingsRecord(tenantId);
        SystemVO.SmsVerificationSettingsVO settings = new SystemVO.SmsVerificationSettingsVO();
        settings.setEnabled(record.enabled());
        settings.setProvider(record.provider());
        settings.setSignName(record.signName());
        settings.setTemplateCode(record.templateCode());
        settings.setAccessKeyId(record.accessKeyId());
        settings.setAccessKeySecret("");
        settings.setAccessKeySecretConfigured(StringUtils.hasText(record.accessKeySecret()));
        settings.setEndpoint(record.endpoint());
        settings.setRegion(record.region());
        settings.setConfigured(record.configured());
        return settings;
    }

    public SystemVO.VerificationSettingsVO getVerificationSettings(Long tenantId) {
        SystemVO.VerificationSettingsVO settings = new SystemVO.VerificationSettingsVO();
        settings.setEnabled(isTotpEnabled(tenantId));
        settings.setEmailLoginEnabled(isEmailLoginEnabled(tenantId));
        settings.setPasswordLoginEnabled(isPasswordLoginEnabled(tenantId));
        settings.setLoginModeOrder(loginModeOrder(tenantId));
        return settings;
    }

    public SystemVO.LoginCapabilitiesVO loadLoginCapabilities(Long tenantId) {
        SystemVO.LoginCapabilitiesVO capabilities = new SystemVO.LoginCapabilitiesVO();
        capabilities.setPasswordLoginAvailable(isPasswordLoginEnabled(tenantId));
        capabilities.setSmsLoginAvailable(isSmsLoginAvailable(tenantId));
        capabilities.setEmailLoginAvailable(isEmailLoginAvailable(tenantId));
        capabilities.setWechatLoginAvailable(wechatLoginSettingsService.isAvailable(tenantId));
        SystemVO.PasskeySettingsVO passkey = getPasskeySettings(tenantId);
        capabilities.setPasskeyLoginAvailable(Boolean.TRUE.equals(passkey.getEnabled()));
        capabilities.setPasskeyPasswordlessAvailable(Boolean.TRUE.equals(passkey.getEnabled()) && Boolean.TRUE.equals(passkey.getPasswordlessEnabled()));
        capabilities.setLoginModeOrder(loginModeOrder(tenantId));
        return capabilities;
    }

    public SystemVO.WechatLoginSettingsVO getWechatSettings(Long tenantId) {
        return wechatLoginSettingsService.getSettings(tenantId);
    }

    public SystemVO.PasskeySettingsVO getPasskeySettings(Long tenantId) {
        Map<String, String> values = loadConfigValuesByKeys(tenantId, passkeyConfigKeys());
        SystemVO.PasskeySettingsVO settings = new SystemVO.PasskeySettingsVO();
        settings.setEnabled(Boolean.parseBoolean(defaultIfBlank(values.get(PASSKEY_ENABLED_KEY), "false")));
        settings.setPasswordlessEnabled(Boolean.parseBoolean(defaultIfBlank(values.get(PASSKEY_PASSWORDLESS_ENABLED_KEY), "false")));
        settings.setSelfBindingEnabled(Boolean.parseBoolean(defaultIfBlank(values.get(PASSKEY_SELF_BINDING_ENABLED_KEY), "false")));
        settings.setRpId(defaultIfBlank(values.get(PASSKEY_RP_ID_KEY), ""));
        settings.setRpName(defaultIfBlank(values.get(PASSKEY_RP_NAME_KEY), ""));
        settings.setAllowedOrigins(splitLines(defaultIfBlank(values.get(PASSKEY_ALLOWED_ORIGINS_KEY), "")));
        settings.setChallengeTtlSeconds(parseInt(defaultIfBlank(values.get(PASSKEY_CHALLENGE_TTL_KEY), "120"), 120));
        return settings;
    }

    public SystemVO.VerificationSettingsVO updateVerificationSettings(CurrentUser currentUser, SystemDTO.VerificationSettingsRequest request) {
        Long tenantId = requireTenantId(currentUser);
        Long operatorId = currentUser.getUserId();
        boolean enabled = request.getEnabled() == null ? isTotpEnabled(tenantId) : request.getEnabled();
        boolean emailLoginEnabled = request.getEmailLoginEnabled() == null ? isEmailLoginEnabled(tenantId) : request.getEmailLoginEnabled();
        boolean passwordLoginEnabled = request.getPasswordLoginEnabled() == null ? isPasswordLoginEnabled(tenantId) : request.getPasswordLoginEnabled();
        upsertPlatformConfigValue(tenantId, TOTP_CONFIG_ENABLED_KEY, "2FA 启用", String.valueOf(enabled), "是否启用 2FA 登录方式", operatorId);
        upsertPlatformConfigValue(tenantId, EMAIL_LOGIN_ENABLED_KEY, "邮箱验证码登录", String.valueOf(emailLoginEnabled), "是否启用邮箱验证码登录", operatorId);
        upsertPlatformConfigValue(tenantId, PASSWORD_LOGIN_ENABLED_KEY, "密码登录", String.valueOf(passwordLoginEnabled), "是否启用账号密码登录", operatorId);
        if (request.getLoginModeOrder() != null) {
            upsertPlatformConfigValue(tenantId, LOGIN_MODE_ORDER_KEY, "登录方式排序", String.join(",", normalizeLoginModeOrder(request.getLoginModeOrder())), "登录页分段控制器展示顺序", operatorId);
        }
        return getVerificationSettings(tenantId);
    }

    public SystemVO.SmsVerificationSettingsVO updateSmsSettings(CurrentUser currentUser, SystemDTO.SmsVerificationSettingsRequest request) {
        Long tenantId = requireTenantId(currentUser);
        Long operatorId = currentUser.getUserId();
        SmsVerificationSettingsRecord current = loadSmsSettingsRecord(tenantId);
        Boolean enabled = request.getEnabled() == null ? current.enabled() : request.getEnabled();
        String provider = sanitizeText(request.getProvider(), current.provider());
        String signName = sanitizeText(request.getSignName(), current.signName());
        String templateCode = sanitizeText(request.getTemplateCode(), current.templateCode());
        String accessKeyId = sanitizeText(request.getAccessKeyId(), current.accessKeyId());
        String existingSecret = defaultIfBlank(current.accessKeySecret(), "");
        String accessKeySecret = StringUtils.hasText(request.getAccessKeySecret()) ? request.getAccessKeySecret() : existingSecret;
        String endpoint = sanitizeText(request.getEndpoint(), current.endpoint());
        String region = sanitizeText(request.getRegion(), current.region());

        upsertSmsConfigValue(tenantId, SMS_CONFIG_ENABLED_KEY, "短信验证码启用", String.valueOf(Boolean.TRUE.equals(enabled)), "是否启用短信验证码服务", operatorId);
        upsertSmsConfigValue(tenantId, SMS_CONFIG_PROVIDER_KEY, "短信验证码服务商", provider, "短信验证码服务提供方", operatorId);
        upsertSmsConfigValue(tenantId, SMS_CONFIG_SIGN_NAME_KEY, "短信签名", signName, "短信验证码签名", operatorId);
        upsertSmsConfigValue(tenantId, SMS_CONFIG_TEMPLATE_CODE_KEY, "短信模板编码", templateCode, "短信验证码模板编码", operatorId);
        upsertSmsConfigValue(tenantId, SMS_CONFIG_ACCESS_KEY_ID_KEY, "短信 Access Key ID", accessKeyId, "短信验证码访问密钥 ID", operatorId);
        upsertSmsConfigValue(tenantId, SMS_CONFIG_ACCESS_KEY_SECRET_KEY, "短信 Access Key Secret", accessKeySecret, "短信验证码访问密钥 Secret", operatorId);
        upsertSmsConfigValue(tenantId, SMS_CONFIG_ENDPOINT_KEY, "短信服务地址", endpoint, "短信验证码服务端点", operatorId);
        upsertSmsConfigValue(tenantId, SMS_CONFIG_REGION_KEY, "短信服务地域", region, "短信验证码服务地域", operatorId);

        return getSmsSettings(tenantId);
    }

    public SystemVO.SmsVerificationSettingsVO resetSmsSettings(CurrentUser currentUser) {
        Long tenantId = requireTenantId(currentUser);
        Long operatorId = currentUser.getUserId();
        upsertSmsConfigValue(tenantId, SMS_CONFIG_ENABLED_KEY, "短信验证码启用", "false", "是否启用短信验证码服务", operatorId);
        upsertSmsConfigValue(tenantId, SMS_CONFIG_PROVIDER_KEY, "短信验证码服务商", "aliyun", "短信验证码服务提供方", operatorId);
        upsertSmsConfigValue(tenantId, SMS_CONFIG_SIGN_NAME_KEY, "短信签名", "", "短信验证码签名", operatorId);
        upsertSmsConfigValue(tenantId, SMS_CONFIG_TEMPLATE_CODE_KEY, "短信模板编码", "", "短信验证码模板编码", operatorId);
        upsertSmsConfigValue(tenantId, SMS_CONFIG_ACCESS_KEY_ID_KEY, "短信 Access Key ID", "", "短信验证码访问密钥 ID", operatorId);
        upsertSmsConfigValue(tenantId, SMS_CONFIG_ACCESS_KEY_SECRET_KEY, "短信 Access Key Secret", "", "短信验证码访问密钥 Secret", operatorId);
        upsertSmsConfigValue(tenantId, SMS_CONFIG_ENDPOINT_KEY, "短信服务地址", "", "短信验证码服务端点", operatorId);
        upsertSmsConfigValue(tenantId, SMS_CONFIG_REGION_KEY, "短信服务地域", "", "短信验证码服务地域", operatorId);
        return getSmsSettings(tenantId);
    }

    public SystemVO.WechatLoginSettingsVO updateWechatSettings(CurrentUser currentUser, SystemDTO.WechatLoginSettingsRequest request) {
        Long tenantId = requireTenantId(currentUser);
        return wechatLoginSettingsService.updateSettings(tenantId, currentUser.getUserId(), request);
    }

    public SystemVO.WechatLoginSettingsVO resetWechatSettings(CurrentUser currentUser) {
        Long tenantId = requireTenantId(currentUser);
        return wechatLoginSettingsService.resetSettings(tenantId, currentUser.getUserId());
    }

    public SystemVO.PasskeySettingsVO updatePasskeySettings(CurrentUser currentUser, SystemDTO.PasskeySettingsRequest request) {
        Long tenantId = requireTenantId(currentUser);
        Long operatorId = currentUser.getUserId();
        SystemVO.PasskeySettingsVO current = getPasskeySettings(tenantId);
        boolean enabled = request.getEnabled() == null ? Boolean.TRUE.equals(current.getEnabled()) : request.getEnabled();
        boolean passwordless = request.getPasswordlessEnabled() == null ? Boolean.TRUE.equals(current.getPasswordlessEnabled()) : request.getPasswordlessEnabled();
        boolean selfBinding = request.getSelfBindingEnabled() == null ? Boolean.TRUE.equals(current.getSelfBindingEnabled()) : request.getSelfBindingEnabled();
        String rpId = sanitizeText(request.getRpId(), current.getRpId());
        String rpName = sanitizeText(request.getRpName(), current.getRpName());
        List<String> origins = request.getAllowedOrigins() == null ? current.getAllowedOrigins() : request.getAllowedOrigins().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        int ttl = request.getChallengeTtlSeconds() == null ? current.getChallengeTtlSeconds() : request.getChallengeTtlSeconds();

        upsertPlatformConfigValue(tenantId, PASSKEY_ENABLED_KEY, "通行密钥启用", String.valueOf(enabled), "是否启用通行密钥登录", operatorId);
        upsertPlatformConfigValue(tenantId, PASSKEY_PASSWORDLESS_ENABLED_KEY, "通行密钥无账号登录", String.valueOf(passwordless), "是否允许发现式凭据无账号登录", operatorId);
        upsertPlatformConfigValue(tenantId, PASSKEY_SELF_BINDING_ENABLED_KEY, "通行密钥自助绑定", String.valueOf(selfBinding), "是否允许用户在个人中心自助绑定通行密钥", operatorId);
        upsertPlatformConfigValue(tenantId, PASSKEY_RP_ID_KEY, "通行密钥 RP ID", rpId, "WebAuthn RP ID", operatorId);
        upsertPlatformConfigValue(tenantId, PASSKEY_RP_NAME_KEY, "通行密钥 RP 名称", rpName, "WebAuthn RP 显示名称", operatorId);
        upsertPlatformConfigValue(tenantId, PASSKEY_ALLOWED_ORIGINS_KEY, "通行密钥允许 Origin", String.join("\n", origins), "WebAuthn 允许的前端 Origin", operatorId);
        upsertPlatformConfigValue(tenantId, PASSKEY_CHALLENGE_TTL_KEY, "通行密钥 Challenge TTL", String.valueOf(ttl), "WebAuthn challenge 有效期秒数", operatorId);
        return getPasskeySettings(tenantId);
    }

    public SystemVO.PasskeySettingsVO resetPasskeySettings(CurrentUser currentUser) {
        Long tenantId = requireTenantId(currentUser);
        Long operatorId = currentUser.getUserId();
        upsertPlatformConfigValue(tenantId, PASSKEY_ENABLED_KEY, "通行密钥启用", "false", "是否启用通行密钥登录", operatorId);
        upsertPlatformConfigValue(tenantId, PASSKEY_PASSWORDLESS_ENABLED_KEY, "通行密钥无账号登录", "false", "是否允许发现式凭据无账号登录", operatorId);
        upsertPlatformConfigValue(tenantId, PASSKEY_SELF_BINDING_ENABLED_KEY, "通行密钥自助绑定", "true", "是否允许用户在个人中心自助绑定通行密钥", operatorId);
        upsertPlatformConfigValue(tenantId, PASSKEY_RP_ID_KEY, "通行密钥 RP ID", "", "WebAuthn RP ID", operatorId);
        upsertPlatformConfigValue(tenantId, PASSKEY_RP_NAME_KEY, "通行密钥 RP 名称", "", "WebAuthn RP 显示名称", operatorId);
        upsertPlatformConfigValue(tenantId, PASSKEY_ALLOWED_ORIGINS_KEY, "通行密钥允许 Origin", "", "WebAuthn 允许的前端 Origin", operatorId);
        upsertPlatformConfigValue(tenantId, PASSKEY_CHALLENGE_TTL_KEY, "通行密钥 Challenge TTL", "120", "WebAuthn challenge 有效期秒数", operatorId);
        return getPasskeySettings(tenantId);
    }

    private SmsVerificationSettingsRecord loadSmsSettingsRecord(Long tenantId) {
        Map<String, String> values = loadConfigValuesByKeys(tenantId, smsConfigKeys());
        boolean enabled = Boolean.parseBoolean(defaultIfBlank(values.get(SMS_CONFIG_ENABLED_KEY), "false"));
        String provider = defaultIfBlank(values.get(SMS_CONFIG_PROVIDER_KEY), "aliyun");
        String signName = defaultIfBlank(values.get(SMS_CONFIG_SIGN_NAME_KEY), "");
        String templateCode = defaultIfBlank(values.get(SMS_CONFIG_TEMPLATE_CODE_KEY), "");
        String accessKeyId = defaultIfBlank(values.get(SMS_CONFIG_ACCESS_KEY_ID_KEY), "");
        String accessKeySecret = defaultIfBlank(values.get(SMS_CONFIG_ACCESS_KEY_SECRET_KEY), "");
        String endpoint = defaultIfBlank(values.get(SMS_CONFIG_ENDPOINT_KEY), "");
        String region = defaultIfBlank(values.get(SMS_CONFIG_REGION_KEY), "");
        boolean configured = StringUtils.hasText(provider)
                && StringUtils.hasText(signName)
                && StringUtils.hasText(templateCode)
                && StringUtils.hasText(accessKeyId)
                && StringUtils.hasText(accessKeySecret);
        return new SmsVerificationSettingsRecord(enabled, provider, signName, templateCode, accessKeyId, accessKeySecret, endpoint, region, configured);
    }

    private void upsertSmsConfigValue(Long tenantId, String configKey, String configName, String configValue, String remark, Long operatorId) {
        upsertConfigValue(tenantId, configKey, configName, configValue, remark, operatorId);
    }

    private void upsertPlatformConfigValue(Long tenantId, String configKey, String configName, String configValue, String remark, Long operatorId) {
        upsertConfigValue(tenantId, configKey, configName, configValue, remark, operatorId);
    }

    private void upsertConfigValue(Long tenantId, String configKey, String configName, String configValue, String remark, Long operatorId) {
        Long existingId = queryConfigId(configKey, tenantId);
        if (existingId == null) {
            jdbcTemplate.update(
                    """
                            insert into sys_config (
                                tenant_id, config_key, config_name, config_value, config_scope, is_system, remark,
                                created_by, updated_by, deleted
                            ) values (?, ?, ?, ?, 'PLATFORM', 0, ?, ?, ?, 0)
                            """,
                    tenantId,
                    configKey,
                    configName,
                    encryptConfigValue(configKey, configValue),
                    remark,
                    operatorId,
                    operatorId
            );
            invalidateConfigCaches();
            return;
        }
        jdbcTemplate.update(
                """
                        update sys_config
                        set config_name = ?, config_value = ?, config_scope = 'PLATFORM', remark = ?,
                            updated_by = ?, updated_at = ?, deleted = 0
                        where id = ?
                        """,
                configName,
                encryptConfigValue(configKey, configValue),
                remark,
                operatorId,
                LocalDateTime.now(),
                existingId
        );
        invalidateConfigCaches();
    }

    private Long queryConfigId(String configKey, Long tenantId) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select id
                            from sys_config
                            where config_key = ? and tenant_id <=> ? and deleted = 0
                            order by id desc
                            limit 1
                            """,
                    Long.class,
                    configKey,
                    tenantId
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<String> smsConfigKeys() {
        return List.of(
                SMS_CONFIG_ENABLED_KEY,
                SMS_CONFIG_PROVIDER_KEY,
                SMS_CONFIG_SIGN_NAME_KEY,
                SMS_CONFIG_TEMPLATE_CODE_KEY,
                SMS_CONFIG_ACCESS_KEY_ID_KEY,
                SMS_CONFIG_ACCESS_KEY_SECRET_KEY,
                SMS_CONFIG_ENDPOINT_KEY,
                SMS_CONFIG_REGION_KEY
        );
    }

    private List<String> passkeyConfigKeys() {
        return List.of(
                PASSKEY_ENABLED_KEY,
                PASSKEY_PASSWORDLESS_ENABLED_KEY,
                PASSKEY_SELF_BINDING_ENABLED_KEY,
                PASSKEY_RP_ID_KEY,
                PASSKEY_RP_NAME_KEY,
                PASSKEY_ALLOWED_ORIGINS_KEY,
                PASSKEY_CHALLENGE_TTL_KEY
        );
    }

    private Map<String, String> loadConfigValuesByKeys(Long tenantId, List<String> keys) {
        Long effectiveTenantId = tenantId == null ? com.lumira.common.constant.PlatformConstants.PLATFORM_TENANT_ID : tenantId;
        String cacheKey = configSnapshotCacheKey(effectiveTenantId, keys);
        Map<String, String> cached = configSnapshotCache.getIfPresent(cacheKey);
        if (cached != null) {
            return new LinkedHashMap<>(cached);
        }
        try {
            CompletableFuture<Map<String, String>> inFlight = configLoadInFlight.get(
                    cacheKey,
                    () -> CompletableFuture.completedFuture(loadConfigValuesByKeysFromDatabase(effectiveTenantId, cacheKey, keys))
            );
            return new LinkedHashMap<>(inFlight.join());
        } catch (CompletionException exception) {
            configLoadInFlight.invalidate(cacheKey);
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Failed to load verification config snapshot", cause);
        } catch (ExecutionException exception) {
            configLoadInFlight.invalidate(cacheKey);
            throw new IllegalStateException("Failed to load verification config snapshot", exception);
        }
    }

    private Map<String, String> loadConfigValuesByKeysFromDatabase(Long tenantId, String cacheKey, List<String> keys) {
        Map<String, String> cached = configSnapshotCache.getIfPresent(cacheKey);
        if (cached != null) {
            return new LinkedHashMap<>(cached);
        }
        String placeholders = keys.stream().map(item -> "?").collect(Collectors.joining(", "));
        String sql = """
                select tenant_id as tenantId, config_key as configKey, config_value as configValue
                from sys_config
                where deleted = 0
                  and config_scope = 'PLATFORM'
                and config_key in (%s)
                and (tenant_id = ? or tenant_id is null)
                order by case when tenant_id = ? then 0 when tenant_id is null then 1 else 2 end, id desc
                """.formatted(placeholders);
        List<Object> params = new ArrayList<>(keys);
        params.add(tenantId);
        params.add(tenantId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
        Map<String, String> valueByKey = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String configKey = String.valueOf(row.get("configKey"));
            if (!valueByKey.containsKey(configKey)) {
                valueByKey.put(configKey, normalizeConfigText(decryptConfigValue(configKey, normalizeConfigTextRaw(row.get("configValue")))));
            }
        }
        configSnapshotCache.put(cacheKey, new LinkedHashMap<>(valueByKey));
        return valueByKey;
    }

    private String configSnapshotCacheKey(Long tenantId, List<String> keys) {
        return tenantId + ":" + keys.stream().sorted().collect(Collectors.joining(","));
    }

    private void invalidateConfigCaches() {
        configSnapshotCache.invalidateAll();
        configLoadInFlight.invalidateAll();
    }

    private String encryptConfigValue(String configKey, String configValue) {
        return isSensitiveConfigKey(configKey) ? fieldCryptoService.encrypt(configValue) : configValue;
    }

    private String decryptConfigValue(String configKey, String configValue) {
        return isSensitiveConfigKey(configKey) ? fieldCryptoService.decrypt(configValue) : configValue;
    }

    private boolean isSensitiveConfigKey(String configKey) {
        return SMS_CONFIG_ACCESS_KEY_SECRET_KEY.equals(configKey);
    }

    private String sanitizeText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private List<String> splitLines(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return value.lines().map(String::trim).filter(StringUtils::hasText).distinct().toList();
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private Long requireTenantId(CurrentUser currentUser) {
        return com.lumira.common.constant.PlatformConstants.PLATFORM_TENANT_ID;
    }

    private boolean isTotpEnabled(Long tenantId) {
        Map<String, String> values = loadConfigValuesByKeys(tenantId, List.of(TOTP_CONFIG_ENABLED_KEY));
        return Boolean.parseBoolean(defaultIfBlank(values.get(TOTP_CONFIG_ENABLED_KEY), "true"));
    }

    private boolean isEmailLoginEnabled(Long tenantId) {
        Map<String, String> values = loadConfigValuesByKeys(tenantId, List.of(EMAIL_LOGIN_ENABLED_KEY));
        return Boolean.parseBoolean(defaultIfBlank(values.get(EMAIL_LOGIN_ENABLED_KEY), String.valueOf(properties.isEmailLoginEnabled())));
    }

    private boolean isPasswordLoginEnabled(Long tenantId) {
        Map<String, String> values = loadConfigValuesByKeys(tenantId, List.of(PASSWORD_LOGIN_ENABLED_KEY));
        return Boolean.parseBoolean(defaultIfBlank(values.get(PASSWORD_LOGIN_ENABLED_KEY), "true"));
    }

    private List<String> loginModeOrder(Long tenantId) {
        Map<String, String> values = loadConfigValuesByKeys(tenantId, List.of(LOGIN_MODE_ORDER_KEY));
        String configured = values.get(LOGIN_MODE_ORDER_KEY);
        if (!StringUtils.hasText(configured)) {
            return DEFAULT_LOGIN_MODE_ORDER;
        }
        List<String> normalized = normalizeLoginModeOrder(List.of(configured.split(",")));
        return normalized.isEmpty() ? DEFAULT_LOGIN_MODE_ORDER : normalized;
    }

    private List<String> normalizeLoginModeOrder(List<String> values) {
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            String mode = value.trim();
            if (("passkey".equals(mode) || "sms".equals(mode) || "email".equals(mode) || "wechat".equals(mode) || "password".equals(mode)) && !normalized.contains(mode)) {
                normalized.add(mode);
            }
        }
        return normalized;
    }

    private boolean isEmailLoginAvailable(Long tenantId) {
        return isEmailLoginEnabled(tenantId) && smtpMailService.isConfigured(tenantId);
    }

    private boolean isSmsLoginAvailable(Long tenantId) {
        SmsVerificationSettingsRecord smsSettings = loadSmsSettingsRecord(tenantId);
        return smsSettings.enabled() && smsSettings.configured();
    }

    private String normalizeConfigText(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String normalizeConfigTextRaw(Object value) {
        return value == null ? "" : value.toString();
    }

    private record SmsVerificationSettingsRecord(
            boolean enabled,
            String provider,
            String signName,
            String templateCode,
            String accessKeyId,
            String accessKeySecret,
            String endpoint,
            String region,
            boolean configured
    ) {
    }
}
