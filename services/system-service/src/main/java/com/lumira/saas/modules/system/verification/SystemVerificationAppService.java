package com.lumira.saas.modules.system.verification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.web.TraceContext;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.FieldCryptoService;
import com.lumira.saas.modules.auth.dto.SecondFactorCompleteRequest;
import com.lumira.saas.modules.auth.dto.LoginCodeCompleteRequest;
import com.lumira.saas.modules.auth.vo.LoginResponseVO;
import com.lumira.saas.modules.auth.vo.LoginCodeChallengeVO;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.vo.SystemVO;
import com.lumira.saas.modules.system.support.SmsVerificationSender;
import com.lumira.saas.modules.system.support.SmtpMailService;
import com.lumira.saas.modules.user.domain.UserDomainService;
import com.lumira.saas.modules.user.entity.SysUserEntity;
import com.lumira.saas.infrastructure.security.service.SecuritySettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
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
    private static final String TOTP_CONFIG_ENABLED_KEY = "verification.totp.enabled";
    private static final String FACTOR_SMS = "sms";
    private static final String FACTOR_EMAIL = "email";
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
    private static final String EMAIL_LOGIN_ENABLED_KEY = "verification.email-login.enabled";
    private static final String AUDIT_SCENE_LOGIN_CODE = "LOGIN_CODE";
    private static final String AUDIT_SCENE_SECOND_FACTOR = "SECOND_FACTOR";
    private static final String AUDIT_SCENE_CONTACT_BIND = "CONTACT_BIND";

    private final MyBatisQueryOperations jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final UserDomainService userDomainService;
    private final SystemVerificationProperties properties;
    private final SmtpMailService smtpMailService;
    private final SmsVerificationSender smsVerificationSender;
    private final VerificationDeliveryAuditService verificationDeliveryAuditService;
    private final SystemVerificationSettingsAppService settingsAppService;
    private final SecuritySettingsService securitySettingsService;
    private final FieldCryptoService fieldCryptoService;
    private final TotpService totpService = new TotpService();

    public SystemVerificationAppService(
            MyBatisQueryOperations jdbcTemplate,
            ObjectMapper objectMapper,
            UserDomainService userDomainService,
            SystemVerificationProperties properties,
            SmtpMailService smtpMailService,
            SmsVerificationSender smsVerificationSender,
            VerificationDeliveryAuditService verificationDeliveryAuditService,
            SystemVerificationSettingsAppService settingsAppService,
            SecuritySettingsService securitySettingsService,
            FieldCryptoService fieldCryptoService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.userDomainService = userDomainService;
        this.properties = properties;
        this.smtpMailService = smtpMailService;
        this.smsVerificationSender = smsVerificationSender;
        this.verificationDeliveryAuditService = verificationDeliveryAuditService;
        this.settingsAppService = settingsAppService;
        this.securitySettingsService = securitySettingsService;
        this.fieldCryptoService = fieldCryptoService;
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

    public List<LoginResponseVO.SecondFactorOptionVO> listLoginOptions(SysUserEntity user, Long tenantId) {
        return collectSecondFactorOptions(user, tenantId);
    }

    public SystemVO.VerificationChallengeVO bindCurrentUser(CurrentUser currentUser, String factorCode) {
        return bind(requireTenantId(currentUser), currentUser.getUserId(), factorCode);
    }

    public boolean unbindCurrentUser(CurrentUser currentUser, String factorCode) {
        return unbind(requireTenantId(currentUser), currentUser.getUserId(), factorCode);
    }

    public boolean isContactBindVerificationRequired(Long tenantId, String contactType) {
        return isContactBindAvailable(tenantId, contactType);
    }

    public boolean isContactBindAvailable(Long tenantId, String contactType) {
        String normalizedContactType = normalizeContactType(contactType);
        if (FACTOR_SMS.equals(normalizedContactType)) {
            return isSmsLoginAvailable(tenantId);
        }
        if (FACTOR_EMAIL.equals(normalizedContactType)) {
            return isEmailLoginEnabled(tenantId) && smtpMailService.isConfigured(tenantId);
        }
        throw new BizException(ErrorCode.NOT_FOUND, "绑定类型不存在");
    }

    @Transactional
    public SystemVO.VerificationChallengeVO startContactBindChallenge(Long tenantId, Long userId, String contactType, String contactValue) {
        String normalizedContactType = normalizeContactType(contactType);
        ensureContactBindSupported(normalizedContactType);
        SysUserEntity user = requireUser(userId);
        String normalizedContactValue = normalizeContactValue(contactValue);
        validateContactBindPrerequisites(tenantId, normalizedContactType, normalizedContactValue);

        String challengeId = generateChallengeId();
        String verificationCode = generateNumericCode(6);
        String codeHash = totpService.sha256(challengeId + ":" + verificationCode);
        String contactHash = hashContactValue(normalizedContactType, normalizedContactValue);
        String maskedContact = FACTOR_SMS.equals(normalizedContactType)
                ? maskMobile(normalizedContactValue)
                : maskEmail(normalizedContactValue);
        ensureVerificationCodeCooldown(tenantId, userId, normalizedContactType, CHALLENGE_TYPE_BIND);

        persistChallenge(
                challengeId,
                tenantId,
                userId,
                normalizedContactType,
                CHALLENGE_TYPE_BIND,
                contactHash,
                null,
                List.of(),
                codeHash,
                maskedContact,
                verificationCode,
                userId
        );

        deliverVerificationCode(
                tenantId,
                userId,
                user.getUsername(),
                normalizedContactType,
                AUDIT_SCENE_CONTACT_BIND,
                normalizedContactValue,
                maskedContact,
                verificationCode,
                challengeId,
                "邮箱验证码",
                FACTOR_SMS.equals(normalizedContactType) ? loadSmsSettingsRecord(tenantId) : null
        );
        return buildChallengeResponse(
                normalizedContactType,
                contactBindFactorName(normalizedContactType),
                challengeId,
                maskedContact,
                FACTOR_SMS.equals(normalizedContactType)
                        ? "验证码已发送至你填写的手机号，请输入 6 位验证码完成绑定"
                        : "验证码已发送至你填写的邮箱，请输入 6 位验证码完成绑定",
                null,
                null,
                List.of(),
                properties.isExposeDebugCode() ? verificationCode : null
        );
    }

    @Transactional
    public SystemVO.VerificationVerificationVO completeContactBind(
            Long tenantId,
            Long userId,
            String contactType,
            String challengeId,
            String verificationCode,
            String contactValue
    ) {
        String normalizedContactType = normalizeContactType(contactType);
        ensureContactBindSupported(normalizedContactType);
        String normalizedContactValue = normalizeContactValue(contactValue);
        ChallengeRecord challenge = loadChallenge(challengeId, normalizedContactType, CHALLENGE_TYPE_BIND);
        if (!Objects.equals(challenge.setupSecret(), hashContactValue(normalizedContactType, normalizedContactValue))) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "验证码与当前绑定信息不匹配");
        }
        verifyChallengeCode(challenge, verificationCode);
        markChallengeConsumed(challenge.challengeId());
        return verificationResult(tenantId, userId, normalizedContactType, "验证成功");
    }

    public SystemVO.SmsVerificationSettingsVO getSmsSettings(Long tenantId) {
        return settingsAppService.getSmsSettings(tenantId);
    }

    public SystemVO.VerificationSettingsVO getVerificationSettings(Long tenantId) {
        return settingsAppService.getVerificationSettings(tenantId);
    }

    public SystemVO.WechatLoginSettingsVO getWechatSettings(Long tenantId) {
        return settingsAppService.getWechatSettings(tenantId);
    }

    public SystemVO.PasskeySettingsVO getPasskeySettings(Long tenantId) {
        return settingsAppService.getPasskeySettings(tenantId);
    }

    public SystemVO.LoginCapabilitiesVO loadLoginCapabilities(Long tenantId) {
        return settingsAppService.loadLoginCapabilities(tenantId);
    }

    @Transactional
    public SystemVO.VerificationSettingsVO updateVerificationSettings(CurrentUser currentUser, SystemDTO.VerificationSettingsRequest request) {
        return settingsAppService.updateVerificationSettings(currentUser, request);
    }

    @Transactional
    public SystemVO.SmsVerificationSettingsVO updateSmsSettings(CurrentUser currentUser, SystemDTO.SmsVerificationSettingsRequest request) {
        return settingsAppService.updateSmsSettings(currentUser, request);
    }

    @Transactional
    public SystemVO.SmsVerificationSettingsVO resetSmsSettings(CurrentUser currentUser) {
        return settingsAppService.resetSmsSettings(currentUser);
    }

    @Transactional
    public SystemVO.WechatLoginSettingsVO updateWechatSettings(CurrentUser currentUser, SystemDTO.WechatLoginSettingsRequest request) {
        return settingsAppService.updateWechatSettings(currentUser, request);
    }

    @Transactional
    public SystemVO.WechatLoginSettingsVO resetWechatSettings(CurrentUser currentUser) {
        return settingsAppService.resetWechatSettings(currentUser);
    }

    @Transactional
    public SystemVO.PasskeySettingsVO updatePasskeySettings(CurrentUser currentUser, SystemDTO.PasskeySettingsRequest request) {
        return settingsAppService.updatePasskeySettings(currentUser, request);
    }

    @Transactional
    public SystemVO.PasskeySettingsVO resetPasskeySettings(CurrentUser currentUser) {
        return settingsAppService.resetPasskeySettings(currentUser);
    }

    @Transactional
    public SystemVO.VerificationChallengeVO bind(Long tenantId, Long userId, String factorCode) {
        return startBindChallenge(tenantId, userId, normalizeFactorCode(factorCode));
    }

    @Transactional
    public SystemVO.VerificationChallengeVO challenge(Long tenantId, Long userId, String factorCode) {
        return startLoginChallenge(tenantId, userId, normalizeFactorCode(factorCode));
    }

    public LoginCodeChallengeVO startLoginCodeChallenge(SysUserEntity user, Long tenantId, String loginType) {
        String normalizedLoginType = normalizeFactorCode(loginType);
        ensureLoginSupported(normalizedLoginType);
        SmsVerificationSettingsRecord smsSettings = null;
        if (FACTOR_SMS.equals(normalizedLoginType)) {
            smsSettings = loadSmsSettingsRecord(tenantId);
            if (!smsSettings.enabled() || !smsSettings.configured()) {
                throw new BizException(ErrorCode.BIZ_ERROR, "请先配置并启用短信验证码登录");
            }
            if (!StringUtils.hasText(user.getMobile())) {
                throw new BizException(ErrorCode.BIZ_ERROR, "请先补充手机号后再使用短信验证码登录");
            }
        } else if (FACTOR_EMAIL.equals(normalizedLoginType)) {
            if (!isEmailLoginEnabled(tenantId)) {
                throw new BizException(ErrorCode.BIZ_ERROR, "请先启用邮箱验证码登录");
            }
            if (!StringUtils.hasText(user.getEmail())) {
                throw new BizException(ErrorCode.BIZ_ERROR, "请先补充邮箱后再使用邮箱验证码登录");
            }
            if (!smtpMailService.isConfigured(tenantId)) {
                throw new BizException(ErrorCode.BIZ_ERROR, "请先配置 SMTP 后再使用邮箱验证码登录");
            }
        }

        String challengeId = generateChallengeId();
        String verificationCode = generateNumericCode(6);
        String codeHash = totpService.sha256(challengeId + ":" + verificationCode);
        String maskedContact = FACTOR_SMS.equals(normalizedLoginType) ? maskMobile(user.getMobile()) : maskEmail(user.getEmail());
        String promptMessage = FACTOR_SMS.equals(normalizedLoginType)
                ? "验证码已发送至绑定手机号，请输入 6 位验证码完成登录"
                : "验证码已发送至绑定邮箱，请输入 6 位验证码完成登录";
        ensureVerificationCodeCooldown(tenantId, user.getId(), normalizedLoginType, CHALLENGE_TYPE_LOGIN);

        persistChallenge(
                challengeId,
                tenantId,
                user.getId(),
                normalizedLoginType,
                CHALLENGE_TYPE_LOGIN,
                null,
                null,
                List.of(),
                codeHash,
                maskedContact,
                verificationCode,
                user.getId()
        );

        try {
            deliverVerificationCode(
                    tenantId,
                    user.getId(),
                    user.getUsername(),
                    normalizedLoginType,
                    AUDIT_SCENE_LOGIN_CODE,
                    FACTOR_SMS.equals(normalizedLoginType) ? user.getMobile() : user.getEmail(),
                    maskedContact,
                    verificationCode,
                    challengeId,
                    "邮箱验证码",
                    smsSettings
            );
        } catch (RuntimeException exception) {
            discardChallenge(challengeId);
            throw exception;
        }

        LoginCodeChallengeVO challenge = new LoginCodeChallengeVO();
        challenge.setLoginType(normalizedLoginType);
        challenge.setFactorName(loginDefinitionOf(normalizedLoginType).factorName());
        challenge.setChallengeId(challengeId);
        challenge.setMaskedContact(maskedContact);
        challenge.setPromptMessage(promptMessage);
        challenge.setExpiresInSeconds(verificationCodeExpireSeconds());
        challenge.setCooldownSeconds(verificationCodeCooldownSeconds());
        challenge.setDebugCode(properties.isExposeDebugCode() ? verificationCode : null);
        return challenge;
    }

    public SystemVO.VerificationVerificationVO completeLoginCodeLogin(LoginCodeCompleteRequest request) {
        ChallengeRecord challenge = loadChallengeById(request.getChallengeId(), CHALLENGE_TYPE_LOGIN);
        String factorCode = normalizeFactorCode(challenge.factorCode());
        if (!FACTOR_SMS.equals(factorCode) && !FACTOR_EMAIL.equals(factorCode)) {
            throw new BizException(ErrorCode.NOT_FOUND, "验证码会话不存在或已过期");
        }
        if (FACTOR_SMS.equals(factorCode) && !isSmsLoginAvailable(challenge.tenantId())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "请先配置并启用短信验证码登录");
        }
        if (FACTOR_EMAIL.equals(factorCode) && !isEmailLoginAvailable(challenge.tenantId())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "请先配置并启用邮箱验证码登录");
        }
        verifySmsCode(challenge, request.getVerificationCode());
        markChallengeConsumed(challenge.challengeId());
        return verificationResult(challenge.tenantId(), challenge.userId(), factorCode, "验证成功");
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
        if (FACTOR_TOTP.equals(normalizedFactor) && !isTotpEnabled(tenantId)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "请先在系统中启用 2FA 功能");
        }
        if (FACTOR_SMS.equals(normalizedFactor) || FACTOR_EMAIL.equals(normalizedFactor)) {
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
            ensureVerificationCodeCooldown(tenantId, userId, normalizedFactor, CHALLENGE_TYPE_LOGIN);
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
            deliverVerificationCode(
                    tenantId,
                    userId,
                    user.getUsername(),
                    normalizedFactor,
                    AUDIT_SCENE_SECOND_FACTOR,
                    user.getMobile(),
                    maskedContact,
                    verificationCode,
                    challengeId,
                    "邮箱验证码",
                    smsSettings
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
        if (FACTOR_EMAIL.equals(normalizedFactor)) {
            if (!isEmailLoginEnabled(tenantId)) {
                throw new BizException(ErrorCode.BIZ_ERROR, "请先启用邮箱验证码登录");
            }
            SysUserEntity user = requireUser(userId);
            if (!StringUtils.hasText(user.getEmail())) {
                throw new BizException(ErrorCode.BIZ_ERROR, "请先补充邮箱后再使用邮箱验证码登录");
            }
            if (!smtpMailService.isConfigured(tenantId)) {
                throw new BizException(ErrorCode.BIZ_ERROR, "请先配置 SMTP 后再使用邮箱验证码登录");
            }
            ensureVerificationCodeCooldown(tenantId, userId, normalizedFactor, CHALLENGE_TYPE_LOGIN);
            String challengeId = generateChallengeId();
            String verificationCode = generateNumericCode(6);
            String codeHash = totpService.sha256(challengeId + ":" + verificationCode);
            String maskedContact = maskEmail(user.getEmail());
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
            deliverVerificationCode(
                    tenantId,
                    userId,
                    user.getUsername(),
                    normalizedFactor,
                    AUDIT_SCENE_SECOND_FACTOR,
                    user.getEmail(),
                    maskedContact,
                    verificationCode,
                    challengeId,
                    "邮箱验证码",
                    null
            );
            return buildChallengeResponse(
                    normalizedFactor,
                    challengeId,
                    maskedContact,
                    "验证码已发送至绑定邮箱，请输入 6 位邮箱验证码完成验证",
                    null,
                    null,
                    List.of(),
                    properties.isExposeDebugCode() ? verificationCode : null
            );
        }
        if (FACTOR_TOTP.equals(normalizedFactor) && !isTotpEnabled(tenantId)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "请先在系统中启用 2FA 功能");
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
        if (isTotpEnabled(tenantId)) {
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
        }
        SmsVerificationSettingsRecord smsSettings = loadSmsSettingsRecord(tenantId);
        if (smsSettings.enabled() && smsSettings.configured() && StringUtils.hasText(user.getMobile())) {
            SystemVO.VerificationChallengeVO challenge = startLoginChallenge(tenantId, user.getId(), FACTOR_SMS);
            result.add(buildSecondFactorOption(
                    FACTOR_SMS,
                    "短信验证码",
                    challenge.getChallengeId(),
                    challenge.getMaskedContact(),
                    "验证码已发送至绑定手机号，请输入 6 位短信验证码完成验证"
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
        if (FACTOR_TOTP.equals(factorCode) && !isTotpEnabled(challenge.tenantId())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "请先在系统中启用 2FA 功能");
        }
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
        provider.setSystemEnabled(isTotpEnabled(tenantId));
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
        ensureTotpEnabled(tenantId);
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
        return buildChallengeResponse(
                factorCode,
                loginDefinitionOf(factorCode).factorName(),
                challengeId,
                maskedContact,
                promptMessage,
                setupUri,
                setupSecret,
                recoveryCodes,
                debugCode
        );
    }

    private SystemVO.VerificationChallengeVO buildChallengeResponse(
            String factorCode,
            String factorName,
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
        challenge.setFactorName(factorName);
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
        verifyChallengeCode(challenge, verificationCode);
    }

    private void verifyChallengeCode(ChallengeRecord challenge, String verificationCode) {
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

    private String normalizeContactType(String contactType) {
        if (!StringUtils.hasText(contactType)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "绑定类型不能为空");
        }
        String normalized = contactType.trim().toLowerCase(Locale.ROOT);
        if ("mobile".equals(normalized) || FACTOR_SMS.equals(normalized)) {
            return FACTOR_SMS;
        }
        if (FACTOR_EMAIL.equals(normalized)) {
            return FACTOR_EMAIL;
        }
        throw new BizException(ErrorCode.NOT_FOUND, "绑定类型不存在");
    }

    private void ensureContactBindSupported(String contactType) {
        if (!FACTOR_SMS.equals(contactType) && !FACTOR_EMAIL.equals(contactType)) {
            throw new BizException(ErrorCode.NOT_FOUND, "绑定类型不存在");
        }
    }

    private String normalizeContactValue(String contactValue) {
        if (!StringUtils.hasText(contactValue)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "绑定信息不能为空");
        }
        return contactValue.trim();
    }

    private String contactBindFactorName(String contactType) {
        if (FACTOR_SMS.equals(contactType)) {
            return "手机号";
        }
        if (FACTOR_EMAIL.equals(contactType)) {
            return "邮箱";
        }
        return "绑定信息";
    }

    private void validateContactBindPrerequisites(Long tenantId, String contactType, String contactValue) {
        if (FACTOR_SMS.equals(contactType)) {
            SmsVerificationSettingsRecord smsSettings = loadSmsSettingsRecord(tenantId);
            if (!smsSettings.enabled() || !smsSettings.configured()) {
                throw new BizException(ErrorCode.BIZ_ERROR, "请先配置并启用短信验证码服务");
            }
            if (!StringUtils.hasText(contactValue)) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "请输入手机号");
            }
        } else if (FACTOR_EMAIL.equals(contactType)) {
            if (!isEmailLoginEnabled(tenantId)) {
                throw new BizException(ErrorCode.BIZ_ERROR, "请先启用邮箱验证码登录");
            }
            if (!smtpMailService.isConfigured(tenantId)) {
                throw new BizException(ErrorCode.BIZ_ERROR, "请先配置 SMTP 后再绑定邮箱");
            }
            if (!StringUtils.hasText(contactValue)) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "请输入邮箱");
            }
        } else {
            throw new BizException(ErrorCode.NOT_FOUND, "绑定类型不存在");
        }
        if (FACTOR_EMAIL.equals(contactType) && !contactValue.contains("@")) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "请输入有效邮箱地址");
        }
        if (FACTOR_SMS.equals(contactType) && contactValue.length() < 7) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "请输入有效手机号");
        }
    }

    private String hashContactValue(String contactType, String contactValue) {
        return totpService.sha256(contactType + ":" + contactValue);
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
        settings.setAccessKeySecretConfigured(StringUtils.hasText(record.accessKeySecret()));
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
                && StringUtils.hasText(templateCode)
                && StringUtils.hasText(accessKeyId)
                && StringUtils.hasText(accessKeySecret);
        return new SmsVerificationSettingsRecord(enabled, provider, signName, templateCode, accessKeyId, accessKeySecret, endpoint, region, configured);
    }

    private void deliverVerificationCode(
            Long tenantId,
            Long userId,
            String username,
            String channel,
            String scene,
            String target,
            String maskedTarget,
            String verificationCode,
            String challengeId,
            String emailSubject,
            SmsVerificationSettingsRecord smsSettings
    ) {
        String normalizedChannel = channel.toUpperCase(Locale.ROOT);
        try {
            String providerDetail;
            if (FACTOR_EMAIL.equals(channel)) {
                smtpMailService.sendVerificationCode(tenantId, target, verificationCode, emailSubject);
                providerDetail = "provider=smtp";
            } else if (FACTOR_SMS.equals(channel)) {
                SmsVerificationSender.SmsSendResult result = smsVerificationSender.send(toSmsSettings(smsSettings), target, verificationCode);
                providerDetail = "provider=" + defaultIfBlank(smsSettings.provider(), "aliyun")
                        + ", providerRequestId=" + defaultIfBlank(result.requestId(), "-")
                        + ", providerBizId=" + defaultIfBlank(result.bizId(), "-");
            } else {
                throw new BizException(ErrorCode.NOT_FOUND, "验证码渠道不存在");
            }
            verificationDeliveryAuditService.log(
                    tenantId,
                    userId,
                    username,
                    normalizedChannel,
                    scene,
                    "SUCCESS",
                    abbreviate("challengeId=" + challengeId + ", target=" + maskedTarget + ", " + providerDetail)
            );
        } catch (RuntimeException exception) {
            verificationDeliveryAuditService.log(
                    tenantId,
                    userId,
                    username,
                    normalizedChannel,
                    scene,
                    "FAIL",
                    abbreviate("challengeId=" + challengeId + ", target=" + maskedTarget + ", reason=" + exception.getMessage())
            );
            throw exception;
        }
    }

    private SmsVerificationSender.SmsSettings toSmsSettings(SmsVerificationSettingsRecord record) {
        return new SmsVerificationSender.SmsSettings(
                record.provider(),
                record.signName(),
                record.templateCode(),
                record.accessKeyId(),
                record.accessKeySecret(),
                record.endpoint(),
                record.region(),
                record.configured()
        );
    }

    private String abbreviate(String value) {
        if (value == null || value.length() <= 1000) {
            return value;
        }
        return value.substring(0, 1000);
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

    private void upsertPlatformConfigValue(Long tenantId, String configKey, String configName, String configValue, String remark, Long operatorId) {
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
        Long effectiveTenantId = tenantId == null ? com.lumira.common.constant.PlatformConstants.PLATFORM_TENANT_ID : tenantId;
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
        return com.lumira.common.constant.PlatformConstants.PLATFORM_TENANT_ID;
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
                fieldCryptoService.encrypt(secretKey),
                encryptStringList(recoveryCodes),
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
                fieldCryptoService.encrypt(binding.secretKey()),
                encryptStringList(binding.recoveryCodes()),
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
                challengeExpiresAt(factorCode, challengeType),
                fieldCryptoService.encrypt(setupSecret),
                setupUri,
                encryptStringList(recoveryCodes),
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
                challengeExpiresAt(factorCode, challengeType),
                false
        );
    }

    private LocalDateTime challengeExpiresAt(String factorCode, String challengeType) {
        if (isDeliveryCodeChallenge(factorCode)) {
            return LocalDateTime.now().plusSeconds(verificationCodeExpireSeconds());
        }
        return LocalDateTime.now().plusMinutes(CHALLENGE_TYPE_BIND.equals(challengeType)
                ? properties.getBindChallengeExpireMinutes()
                : properties.getLoginChallengeExpireMinutes());
    }

    private void ensureVerificationCodeCooldown(Long tenantId, Long userId, String factorCode, String challengeType) {
        long cooldownSeconds = verificationCodeCooldownSeconds();
        if (cooldownSeconds <= 0 || !isDeliveryCodeChallenge(factorCode)) {
            return;
        }
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(cooldownSeconds);
        LocalDateTime latestCreatedAt = jdbcTemplate.query(
                """
                        select created_at
                        from sys_verification_challenge
                        where tenant_id = ? and user_id = ? and factor_code = ? and challenge_type = ?
                          and created_at > ? and deleted = 0
                        order by created_at desc
                        limit 1
                        """,
                rs -> rs.next() ? rs.getTimestamp("created_at").toLocalDateTime() : null,
                tenantId,
                userId,
                factorCode,
                challengeType,
                threshold
        );
        if (latestCreatedAt == null) {
            return;
        }
        long elapsedSeconds = Math.max(0L, Duration.between(latestCreatedAt, LocalDateTime.now()).getSeconds());
        long remainingSeconds = Math.max(1L, cooldownSeconds - elapsedSeconds);
        throw new BizException(ErrorCode.LOGIN_RATE_LIMITED, "验证码已发送，请 " + remainingSeconds + " 秒后再试");
    }

    private boolean isDeliveryCodeChallenge(String factorCode) {
        return FACTOR_SMS.equals(factorCode) || FACTOR_EMAIL.equals(factorCode);
    }

    private void discardChallenge(String challengeId) {
        jdbcTemplate.update(
                "update sys_verification_challenge set deleted = 1, updated_at = current_timestamp where challenge_id = ? and consumed_flag = 0",
                challengeId
        );
    }

    private long verificationCodeExpireSeconds() {
        return Math.max(1L, securitySettingsService.getVerificationCodeExpireSeconds());
    }

    private long verificationCodeCooldownSeconds() {
        return Math.max(1L, securitySettingsService.getVerificationCodeCooldownSeconds());
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
                            fieldCryptoService.decrypt(rs.getString("setup_secret")),
                            rs.getString("setup_uri"),
                            decryptStringList(rs.getString("recovery_codes_json")),
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

    private ChallengeRecord loadChallengeById(String challengeId, String challengeType) {
        ChallengeRecord challenge = jdbcTemplate.query(
                """
                        select challenge_id, tenant_id, user_id, factor_code, challenge_type, expires_at, consumed_flag,
                               setup_secret, setup_uri, recovery_codes_json, code_hash, masked_contact, debug_code
                        from sys_verification_challenge
                        where challenge_id = ? and challenge_type = ? and deleted = 0
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
                            fieldCryptoService.decrypt(rs.getString("setup_secret")),
                            rs.getString("setup_uri"),
                            decryptStringList(rs.getString("recovery_codes_json")),
                            rs.getString("code_hash"),
                            rs.getString("masked_contact"),
                            rs.getString("debug_code"),
                            rs.getTimestamp("expires_at").toLocalDateTime(),
                            rs.getInt("consumed_flag") == 1
                    );
                },
                challengeId,
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
                            fieldCryptoService.decrypt(rs.getString("secret_key")),
                            decryptStringList(rs.getString("recovery_codes_json")),
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
        if (!isTotpEnabled(tenantId)) {
            return "系统未启用";
        }
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

    private void ensureTotpEnabled(Long tenantId) {
        if (!isTotpEnabled(tenantId)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "请先在系统中启用 2FA 功能");
        }
    }

    private void ensureLoginSupported(String factorCode) {
        if (!FACTOR_TOTP.equals(factorCode) && !FACTOR_SMS.equals(factorCode) && !FACTOR_EMAIL.equals(factorCode)) {
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
                new VerificationMethodDefinition(FACTOR_SMS, "短信验证码", false, true, false),
                new VerificationMethodDefinition(FACTOR_EMAIL, "邮箱验证码", true, false, false)
        );
    }

    private int factorOrder(String factorCode) {
        if (FACTOR_TOTP.equals(factorCode)) {
            return 0;
        }
        if (FACTOR_SMS.equals(factorCode)) {
            return 1;
        }
        if (FACTOR_EMAIL.equals(factorCode)) {
            return 2;
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

    private SystemVO.VerificationSettingsVO loadVerificationSettings(Long tenantId) {
        SystemVO.VerificationSettingsVO settings = new SystemVO.VerificationSettingsVO();
        settings.setEnabled(isTotpEnabled(tenantId));
        settings.setEmailLoginEnabled(isEmailLoginEnabled(tenantId));
        return settings;
    }

    private String toJson(List<String> values) {
        try {
            return values == null ? null : objectMapper.writeValueAsString(values);
        } catch (Exception exception) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "验证码数据序列化失败");
        }
    }

    private String encryptStringList(List<String> values) {
        String json = toJson(values);
        if (!StringUtils.hasText(json)) {
            return json;
        }
        try {
            return objectMapper.writeValueAsString(fieldCryptoService.encrypt(json));
        } catch (Exception exception) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "验证码数据加密失败");
        }
    }

    private List<String> decryptStringList(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        String normalized = value.trim();
        if (normalized.startsWith("[")) {
            return parseStringList(normalized);
        }
        try {
            String encrypted = objectMapper.readValue(normalized, String.class);
            return parseStringList(fieldCryptoService.decrypt(encrypted));
        } catch (Exception exception) {
            return parseStringList(fieldCryptoService.decrypt(normalized));
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
