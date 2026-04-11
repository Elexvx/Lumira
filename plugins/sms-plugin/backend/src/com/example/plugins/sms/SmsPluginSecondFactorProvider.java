package com.example.plugins.sms;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.teaopenapi.models.Config;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.sms.v20190711.SmsClient;
import com.yourcompany.saas.common.enums.ErrorCode;
import com.yourcompany.saas.common.exception.BizException;
import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeContext;
import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginSecondFactorChallenge;
import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginSecondFactorProfile;
import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginSecondFactorVerification;
import com.yourcompany.saas.modules.plugin.runtime.spi.PluginSecondFactorProvider;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SmsPluginSecondFactorProvider implements PluginSecondFactorProvider {

    private static final String PLUGIN_CODE = "sms";
    private static final String PLUGIN_NAME = "短信验证码插件";
    private static final String FACTOR_CODE = "sms";
    private static final String FACTOR_NAME = "短信验证码";
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {
    };

    @Override
    public String factorCode() {
        return FACTOR_CODE;
    }

    @Override
    public String factorName() {
        return FACTOR_NAME;
    }

    @Override
    public boolean requiresEmail() {
        return false;
    }

    @Override
    public PluginSecondFactorProfile profile(PluginRuntimeContext context, Long tenantId, Long userId) {
        BindingRecord binding = loadBinding(context, tenantId, userId);
        boolean enabled = binding != null && binding.enabled;
        boolean bound = binding != null && StringUtils.hasText(binding.mobile);
        String maskedContact = binding == null ? "" : maskMobile(binding.mobile);
        String statusMessage = bound ? "已绑定短信验证码，可用于登录" : "请先绑定手机号";
        return new PluginSecondFactorProfile(
                PLUGIN_CODE,
                PLUGIN_NAME,
                FACTOR_CODE,
                FACTOR_NAME,
                enabled,
                bound,
                true,
                maskedContact,
                statusMessage
        );
    }

    @Override
    public PluginSecondFactorChallenge prepareChallenge(PluginRuntimeContext context, Long tenantId, Long userId) {
        BindingRecord binding = requireBinding(context, tenantId, userId);
        String verificationCode = randomCode();
        String challengeId = UUID.randomUUID().toString();
        context.getJdbcTemplate().update(
                """
                        insert into plugin_sms_challenge (
                            challenge_id, tenant_id, user_id, mobile, provider_type, verification_hash, expires_at,
                            consumed_flag, created_at, updated_at, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, 0, current_timestamp, current_timestamp, 0)
                        on duplicate key update
                            mobile = values(mobile),
                            provider_type = values(provider_type),
                            verification_hash = values(verification_hash),
                            expires_at = values(expires_at),
                            consumed_flag = 0,
                            updated_at = current_timestamp,
                            deleted = 0
                        """,
                challengeId,
                tenantId,
                userId,
                binding.mobile,
                resolveProviderType(context, tenantId),
                sha256Hex(verificationCode),
                Timestamp.from(Instant.now().plus(CHALLENGE_TTL))
        );
        sendVerificationCode(context, tenantId, binding.mobile, verificationCode);
        audit(context, tenantId, userId, "CHALLENGE", "短信验证码已发送至 " + maskMobile(binding.mobile));
        return new PluginSecondFactorChallenge(
                PLUGIN_CODE,
                PLUGIN_NAME,
                FACTOR_CODE,
                FACTOR_NAME,
                challengeId,
                maskMobile(binding.mobile),
                "请输入收到的短信验证码",
                null,
                null,
                List.of()
        );
    }

    @Override
    public PluginSecondFactorVerification verify(PluginRuntimeContext context, String challengeId, String verificationCode) {
        ChallengeRecord challenge = loadChallenge(context, challengeId);
        if (challenge == null) {
            return PluginSecondFactorVerification.failure("验证码挑战不存在或已失效");
        }
        if (challenge.consumedFlag || challenge.expiresAt == null || challenge.expiresAt.toInstant().isBefore(Instant.now())) {
            return PluginSecondFactorVerification.failure("验证码已过期，请重新获取");
        }
        if (!sha256Hex(defaultIfBlank(verificationCode, "")).equalsIgnoreCase(challenge.verificationHash)) {
            return PluginSecondFactorVerification.failure("验证码错误，请重试");
        }
        context.getJdbcTemplate().update(
                "update plugin_sms_challenge set consumed_flag = 1, updated_at = current_timestamp where challenge_id = ? and deleted = 0",
                challengeId
        );
        audit(context, challenge.tenantId, challenge.userId, "VERIFY", "短信验证码验证成功");
        return PluginSecondFactorVerification.success(challenge.tenantId, challenge.userId, getUsername(context, challenge.userId), "验证成功");
    }

    @Override
    public PluginSecondFactorChallenge bind(PluginRuntimeContext context, Long tenantId, Long userId, String email, String mobile) {
        String normalizedMobile = normalizeMobile(mobile);
        if (!StringUtils.hasText(normalizedMobile)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "请先补充手机号后再启用短信验证");
        }
        context.getJdbcTemplate().update(
                """
                        insert into plugin_sms_binding (
                            tenant_id, user_id, mobile, enabled, created_at, updated_at, deleted
                        ) values (?, ?, ?, 1, current_timestamp, current_timestamp, 0)
                        on duplicate key update
                            mobile = values(mobile),
                            enabled = 1,
                            updated_at = current_timestamp,
                            deleted = 0
                        """,
                tenantId,
                userId,
                normalizedMobile
        );
        audit(context, tenantId, userId, "BIND", "短信验证码绑定完成");
        return prepareChallenge(context, tenantId, userId);
    }

    @Override
    public void unbind(PluginRuntimeContext context, Long tenantId, Long userId) {
        context.getJdbcTemplate().update(
                """
                        update plugin_sms_binding
                        set enabled = 0, deleted = 1, updated_at = current_timestamp
                        where tenant_id = ? and user_id = ? and deleted = 0
                        """,
                tenantId,
                userId
        );
        context.getJdbcTemplate().update(
                "update plugin_sms_challenge set deleted = 1, updated_at = current_timestamp where tenant_id = ? and user_id = ? and deleted = 0",
                tenantId,
                userId
        );
        audit(context, tenantId, userId, "UNBIND", "解绑短信验证码");
    }

    public SmsGatewayConfig loadGatewayConfig(PluginRuntimeContext context, Long tenantId) {
        String raw = context.getJdbcTemplate().query(
                """
                        select config_json
                        from plugin_sms_provider_config
                        where tenant_id = ? and deleted = 0 and enabled = 1
                        order by id desc
                        limit 1
                        """,
                rs -> rs.next() ? rs.getString("config_json") : null,
                tenantId
        );
        if (!StringUtils.hasText(raw)) {
            return new SmsGatewayConfig("MOCK", "", "", "", "", "", "", "", false);
        }
        try {
            SmsGatewayConfig config = context.getObjectMapper().readValue(raw, SmsGatewayConfig.class);
            return config.withDefaults();
        } catch (Exception exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "短信配置解析失败: " + exception.getMessage());
        }
    }

    public SmsGatewayConfig saveGatewayConfig(PluginRuntimeContext context, Long tenantId, SmsGatewayConfig request) {
        SmsGatewayConfig current = loadGatewayConfig(context, tenantId);
        SmsGatewayConfig next = request.merge(current).withDefaults();
        try {
            String json = context.getObjectMapper().writeValueAsString(next);
            context.getJdbcTemplate().update(
                    """
                            insert into plugin_sms_provider_config (
                                tenant_id, config_json, enabled, created_at, updated_at, deleted
                            ) values (?, ?, 1, current_timestamp, current_timestamp, 0)
                            on duplicate key update
                                config_json = values(config_json),
                                enabled = 1,
                                updated_at = current_timestamp,
                                deleted = 0
                            """,
                    tenantId,
                    json
            );
            audit(context, tenantId, null, "CONFIG", "短信供应商配置已更新");
            return next.masked();
        } catch (Exception exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "短信配置保存失败: " + exception.getMessage());
        }
    }

    public SmsGatewayConfig getGatewayConfigView(PluginRuntimeContext context, Long tenantId) {
        return loadGatewayConfig(context, tenantId).masked();
    }

    private void sendVerificationCode(PluginRuntimeContext context, Long tenantId, String mobile, String code) {
        SmsGatewayConfig config = loadGatewayConfig(context, tenantId);
        if (!StringUtils.hasText(config.providerType())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "请先配置短信供应商");
        }
        try {
            switch (config.providerType().toUpperCase()) {
                case "ALIYUN" -> sendByAliyun(config, mobile, code);
                case "TENCENT" -> sendByTencent(config, mobile, code);
                case "MOCK" -> {
                    // 本地开发兜底，不做真实发送。
                }
                default -> throw new BizException(ErrorCode.BIZ_ERROR, "不支持的短信供应商: " + config.providerType());
            }
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "短信发送失败: " + exception.getMessage());
        }
    }

    private void sendByAliyun(SmsGatewayConfig config, String mobile, String code) throws Exception {
        if (!StringUtils.hasText(config.accessKeyId()) || !StringUtils.hasText(config.accessKeySecret())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "请先补充阿里云 AccessKey");
        }
        Config aliyunConfig = new Config()
                .setAccessKeyId(config.accessKeyId())
                .setAccessKeySecret(config.accessKeySecret());
        aliyunConfig.endpoint = StringUtils.hasText(config.endpoint()) ? config.endpoint() : "dysmsapi.aliyuncs.com";
        if (StringUtils.hasText(config.regionId())) {
            aliyunConfig.setRegionId(config.regionId());
        }
        Client client = new Client(aliyunConfig);
        SendSmsRequest request = new SendSmsRequest()
                .setPhoneNumbers(normalizeAliyunPhone(mobile))
                .setSignName(defaultIfBlank(config.signName(), "短信验证码"))
                .setTemplateCode(defaultIfBlank(config.templateCode(), ""))
                .setTemplateParam("{\"code\":\"" + code + "\"}");
        client.sendSms(request);
    }

    private void sendByTencent(SmsGatewayConfig config, String mobile, String code) throws TencentCloudSDKException {
        if (!StringUtils.hasText(config.accessKeyId()) || !StringUtils.hasText(config.accessKeySecret())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "请先补充腾讯云 SecretId/SecretKey");
        }
        Credential cred = new Credential(config.accessKeyId(), config.accessKeySecret());
        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint(StringUtils.hasText(config.endpoint()) ? config.endpoint() : "sms.tencentcloudapi.com");
        httpProfile.setReqMethod("POST");
        httpProfile.setConnTimeout(30);
        httpProfile.setWriteTimeout(30);
        httpProfile.setReadTimeout(30);
        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setSignMethod("HmacSHA256");
        clientProfile.setHttpProfile(httpProfile);
        SmsClient client = new SmsClient(cred, defaultIfBlank(config.regionId(), ""), clientProfile);
        com.tencentcloudapi.sms.v20190711.models.SendSmsRequest request = new com.tencentcloudapi.sms.v20190711.models.SendSmsRequest();
        request.setSmsSdkAppid(defaultIfBlank(config.smsSdkAppId(), ""));
        request.setSign(defaultIfBlank(config.signName(), "短信验证码"));
        request.setTemplateID(defaultIfBlank(config.templateCode(), ""));
        request.setPhoneNumberSet(new String[]{normalizeTencentPhone(mobile)});
        request.setTemplateParamSet(new String[]{code});
        client.SendSms(request);
    }

    private BindingRecord requireBinding(PluginRuntimeContext context, Long tenantId, Long userId) {
        BindingRecord binding = loadBinding(context, tenantId, userId);
        if (binding == null || !StringUtils.hasText(binding.mobile)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "请先绑定手机号");
        }
        return binding;
    }

    private BindingRecord loadBinding(PluginRuntimeContext context, Long tenantId, Long userId) {
        return context.getJdbcTemplate().query(
                """
                        select tenant_id, user_id, mobile, enabled
                        from plugin_sms_binding
                        where tenant_id = ? and user_id = ? and deleted = 0
                        order by id desc
                        limit 1
                        """,
                bindingRowMapper(),
                tenantId,
                userId
        ).stream().findFirst().orElse(null);
    }

    private ChallengeRecord loadChallenge(PluginRuntimeContext context, String challengeId) {
        return context.getJdbcTemplate().query(
                """
                        select challenge_id, tenant_id, user_id, mobile, provider_type, verification_hash, expires_at, consumed_flag
                        from plugin_sms_challenge
                        where challenge_id = ? and deleted = 0
                        limit 1
                        """,
                challengeRowMapper(),
                challengeId
        ).stream().findFirst().orElse(null);
    }

    private RowMapper<BindingRecord> bindingRowMapper() {
        return (rs, rowNum) -> new BindingRecord(
                rs.getLong("tenant_id"),
                rs.getLong("user_id"),
                rs.getString("mobile"),
                rs.getInt("enabled") == 1
        );
    }

    private RowMapper<ChallengeRecord> challengeRowMapper() {
        return (rs, rowNum) -> new ChallengeRecord(
                rs.getString("challenge_id"),
                rs.getLong("tenant_id"),
                rs.getLong("user_id"),
                rs.getString("mobile"),
                rs.getString("provider_type"),
                rs.getString("verification_hash"),
                rs.getTimestamp("expires_at"),
                rs.getInt("consumed_flag") == 1
        );
    }

    private String resolveProviderType(PluginRuntimeContext context, Long tenantId) {
        return loadGatewayConfig(context, tenantId).providerType();
    }

    private void audit(PluginRuntimeContext context, Long tenantId, Long userId, String actionType, String detail) {
        context.getJdbcTemplate().update(
                """
                        insert into plugin_sms_audit (
                            tenant_id, user_id, action_type, detail_message, created_at, deleted
                        ) values (?, ?, ?, ?, current_timestamp, 0)
                        """,
                tenantId,
                userId == null ? 0L : userId,
                actionType,
                detail
        );
    }

    private String getUsername(PluginRuntimeContext context, Long userId) {
        try {
            return context.getJdbcTemplate().queryForObject(
                    "select username from sys_user where id = ? and deleted = 0",
                    String.class,
                    userId
            );
        } catch (Exception exception) {
            return "user-" + userId;
        }
    }

    private String randomCode() {
        int code = SECURE_RANDOM.nextInt(900000) + 100000;
        return String.valueOf(code);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成哈希", exception);
        }
    }

    private String maskMobile(String mobile) {
        String normalized = normalizeMobile(mobile);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        if (normalized.length() <= 7) {
            return "****";
        }
        return normalized.substring(0, 3) + "****" + normalized.substring(normalized.length() - 4);
    }

    private String normalizeMobile(String mobile) {
        if (!StringUtils.hasText(mobile)) {
            return "";
        }
        return mobile.trim().replaceAll("\\s+", "");
    }

    private String normalizeAliyunPhone(String mobile) {
        String normalized = normalizeMobile(mobile);
        if (normalized.startsWith("+86")) {
            return normalized.substring(3);
        }
        if (normalized.startsWith("86") && normalized.length() > 11) {
            return normalized.substring(2);
        }
        return normalized;
    }

    private String normalizeTencentPhone(String mobile) {
        String normalized = normalizeMobile(mobile);
        if (normalized.startsWith("+")) {
            return normalized;
        }
        if (normalized.startsWith("86")) {
            return "+" + normalized;
        }
        return "+86" + normalized;
    }

    private static String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    public record SmsGatewayConfig(
            String providerType,
            String accessKeyId,
            String accessKeySecret,
            String signName,
            String templateCode,
            String smsSdkAppId,
            String regionId,
            String endpoint,
            Boolean configured
    ) {
        SmsGatewayConfig withDefaults() {
            return new SmsGatewayConfig(
                    defaultIfBlank(providerType, "MOCK"),
                    defaultIfBlank(accessKeyId, ""),
                    defaultIfBlank(accessKeySecret, ""),
                    defaultIfBlank(signName, "短信验证码"),
                    defaultIfBlank(templateCode, ""),
                    defaultIfBlank(smsSdkAppId, ""),
                    defaultIfBlank(regionId, ""),
                    defaultIfBlank(endpoint, ""),
                    Boolean.TRUE.equals(configured) || hasCredentials()
            );
        }

        SmsGatewayConfig merge(SmsGatewayConfig current) {
            return new SmsGatewayConfig(
                    defaultIfBlank(providerType, current.providerType()),
                    defaultIfBlank(accessKeyId, current.accessKeyId()),
                    defaultIfBlank(accessKeySecret, current.accessKeySecret()),
                    defaultIfBlank(signName, current.signName()),
                    defaultIfBlank(templateCode, current.templateCode()),
                    defaultIfBlank(smsSdkAppId, current.smsSdkAppId()),
                    defaultIfBlank(regionId, current.regionId()),
                    defaultIfBlank(endpoint, current.endpoint()),
                    Boolean.TRUE.equals(configured) || Boolean.TRUE.equals(current.configured())
            );
        }

        SmsGatewayConfig masked() {
            return new SmsGatewayConfig(
                    providerType,
                    accessKeyId,
                    "",
                    signName,
                    templateCode,
                    smsSdkAppId,
                    regionId,
                    endpoint,
                    configured
            );
        }

        private boolean hasCredentials() {
            return StringUtils.hasText(accessKeyId) && StringUtils.hasText(accessKeySecret);
        }
    }

    private record BindingRecord(Long tenantId, Long userId, String mobile, boolean enabled) {
    }

    private record ChallengeRecord(
            String challengeId,
            Long tenantId,
            Long userId,
            String mobile,
            String providerType,
            String verificationHash,
            Timestamp expiresAt,
            boolean consumedFlag
    ) {
    }

    public record BindingContact(String email, String mobile) {
    }
}
