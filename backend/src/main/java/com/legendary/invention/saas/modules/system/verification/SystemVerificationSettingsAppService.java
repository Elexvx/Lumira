package com.legendary.invention.saas.modules.system.verification;

import com.legendary.invention.saas.common.enums.ErrorCode;
import com.legendary.invention.saas.common.exception.BizException;
import com.legendary.invention.saas.infrastructure.security.CurrentUser;
import com.legendary.invention.saas.modules.auth.app.WechatLoginService;
import com.legendary.invention.saas.modules.system.dto.SystemDTO;
import com.legendary.invention.saas.modules.system.vo.SystemVO;
import com.legendary.invention.saas.modules.system.support.SmtpMailService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SystemVerificationSettingsAppService {

    private static final String TOTP_CONFIG_ENABLED_KEY = "verification.totp.enabled";
    private static final String EMAIL_LOGIN_ENABLED_KEY = "verification.email-login.enabled";
    private static final String SMS_CONFIG_ENABLED_KEY = "verification.sms.enabled";
    private static final String SMS_CONFIG_PROVIDER_KEY = "verification.sms.provider";
    private static final String SMS_CONFIG_SIGN_NAME_KEY = "verification.sms.sign-name";
    private static final String SMS_CONFIG_TEMPLATE_CODE_KEY = "verification.sms.template-code";
    private static final String SMS_CONFIG_ACCESS_KEY_ID_KEY = "verification.sms.access-key-id";
    private static final String SMS_CONFIG_ACCESS_KEY_SECRET_KEY = "verification.sms.access-key-secret";
    private static final String SMS_CONFIG_ENDPOINT_KEY = "verification.sms.endpoint";
    private static final String SMS_CONFIG_REGION_KEY = "verification.sms.region";

    private final JdbcTemplate jdbcTemplate;
    private final SystemVerificationProperties properties;
    private final SmtpMailService smtpMailService;
    private final WechatLoginService wechatLoginService;

    public SystemVerificationSettingsAppService(
            JdbcTemplate jdbcTemplate,
            SystemVerificationProperties properties,
            SmtpMailService smtpMailService,
            WechatLoginService wechatLoginService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.smtpMailService = smtpMailService;
        this.wechatLoginService = wechatLoginService;
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
        return settings;
    }

    public SystemVO.LoginCapabilitiesVO loadLoginCapabilities(Long tenantId) {
        SystemVO.LoginCapabilitiesVO capabilities = new SystemVO.LoginCapabilitiesVO();
        capabilities.setPasswordLoginAvailable(true);
        capabilities.setSmsLoginAvailable(isSmsLoginAvailable(tenantId));
        capabilities.setEmailLoginAvailable(isEmailLoginAvailable(tenantId));
        capabilities.setWechatLoginAvailable(wechatLoginService.isAvailable());
        return capabilities;
    }

    public SystemVO.VerificationSettingsVO updateVerificationSettings(CurrentUser currentUser, SystemDTO.VerificationSettingsRequest request) {
        Long tenantId = requireTenantId(currentUser);
        Long operatorId = currentUser.getUserId();
        boolean enabled = request.getEnabled() == null ? isTotpEnabled(tenantId) : request.getEnabled();
        boolean emailLoginEnabled = request.getEmailLoginEnabled() == null ? isEmailLoginEnabled(tenantId) : request.getEmailLoginEnabled();
        upsertPlatformConfigValue(tenantId, TOTP_CONFIG_ENABLED_KEY, "2FA 启用", String.valueOf(enabled), "是否启用 2FA 登录方式", operatorId);
        upsertPlatformConfigValue(tenantId, EMAIL_LOGIN_ENABLED_KEY, "邮箱验证码登录", String.valueOf(emailLoginEnabled), "是否启用邮箱验证码登录", operatorId);
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
        boolean configured = enabled
                && StringUtils.hasText(provider)
                && StringUtils.hasText(signName)
                && StringUtils.hasText(templateCode);
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
                    configValue,
                    remark,
                    operatorId,
                    operatorId
            );
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
                configValue,
                remark,
                operatorId,
                LocalDateTime.now(),
                existingId
        );
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

    private Map<String, String> loadConfigValuesByKeys(Long tenantId, List<String> keys) {
        Long effectiveTenantId = tenantId == null ? 1001L : tenantId;
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
        params.add(effectiveTenantId);
        params.add(effectiveTenantId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
        Map<String, String> valueByKey = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String configKey = String.valueOf(row.get("configKey"));
            if (!valueByKey.containsKey(configKey)) {
                valueByKey.put(configKey, normalizeConfigText(row.get("configValue")));
            }
        }
        return valueByKey;
    }

    private String sanitizeText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private Long requireTenantId(CurrentUser currentUser) {
        if (currentUser.getCurrentTenantId() == null) {
            return 1001L;
        }
        return currentUser.getCurrentTenantId();
    }

    private boolean isTotpEnabled(Long tenantId) {
        Map<String, String> values = loadConfigValuesByKeys(tenantId, List.of(TOTP_CONFIG_ENABLED_KEY));
        return Boolean.parseBoolean(defaultIfBlank(values.get(TOTP_CONFIG_ENABLED_KEY), "true"));
    }

    private boolean isEmailLoginEnabled(Long tenantId) {
        Map<String, String> values = loadConfigValuesByKeys(tenantId, List.of(EMAIL_LOGIN_ENABLED_KEY));
        return Boolean.parseBoolean(defaultIfBlank(values.get(EMAIL_LOGIN_ENABLED_KEY), String.valueOf(properties.isEmailLoginEnabled())));
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
