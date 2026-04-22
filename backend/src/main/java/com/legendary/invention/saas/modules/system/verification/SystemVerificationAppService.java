package com.legendary.invention.saas.modules.system.verification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legendary.invention.saas.common.enums.ErrorCode;
import com.legendary.invention.saas.common.exception.BizException;
import com.legendary.invention.saas.infrastructure.observability.TraceContext;
import com.legendary.invention.saas.infrastructure.security.CurrentUser;
import com.legendary.invention.saas.modules.auth.dto.SecondFactorCompleteRequest;
import com.legendary.invention.saas.modules.auth.vo.LoginResponseVO;
import com.legendary.invention.saas.modules.system.dto.SystemDTO;
import com.legendary.invention.saas.modules.system.vo.SystemVO;
import com.legendary.invention.saas.modules.user.domain.UserDomainService;
import com.legendary.invention.saas.modules.user.entity.SysUserEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class SystemVerificationAppService {

    private static final Logger log = LoggerFactory.getLogger(SystemVerificationAppService.class);
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final String FACTOR_TOTP = "totp";
    private static final String FACTOR_SMS = "sms";
    private static final String CHALLENGE_TYPE_BIND = "BIND";
    private static final String CHALLENGE_TYPE_LOGIN = "LOGIN";
    private static final String SMS_CONFIG_ENABLED_KEY = "verification.sms.enabled";
    private static final String SMS_CONFIG_PROVIDER_KEY = "verification.sms.provider";
    private static final String SMS_CONFIG_SIGN_NAME_KEY = "verification.sms.sign-name";
    private static final String SMS_CONFIG_TEMPLATE_CODE_KEY = "verification.sms.template-code";
    private static final String SMS_CONFIG_ACCESS_KEY_ID_KEY = "verification.sms.access-key-id";
    private static final String SMS_CONFIG_ACCESS_KEY_SECRET_KEY = "verification.sms.access-key-secret";
    private static final String SMS_CONFIG_ENDPOINT_KEY = "verification.sms.endpoint";
    private static final String SMS_CONFIG_REGION_KEY = "verification.sms.region";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final UserDomainService userDomainService;
    private final SystemVerificationProperties properties;
    private final TotpService totpService = new TotpService();

    public SystemVerificationAppService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            UserDomainService userDomainService,
            SystemVerificationProperties properties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.userDomainService = userDomainService;
        this.properties = properties;
    }

    public List<SystemVO.VerificationProviderVO> listProviders(Long tenantId, Long userId) {
        return supportedBindingFactors().stream()
                .map(factor -> resolveProvider(tenantId, userId, factor.factorCode()))
                .sorted(Comparator.comparingInt(provider -> factorOrder(provider.getFactorCode())))
                .toList();
    }

    public SystemVO.VerificationProviderVO provider(Long tenantId, Long userId, String factorCode) {
        return resolveProvider(tenantId, userId, normalizeFactorCode(factorCode));
    }

    public SystemVO.SmsVerificationSettingsVO getSmsSettings(Long tenantId) {
        return loadSmsSettings(tenantId);
    }

    @Transactional
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

        return loadSmsSettings(tenantId);
    }

    @Transactional
    public SystemVO.VerificationChallengeVO bind(Long tenantId, Long userId, String factorCode) {
        return startBindChallenge(tenantId, userId, normalizeFactorCode(factorCode));
    }

    @Transactional
    public SystemVO.VerificationChallengeVO challenge(Long tenantId, Long userId, String factorCode) {
        return startLoginChallenge(tenantId, userId, normalizeFactorCode(factorCode));
    }

    @Transactional
    public boolean unbind(Long tenantId, Long userId, String factorCode) {
        String normalizedFactor = normalizeFactorCode(factorCode);
        ensureBindSupported(normalizedFactor);
        jdbcTemplate.update(
                "update sys_verification_binding set enabled = 0, bound = 0, secret_key = null, recovery_codes_json = null, verified_at = null, updated_by = ?, updated_at = ? where tenant_id = ? and user_id = ? and factor_code = ? and deleted = 0",
                userId,
                LocalDateTime.now(),
                tenantId,
                userId,
                normalizedFactor
        );
        jdbcTemplate.update(
                "update sys_verification_challenge set deleted = 1, updated_at = ? where tenant_id = ? and user_id = ? and factor_code = ? and deleted = 0",
                LocalDateTime.now(),
                tenantId,
                userId,
                normalizedFactor
        );
        return true;
    }

    @Transactional
    public SystemVO.VerificationVerificationVO completeBind(Long tenantId, Long userId, String factorCode, String challengeId, String verificationCode) {
        String normalizedFactor = normalizeFactorCode(factorCode);
        ensureBindSupported(normalizedFactor);
        ChallengeRecord challenge = loadChallenge(challengeId, normalizedFactor, CHALLENGE_TYPE_BIND);
        VerificationBindingRecord binding = loadBinding(tenantId, userId, normalizedFactor)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "验证信息不存在"));
        verifyTotpCode(binding, verificationCode);
        markChallengeConsumed(challenge.challengeId());
        markBindingEnabled(tenantId, userId, normalizedFactor, binding);
        return verificationResult(tenantId, userId, normalizedFactor, "绑定成功");
    }

    @Transactional
    public SystemVO.VerificationVerificationVO verifyLogin(Long tenantId, Long userId, String factorCode, String challengeId, String verificationCode) {
        String normalizedFactor = normalizeFactorCode(factorCode);
        ensureLoginSupported(normalizedFactor);
        ChallengeRecord challenge = loadChallenge(challengeId, normalizedFactor, CHALLENGE_TYPE_LOGIN);
        if (FACTOR_SMS.equals(normalizedFactor)) {
            verifySmsCode(challenge, verificationCode);
        } else {
            VerificationBindingRecord binding = loadEnabledBinding(tenantId, userId, normalizedFactor)
                    .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "验证方式未启用"));
            verifyTotpCode(binding, verificationCode);
        }
        markChallengeConsumed(challenge.challengeId());
        return verificationResult(tenantId, userId, normalizedFactor, "验证成功");
    }

    @Transactional
    public SystemVO.VerificationChallengeVO startLoginChallenge(Long tenantId, Long userId, String factorCode) {
        String normalizedFactor = normalizeFactorCode(factorCode);
        ensureLoginSupported(normalizedFactor);
        if (FACTOR_SMS.equals(normalizedFactor)) {
            SmsVerificationSettingsRecord smsSettings = loadSmsSettingsRecord(tenantId);
            if (!smsSettings.enabled() || !smsSettings.configured()) {
                throw new BizException(ErrorCode.BIZ_ERROR, "请先配置并启用短信验证码服务");
            }
            SysUserEntity user = requireUser(userId);
            if (!StringUtils.hasText(user.getMobile())) {
                throw new BizException(ErrorCode.BIZ_ERROR, "请先补充手机号后再使用短信验证码");
            }
            String challengeId = generateChallengeId();
            String verificationCode = generateNumericCode(6);
            String codeHash = totpService.sha256(challengeId + ":" + verificationCode);
            String maskedContact = maskMobile(user.getMobile());
            persistChallenge(
                    challengeId,
                    tenantId,
                    userId,
                    normalizedFactor,
                    CHALLENGE_TYPE_LOGIN,
                    null,
                    null,
                    List.of(),
                    codeHash,
                    maskedContact,
                    verificationCode,
                    userId
            );
            log.info("SMS login code generated tenantId={} userId={} challengeId={} code={}",
                    tenantId,
                    userId,
                    challengeId,
                    verificationCode
            );
            return buildChallengeResponse(
                    normalizedFactor,
                    challengeId,
                    maskedContact,
                    "验证码已发送至绑定手机号，请输入 6 位短信验证码完成验证",
                    null,
                    null,
                    List.of(),
                    properties.isExposeDebugCode() ? verificationCode : null
            );
        }
        VerificationBindingRecord binding = loadEnabledBinding(tenantId, userId, normalizedFactor)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "验证方式未启用"));
        ChallengeRecord challenge = createChallenge(tenantId, userId, normalizedFactor, CHALLENGE_TYPE_LOGIN, binding);
        return toChallengeVO(binding, challenge, normalizedFactor, false);
    }

    public List<LoginResponseVO.SecondFactorOptionVO> collectSecondFactorOptions(SysUserEntity user, Long tenantId) {
        if (tenantId == null) {
            return List.of();
        }
        List<LoginResponseVO.SecondFactorOptionVO> result = new ArrayList<>();
        loadBinding(tenantId, user.getId(), FACTOR_TOTP)
                .filter(binding -> binding.enabled() && binding.bound())
                .ifPresent(binding -> {
                    SystemVO.VerificationChallengeVO challenge = startLoginChallenge(tenantId, user.getId(), FACTOR_TOTP);
                    result.add(buildSecondFactorOption(
                            FACTOR_TOTP,
                            "2FA",
                            challenge.getChallengeId(),
                            binding.maskedContact(),
                            "请输入认证器中的 6 位验证码完成验证"
                    ));
                });

        SmsVerificationSettingsRecord smsSettings = loadSmsSettingsRecord(tenantId);
        if (smsSettings.enabled() && smsSettings.configured() && StringUtils.hasText(user.getMobile())) {
            SystemVO.VerificationChallengeVO challenge = startLoginChallenge(tenantId, user.getId(), FACTOR_SMS);
            result.add(buildSecondFactorOption(
                    FACTOR_SMS,
                    "短信验证码",
                    challenge.getChallengeId(),
                    challenge.getMaskedContact(),
                    challenge.getPromptMessage()
            ));
        }
        return result;
    }

    private LoginResponseVO.SecondFactorOptionVO buildSecondFactorOption(
            String factorCode,
            String factorName,
            String challengeId,
            String maskedContact,
            String promptMessage
    ) {
        LoginResponseVO.SecondFactorOptionVO option = new LoginResponseVO.SecondFactorOptionVO();
        option.setFactorCode(factorCode);
        option.setFactorName(factorName);
        option.setChallengeId(challengeId);
        option.setMaskedContact(maskedContact);
        option.setPromptMessage(promptMessage);
        return option;
    }

    @Transactional
    public SystemVO.VerificationVerificationVO completeSecondFactorLogin(SecondFactorCompleteRequest request, String loginIp, String userAgent) {
        String factorCode = normalizeFactorCode(request.getFactorCode());
        ChallengeRecord challenge = loadChallenge(request.getChallengeId(), factorCode, CHALLENGE_TYPE_LOGIN);
        if (FACTOR_SMS.equals(factorCode)) {
            verifySmsCode(challenge, request.getVerificationCode());
        } else {
            VerificationBindingRecord binding = loadEnabledBinding(challenge.tenantId(), challenge.userId(), factorCode)
                    .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "验证方式未启用"));
            verifyTotpCode(binding, request.getVerificationCode());
        }
        markChallengeConsumed(challenge.challengeId());
        SystemVO.VerificationVerificationVO result = verificationResult(challenge.tenantId(), challenge.userId(), factorCode, "验证成功");
        log.info("Second factor verified factorCode={} tenantId={} userId={} requestId={} loginIp={} userAgent={}",
                factorCode,
                challenge.tenantId(),
                challenge.userId(),
                TraceContext.getRequestId(),
                loginIp,
                userAgent
        );
        return result;
    }

    private SystemVO.VerificationProviderVO resolveProvider(Long tenantId, Long userId, String factorCode) {
        ensureBindSupported(factorCode);
        VerificationMethodDefinition definition = bindingDefinitionOf(factorCode);
        Optional<VerificationBindingRecord> binding = loadBinding(tenantId, userId, factorCode);
        SystemVO.VerificationProviderVO provider = new SystemVO.VerificationProviderVO();
        provider.setFactorCode(definition.factorCode());
        provider.setFactorName(definition.factorName());
        provider.setEnabled(binding.map(VerificationBindingRecord::enabled).orElse(false));
        provider.setBound(binding.map(VerificationBindingRecord::bound).orElse(false));
        provider.setEmailRequired(definition.emailRequired());
        provider.setMobileRequired(definition.mobileRequired());
        provider.setMaskedContact(binding.map(VerificationBindingRecord::maskedContact).orElseGet(() -> defaultMaskedContact(userId, factorCode)));
        provider.setStatusMessage(resolveStatusMessage(definition, binding.orElse(null), tenantId, userId));
        return provider;
    }

    private SystemVO.VerificationChallengeVO startBindChallenge(Long tenantId, Long userId, String factorCode) {
        ensureBindSupported(factorCode);
        VerificationMethodDefinition definition = bindingDefinitionOf(factorCode);
        SysUserEntity user = requireUser(userId);
        validatePrerequisites(definition, user);

        String secret = totpService.generateSecret();
        List<String> recoveryCodes = totpService.generateRecoveryCodes(properties.getRecoveryCodeCount(), properties.getRecoveryCodeLength());
        String challengeId = generateChallengeId();
        String setupUri = totpService.buildSetupUri(properties.getIssuer(), user.getUsername(), secret, properties.getTotpDigits(), properties.getTotpStepSeconds());
        persistBinding(tenantId, userId, factorCode, false, false, definition.emailRequired(), defaultMaskedContact(userId, factorCode), secret, recoveryCodes, null, user.getId());
        persistChallenge(
                challengeId,
                tenantId,
                userId,
                factorCode,
                CHALLENGE_TYPE_BIND,
                secret,
                setupUri,
                recoveryCodes,
                null,
                null,
                null,
                user.getId()
        );
        return buildChallengeResponse(factorCode, challengeId, defaultMaskedContact(userId, factorCode), "请使用认证器扫描二维码后输入首个验证码完成绑定", setupUri, secret, recoveryCodes, null);
    }

    private SystemVO.VerificationChallengeVO buildChallengeResponse(
            String factorCode,
            String challengeId,
            String maskedContact,
            String promptMessage,
            String setupUri,
            String setupSecret,
            List<String> recoveryCodes,
            String debugCode
    ) {
        SystemVO.VerificationChallengeVO challenge = new SystemVO.VerificationChallengeVO();
        challenge.setFactorCode(factorCode);
        challenge.setFactorName(loginDefinitionOf(factorCode).factorName());
        challenge.setChallengeId(challengeId);
        challenge.setMaskedContact(maskedContact);
        challenge.setPromptMessage(promptMessage);
        challenge.setSetupUri(setupUri);
        challenge.setSetupSecret(setupSecret);
        challenge.setRecoveryCodes(recoveryCodes);
        challenge.setDebugCode(debugCode);
        return challenge;
    }

    private SystemVO.VerificationChallengeVO toChallengeVO(
            VerificationBindingRecord binding,
            ChallengeRecord challenge,
            String factorCode,
            boolean bindFlow
    ) {
        SystemVO.VerificationChallengeVO result = new SystemVO.VerificationChallengeVO();
        result.setFactorCode(factorCode);
        result.setFactorName(bindingDefinitionOf(factorCode).factorName());
        result.setChallengeId(challenge.challengeId());
        result.setMaskedContact(binding.maskedContact());
        result.setPromptMessage(bindFlow
                ? "请按页面提示输入验证码完成绑定"
                : "请输入收到的验证码完成验证");
        result.setSetupUri(challenge.setupUri());
        result.setSetupSecret(challenge.setupSecret());
        result.setRecoveryCodes(challenge.recoveryCodes());
        result.setDebugCode(challenge.debugCode());
        return result;
    }

    private SystemVO.VerificationVerificationVO verificationResult(Long tenantId, Long userId, String factorCode, String message) {
        SysUserEntity user = requireUser(userId);
        SystemVO.VerificationVerificationVO result = new SystemVO.VerificationVerificationVO();
        result.setVerified(true);
        result.setTenantId(tenantId);
        result.setUserId(userId);
        result.setUsername(user.getUsername());
        result.setMessage(message);
        return result;
    }

    private void verifyTotpCode(VerificationBindingRecord binding, String verificationCode) {
        if (!StringUtils.hasText(verificationCode)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "验证码不能为空");
        }
        boolean verified = totpService.verifyCode(binding.secretKey(), verificationCode.trim(), properties.getTotpDigits(), properties.getTotpStepSeconds());
        if (!verified && totpService.matchesRecoveryCode(binding.recoveryCodes(), verificationCode.trim())) {
            verified = true;
        }
        if (!verified) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "验证码错误，请重试");
        }
    }

    private void verifySmsCode(ChallengeRecord challenge, String verificationCode) {
        if (!StringUtils.hasText(verificationCode)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "验证码不能为空");
        }
        if (!StringUtils.hasText(challenge.codeHash())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "验证码会话已失效，请重新获取");
        }
        String actualHash = totpService.sha256(challenge.challengeId() + ":" + verificationCode.trim());
        if (!Objects.equals(challenge.codeHash(), actualHash)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "验证码错误，请重试");
        }
    }

    private SystemVO.SmsVerificationSettingsVO loadSmsSettings(Long tenantId) {
        SmsVerificationSettingsRecord record = loadSmsSettingsRecord(tenantId);
        SystemVO.SmsVerificationSettingsVO settings = new SystemVO.SmsVerificationSettingsVO();
        settings.setEnabled(record.enabled());
        settings.setProvider(record.provider());
        settings.setSignName(record.signName());
        settings.setTemplateCode(record.templateCode());
        settings.setAccessKeyId(record.accessKeyId());
        settings.setAccessKeySecret("");
        settings.setEndpoint(record.endpoint());
        settings.setRegion(record.region());
        settings.setConfigured(record.configured());
        return settings;
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
            throw new BizException(ErrorCode.TENANT_ERROR, "当前未选择租户");
        }
        return currentUser.getCurrentTenantId();
    }

    private void persistBinding(
            Long tenantId,
            Long userId,
            String factorCode,
            boolean enabled,
            boolean bound,
            boolean emailRequired,
            String maskedContact,
            String secretKey,
            List<String> recoveryCodes,
            LocalDateTime verifiedAt,
            Long operatorId
    ) {
        jdbcTemplate.update(
                """
                        insert into sys_verification_binding (
                            tenant_id, user_id, factor_code, factor_name, enabled, bound, email_required, masked_contact,
                            secret_key, recovery_codes_json, verified_at, created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        on duplicate key update
                            factor_name = values(factor_name),
                            enabled = values(enabled),
                            bound = values(bound),
                            email_required = values(email_required),
                            masked_contact = values(masked_contact),
                            secret_key = values(secret_key),
                            recovery_codes_json = values(recovery_codes_json),
                            verified_at = values(verified_at),
                            updated_by = values(updated_by),
                            updated_at = current_timestamp,
                            deleted = 0
                        """,
                tenantId,
                userId,
                factorCode,
                bindingDefinitionOf(factorCode).factorName(),
                enabled ? 1 : 0,
                bound ? 1 : 0,
                emailRequired ? 1 : 0,
                maskedContact,
                secretKey,
                toJson(recoveryCodes),
                verifiedAt,
                operatorId,
                operatorId
        );
    }

    private void markBindingEnabled(Long tenantId, Long userId, String factorCode, VerificationBindingRecord binding) {
        jdbcTemplate.update(
                """
                        update sys_verification_binding
                        set enabled = 1, bound = 1, secret_key = ?, recovery_codes_json = ?, verified_at = current_timestamp,
                            updated_by = ?, updated_at = current_timestamp
                        where tenant_id = ? and user_id = ? and factor_code = ? and deleted = 0
                        """,
                binding.secretKey(),
                toJson(binding.recoveryCodes()),
                userId,
                tenantId,
                userId,
                factorCode
        );
    }

    private void persistChallenge(
            String challengeId,
            Long tenantId,
            Long userId,
            String factorCode,
            String challengeType,
            String setupSecret,
            String setupUri,
            List<String> recoveryCodes,
            String codeHash,
            String maskedContact,
            String debugCode,
            Long operatorId
    ) {
        jdbcTemplate.update(
                """
                        insert into sys_verification_challenge (
                            challenge_id, tenant_id, user_id, factor_code, challenge_type, expires_at, consumed_flag,
                            setup_secret, setup_uri, recovery_codes_json, code_hash, masked_contact, debug_code,
                            created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        on duplicate key update
                            tenant_id = values(tenant_id),
                            user_id = values(user_id),
                            factor_code = values(factor_code),
                            challenge_type = values(challenge_type),
                            expires_at = values(expires_at),
                            consumed_flag = 0,
                            setup_secret = values(setup_secret),
                            setup_uri = values(setup_uri),
                            recovery_codes_json = values(recovery_codes_json),
                            code_hash = values(code_hash),
                            masked_contact = values(masked_contact),
                            debug_code = values(debug_code),
                            updated_by = values(updated_by),
                            updated_at = current_timestamp,
                            deleted = 0
                        """,
                challengeId,
                tenantId,
                userId,
                factorCode,
                challengeType,
                LocalDateTime.now().plusMinutes(CHALLENGE_TYPE_BIND.equals(challengeType)
                        ? properties.getBindChallengeExpireMinutes()
                        : properties.getLoginChallengeExpireMinutes()),
                setupSecret,
                setupUri,
                toJson(recoveryCodes),
                codeHash,
                maskedContact,
                properties.isExposeDebugCode() ? debugCode : null,
                operatorId,
                operatorId
        );
    }

    private ChallengeRecord createChallenge(Long tenantId, Long userId, String factorCode, String challengeType, VerificationBindingRecord binding) {
        String challengeId = generateChallengeId();
        String debugCode = null;
        String codeHash = null;
        if (FACTOR_SMS.equals(factorCode)) {
            debugCode = generateNumericCode(6);
            codeHash = totpService.sha256(challengeId + ":" + debugCode);
        }
        persistChallenge(
                challengeId,
                tenantId,
                userId,
                factorCode,
                challengeType,
                binding == null ? null : binding.secretKey(),
                null,
                binding == null ? List.of() : binding.recoveryCodes(),
                codeHash,
                binding == null ? null : binding.maskedContact(),
                debugCode,
                userId
        );
        return new ChallengeRecord(
                challengeId,
                tenantId,
                userId,
                factorCode,
                challengeType,
                binding == null ? null : binding.secretKey(),
                null,
                binding == null ? List.of() : binding.recoveryCodes(),
                codeHash,
                binding == null ? null : binding.maskedContact(),
                debugCode,
                LocalDateTime.now().plusMinutes(CHALLENGE_TYPE_BIND.equals(challengeType)
                        ? properties.getBindChallengeExpireMinutes()
                        : properties.getLoginChallengeExpireMinutes()),
                false
        );
    }

    private ChallengeRecord loadChallenge(String challengeId, String factorCode, String challengeType) {
        ChallengeRecord challenge = jdbcTemplate.query(
                """
                        select challenge_id, tenant_id, user_id, factor_code, challenge_type, expires_at, consumed_flag,
                               setup_secret, setup_uri, recovery_codes_json, code_hash, masked_contact, debug_code
                        from sys_verification_challenge
                        where challenge_id = ? and factor_code = ? and challenge_type = ? and deleted = 0
                        limit 1
                        """,
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    return new ChallengeRecord(
                            rs.getString("challenge_id"),
                            rs.getLong("tenant_id"),
                            rs.getLong("user_id"),
                            rs.getString("factor_code"),
                            rs.getString("challenge_type"),
                            rs.getString("setup_secret"),
                            rs.getString("setup_uri"),
                            parseStringList(rs.getString("recovery_codes_json")),
                            rs.getString("code_hash"),
                            rs.getString("masked_contact"),
                            rs.getString("debug_code"),
                            rs.getTimestamp("expires_at").toLocalDateTime(),
                            rs.getInt("consumed_flag") == 1
                    );
                },
                challengeId,
                factorCode,
                challengeType
        );
        if (challenge == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "验证码会话不存在或已过期");
        }
        if (challenge.consumedFlag()) {
            throw new BizException(ErrorCode.BIZ_ERROR, "验证码会话已使用，请重新获取");
        }
        if (challenge.expiresAt().isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "验证码已过期，请重新获取");
        }
        return challenge;
    }

    private Optional<VerificationBindingRecord> loadBinding(Long tenantId, Long userId, String factorCode) {
        return jdbcTemplate.query(
                """
                        select tenant_id, user_id, factor_code, factor_name, enabled, bound, email_required, masked_contact,
                               secret_key, recovery_codes_json, verified_at
                        from sys_verification_binding
                        where tenant_id = ? and user_id = ? and factor_code = ? and deleted = 0
                        limit 1
                        """,
                rs -> {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new VerificationBindingRecord(
                            rs.getLong("tenant_id"),
                            rs.getLong("user_id"),
                            rs.getString("factor_code"),
                            rs.getString("factor_name"),
                            rs.getInt("enabled") == 1,
                            rs.getInt("bound") == 1,
                            rs.getInt("email_required") == 1,
                            rs.getString("masked_contact"),
                            rs.getString("secret_key"),
                            parseStringList(rs.getString("recovery_codes_json")),
                            rs.getTimestamp("verified_at") == null ? null : rs.getTimestamp("verified_at").toLocalDateTime()
                    ));
                },
                tenantId,
                userId,
                factorCode
        );
    }

    private Optional<VerificationBindingRecord> loadEnabledBinding(Long tenantId, Long userId, String factorCode) {
        return loadBinding(tenantId, userId, factorCode)
                .filter(record -> record.enabled() && record.bound());
    }

    private void markChallengeConsumed(String challengeId) {
        jdbcTemplate.update(
                "update sys_verification_challenge set consumed_flag = 1, updated_at = current_timestamp where challenge_id = ? and deleted = 0",
                challengeId
        );
    }

    private void validatePrerequisites(VerificationMethodDefinition definition, SysUserEntity user) {
        if (definition.emailRequired() && !StringUtils.hasText(user.getEmail())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "请先补充邮箱后再启用该验证方式");
        }
        if (definition.mobileRequired() && !StringUtils.hasText(user.getMobile())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "请先补充手机号后再启用短信验证码");
        }
    }

    private String resolveStatusMessage(VerificationMethodDefinition definition, VerificationBindingRecord binding, Long tenantId, Long userId) {
        SysUserEntity user = requireUser(userId);
        if (definition.emailRequired() && !StringUtils.hasText(user.getEmail())) {
            return "请先补充邮箱后再启用该验证方式";
        }
        if (definition.mobileRequired() && !StringUtils.hasText(user.getMobile())) {
            return "请先补充手机号后再启用短信验证码";
        }
        if (binding == null || !binding.enabled() || !binding.bound()) {
            return "未绑定";
        }
        return "已绑定，可用于登录";
    }

    private String defaultMaskedContact(Long userId, String factorCode) {
        SysUserEntity user = requireUser(userId);
        if (FACTOR_SMS.equals(factorCode)) {
            return maskMobile(user.getMobile());
        }
        return maskEmail(user.getEmail());
    }

    private SysUserEntity requireUser(Long userId) {
        return userDomainService.findById(userId)
                .orElseThrow(() -> new BizException(ErrorCode.ACCOUNT_NOT_FOUND, "用户不存在"));
    }

    private void ensureBindSupported(String factorCode) {
        if (!FACTOR_TOTP.equals(factorCode)) {
            throw new BizException(ErrorCode.NOT_FOUND, "验证方式不存在");
        }
    }

    private void ensureLoginSupported(String factorCode) {
        if (!FACTOR_TOTP.equals(factorCode) && !FACTOR_SMS.equals(factorCode)) {
            throw new BizException(ErrorCode.NOT_FOUND, "验证方式不存在");
        }
    }

    private VerificationMethodDefinition bindingDefinitionOf(String factorCode) {
        return supportedBindingFactors().stream()
                .filter(definition -> definition.factorCode().equals(factorCode))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "验证方式不存在"));
    }

    private VerificationMethodDefinition loginDefinitionOf(String factorCode) {
        return supportedLoginFactors().stream()
                .filter(definition -> definition.factorCode().equals(factorCode))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "验证方式不存在"));
    }

    private List<VerificationMethodDefinition> supportedBindingFactors() {
        return List.of(new VerificationMethodDefinition(FACTOR_TOTP, "2FA", true, false, true));
    }

    private List<VerificationMethodDefinition> supportedLoginFactors() {
        return List.of(
                new VerificationMethodDefinition(FACTOR_TOTP, "2FA", true, false, true),
                new VerificationMethodDefinition(FACTOR_SMS, "短信验证码", false, true, false)
        );
    }

    private int factorOrder(String factorCode) {
        if (FACTOR_TOTP.equals(factorCode)) {
            return 0;
        }
        if (FACTOR_SMS.equals(factorCode)) {
            return 1;
        }
        return Integer.MAX_VALUE;
    }

    private String normalizeFactorCode(String factorCode) {
        if (!StringUtils.hasText(factorCode)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "验证方式不能为空");
        }
        return factorCode.trim().toLowerCase(Locale.ROOT);
    }

    private String generateChallengeId() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    private String generateNumericCode(int digits) {
        int upperBound = (int) Math.pow(10, digits);
        return String.format(Locale.ROOT, "%0" + digits + "d", ThreadLocalRandom.current().nextInt(upperBound));
    }

    private String normalizeConfigText(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String toJson(List<String> values) {
        try {
            return values == null ? null : objectMapper.writeValueAsString(values);
        } catch (Exception exception) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "验证码数据序列化失败");
        }
    }

    private List<String> parseStringList(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, STRING_LIST);
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String maskEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***" + email.substring(Math.max(atIndex, 0));
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }

    private String maskMobile(String mobile) {
        if (!StringUtils.hasText(mobile)) {
            return null;
        }
        if (mobile.length() <= 4) {
            return "****";
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }

    private record VerificationMethodDefinition(
            String factorCode,
            String factorName,
            boolean emailRequired,
            boolean mobileRequired,
            boolean bindSupported
    ) {
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

    private record VerificationBindingRecord(
            Long tenantId,
            Long userId,
            String factorCode,
            String factorName,
            boolean enabled,
            boolean bound,
            boolean emailRequired,
            String maskedContact,
            String secretKey,
            List<String> recoveryCodes,
            LocalDateTime verifiedAt
    ) {
        String contactValue() {
            return maskedContact;
        }
    }

    private record ChallengeRecord(
            String challengeId,
            Long tenantId,
            Long userId,
            String factorCode,
            String challengeType,
            String setupSecret,
            String setupUri,
            List<String> recoveryCodes,
            String codeHash,
            String maskedContact,
            String debugCode,
            LocalDateTime expiresAt,
            boolean consumedFlag
    ) {
    }
}
