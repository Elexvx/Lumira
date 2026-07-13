/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.fasterxml.jackson.core.type.TypeReference
 *  com.fasterxml.jackson.databind.ObjectMapper
 *  com.lumira.common.enums.ErrorCode
 *  com.lumira.common.exception.BizException
 *  com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled
 *  com.lumira.common.security.AuthenticationTrustSupport
 *  com.lumira.common.security.CurrentUser
 *  com.lumira.common.security.FieldCryptoService
 *  com.lumira.common.web.TraceContext
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.stereotype.Service
 *  org.springframework.transaction.annotation.Transactional
 *  org.springframework.util.StringUtils
 */
package com.lumira.saas.modules.system.verification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.FieldCryptoService;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.infrastructure.security.service.SecuritySettingsService;
import com.lumira.saas.modules.iam.service.IamUserAccount;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.iam.service.IamUserService;
import com.lumira.saas.modules.auth.dto.LoginCodeCompleteRequest;
import com.lumira.saas.modules.auth.dto.SecondFactorCompleteRequest;
import com.lumira.saas.modules.auth.vo.LoginCodeChallengeVO;
import com.lumira.saas.modules.auth.vo.LoginResponseVO;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.support.SmsVerificationSender;
import com.lumira.saas.modules.system.support.SmtpMailService;
import com.lumira.saas.modules.system.verification.SystemVerificationProperties;
import com.lumira.saas.modules.system.verification.SystemVerificationSettingsAppService;
import com.lumira.saas.modules.system.verification.TotpService;
import com.lumira.saas.modules.system.verification.VerificationDeliveryAuditService;
import com.lumira.saas.modules.system.vo.SystemVO;
import com.lumira.saas.modules.user.domain.UserDomainService;
import com.lumira.saas.modules.user.entity.SysUserEntity;
import java.nio.charset.StandardCharsets;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnLumiraControlPlaneEnabled
public class SystemVerificationAppService {
    private static final Logger log = LoggerFactory.getLogger(SystemVerificationAppService.class);
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<List<String>>(){};
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
    private static final String PENDING_LOGIN_REGISTRATION_MARKER = "PENDING_LOGIN_REGISTRATION";
    private static final String PERMISSION_VERIFICATION_VIEW = "system:verification:view";
    private static final String PERMISSION_VERIFICATION_MANAGE = "system:verification:manage";
    private static final String PERMISSION_CONFIG_VIEW = "system:config:view";
    private static final String PERMISSION_CONFIG_UPDATE = "system:config:update";
    private static final String STATUS_ENABLED = "ENABLED";
    private static final int CHALLENGE_ID_LENGTH = 32;
    private static final int MAX_VERIFICATION_CODE_LENGTH = 32;
    private final MyBatisQueryOperations jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final UserDomainService userDomainService;
    private final SystemVerificationProperties properties;
    private final SmtpMailService smtpMailService;
    private final SmsVerificationSender smsVerificationSender;
    private final VerificationDeliveryAuditService verificationDeliveryAuditService;
    private final SystemVerificationSettingsAppService settingsAppService;
    private final SecuritySettingsService securitySettingsService;
    private final IamUserService iamUserService;
    private final PasswordEncoder passwordEncoder;
    private final FieldCryptoService fieldCryptoService;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;
    private final boolean enforceTrustedUserResolution;
    private final TotpService totpService = new TotpService();

    public SystemVerificationAppService(MyBatisQueryOperations jdbcTemplate, ObjectMapper objectMapper, UserDomainService userDomainService, SystemVerificationProperties properties, SmtpMailService smtpMailService, SmsVerificationSender smsVerificationSender, VerificationDeliveryAuditService verificationDeliveryAuditService, SystemVerificationSettingsAppService settingsAppService, SecuritySettingsService securitySettingsService, IamUserService iamUserService, PasswordEncoder passwordEncoder, FieldCryptoService fieldCryptoService, PermissionSnapshotService permissionSnapshotService) {
        this(jdbcTemplate, objectMapper, userDomainService, properties, smtpMailService, smsVerificationSender, verificationDeliveryAuditService, settingsAppService, securitySettingsService, iamUserService, passwordEncoder, fieldCryptoService, permissionSnapshotService, null, null, false);
    }

    public SystemVerificationAppService(MyBatisQueryOperations jdbcTemplate, ObjectMapper objectMapper, UserDomainService userDomainService, SystemVerificationProperties properties, SmtpMailService smtpMailService, SmsVerificationSender smsVerificationSender, VerificationDeliveryAuditService verificationDeliveryAuditService, SystemVerificationSettingsAppService settingsAppService, SecuritySettingsService securitySettingsService, IamUserService iamUserService, PasswordEncoder passwordEncoder, FieldCryptoService fieldCryptoService, PermissionSnapshotService permissionSnapshotService, SessionAuthenticationService sessionAuthenticationService) {
        this(jdbcTemplate, objectMapper, userDomainService, properties, smtpMailService, smsVerificationSender, verificationDeliveryAuditService, settingsAppService, securitySettingsService, iamUserService, passwordEncoder, fieldCryptoService, permissionSnapshotService, null, sessionAuthenticationService, false);
    }

    @Autowired
    public SystemVerificationAppService(MyBatisQueryOperations jdbcTemplate, ObjectMapper objectMapper, UserDomainService userDomainService, SystemVerificationProperties properties, SmtpMailService smtpMailService, SmsVerificationSender smsVerificationSender, VerificationDeliveryAuditService verificationDeliveryAuditService, SystemVerificationSettingsAppService settingsAppService, SecuritySettingsService securitySettingsService, IamUserService iamUserService, PasswordEncoder passwordEncoder, FieldCryptoService fieldCryptoService, PermissionSnapshotService permissionSnapshotService, @Lazy SystemInternalApi systemInternalApi, SessionAuthenticationService sessionAuthenticationService) {
        this(jdbcTemplate, objectMapper, userDomainService, properties, smtpMailService, smsVerificationSender, verificationDeliveryAuditService, settingsAppService, securitySettingsService, iamUserService, passwordEncoder, fieldCryptoService, permissionSnapshotService, systemInternalApi, sessionAuthenticationService, true);
    }

    private SystemVerificationAppService(MyBatisQueryOperations jdbcTemplate, ObjectMapper objectMapper, UserDomainService userDomainService, SystemVerificationProperties properties, SmtpMailService smtpMailService, SmsVerificationSender smsVerificationSender, VerificationDeliveryAuditService verificationDeliveryAuditService, SystemVerificationSettingsAppService settingsAppService, SecuritySettingsService securitySettingsService, IamUserService iamUserService, PasswordEncoder passwordEncoder, FieldCryptoService fieldCryptoService, PermissionSnapshotService permissionSnapshotService, SystemInternalApi systemInternalApi, SessionAuthenticationService sessionAuthenticationService, boolean enforceTrustedUserResolution) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.userDomainService = userDomainService;
        this.properties = properties;
        this.smtpMailService = smtpMailService;
        this.smsVerificationSender = smsVerificationSender;
        this.verificationDeliveryAuditService = verificationDeliveryAuditService;
        this.settingsAppService = settingsAppService;
        this.securitySettingsService = securitySettingsService;
        this.iamUserService = iamUserService;
        this.passwordEncoder = passwordEncoder;
        this.fieldCryptoService = fieldCryptoService;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    public SystemVerificationAppService(MyBatisQueryOperations jdbcTemplate, ObjectMapper objectMapper, UserDomainService userDomainService, SystemVerificationProperties properties, SmtpMailService smtpMailService, SmsVerificationSender smsVerificationSender, VerificationDeliveryAuditService verificationDeliveryAuditService, SystemVerificationSettingsAppService settingsAppService, SecuritySettingsService securitySettingsService, IamUserService iamUserService, PasswordEncoder passwordEncoder, FieldCryptoService fieldCryptoService) {
        this(jdbcTemplate, objectMapper, userDomainService, properties, smtpMailService, smsVerificationSender, verificationDeliveryAuditService, settingsAppService, securitySettingsService, iamUserService, passwordEncoder, fieldCryptoService, null, null, null, false);
    }

    public List<SystemVO.VerificationProviderVO> listProviders(Long userId, String userUuid) {
        Long trustedUserId = this.requireUserIdentity(userId, userUuid).getId();
        String trustedUserUuid = this.normalizeUserUuid(userUuid);
        return this.supportedBindingFactors().stream().map(factor -> this.resolveProvider(trustedUserId, trustedUserUuid, factor.factorCode())).sorted(Comparator.comparingInt(provider -> this.factorOrder(provider.getFactorCode()))).toList();
    }

    public List<SystemVO.VerificationProviderVO> listProviders(CurrentUser currentUser) {
        this.requireVerificationViewPermission(currentUser);
        return this.listProviders(currentUser.getUserId(), currentUser.getUserUuid());
    }

    public SystemVO.VerificationProviderVO provider(Long userId, String userUuid, String factorCode) {
        SysUserEntity user = this.requireUserIdentity(userId, userUuid);
        return this.resolveProvider(user.getId(), user.getUuid(), this.normalizeFactorCode(factorCode));
    }

    public SystemVO.VerificationProviderVO provider(CurrentUser currentUser, String factorCode) {
        this.requireVerificationViewPermission(currentUser);
        return this.provider(currentUser.getUserId(), currentUser.getUserUuid(), factorCode);
    }

    public List<LoginResponseVO.SecondFactorOptionVO> listLoginOptions(SysUserEntity user) {
        return this.collectSecondFactorOptions(user);
    }

    private Long requireAuthenticatedUserId(CurrentUser currentUser) {
        this.refreshTrustedCurrentUser(currentUser);
        if (!AuthenticationTrustSupport.isTrustedCurrentUser((CurrentUser)currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return currentUser.getUserId();
    }

    private Long requireVerificationViewPermission(CurrentUser currentUser) {
        Long userId = this.requireAuthenticatedUserId(currentUser);
        if (!this.hasAnyPermission(currentUser, PERMISSION_VERIFICATION_VIEW, PERMISSION_VERIFICATION_MANAGE, PERMISSION_CONFIG_VIEW, PERMISSION_CONFIG_UPDATE)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Missing permission: system:verification:view");
        }
        return userId;
    }

    private Long requireVerificationManagePermission(CurrentUser currentUser) {
        Long userId = this.requireAuthenticatedUserId(currentUser);
        if (!this.hasAnyPermission(currentUser, PERMISSION_VERIFICATION_MANAGE)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Missing permission: system:verification:manage");
        }
        return userId;
    }

    private boolean hasAnyPermission(CurrentUser currentUser, String ... permissionKeys) {
        Set permissions;
        if (!AuthenticationTrustSupport.isTrustedCurrentUser((CurrentUser)currentUser)) {
            return false;
        }
        Set set = permissions = currentUser.getPermissions() == null ? Set.of() : currentUser.getPermissions();
        if (permissions.contains("*")) {
            return true;
        }
        for (String permissionKey : permissionKeys) {
            if (!permissions.contains(permissionKey)) continue;
            return true;
        }
        return false;
    }

    private void refreshTrustedCurrentUser(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser((CurrentUser)currentUser)) {
            return;
        }
        if (this.sessionAuthenticationService != null) {
            CurrentUser refreshedUser = this.requireTrustedAuthenticatedCurrentUser(this.sessionAuthenticationService.authenticateSessionTicket(currentUser.getSessionId(), currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getSimulatedRoleId(), currentUser.getSessionVersion(), currentUser.getPermissionsVersion()));
            this.copyTrustedCurrentUser(currentUser, refreshedUser);
            return;
        }
        if (this.permissionSnapshotService == null) {
            if (this.enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user resolver is unavailable");
            }
            return;
        }
        Long userId = currentUser.getUserId();
        String normalizedUserUuid = StringUtils.hasText((String)currentUser.getUserUuid()) ? currentUser.getUserUuid().trim() : null;
        Long simulatedRoleId = normalizeSimulatedRoleId(currentUser.getSimulatedRoleId());
        if (userId == null || userId <= 0L || !StringUtils.hasText((String)normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        if (this.systemInternalApi != null) {
            SystemUserSnapshotDTO userSnapshot = this.systemInternalApi.findUserIdentityById(userId);
            if (userSnapshot == null || userSnapshot.userId() == null || !userId.equals(userSnapshot.userId())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
            }
            if (!StringUtils.hasText((String)userSnapshot.userUuid()) || !normalizedUserUuid.equals(userSnapshot.userUuid().trim())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
            }
            if (!STATUS_ENABLED.equalsIgnoreCase(userSnapshot.status())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
            }
            String currentUsername = StringUtils.hasText((String)userSnapshot.username()) ? userSnapshot.username().trim() : null;
            if (!StringUtils.hasText((String)currentUsername)) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user username is unavailable");
            }
            userId = userSnapshot.userId();
            normalizedUserUuid = userSnapshot.userUuid().trim();
            currentUser.setUserId(userId);
            currentUser.setUserUuid(normalizedUserUuid);
            currentUser.setUsername(currentUsername);
        }
        if (!this.permissionSnapshotService.isTrustedActiveUser(userId, normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
        }
        PermissionSnapshotService.PermissionSnapshot snapshot = simulatedRoleId != null
                ? this.permissionSnapshotService.loadGrantedRoleSnapshot(userId, normalizedUserUuid, simulatedRoleId)
                : this.permissionSnapshotService.loadSnapshot(userId, normalizedUserUuid);
        if (snapshot == null) {
            if (this.enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user permission snapshot is unavailable");
            }
            return;
        }
        currentUser.setUserUuid(normalizedUserUuid);
        currentUser.setSimulatedRoleId(simulatedRoleId);
        currentUser.setPermissions(snapshot.getPermissions() == null ? Set.of() : Set.copyOf(snapshot.getPermissions()));
        currentUser.setRoleIds(snapshot.getRoleIds() == null ? Set.of() : Set.copyOf(snapshot.getRoleIds()));
        currentUser.setPrimaryDeptId(snapshot.getPrimaryDeptId());
        currentUser.setDeptIds(snapshot.getDeptIds() == null ? Set.of() : Set.copyOf(snapshot.getDeptIds()));
        currentUser.setDescendantDeptIds(snapshot.getDescendantDeptIds() == null ? Set.of() : Set.copyOf(snapshot.getDescendantDeptIds()));
        currentUser.setDataScopes(snapshot.getDataScopes() == null ? List.of() : List.copyOf(snapshot.getDataScopes()));
        currentUser.setPermissionsVersion(snapshot.getVersion());
        currentUser.setDefaultHomePath(snapshot.getDefaultHomePath());
    }

    private CurrentUser requireTrustedAuthenticatedCurrentUser(SessionAuthenticationService.AuthenticatedAccess authenticatedAccess) {
        CurrentUser refreshedUser = authenticatedAccess == null ? null : authenticatedAccess.currentUser();
        if (!AuthenticationTrustSupport.isTrustedCurrentUser((CurrentUser)refreshedUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return refreshedUser;
    }

    private void copyTrustedCurrentUser(CurrentUser target, CurrentUser source) {
        target.setUserId(source.getUserId());
        target.setUserUuid(source.getUserUuid());
        target.setUsername(source.getUsername());
        target.setSessionId(source.getSessionId());
        target.setSessionVersion(source.getSessionVersion());
        target.setAuthenticated(source.isAuthenticated());
        target.setPermissions(source.getPermissions() == null ? Set.of() : Set.copyOf(source.getPermissions()));
        target.setRoleIds(source.getRoleIds() == null ? Set.of() : Set.copyOf(source.getRoleIds()));
        target.setPrimaryDeptId(source.getPrimaryDeptId());
        target.setDeptIds(source.getDeptIds() == null ? Set.of() : Set.copyOf(source.getDeptIds()));
        target.setDescendantDeptIds(source.getDescendantDeptIds() == null ? Set.of() : Set.copyOf(source.getDescendantDeptIds()));
        target.setDataScopes(source.getDataScopes() == null ? List.of() : List.copyOf(source.getDataScopes()));
        target.setPermissionsVersion(source.getPermissionsVersion());
        target.setRequiresPasswordChange(source.getRequiresPasswordChange());
        target.setDefaultHomePath(source.getDefaultHomePath());
        target.setSimulatedRoleId(normalizeSimulatedRoleId(source.getSimulatedRoleId()));
        target.setLoginType(source.getLoginType());
    }

    private Long normalizeSimulatedRoleId(Long simulatedRoleId) {
        return simulatedRoleId == null || simulatedRoleId <= 0 ? null : simulatedRoleId;
    }

    public SystemVO.VerificationChallengeVO bindCurrentUser(CurrentUser currentUser, String factorCode) {
        return this.bindCurrentUser(currentUser, factorCode, null, null, null, null);
    }

    public SystemVO.VerificationChallengeVO bindCurrentUser(CurrentUser currentUser, String factorCode, String currentPassword, String currentFactorCode, String currentChallengeId, String currentVerificationCode) {
        this.requireVerificationManagePermission(currentUser);
        return this.bind(currentUser.getUserId(), currentUser.getUserUuid(), factorCode, currentPassword, currentFactorCode, currentChallengeId, currentVerificationCode);
    }

    public boolean unbindCurrentUser(CurrentUser currentUser, String factorCode, String challengeId, String verificationCode) {
        this.requireVerificationManagePermission(currentUser);
        return this.unbind(currentUser.getUserId(), currentUser.getUserUuid(), factorCode, challengeId, verificationCode);
    }

    public SystemVO.VerificationChallengeVO challengeCurrentUser(CurrentUser currentUser, String factorCode) {
        this.requireVerificationManagePermission(currentUser);
        return this.challenge(currentUser.getUserId(), currentUser.getUserUuid(), factorCode);
    }

    public SystemVO.VerificationVerificationVO completeBindCurrentUser(CurrentUser currentUser, String factorCode, String challengeId, String verificationCode) {
        this.requireVerificationManagePermission(currentUser);
        return this.completeBind(currentUser.getUserId(), currentUser.getUserUuid(), factorCode, challengeId, verificationCode);
    }

    public boolean isContactBindVerificationRequired(String contactType) {
        return this.isContactBindAvailable(contactType);
    }

    public boolean isContactBindAvailable(String contactType) {
        String normalizedContactType = this.normalizeContactType(contactType);
        if (FACTOR_SMS.equals(normalizedContactType)) {
            return this.isSmsLoginAvailable();
        }
        if (FACTOR_EMAIL.equals(normalizedContactType)) {
            return this.isEmailLoginEnabled() && this.smtpMailService.isConfigured();
        }
        throw new BizException(ErrorCode.NOT_FOUND, "\u7ed1\u5b9a\u7c7b\u578b\u4e0d\u5b58\u5728");
    }

    @Transactional
    public SystemVO.VerificationChallengeVO startContactBindChallenge(Long userId, String userUuid, String contactType, String contactValue, String currentFactorCode, String currentChallengeId, String currentVerificationCode) {
        SysUserEntity user = this.requireUserIdentity(userId, userUuid);
        String trustedUserUuid = this.normalizeUserUuid(userUuid);
        String normalizedContactType = this.normalizeContactType(contactType);
        this.ensureContactBindSupported(normalizedContactType);
        this.requireSensitiveContactChangeVerification(user, trustedUserUuid, currentFactorCode, currentChallengeId, currentVerificationCode);
        String normalizedContactValue = this.normalizeContactValue(contactValue);
        this.validateContactBindPrerequisites(normalizedContactType, normalizedContactValue);
        String challengeId = this.generateChallengeId();
        String verificationCode = this.generateNumericCode(6);
        String codeHash = this.totpService.sha256(challengeId + ":" + verificationCode);
        String contactHash = this.hashContactValue(normalizedContactType, normalizedContactValue);
        String maskedContact = FACTOR_SMS.equals(normalizedContactType) ? this.maskMobile(normalizedContactValue) : this.maskEmail(normalizedContactValue);
        this.ensureVerificationCodeCooldown(userId, trustedUserUuid, normalizedContactType, CHALLENGE_TYPE_BIND);
        this.persistChallenge(challengeId, userId, trustedUserUuid, normalizedContactType, CHALLENGE_TYPE_BIND, contactHash, null, List.of(), codeHash, maskedContact, verificationCode, userId);
        this.deliverVerificationCode(userId, trustedUserUuid, user.getUsername(), normalizedContactType, AUDIT_SCENE_CONTACT_BIND, normalizedContactValue, maskedContact, verificationCode, challengeId, "\u90ae\u7bb1\u9a8c\u8bc1\u7801", FACTOR_SMS.equals(normalizedContactType) ? this.loadSmsSettingsRecord() : null);
        return this.buildChallengeResponse(normalizedContactType, this.contactBindFactorName(normalizedContactType), challengeId, maskedContact, FACTOR_SMS.equals(normalizedContactType) ? "\u9a8c\u8bc1\u7801\u5df2\u53d1\u9001\u81f3\u4f60\u586b\u5199\u7684\u624b\u673a\u53f7\uff0c\u8bf7\u8f93\u5165 6 \u4f4d\u9a8c\u8bc1\u7801\u5b8c\u6210\u7ed1\u5b9a" : "\u9a8c\u8bc1\u7801\u5df2\u53d1\u9001\u81f3\u4f60\u586b\u5199\u7684\u90ae\u7bb1\uff0c\u8bf7\u8f93\u5165 6 \u4f4d\u9a8c\u8bc1\u7801\u5b8c\u6210\u7ed1\u5b9a", null, null, List.of(), this.properties.isExposeDebugCode() ? verificationCode : null);
    }

    private void requireSensitiveContactChangeVerification(SysUserEntity user, String trustedUserUuid, String currentFactorCode, String currentChallengeId, String currentVerificationCode) {
        List<String> availableFactors = this.availableSensitiveContactChangeFactors(user, trustedUserUuid);
        if (availableFactors.isEmpty()) {
            return;
        }
        if (!StringUtils.hasText(currentFactorCode) || !StringUtils.hasText(currentChallengeId) || !StringUtils.hasText(currentVerificationCode)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "\u8bf7\u5148\u4f7f\u7528\u5f53\u524d\u5df2\u7ed1\u5b9a\u7684\u9a8c\u8bc1\u65b9\u5f0f\u5b8c\u6210\u8eab\u4efd\u786e\u8ba4");
        }
        String normalizedCurrentFactor = this.normalizeFactorCode(currentFactorCode);
        if (!availableFactors.contains(normalizedCurrentFactor)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "\u5f53\u524d\u9a8c\u8bc1\u65b9\u5f0f\u672a\u7ed1\u5b9a\u6216\u4e0d\u53ef\u7528");
        }
        this.verifyLogin(user.getId(), trustedUserUuid, normalizedCurrentFactor, currentChallengeId, currentVerificationCode);
    }

    private List<String> availableSensitiveContactChangeFactors(SysUserEntity user, String trustedUserUuid) {
        List<String> factors = new ArrayList<>();
        if (this.isTotpEnabled() && this.loadEnabledBinding(user.getId(), trustedUserUuid, FACTOR_TOTP).isPresent()) {
            factors.add(FACTOR_TOTP);
        }
        if (this.isSmsLoginAvailable() && StringUtils.hasText(user.getMobile())) {
            factors.add(FACTOR_SMS);
        }
        if (this.isEmailLoginEnabled() && this.smtpMailService.isConfigured() && StringUtils.hasText(user.getEmail())) {
            factors.add(FACTOR_EMAIL);
        }
        return factors;
    }

    @Transactional
    public SystemVO.VerificationVerificationVO completeContactBind(Long userId, String userUuid, String contactType, String challengeId, String verificationCode, String contactValue) {
        SysUserEntity user = this.requireUserIdentity(userId, userUuid);
        String trustedUserUuid = this.normalizeUserUuid(userUuid);
        String normalizedContactType = this.normalizeContactType(contactType);
        this.ensureContactBindSupported(normalizedContactType);
        String normalizedContactValue = this.normalizeContactValue(contactValue);
        ChallengeRecord challenge = this.loadChallenge(challengeId, normalizedContactType, CHALLENGE_TYPE_BIND);
        this.requireChallengeOwner(challenge, user.getId(), trustedUserUuid);
        if (!Objects.equals(challenge.setupSecret(), this.hashContactValue(normalizedContactType, normalizedContactValue))) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "\u9a8c\u8bc1\u7801\u4e0e\u5f53\u524d\u7ed1\u5b9a\u4fe1\u606f\u4e0d\u5339\u914d");
        }
        this.verifyChallengeCode(challenge, verificationCode);
        this.markChallengeConsumed(challenge);
        return this.verificationResult(userId, normalizedContactType, "\u9a8c\u8bc1\u6210\u529f");
    }

    public SystemVO.SmsVerificationSettingsVO getSmsSettings() {
        return this.settingsAppService.getSmsSettings();
    }

    public SystemVO.SmsVerificationSettingsVO getSmsSettings(CurrentUser currentUser) {
        this.requireVerificationViewPermission(currentUser);
        return this.getSmsSettings();
    }

    public SystemVO.VerificationSettingsVO getVerificationSettings() {
        return this.settingsAppService.getVerificationSettings();
    }

    public SystemVO.VerificationSettingsVO getVerificationSettings(CurrentUser currentUser) {
        this.requireVerificationViewPermission(currentUser);
        return this.getVerificationSettings();
    }

    public SystemVO.WechatLoginSettingsVO getWechatSettings() {
        return this.settingsAppService.getWechatSettings();
    }

    public SystemVO.WechatLoginSettingsVO getWechatSettings(CurrentUser currentUser) {
        this.requireVerificationViewPermission(currentUser);
        return this.getWechatSettings();
    }

    public SystemVO.PasskeySettingsVO getPasskeySettings() {
        return this.settingsAppService.getPasskeySettings();
    }

    public SystemVO.PasskeySettingsVO getPasskeySettings(CurrentUser currentUser) {
        this.requireVerificationViewPermission(currentUser);
        return this.getPasskeySettings();
    }

    public SystemVO.LoginCapabilitiesVO loadLoginCapabilities() {
        return this.settingsAppService.loadLoginCapabilities();
    }

    public SystemVO.LoginCapabilitiesVO loadLoginCapabilitiesFresh() {
        return this.settingsAppService.loadLoginCapabilitiesFresh();
    }

    @Transactional
    public SystemVO.VerificationSettingsVO updateVerificationSettings(CurrentUser currentUser, SystemDTO.VerificationSettingsRequest request) {
        return this.settingsAppService.updateVerificationSettings(currentUser, request);
    }

    @Transactional
    public SystemVO.SmsVerificationSettingsVO updateSmsSettings(CurrentUser currentUser, SystemDTO.SmsVerificationSettingsRequest request) {
        return this.settingsAppService.updateSmsSettings(currentUser, request);
    }

    @Transactional
    public SystemVO.SmsVerificationSettingsVO resetSmsSettings(CurrentUser currentUser) {
        return this.settingsAppService.resetSmsSettings(currentUser);
    }

    @Transactional
    public SystemVO.WechatLoginSettingsVO updateWechatSettings(CurrentUser currentUser, SystemDTO.WechatLoginSettingsRequest request) {
        return this.settingsAppService.updateWechatSettings(currentUser, request);
    }

    @Transactional
    public SystemVO.WechatLoginSettingsVO resetWechatSettings(CurrentUser currentUser) {
        return this.settingsAppService.resetWechatSettings(currentUser);
    }

    @Transactional
    public SystemVO.PasskeySettingsVO updatePasskeySettings(CurrentUser currentUser, SystemDTO.PasskeySettingsRequest request) {
        return this.settingsAppService.updatePasskeySettings(currentUser, request);
    }

    @Transactional
    public SystemVO.PasskeySettingsVO resetPasskeySettings(CurrentUser currentUser) {
        return this.settingsAppService.resetPasskeySettings(currentUser);
    }

    @Transactional
    public SystemVO.VerificationChallengeVO bind(Long userId, String userUuid, String factorCode) {
        return this.bind(userId, userUuid, factorCode, null, null, null, null);
    }

    @Transactional
    public SystemVO.VerificationChallengeVO bind(Long userId, String userUuid, String factorCode, String currentPassword, String currentFactorCode, String currentChallengeId, String currentVerificationCode) {
        SysUserEntity user = this.requireUserIdentity(userId, userUuid);
        return this.startBindChallenge(
                user.getId(),
                user.getUuid(),
                this.normalizeFactorCode(factorCode),
                currentPassword,
                currentFactorCode,
                currentChallengeId,
                currentVerificationCode
        );
    }

    @Transactional
    public SystemVO.VerificationChallengeVO challenge(Long userId, String userUuid, String factorCode) {
        SysUserEntity user = this.requireUserIdentity(userId, userUuid);
        return this.startLoginChallenge(user.getId(), user.getUuid(), this.normalizeFactorCode(factorCode));
    }

    public LoginCodeChallengeVO startLoginCodeChallenge(SysUserEntity user, String loginType) {
        user = this.requireTrustedActiveUser(user);
        String normalizedLoginType = this.normalizeFactorCode(loginType);
        this.ensureLoginSupported(normalizedLoginType);
        SmsVerificationSettingsRecord smsSettings = null;
        if (FACTOR_SMS.equals(normalizedLoginType)) {
            smsSettings = this.loadSmsSettingsRecord();
            if (!smsSettings.enabled() || !smsSettings.configured()) {
                throw new BizException(ErrorCode.BIZ_ERROR, "\u8bf7\u5148\u914d\u7f6e\u5e76\u542f\u7528\u77ed\u4fe1\u9a8c\u8bc1\u7801\u767b\u5f55");
            }
            if (!StringUtils.hasText((String)user.getMobile())) {
                throw new BizException(ErrorCode.BIZ_ERROR, "\u8bf7\u5148\u8865\u5145\u624b\u673a\u53f7\u540e\u518d\u4f7f\u7528\u77ed\u4fe1\u9a8c\u8bc1\u7801\u767b\u5f55");
            }
        } else if (FACTOR_EMAIL.equals(normalizedLoginType)) {
            if (!this.isEmailLoginEnabled()) {
                throw new BizException(ErrorCode.BIZ_ERROR, "\u8bf7\u5148\u542f\u7528\u90ae\u7bb1\u9a8c\u8bc1\u7801\u767b\u5f55");
            }
            if (!StringUtils.hasText((String)user.getEmail())) {
                throw new BizException(ErrorCode.BIZ_ERROR, "\u8bf7\u5148\u8865\u5145\u90ae\u7bb1\u540e\u518d\u4f7f\u7528\u90ae\u7bb1\u9a8c\u8bc1\u7801\u767b\u5f55");
            }
            if (!this.smtpMailService.isConfigured()) {
                throw new BizException(ErrorCode.BIZ_ERROR, "\u8bf7\u5148\u914d\u7f6e SMTP \u540e\u518d\u4f7f\u7528\u90ae\u7bb1\u9a8c\u8bc1\u7801\u767b\u5f55");
            }
        }
        String challengeId = this.generateChallengeId();
        String verificationCode = this.generateNumericCode(6);
        String codeHash = this.totpService.sha256(challengeId + ":" + verificationCode);
        String maskedContact = FACTOR_SMS.equals(normalizedLoginType) ? this.maskMobile(user.getMobile()) : this.maskEmail(user.getEmail());
        String promptMessage = FACTOR_SMS.equals(normalizedLoginType) ? "\u9a8c\u8bc1\u7801\u5df2\u53d1\u9001\u81f3\u7ed1\u5b9a\u624b\u673a\u53f7\uff0c\u8bf7\u8f93\u5165 6 \u4f4d\u9a8c\u8bc1\u7801\u5b8c\u6210\u767b\u5f55" : "\u9a8c\u8bc1\u7801\u5df2\u53d1\u9001\u81f3\u7ed1\u5b9a\u90ae\u7bb1\uff0c\u8bf7\u8f93\u5165 6 \u4f4d\u9a8c\u8bc1\u7801\u5b8c\u6210\u767b\u5f55";
        String trustedUserUuid = this.normalizeUserUuid(user.getUuid());
        this.ensureVerificationCodeCooldown(user.getId(), trustedUserUuid, normalizedLoginType, CHALLENGE_TYPE_LOGIN);
        this.persistChallenge(challengeId, user.getId(), trustedUserUuid, normalizedLoginType, CHALLENGE_TYPE_LOGIN, null, null, List.of(), codeHash, maskedContact, verificationCode, user.getId());
        try {
            this.deliverVerificationCode(user.getId(), trustedUserUuid, user.getUsername(), normalizedLoginType, AUDIT_SCENE_LOGIN_CODE, FACTOR_SMS.equals(normalizedLoginType) ? user.getMobile() : user.getEmail(), maskedContact, verificationCode, challengeId, "\u90ae\u7bb1\u9a8c\u8bc1\u7801", smsSettings);
        }
        catch (RuntimeException exception) {
            this.discardChallenge(challengeId, user.getId(), trustedUserUuid, normalizedLoginType, CHALLENGE_TYPE_LOGIN);
            throw exception;
        }
        LoginCodeChallengeVO challenge = new LoginCodeChallengeVO();
        challenge.setLoginType(normalizedLoginType);
        challenge.setFactorName(this.loginDefinitionOf(normalizedLoginType).factorName());
        challenge.setChallengeId(challengeId);
        challenge.setMaskedContact(maskedContact);
        challenge.setPromptMessage(promptMessage);
        challenge.setExpiresInSeconds(this.verificationCodeExpireSeconds());
        challenge.setCooldownSeconds(this.verificationCodeCooldownSeconds());
        challenge.setDebugCode(this.properties.isExposeDebugCode() ? verificationCode : null);
        return challenge;
    }

    public LoginCodeChallengeVO startPendingLoginCodeChallenge(String account, String loginType) {
        String normalizedLoginType = this.normalizeFactorCode(loginType);
        if (!FACTOR_SMS.equals(normalizedLoginType)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Only sms login supports automatic registration");
        }
        String normalizedAccount = this.normalizePendingLoginRegistrationAccount(account, normalizedLoginType);
        this.ensureLoginSupported(normalizedLoginType);
        SmsVerificationSettingsRecord smsSettings = this.loadSmsSettingsRecord();
        if (!smsSettings.enabled() || !smsSettings.configured()) {
            throw new BizException(ErrorCode.BIZ_ERROR, "请先配置并启用短信验证码登录");
        }
        PendingLoginRegistrationIdentity pendingIdentity = this.pendingLoginRegistrationIdentity(normalizedLoginType, normalizedAccount);
        String challengeId = this.generateChallengeId();
        String verificationCode = this.generateNumericCode(6);
        String codeHash = this.totpService.sha256(challengeId + ":" + verificationCode);
        String maskedContact = this.maskMobile(normalizedAccount);
        String promptMessage = "验证码已发送至待验证手机号，请输入 6 位验证码完成登录";
        this.ensureVerificationCodeCooldown(pendingIdentity.userId(), pendingIdentity.userUuid(), normalizedLoginType, CHALLENGE_TYPE_LOGIN);
        this.persistChallenge(
                challengeId,
                pendingIdentity.userId(),
                pendingIdentity.userUuid(),
                normalizedLoginType,
                CHALLENGE_TYPE_LOGIN,
                normalizedAccount,
                PENDING_LOGIN_REGISTRATION_MARKER,
                List.of(),
                codeHash,
                maskedContact,
                verificationCode,
                pendingIdentity.userId()
        );
        try {
            this.deliverVerificationCode(
                    pendingIdentity.userId(),
                    pendingIdentity.userUuid(),
                    pendingIdentity.auditUsername(),
                    normalizedLoginType,
                    AUDIT_SCENE_LOGIN_CODE,
                    normalizedAccount,
                    maskedContact,
                    verificationCode,
                    challengeId,
                    "Email verification code",
                    smsSettings
            );
        } catch (RuntimeException exception) {
            this.discardChallenge(challengeId, pendingIdentity.userId(), pendingIdentity.userUuid(), normalizedLoginType, CHALLENGE_TYPE_LOGIN);
            throw exception;
        }
        LoginCodeChallengeVO challenge = new LoginCodeChallengeVO();
        challenge.setLoginType(normalizedLoginType);
        challenge.setFactorName(this.loginDefinitionOf(normalizedLoginType).factorName());
        challenge.setChallengeId(challengeId);
        challenge.setMaskedContact(maskedContact);
        challenge.setPromptMessage(promptMessage);
        challenge.setExpiresInSeconds(this.verificationCodeExpireSeconds());
        challenge.setCooldownSeconds(this.verificationCodeCooldownSeconds());
        challenge.setDebugCode(this.properties.isExposeDebugCode() ? verificationCode : null);
        return challenge;
    }

    public Optional<PendingLoginCodeVerification> completePendingLoginCodeLoginIfPresent(LoginCodeCompleteRequest request) {
        this.requireRequest(request, "Login code request is required");
        ChallengeRecord challenge = this.loadChallengeById(request.getChallengeId(), CHALLENGE_TYPE_LOGIN);
        if (!this.isPendingLoginRegistrationChallenge(challenge)) {
            return Optional.empty();
        }
        this.verifySmsCode(challenge, request.getVerificationCode());
        this.markChallengeConsumed(challenge);
        return Optional.of(new PendingLoginCodeVerification(challenge.setupSecret(), challenge.factorCode(), "验证成功"));
    }

    public SystemVO.VerificationVerificationVO completeLoginCodeLogin(LoginCodeCompleteRequest request) {
        this.requireRequest(request, "Login code request is required");
        ChallengeRecord challenge = this.loadChallengeById(request.getChallengeId(), CHALLENGE_TYPE_LOGIN);
        if (this.isPendingLoginRegistrationChallenge(challenge)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Pending login registration challenge must be completed by control plane");
        }
        SysUserEntity user = this.requireUserIdentity(challenge.userId(), challenge.userUuid());
        String factorCode = this.normalizeFactorCode(challenge.factorCode());
        if (!FACTOR_SMS.equals(factorCode) && !FACTOR_EMAIL.equals(factorCode)) {
            throw new BizException(ErrorCode.NOT_FOUND, "\u9a8c\u8bc1\u7801\u4f1a\u8bdd\u4e0d\u5b58\u5728\u6216\u5df2\u8fc7\u671f");
        }
        if (FACTOR_SMS.equals(factorCode) && !this.isSmsLoginAvailable()) {
            throw new BizException(ErrorCode.BIZ_ERROR, "\u8bf7\u5148\u914d\u7f6e\u5e76\u542f\u7528\u77ed\u4fe1\u9a8c\u8bc1\u7801\u767b\u5f55");
        }
        if (FACTOR_EMAIL.equals(factorCode) && !this.isEmailLoginAvailable()) {
            throw new BizException(ErrorCode.BIZ_ERROR, "\u8bf7\u5148\u914d\u7f6e\u5e76\u542f\u7528\u90ae\u7bb1\u9a8c\u8bc1\u7801\u767b\u5f55");
        }
        this.verifySmsCode(challenge, request.getVerificationCode());
        this.markChallengeConsumed(challenge);
        return this.verificationResult(user.getId(), factorCode, "\u9a8c\u8bc1\u6210\u529f");
    }

    @Transactional
    public boolean unbind(Long userId, String userUuid, String factorCode, String challengeId, String verificationCode) {
        SysUserEntity user = this.requireUserIdentity(userId, userUuid);
        String trustedUserUuid = this.normalizeUserUuid(userUuid);
        String normalizedFactor = this.normalizeFactorCode(factorCode);
        this.ensureBindSupported(normalizedFactor);
        this.verifyLogin(user.getId(), trustedUserUuid, normalizedFactor, challengeId, verificationCode);
        return this.unbindVerified(user.getId(), trustedUserUuid, normalizedFactor);
    }

    private boolean unbindVerified(Long userId, String userUuid, String factorCode) {
        String trustedUserUuid = this.normalizeUserUuid(userUuid);
        String normalizedFactor = this.normalizeFactorCode(factorCode);
        int bindingUpdated = this.jdbcTemplate.update("update sys_verification_binding set enabled = 0, bound = 0, secret_key = null, recovery_codes_json = null, verified_at = null, updated_by = ?, updated_by_uuid = ?, updated_at = ? where user_id = ? and user_uuid = ? and factor_code = ? and deleted = 0", userId, trustedUserUuid, LocalDateTime.now(), userId, trustedUserUuid, normalizedFactor);
        this.requireVerificationWrite(bindingUpdated, "Verification binding changed, please retry");
        this.jdbcTemplate.update("update sys_verification_challenge set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ? where user_id = ? and user_uuid = ? and factor_code = ? and deleted = 0", userId, trustedUserUuid, LocalDateTime.now(), userId, trustedUserUuid, normalizedFactor);
        return true;
    }

    @Transactional
    public SystemVO.VerificationVerificationVO completeBind(Long userId, String userUuid, String factorCode, String challengeId, String verificationCode) {
        SysUserEntity user = this.requireUserIdentity(userId, userUuid);
        String trustedUserUuid = this.normalizeUserUuid(userUuid);
        String normalizedFactor = this.normalizeFactorCode(factorCode);
        this.ensureBindSupported(normalizedFactor);
        ChallengeRecord challenge = this.loadChallenge(challengeId, normalizedFactor, CHALLENGE_TYPE_BIND);
        this.requireChallengeOwner(challenge, user.getId(), trustedUserUuid);
        VerificationBindingRecord binding = this.loadBinding(user.getId(), trustedUserUuid, normalizedFactor).orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "\u9a8c\u8bc1\u4fe1\u606f\u4e0d\u5b58\u5728"));
        this.verifyTotpEnrollmentCode(binding, verificationCode);
        List<String> recoveryCodes = this.totpService.generateRecoveryCodes(this.properties.getRecoveryCodeCount(), this.properties.getRecoveryCodeLength());
        this.markChallengeConsumed(challenge);
        this.markBindingEnabled(user.getId(), trustedUserUuid, normalizedFactor, binding, recoveryCodes);
        return this.verificationResult(userId, normalizedFactor, "\u7ed1\u5b9a\u6210\u529f", recoveryCodes);
    }

    @Transactional
    public SystemVO.VerificationVerificationVO verifyLogin(Long userId, String userUuid, String factorCode, String challengeId, String verificationCode) {
        SysUserEntity user = this.requireUserIdentity(userId, userUuid);
        String trustedUserUuid = this.normalizeUserUuid(userUuid);
        String normalizedFactor = this.normalizeFactorCode(factorCode);
        this.ensureLoginSupported(normalizedFactor);
        ChallengeRecord challenge = this.loadChallenge(challengeId, normalizedFactor, CHALLENGE_TYPE_LOGIN);
        this.requireChallengeOwner(challenge, user.getId(), trustedUserUuid);
        if (FACTOR_TOTP.equals(normalizedFactor) && !this.isTotpEnabled()) {
            throw new BizException(ErrorCode.BIZ_ERROR, "\u8bf7\u5148\u5728\u7cfb\u7edf\u4e2d\u542f\u7528 2FA \u529f\u80fd");
        }
        if (FACTOR_SMS.equals(normalizedFactor) || FACTOR_EMAIL.equals(normalizedFactor)) {
            this.verifySmsCode(challenge, verificationCode);
        } else {
            VerificationBindingRecord binding = this.loadEnabledBinding(user.getId(), trustedUserUuid, normalizedFactor).orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "\u9a8c\u8bc1\u65b9\u5f0f\u672a\u542f\u7528"));
            TotpVerificationResult verification = this.verifyTotpLoginCode(binding, verificationCode);
            if (verification.recoveryCodeUsed()) {
                this.markBindingRecoveryCodes(user.getId(), trustedUserUuid, normalizedFactor, verification.recoveryCodes());
            }
        }
        this.markChallengeConsumed(challenge);
        return this.verificationResult(userId, normalizedFactor, "\u9a8c\u8bc1\u6210\u529f");
    }

    @Transactional
    public SystemVO.VerificationChallengeVO startLoginChallenge(Long userId, String userUuid, String factorCode) {
        SysUserEntity trustedUser = this.requireUserIdentity(userId, userUuid);
        String trustedUserUuid = this.normalizeUserUuid(userUuid);
        String normalizedFactor = this.normalizeFactorCode(factorCode);
        this.ensureLoginSupported(normalizedFactor);
        if (FACTOR_SMS.equals(normalizedFactor)) {
            SmsVerificationSettingsRecord smsSettings = this.loadSmsSettingsRecord();
            if (!smsSettings.enabled() || !smsSettings.configured()) {
                throw new BizException(ErrorCode.BIZ_ERROR, "\u8bf7\u5148\u914d\u7f6e\u5e76\u542f\u7528\u77ed\u4fe1\u9a8c\u8bc1\u7801\u670d\u52a1");
            }
            SysUserEntity user = trustedUser;
            if (!StringUtils.hasText((String)user.getMobile())) {
                throw new BizException(ErrorCode.BIZ_ERROR, "\u8bf7\u5148\u8865\u5145\u624b\u673a\u53f7\u540e\u518d\u4f7f\u7528\u77ed\u4fe1\u9a8c\u8bc1\u7801");
            }
            this.ensureVerificationCodeCooldown(user.getId(), trustedUserUuid, normalizedFactor, CHALLENGE_TYPE_LOGIN);
            String challengeId = this.generateChallengeId();
            String verificationCode = this.generateNumericCode(6);
            String codeHash = this.totpService.sha256(challengeId + ":" + verificationCode);
            String maskedContact = this.maskMobile(user.getMobile());
            this.persistChallenge(challengeId, user.getId(), trustedUserUuid, normalizedFactor, CHALLENGE_TYPE_LOGIN, null, null, List.of(), codeHash, maskedContact, verificationCode, user.getId());
            this.deliverVerificationCode(user.getId(), trustedUserUuid, user.getUsername(), normalizedFactor, AUDIT_SCENE_SECOND_FACTOR, user.getMobile(), maskedContact, verificationCode, challengeId, "\u90ae\u7bb1\u9a8c\u8bc1\u7801", smsSettings);
            return this.buildChallengeResponse(normalizedFactor, challengeId, maskedContact, "\u9a8c\u8bc1\u7801\u5df2\u53d1\u9001\u81f3\u7ed1\u5b9a\u624b\u673a\u53f7\uff0c\u8bf7\u8f93\u5165 6 \u4f4d\u77ed\u4fe1\u9a8c\u8bc1\u7801\u5b8c\u6210\u9a8c\u8bc1", null, null, List.of(), this.properties.isExposeDebugCode() ? verificationCode : null);
        }
        if (FACTOR_EMAIL.equals(normalizedFactor)) {
            if (!this.isEmailLoginEnabled()) {
                throw new BizException(ErrorCode.BIZ_ERROR, "\u8bf7\u5148\u542f\u7528\u90ae\u7bb1\u9a8c\u8bc1\u7801\u767b\u5f55");
            }
            SysUserEntity user = trustedUser;
            if (!StringUtils.hasText((String)user.getEmail())) {
                throw new BizException(ErrorCode.BIZ_ERROR, "\u8bf7\u5148\u8865\u5145\u90ae\u7bb1\u540e\u518d\u4f7f\u7528\u90ae\u7bb1\u9a8c\u8bc1\u7801\u767b\u5f55");
            }
            if (!this.smtpMailService.isConfigured()) {
                throw new BizException(ErrorCode.BIZ_ERROR, "\u8bf7\u5148\u914d\u7f6e SMTP \u540e\u518d\u4f7f\u7528\u90ae\u7bb1\u9a8c\u8bc1\u7801\u767b\u5f55");
            }
            this.ensureVerificationCodeCooldown(user.getId(), trustedUserUuid, normalizedFactor, CHALLENGE_TYPE_LOGIN);
            String challengeId = this.generateChallengeId();
            String verificationCode = this.generateNumericCode(6);
            String codeHash = this.totpService.sha256(challengeId + ":" + verificationCode);
            String maskedContact = this.maskEmail(user.getEmail());
            this.persistChallenge(challengeId, user.getId(), trustedUserUuid, normalizedFactor, CHALLENGE_TYPE_LOGIN, null, null, List.of(), codeHash, maskedContact, verificationCode, user.getId());
            this.deliverVerificationCode(user.getId(), trustedUserUuid, user.getUsername(), normalizedFactor, AUDIT_SCENE_SECOND_FACTOR, user.getEmail(), maskedContact, verificationCode, challengeId, "\u90ae\u7bb1\u9a8c\u8bc1\u7801", null);
            return this.buildChallengeResponse(normalizedFactor, challengeId, maskedContact, "\u9a8c\u8bc1\u7801\u5df2\u53d1\u9001\u81f3\u7ed1\u5b9a\u90ae\u7bb1\uff0c\u8bf7\u8f93\u5165 6 \u4f4d\u90ae\u7bb1\u9a8c\u8bc1\u7801\u5b8c\u6210\u9a8c\u8bc1", null, null, List.of(), this.properties.isExposeDebugCode() ? verificationCode : null);
        }
        if (FACTOR_TOTP.equals(normalizedFactor) && !this.isTotpEnabled()) {
            throw new BizException(ErrorCode.BIZ_ERROR, "\u8bf7\u5148\u5728\u7cfb\u7edf\u4e2d\u542f\u7528 2FA \u529f\u80fd");
        }
        VerificationBindingRecord binding = this.loadEnabledBinding(trustedUser.getId(), trustedUserUuid, normalizedFactor).orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "\u9a8c\u8bc1\u65b9\u5f0f\u672a\u542f\u7528"));
        ChallengeRecord challenge = this.createChallenge(trustedUser.getId(), trustedUserUuid, normalizedFactor, CHALLENGE_TYPE_LOGIN, binding);
        return this.toChallengeVO(binding, challenge, normalizedFactor, false);
    }

    public List<LoginResponseVO.SecondFactorOptionVO> collectSecondFactorOptions(SysUserEntity user) {
        SmsVerificationSettingsRecord smsSettings;
        ArrayList<LoginResponseVO.SecondFactorOptionVO> result = new ArrayList<LoginResponseVO.SecondFactorOptionVO>();
        String trustedUserUuid = this.normalizeUserUuid(user.getUuid());
        if (this.isTotpEnabled()) {
            this.loadBinding(user.getId(), trustedUserUuid, FACTOR_TOTP).filter(binding -> binding.enabled() && binding.bound()).ifPresent(binding -> {
                SystemVO.VerificationChallengeVO challenge = this.startLoginChallenge(user.getId(), trustedUserUuid, FACTOR_TOTP);
                result.add(this.buildSecondFactorOption(FACTOR_TOTP, "2FA", challenge.getChallengeId(), binding.maskedContact(), "\u8bf7\u8f93\u5165\u8ba4\u8bc1\u5668\u4e2d\u7684 6 \u4f4d\u9a8c\u8bc1\u7801\u5b8c\u6210\u9a8c\u8bc1"));
            });
        }
        if ((smsSettings = this.loadSmsSettingsRecord()).enabled() && smsSettings.configured() && StringUtils.hasText((String)user.getMobile())) {
            SystemVO.VerificationChallengeVO challenge = this.startLoginChallenge(user.getId(), trustedUserUuid, FACTOR_SMS);
            result.add(this.buildSecondFactorOption(FACTOR_SMS, "\u77ed\u4fe1\u9a8c\u8bc1\u7801", challenge.getChallengeId(), challenge.getMaskedContact(), "\u9a8c\u8bc1\u7801\u5df2\u53d1\u9001\u81f3\u7ed1\u5b9a\u624b\u673a\u53f7\uff0c\u8bf7\u8f93\u5165 6 \u4f4d\u77ed\u4fe1\u9a8c\u8bc1\u7801\u5b8c\u6210\u9a8c\u8bc1"));
        }
        return result;
    }

    private LoginResponseVO.SecondFactorOptionVO buildSecondFactorOption(String factorCode, String factorName, String challengeId, String maskedContact, String promptMessage) {
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
        this.requireRequest(request, "Second factor request is required");
        String factorCode = this.normalizeFactorCode(request.getFactorCode());
        ChallengeRecord challenge = this.loadChallenge(request.getChallengeId(), factorCode, CHALLENGE_TYPE_LOGIN);
        SysUserEntity user = this.requireUserIdentity(challenge.userId(), challenge.userUuid());
        if (FACTOR_TOTP.equals(factorCode) && !this.isTotpEnabled()) {
            throw new BizException(ErrorCode.BIZ_ERROR, "\u8bf7\u5148\u5728\u7cfb\u7edf\u4e2d\u542f\u7528 2FA \u529f\u80fd");
        }
        if (FACTOR_SMS.equals(factorCode)) {
            this.verifySmsCode(challenge, request.getVerificationCode());
        } else {
            VerificationBindingRecord binding = this.loadEnabledBinding(challenge.userId(), challenge.userUuid(), factorCode).orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "\u9a8c\u8bc1\u65b9\u5f0f\u672a\u542f\u7528"));
            TotpVerificationResult verification = this.verifyTotpLoginCode(binding, request.getVerificationCode());
            if (verification.recoveryCodeUsed()) {
                this.markBindingRecoveryCodes(challenge.userId(), challenge.userUuid(), factorCode, verification.recoveryCodes());
            }
        }
        this.markChallengeConsumed(challenge);
        SystemVO.VerificationVerificationVO result = this.verificationResult(user.getId(), factorCode, "\u9a8c\u8bc1\u6210\u529f");
        log.info("Second factor verified factorCode={} userId={} requestId={} loginIp={} userAgent={}", new Object[]{factorCode, challenge.userId(), TraceContext.getRequestId(), loginIp, userAgent});
        return result;
    }

    private SystemVO.VerificationProviderVO resolveProvider(Long userId, String userUuid, String factorCode) {
        this.ensureBindSupported(factorCode);
        VerificationMethodDefinition definition = this.bindingDefinitionOf(factorCode);
        Optional<VerificationBindingRecord> binding = this.loadBinding(userId, userUuid, factorCode);
        SystemVO.VerificationProviderVO provider = new SystemVO.VerificationProviderVO();
        provider.setFactorCode(definition.factorCode());
        provider.setFactorName(definition.factorName());
        provider.setSystemEnabled(this.isTotpEnabled());
        provider.setEnabled(binding.map(VerificationBindingRecord::enabled).orElse(false));
        provider.setBound(binding.map(VerificationBindingRecord::bound).orElse(false));
        provider.setEmailRequired(definition.emailRequired());
        provider.setMobileRequired(definition.mobileRequired());
        provider.setMaskedContact(binding.map(VerificationBindingRecord::maskedContact).orElseGet(() -> this.defaultMaskedContact(userId, factorCode)));
        provider.setStatusMessage(this.resolveStatusMessage(definition, binding.orElse(null), userId));
        return provider;
    }

    private SystemVO.VerificationChallengeVO startBindChallenge(Long userId, String userUuid, String factorCode, String currentPassword, String currentFactorCode, String currentChallengeId, String currentVerificationCode) {
        this.ensureBindSupported(factorCode);
        this.ensureTotpEnabled();
        VerificationMethodDefinition definition = this.bindingDefinitionOf(factorCode);
        SysUserEntity user = this.requireUserIdentity(userId, userUuid);
        String trustedUserUuid = this.normalizeUserUuid(userUuid);
        if (this.loadEnabledBinding(userId, trustedUserUuid, factorCode).isPresent()) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Verification method already bound, please unbind first");
        }
        this.requireSensitiveBindVerification(user, trustedUserUuid, currentPassword, currentFactorCode, currentChallengeId, currentVerificationCode);
        this.validatePrerequisites(definition, user);
        String secret = this.totpService.generateSecret();
        String challengeId = this.generateChallengeId();
        String setupUri = this.totpService.buildSetupUri(this.properties.getIssuer(), user.getUsername(), secret, this.properties.getTotpDigits(), this.properties.getTotpStepSeconds());
        this.persistBinding(userId, trustedUserUuid, factorCode, false, false, definition.emailRequired(), this.defaultMaskedContact(userId, factorCode), secret, List.of(), null, user.getId());
        this.persistChallenge(challengeId, userId, trustedUserUuid, factorCode, CHALLENGE_TYPE_BIND, secret, setupUri, null, null, null, null, user.getId());
        return this.buildChallengeResponse(factorCode, challengeId, this.defaultMaskedContact(userId, factorCode), "\u8bf7\u4f7f\u7528\u8ba4\u8bc1\u5668\u626b\u63cf\u4e8c\u7ef4\u7801\u540e\u8f93\u5165\u9996\u4e2a\u9a8c\u8bc1\u7801\u5b8c\u6210\u7ed1\u5b9a", setupUri, secret, null, null);
    }

    private void requireSensitiveBindVerification(SysUserEntity user, String trustedUserUuid, String currentPassword, String currentFactorCode, String currentChallengeId, String currentVerificationCode) {
        String normalizedCurrentPassword = this.normalizeNullableText(currentPassword);
        if (StringUtils.hasText(normalizedCurrentPassword)) {
            this.verifyCurrentPassword(user, trustedUserUuid, normalizedCurrentPassword);
            return;
        }
        List<String> availableFactors = this.availableSensitiveBindFactors(user, trustedUserUuid);
        if (!availableFactors.isEmpty()) {
            this.requireCurrentBindFactorVerification(user, trustedUserUuid, currentFactorCode, currentChallengeId, currentVerificationCode, availableFactors);
            return;
        }
        throw new BizException(ErrorCode.VALIDATION_ERROR, "Please enter your current password before binding this sign-in method");
    }

    private List<String> availableSensitiveBindFactors(SysUserEntity user, String trustedUserUuid) {
        return this.availableSensitiveContactChangeFactors(user, trustedUserUuid);
    }

    private void requireCurrentBindFactorVerification(SysUserEntity user, String trustedUserUuid, String currentFactorCode, String currentChallengeId, String currentVerificationCode, List<String> availableFactors) {
        if (!StringUtils.hasText(currentFactorCode) || !StringUtils.hasText(currentChallengeId) || !StringUtils.hasText(currentVerificationCode)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Please verify your current sign-in method before binding this sign-in method");
        }
        String normalizedCurrentFactor = this.normalizeFactorCode(currentFactorCode);
        if (!availableFactors.contains(normalizedCurrentFactor)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Current verification method is not available");
        }
        this.verifyLogin(user.getId(), trustedUserUuid, normalizedCurrentFactor, currentChallengeId, currentVerificationCode);
    }

    private void verifyCurrentPassword(SysUserEntity user, String trustedUserUuid, String currentPassword) {
        String currentPasswordHash = this.iamUserService.findActiveCredential(user.getId(), trustedUserUuid, "PASSWORD")
                .map(IamUserAccount.CredentialView::getCredentialSecret)
                .orElse(null);
        if (!StringUtils.hasText(currentPasswordHash)) {
            currentPasswordHash = user.getPasswordHash();
        }
        if (!StringUtils.hasText(currentPasswordHash) || !this.passwordEncoder.matches(currentPassword, currentPasswordHash)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Current password is incorrect");
        }
    }

    private SystemVO.VerificationChallengeVO buildChallengeResponse(String factorCode, String challengeId, String maskedContact, String promptMessage, String setupUri, String setupSecret, List<String> recoveryCodes, String debugCode) {
        return this.buildChallengeResponse(factorCode, this.loginDefinitionOf(factorCode).factorName(), challengeId, maskedContact, promptMessage, setupUri, setupSecret, recoveryCodes, debugCode);
    }

    private SystemVO.VerificationChallengeVO buildChallengeResponse(String factorCode, String factorName, String challengeId, String maskedContact, String promptMessage, String setupUri, String setupSecret, List<String> recoveryCodes, String debugCode) {
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

    private SystemVO.VerificationChallengeVO toChallengeVO(VerificationBindingRecord binding, ChallengeRecord challenge, String factorCode, boolean bindFlow) {
        SystemVO.VerificationChallengeVO result = new SystemVO.VerificationChallengeVO();
        result.setFactorCode(factorCode);
        result.setFactorName(this.bindingDefinitionOf(factorCode).factorName());
        result.setChallengeId(challenge.challengeId());
        result.setMaskedContact(binding.maskedContact());
        result.setPromptMessage(bindFlow ? "\u8bf7\u6309\u9875\u9762\u63d0\u793a\u8f93\u5165\u9a8c\u8bc1\u7801\u5b8c\u6210\u7ed1\u5b9a" : "\u8bf7\u8f93\u5165\u6536\u5230\u7684\u9a8c\u8bc1\u7801\u5b8c\u6210\u9a8c\u8bc1");
        result.setSetupUri(challenge.setupUri());
        result.setSetupSecret(challenge.setupSecret());
        result.setRecoveryCodes(challenge.recoveryCodes());
        result.setDebugCode(challenge.debugCode());
        return result;
    }

    private SystemVO.VerificationVerificationVO verificationResult(Long userId, String factorCode, String message) {
        SysUserEntity user = this.requireUser(userId);
        SystemVO.VerificationVerificationVO result = new SystemVO.VerificationVerificationVO();
        result.setVerified(true);
        result.setUserId(userId);
        result.setUserUuid(user.getUuid());
        result.setUsername(user.getUsername());
        result.setMessage(message);
        return result;
    }

    private SystemVO.VerificationVerificationVO verificationResult(Long userId, String factorCode, String message, List<String> recoveryCodes) {
        SystemVO.VerificationVerificationVO result = this.verificationResult(userId, factorCode, message);
        result.setRecoveryCodes(recoveryCodes);
        return result;
    }

    private void verifyTotpEnrollmentCode(VerificationBindingRecord binding, String verificationCode) {
        String normalizedCode = this.normalizeVerificationCode(verificationCode);
        if (!this.totpService.verifyCode(binding.secretKey(), normalizedCode, this.properties.getTotpDigits(), this.properties.getTotpStepSeconds())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "\u9a8c\u8bc1\u7801\u9519\u8bef\uff0c\u8bf7\u91cd\u8bd5");
        }
    }

    private TotpVerificationResult verifyTotpLoginCode(VerificationBindingRecord binding, String verificationCode) {
        String normalizedCode = this.normalizeVerificationCode(verificationCode);
        if (this.totpService.verifyCode(binding.secretKey(), normalizedCode, this.properties.getTotpDigits(), this.properties.getTotpStepSeconds())) {
            return new TotpVerificationResult(false, binding.recoveryCodes());
        }
        List<String> remainingRecoveryCodes = this.totpService.consumeRecoveryCode(binding.recoveryCodes(), normalizedCode);
        if (remainingRecoveryCodes == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "\u9a8c\u8bc1\u7801\u9519\u8bef\uff0c\u8bf7\u91cd\u8bd5");
        }
        return new TotpVerificationResult(true, remainingRecoveryCodes);
    }

    private void verifySmsCode(ChallengeRecord challenge, String verificationCode) {
        this.verifyChallengeCode(challenge, verificationCode);
    }

    private String normalizePendingLoginRegistrationAccount(String account, String normalizedLoginType) {
        String identityType = this.iamUserService.detectIdentityType(account);
        if (FACTOR_SMS.equals(normalizedLoginType) && !IamUserService.IDENTITY_MOBILE.equals(identityType)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "sms login requires a mobile account");
        }
        String normalizedAccount = this.iamUserService.normalizeIdentifier(identityType, account);
        if (!StringUtils.hasText((String)normalizedAccount)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "account must not be blank");
        }
        return normalizedAccount;
    }

    private void verifyChallengeCode(ChallengeRecord challenge, String verificationCode) {
        String normalizedCode = this.normalizeVerificationCode(verificationCode);
        if (!StringUtils.hasText((String)challenge.codeHash())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "\u9a8c\u8bc1\u7801\u4f1a\u8bdd\u5df2\u5931\u6548\uff0c\u8bf7\u91cd\u65b0\u83b7\u53d6");
        }
        String actualHash = this.totpService.sha256(challenge.challengeId() + ":" + normalizedCode);
        if (!Objects.equals(challenge.codeHash(), actualHash)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "\u9a8c\u8bc1\u7801\u9519\u8bef\uff0c\u8bf7\u91cd\u8bd5");
        }
    }

    private void requireChallengeOwner(ChallengeRecord challenge, Long expectedUserId, String expectedUserUuid) {
        String normalizedExpectedUuid = this.normalizeUserUuid(expectedUserUuid);
        if (!(challenge != null && expectedUserId != null && expectedUserId > 0L && challenge.userId() != null && Objects.equals(challenge.userId(), expectedUserId) && StringUtils.hasText((String)challenge.userUuid()) && Objects.equals(challenge.userUuid(), normalizedExpectedUuid))) {
            throw new BizException(ErrorCode.FORBIDDEN, "Verification challenge does not belong to current user");
        }
    }

    private String normalizeContactType(String contactType) {
        if (!StringUtils.hasText((String)contactType)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "\u7ed1\u5b9a\u7c7b\u578b\u4e0d\u80fd\u4e3a\u7a7a");
        }
        String normalized = contactType.trim().toLowerCase(Locale.ROOT);
        if ("mobile".equals(normalized) || FACTOR_SMS.equals(normalized)) {
            return FACTOR_SMS;
        }
        if (FACTOR_EMAIL.equals(normalized)) {
            return FACTOR_EMAIL;
        }
        throw new BizException(ErrorCode.NOT_FOUND, "\u7ed1\u5b9a\u7c7b\u578b\u4e0d\u5b58\u5728");
    }

    private void ensureContactBindSupported(String contactType) {
        if (!FACTOR_SMS.equals(contactType) && !FACTOR_EMAIL.equals(contactType)) {
            throw new BizException(ErrorCode.NOT_FOUND, "\u7ed1\u5b9a\u7c7b\u578b\u4e0d\u5b58\u5728");
        }
    }

    private String normalizeContactValue(String contactValue) {
        if (!StringUtils.hasText((String)contactValue)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "\u7ed1\u5b9a\u4fe1\u606f\u4e0d\u80fd\u4e3a\u7a7a");
        }
        return contactValue.trim();
    }

    private String contactBindFactorName(String contactType) {
        if (FACTOR_SMS.equals(contactType)) {
            return "\u624b\u673a\u53f7";
        }
        if (FACTOR_EMAIL.equals(contactType)) {
            return "\u90ae\u7bb1";
        }
        return "\u7ed1\u5b9a\u4fe1\u606f";
    }

    private void validateContactBindPrerequisites(String contactType, String contactValue) {
        if (FACTOR_SMS.equals(contactType)) {
            SmsVerificationSettingsRecord smsSettings = this.loadSmsSettingsRecord();
            if (!smsSettings.enabled() || !smsSettings.configured()) {
                throw new BizException(ErrorCode.BIZ_ERROR, "\u8bf7\u5148\u914d\u7f6e\u5e76\u542f\u7528\u77ed\u4fe1\u9a8c\u8bc1\u7801\u670d\u52a1");
            }
            if (!StringUtils.hasText((String)contactValue)) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "\u8bf7\u8f93\u5165\u624b\u673a\u53f7");
            }
        } else if (FACTOR_EMAIL.equals(contactType)) {
            if (!this.isEmailLoginEnabled()) {
                throw new BizException(ErrorCode.BIZ_ERROR, "\u8bf7\u5148\u542f\u7528\u90ae\u7bb1\u9a8c\u8bc1\u7801\u767b\u5f55");
            }
            if (!this.smtpMailService.isConfigured()) {
                throw new BizException(ErrorCode.BIZ_ERROR, "\u8bf7\u5148\u914d\u7f6e SMTP \u540e\u518d\u7ed1\u5b9a\u90ae\u7bb1");
            }
            if (!StringUtils.hasText((String)contactValue)) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "\u8bf7\u8f93\u5165\u90ae\u7bb1");
            }
        } else {
            throw new BizException(ErrorCode.NOT_FOUND, "\u7ed1\u5b9a\u7c7b\u578b\u4e0d\u5b58\u5728");
        }
        if (FACTOR_EMAIL.equals(contactType) && !contactValue.contains("@")) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "\u8bf7\u8f93\u5165\u6709\u6548\u90ae\u7bb1\u5730\u5740");
        }
        if (FACTOR_SMS.equals(contactType) && contactValue.length() < 7) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "\u8bf7\u8f93\u5165\u6709\u6548\u624b\u673a\u53f7");
        }
    }

    private String hashContactValue(String contactType, String contactValue) {
        return this.totpService.sha256(contactType + ":" + contactValue);
    }

    private SmsVerificationSettingsRecord loadSmsSettingsRecord() {
        Map<String, String> values = this.loadConfigValuesByKeys(this.smsConfigKeys());
        boolean enabled = Boolean.parseBoolean(this.defaultIfBlank(values.get(SMS_CONFIG_ENABLED_KEY), "false"));
        String provider = this.defaultIfBlank(values.get(SMS_CONFIG_PROVIDER_KEY), "aliyun");
        String signName = this.defaultIfBlank(values.get(SMS_CONFIG_SIGN_NAME_KEY), "");
        String templateCode = this.defaultIfBlank(values.get(SMS_CONFIG_TEMPLATE_CODE_KEY), "");
        String accessKeyId = this.defaultIfBlank(values.get(SMS_CONFIG_ACCESS_KEY_ID_KEY), "");
        String accessKeySecret = this.defaultIfBlank(values.get(SMS_CONFIG_ACCESS_KEY_SECRET_KEY), "");
        String endpoint = this.defaultIfBlank(values.get(SMS_CONFIG_ENDPOINT_KEY), "");
        String region = this.defaultIfBlank(values.get(SMS_CONFIG_REGION_KEY), "");
        boolean configured = enabled && StringUtils.hasText((String)provider) && StringUtils.hasText((String)signName) && StringUtils.hasText((String)templateCode) && StringUtils.hasText((String)accessKeyId) && StringUtils.hasText((String)accessKeySecret);
        return new SmsVerificationSettingsRecord(enabled, provider, signName, templateCode, accessKeyId, accessKeySecret, endpoint, region, configured);
    }

    private void deliverVerificationCode(Long userId, String userUuid, String username, String channel, String scene, String target, String maskedTarget, String verificationCode, String challengeId, String emailSubject, SmsVerificationSettingsRecord smsSettings) {
        String normalizedChannel = channel.toUpperCase(Locale.ROOT);
        try {
            Object providerDetail;
            if (FACTOR_EMAIL.equals(channel)) {
                this.smtpMailService.sendVerificationCode(target, verificationCode, emailSubject);
                providerDetail = "provider=smtp";
            } else if (FACTOR_SMS.equals(channel)) {
                SmsVerificationSender.SmsSendResult result = this.smsVerificationSender.send(this.toSmsSettings(smsSettings), target, verificationCode);
                providerDetail = "provider=" + this.defaultIfBlank(smsSettings.provider(), "aliyun") + ", providerRequestId=" + this.defaultIfBlank(result.requestId(), "-") + ", providerBizId=" + this.defaultIfBlank(result.bizId(), "-");
            } else {
                throw new BizException(ErrorCode.NOT_FOUND, "\u9a8c\u8bc1\u7801\u6e20\u9053\u4e0d\u5b58\u5728");
            }
            this.verificationDeliveryAuditService.log(userId, userUuid, username, normalizedChannel, scene, "SUCCESS", this.abbreviate("challengeId=" + challengeId + ", target=" + maskedTarget + ", " + (String)providerDetail));
        }
        catch (RuntimeException exception) {
            this.verificationDeliveryAuditService.log(userId, userUuid, username, normalizedChannel, scene, "FAIL", this.abbreviate("challengeId=" + challengeId + ", target=" + maskedTarget + ", reason=" + exception.getMessage()));
            throw exception;
        }
    }

    private SmsVerificationSender.SmsSettings toSmsSettings(SmsVerificationSettingsRecord record) {
        return new SmsVerificationSender.SmsSettings(record.provider(), record.signName(), record.templateCode(), record.accessKeyId(), record.accessKeySecret(), record.endpoint(), record.region(), record.configured());
    }

    private String abbreviate(String value) {
        if (value == null || value.length() <= 1000) {
            return value;
        }
        return value.substring(0, 1000);
    }

    private Long queryConfigId(String configKey) {
        try {
            return this.jdbcTemplate.queryForObject("select id\nfrom sys_config\nwhere config_key = ? and deleted = 0\norder by id desc\nlimit 1\n", Long.class, configKey);
        }
        catch (Exception ignored) {
            return null;
        }
    }

    private List<String> smsConfigKeys() {
        return List.of(SMS_CONFIG_ENABLED_KEY, SMS_CONFIG_PROVIDER_KEY, SMS_CONFIG_SIGN_NAME_KEY, SMS_CONFIG_TEMPLATE_CODE_KEY, SMS_CONFIG_ACCESS_KEY_ID_KEY, SMS_CONFIG_ACCESS_KEY_SECRET_KEY, SMS_CONFIG_ENDPOINT_KEY, SMS_CONFIG_REGION_KEY);
    }

    private Map<String, String> loadConfigValuesByKeys(List<String> keys) {
        String placeholders = keys.stream().map(item -> "?").collect(Collectors.joining(", "));
        String sql = "select config_key as configKey, config_value as configValue\nfrom sys_config\nwhere deleted = 0\n  and config_scope = 'PLATFORM'\nand config_key in (%s)\norder by id desc\n".formatted(placeholders);
        ArrayList<String> params = new ArrayList<String>(keys);
        List<Map<String, Object>> rows = this.jdbcTemplate.queryForList(sql, params.toArray());
        LinkedHashMap<String, String> valueByKey = new LinkedHashMap<String, String>();
        for (Map<String, Object> row : rows) {
            String configKey = String.valueOf(row.get("configKey"));
            if (valueByKey.containsKey(configKey)) continue;
            valueByKey.put(configKey, this.normalizeConfigText(row.get("configValue")));
        }
        return valueByKey;
    }

    private String sanitizeText(String value, String fallback) {
        return StringUtils.hasText((String)value) ? value.trim() : fallback;
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText((String)value) ? value : fallback;
    }

    private void persistBinding(Long userId, String userUuid, String factorCode, boolean enabled, boolean bound, boolean emailRequired, String maskedContact, String secretKey, List<String> recoveryCodes, LocalDateTime verifiedAt, Long operatorId) {
        int updated = this.jdbcTemplate.update("insert into sys_verification_binding (\n    user_id, user_uuid, factor_code, factor_name, enabled, bound, email_required, masked_contact,\n    secret_key, recovery_codes_json, verified_at, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted\n) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)\non duplicate key update\n    factor_name = case when user_id = values(user_id) and user_uuid = values(user_uuid) and factor_code = values(factor_code) then values(factor_name) else factor_name end,\n    enabled = case when user_id = values(user_id) and user_uuid = values(user_uuid) and factor_code = values(factor_code) then values(enabled) else enabled end,\n    bound = case when user_id = values(user_id) and user_uuid = values(user_uuid) and factor_code = values(factor_code) then values(bound) else bound end,\n    email_required = case when user_id = values(user_id) and user_uuid = values(user_uuid) and factor_code = values(factor_code) then values(email_required) else email_required end,\n    masked_contact = case when user_id = values(user_id) and user_uuid = values(user_uuid) and factor_code = values(factor_code) then values(masked_contact) else masked_contact end,\n    secret_key = case when user_id = values(user_id) and user_uuid = values(user_uuid) and factor_code = values(factor_code) then values(secret_key) else secret_key end,\n    recovery_codes_json = case when user_id = values(user_id) and user_uuid = values(user_uuid) and factor_code = values(factor_code) then values(recovery_codes_json) else recovery_codes_json end,\n    verified_at = case when user_id = values(user_id) and user_uuid = values(user_uuid) and factor_code = values(factor_code) then values(verified_at) else verified_at end,\n    updated_by = case when user_id = values(user_id) and user_uuid = values(user_uuid) and factor_code = values(factor_code) then values(updated_by) else updated_by end,\n    updated_by_uuid = case when user_id = values(user_id) and user_uuid = values(user_uuid) and factor_code = values(factor_code) then values(updated_by_uuid) else updated_by_uuid end,\n    updated_at = case when user_id = values(user_id) and user_uuid = values(user_uuid) and factor_code = values(factor_code) then current_timestamp else updated_at end,\n    deleted = case when user_id = values(user_id) and user_uuid = values(user_uuid) and factor_code = values(factor_code) then 0 else deleted end\n", userId, userUuid, factorCode, this.bindingDefinitionOf(factorCode).factorName(), enabled ? 1 : 0, bound ? 1 : 0, emailRequired ? 1 : 0, maskedContact, this.fieldCryptoService.encrypt(secretKey), this.encryptStringList(recoveryCodes), verifiedAt, operatorId, userUuid, operatorId, userUuid);
        this.requireVerificationWrite(updated, "Verification binding changed, please retry");
    }

    private void markBindingEnabled(Long userId, String userUuid, String factorCode, VerificationBindingRecord binding, List<String> recoveryCodes) {
        int updated = this.jdbcTemplate.update("update sys_verification_binding\nset enabled = 1, bound = 1, secret_key = ?, recovery_codes_json = ?, verified_at = current_timestamp,\n    updated_by = ?, updated_by_uuid = ?, updated_at = current_timestamp\nwhere user_id = ? and user_uuid = ? and factor_code = ? and deleted = 0\n", this.fieldCryptoService.encrypt(binding.secretKey()), this.encryptStringList(recoveryCodes), userId, userUuid, userId, userUuid, factorCode);
        this.requireVerificationWrite(updated, "Verification binding changed, please retry");
    }

    private void markBindingRecoveryCodes(Long userId, String userUuid, String factorCode, List<String> recoveryCodes) {
        int updated = this.jdbcTemplate.update("update sys_verification_binding\nset recovery_codes_json = ?, updated_by = ?, updated_by_uuid = ?, updated_at = current_timestamp\nwhere user_id = ? and user_uuid = ? and factor_code = ? and deleted = 0\n", this.encryptStringList(recoveryCodes), userId, userUuid, userId, userUuid, factorCode);
        this.requireVerificationWrite(updated, "Verification binding changed, please retry");
    }

    private void persistChallenge(String challengeId, Long userId, String userUuid, String factorCode, String challengeType, String setupSecret, String setupUri, List<String> recoveryCodes, String codeHash, String maskedContact, String debugCode, Long operatorId) {
        int updated = this.jdbcTemplate.update("insert into sys_verification_challenge (\n    challenge_id, user_id, user_uuid, factor_code, challenge_type, expires_at, consumed_flag,\n    setup_secret, setup_uri, recovery_codes_json, code_hash, masked_contact, debug_code,\n    created_by, created_by_uuid, updated_by, updated_by_uuid, deleted\n) values (?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)\non duplicate key update\n    factor_code = case when user_id = values(user_id) and user_uuid = values(user_uuid) then values(factor_code) else factor_code end,\n    challenge_type = case when user_id = values(user_id) and user_uuid = values(user_uuid) then values(challenge_type) else challenge_type end,\n    expires_at = case when user_id = values(user_id) and user_uuid = values(user_uuid) then values(expires_at) else expires_at end,\n    consumed_flag = case when user_id = values(user_id) and user_uuid = values(user_uuid) then 0 else consumed_flag end,\n    setup_secret = case when user_id = values(user_id) and user_uuid = values(user_uuid) then values(setup_secret) else setup_secret end,\n    setup_uri = case when user_id = values(user_id) and user_uuid = values(user_uuid) then values(setup_uri) else setup_uri end,\n    recovery_codes_json = case when user_id = values(user_id) and user_uuid = values(user_uuid) then values(recovery_codes_json) else recovery_codes_json end,\n    code_hash = case when user_id = values(user_id) and user_uuid = values(user_uuid) then values(code_hash) else code_hash end,\n    masked_contact = case when user_id = values(user_id) and user_uuid = values(user_uuid) then values(masked_contact) else masked_contact end,\n    debug_code = case when user_id = values(user_id) and user_uuid = values(user_uuid) then values(debug_code) else debug_code end,\n    updated_by = case when user_id = values(user_id) and user_uuid = values(user_uuid) then values(updated_by) else updated_by end,\n    updated_by_uuid = case when user_id = values(user_id) and user_uuid = values(user_uuid) then values(updated_by_uuid) else updated_by_uuid end,\n    updated_at = case when user_id = values(user_id) and user_uuid = values(user_uuid) then current_timestamp else updated_at end,\n    deleted = case when user_id = values(user_id) and user_uuid = values(user_uuid) then 0 else deleted end\n", challengeId, userId, userUuid, factorCode, challengeType, this.challengeExpiresAt(factorCode, challengeType), this.fieldCryptoService.encrypt(setupSecret), setupUri, this.encryptStringList(recoveryCodes), codeHash, maskedContact, this.properties.isExposeDebugCode() ? debugCode : null, operatorId, userUuid, operatorId, userUuid);
        this.requireVerificationWrite(updated, "Verification challenge changed, please retry");
    }

    private ChallengeRecord createChallenge(Long userId, String userUuid, String factorCode, String challengeType, VerificationBindingRecord binding) {
        String challengeId = this.generateChallengeId();
        String debugCode = null;
        String codeHash = null;
        if (FACTOR_SMS.equals(factorCode)) {
            debugCode = this.generateNumericCode(6);
            codeHash = this.totpService.sha256(challengeId + ":" + debugCode);
        }
        this.persistChallenge(challengeId, userId, userUuid, factorCode, challengeType, binding == null ? null : binding.secretKey(), null, binding == null ? List.of() : binding.recoveryCodes(), codeHash, binding == null ? null : binding.maskedContact(), debugCode, userId);
        return new ChallengeRecord(challengeId, userId, userUuid, factorCode, challengeType, binding == null ? null : binding.secretKey(), null, binding == null ? List.of() : binding.recoveryCodes(), codeHash, binding == null ? null : binding.maskedContact(), debugCode, this.challengeExpiresAt(factorCode, challengeType), false);
    }

    private LocalDateTime challengeExpiresAt(String factorCode, String challengeType) {
        if (this.isDeliveryCodeChallenge(factorCode)) {
            return LocalDateTime.now().plusSeconds(this.verificationCodeExpireSeconds());
        }
        return LocalDateTime.now().plusMinutes(CHALLENGE_TYPE_BIND.equals(challengeType) ? (long)this.properties.getBindChallengeExpireMinutes() : (long)this.properties.getLoginChallengeExpireMinutes());
    }

    private void ensureVerificationCodeCooldown(Long userId, String userUuid, String factorCode, String challengeType) {
        long cooldownSeconds = this.verificationCodeCooldownSeconds();
        if (cooldownSeconds <= 0L || !this.isDeliveryCodeChallenge(factorCode)) {
            return;
        }
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(cooldownSeconds);
        LocalDateTime latestCreatedAt = this.jdbcTemplate.query("select created_at\nfrom sys_verification_challenge\nwhere user_id = ? and user_uuid = ? and factor_code = ? and challenge_type = ?\n  and created_at > ? and deleted = 0\norder by created_at desc\nlimit 1\n", rs -> rs.next() ? rs.getTimestamp("created_at").toLocalDateTime() : null, userId, userUuid, factorCode, challengeType, threshold);
        if (latestCreatedAt == null) {
            return;
        }
        long elapsedSeconds = Math.max(0L, Duration.between(latestCreatedAt, LocalDateTime.now()).getSeconds());
        long remainingSeconds = Math.max(1L, cooldownSeconds - elapsedSeconds);
        throw new BizException(ErrorCode.LOGIN_RATE_LIMITED, "\u9a8c\u8bc1\u7801\u5df2\u53d1\u9001\uff0c\u8bf7 " + remainingSeconds + " \u79d2\u540e\u518d\u8bd5");
    }

    private boolean isDeliveryCodeChallenge(String factorCode) {
        return FACTOR_SMS.equals(factorCode) || FACTOR_EMAIL.equals(factorCode);
    }

    private boolean isPendingLoginRegistrationChallenge(ChallengeRecord challenge) {
        if (challenge == null
                || !CHALLENGE_TYPE_LOGIN.equals(challenge.challengeType())
                || !FACTOR_SMS.equals(challenge.factorCode())
                || !PENDING_LOGIN_REGISTRATION_MARKER.equals(challenge.setupUri())
                || !StringUtils.hasText((String)challenge.setupSecret())) {
            return false;
        }
        PendingLoginRegistrationIdentity pendingIdentity = this.pendingLoginRegistrationIdentity(challenge.factorCode(), challenge.setupSecret());
        return Objects.equals(challenge.userId(), pendingIdentity.userId())
                && Objects.equals(challenge.userUuid(), pendingIdentity.userUuid());
    }

    private void discardChallenge(String challengeId, Long userId, String userUuid, String factorCode, String challengeType) {
        this.jdbcTemplate.update("update sys_verification_challenge\nset deleted = 1,\n    updated_by = ?,\n    updated_by_uuid = ?,\n    updated_at = current_timestamp\nwhere challenge_id = ?\n  and user_id = ?\n  and user_uuid = ?\n  and factor_code = ?\n  and challenge_type = ?\n  and consumed_flag = 0\n  and deleted = 0\n", userId, userUuid, challengeId, userId, userUuid, factorCode, challengeType);
    }

    private long verificationCodeExpireSeconds() {
        return Math.max(1L, this.securitySettingsService.getVerificationCodeExpireSeconds());
    }

    private long verificationCodeCooldownSeconds() {
        return Math.max(1L, this.securitySettingsService.getVerificationCodeCooldownSeconds());
    }

    private ChallengeRecord loadChallenge(String challengeId, String factorCode, String challengeType) {
        String normalizedChallengeId = this.normalizeChallengeId(challengeId);
        ChallengeRecord challenge = this.jdbcTemplate.query("select challenge_id, user_id, user_uuid, factor_code, challenge_type, expires_at, consumed_flag,\n       setup_secret, setup_uri, recovery_codes_json, code_hash, masked_contact, debug_code\nfrom sys_verification_challenge\nwhere challenge_id = ? and factor_code = ? and challenge_type = ? and deleted = 0\nlimit 1\n", rs -> {
            if (!rs.next()) {
                return null;
            }
            return new ChallengeRecord(rs.getString("challenge_id"), rs.getLong("user_id"), rs.getString("user_uuid"), rs.getString("factor_code"), rs.getString("challenge_type"), this.fieldCryptoService.decrypt(rs.getString("setup_secret")), rs.getString("setup_uri"), this.decryptStringList(rs.getString("recovery_codes_json")), rs.getString("code_hash"), rs.getString("masked_contact"), rs.getString("debug_code"), rs.getTimestamp("expires_at").toLocalDateTime(), rs.getInt("consumed_flag") == 1);
        }, normalizedChallengeId, factorCode, challengeType);
        if (challenge == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "\u9a8c\u8bc1\u7801\u4f1a\u8bdd\u4e0d\u5b58\u5728\u6216\u5df2\u8fc7\u671f");
        }
        if (challenge.consumedFlag()) {
            throw new BizException(ErrorCode.BIZ_ERROR, "\u9a8c\u8bc1\u7801\u4f1a\u8bdd\u5df2\u4f7f\u7528\uff0c\u8bf7\u91cd\u65b0\u83b7\u53d6");
        }
        if (challenge.expiresAt().isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "\u9a8c\u8bc1\u7801\u5df2\u8fc7\u671f\uff0c\u8bf7\u91cd\u65b0\u83b7\u53d6");
        }
        return challenge;
    }

    private ChallengeRecord loadChallengeById(String challengeId, String challengeType) {
        String normalizedChallengeId = this.normalizeChallengeId(challengeId);
        ChallengeRecord challenge = this.jdbcTemplate.query("select challenge_id, user_id, user_uuid, factor_code, challenge_type, expires_at, consumed_flag,\n       setup_secret, setup_uri, recovery_codes_json, code_hash, masked_contact, debug_code\nfrom sys_verification_challenge\nwhere challenge_id = ? and challenge_type = ? and deleted = 0\nlimit 1\n", rs -> {
            if (!rs.next()) {
                return null;
            }
            return new ChallengeRecord(rs.getString("challenge_id"), rs.getLong("user_id"), rs.getString("user_uuid"), rs.getString("factor_code"), rs.getString("challenge_type"), this.fieldCryptoService.decrypt(rs.getString("setup_secret")), rs.getString("setup_uri"), this.decryptStringList(rs.getString("recovery_codes_json")), rs.getString("code_hash"), rs.getString("masked_contact"), rs.getString("debug_code"), rs.getTimestamp("expires_at").toLocalDateTime(), rs.getInt("consumed_flag") == 1);
        }, normalizedChallengeId, challengeType);
        if (challenge == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "\u9a8c\u8bc1\u7801\u4f1a\u8bdd\u4e0d\u5b58\u5728\u6216\u5df2\u8fc7\u671f");
        }
        if (challenge.consumedFlag()) {
            throw new BizException(ErrorCode.BIZ_ERROR, "\u9a8c\u8bc1\u7801\u4f1a\u8bdd\u5df2\u4f7f\u7528\uff0c\u8bf7\u91cd\u65b0\u83b7\u53d6");
        }
        if (challenge.expiresAt().isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "\u9a8c\u8bc1\u7801\u5df2\u8fc7\u671f\uff0c\u8bf7\u91cd\u65b0\u83b7\u53d6");
        }
        return challenge;
    }

    private Optional<VerificationBindingRecord> loadBinding(Long userId, String userUuid, String factorCode) {
        return this.jdbcTemplate.query("select user_id, user_uuid, factor_code, factor_name, enabled, bound, email_required, masked_contact,\n       secret_key, recovery_codes_json, verified_at\nfrom sys_verification_binding\nwhere user_id = ? and user_uuid = ? and factor_code = ? and deleted = 0\nlimit 1\n", rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(new VerificationBindingRecord(rs.getLong("user_id"), rs.getString("user_uuid"), rs.getString("factor_code"), rs.getString("factor_name"), rs.getInt("enabled") == 1, rs.getInt("bound") == 1, rs.getInt("email_required") == 1, rs.getString("masked_contact"), this.fieldCryptoService.decrypt(rs.getString("secret_key")), this.decryptStringList(rs.getString("recovery_codes_json")), rs.getTimestamp("verified_at") == null ? null : rs.getTimestamp("verified_at").toLocalDateTime()));
        }, userId, userUuid, factorCode);
    }

    private Optional<VerificationBindingRecord> loadEnabledBinding(Long userId, String userUuid, String factorCode) {
        return this.loadBinding(userId, userUuid, factorCode).filter(record -> record.enabled() && record.bound());
    }

    private void markChallengeConsumed(ChallengeRecord challenge) {
        if (challenge == null || !StringUtils.hasText((String)challenge.challengeId()) || challenge.userId() == null || !StringUtils.hasText((String)challenge.userUuid())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Verification challenge identity is required");
        }
        int updated = this.jdbcTemplate.update("update sys_verification_challenge\nset consumed_flag = 1,\n    updated_by = ?,\n    updated_by_uuid = ?,\n    updated_at = current_timestamp\nwhere challenge_id = ?\n  and user_id = ?\n  and user_uuid = ?\n  and factor_code = ?\n  and challenge_type = ?\n  and consumed_flag = 0\n  and expires_at = ?\n  and deleted = 0\n", challenge.userId(), challenge.userUuid(), challenge.challengeId(), challenge.userId(), challenge.userUuid(), challenge.factorCode(), challenge.challengeType(), challenge.expiresAt());
        if (updated <= 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Verification challenge state changed, please retry");
        }
    }

    private void validatePrerequisites(VerificationMethodDefinition definition, SysUserEntity user) {
        if (definition.emailRequired() && !StringUtils.hasText((String)user.getEmail())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "\u8bf7\u5148\u8865\u5145\u90ae\u7bb1\u540e\u518d\u542f\u7528\u8be5\u9a8c\u8bc1\u65b9\u5f0f");
        }
        if (definition.mobileRequired() && !StringUtils.hasText((String)user.getMobile())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "\u8bf7\u5148\u8865\u5145\u624b\u673a\u53f7\u540e\u518d\u542f\u7528\u77ed\u4fe1\u9a8c\u8bc1\u7801");
        }
    }

    private String resolveStatusMessage(VerificationMethodDefinition definition, VerificationBindingRecord binding, Long userId) {
        SysUserEntity user = this.requireUser(userId);
        if (!this.isTotpEnabled()) {
            return "\u7cfb\u7edf\u672a\u542f\u7528";
        }
        if (definition.emailRequired() && !StringUtils.hasText((String)user.getEmail())) {
            return "\u8bf7\u5148\u8865\u5145\u90ae\u7bb1\u540e\u518d\u542f\u7528\u8be5\u9a8c\u8bc1\u65b9\u5f0f";
        }
        if (definition.mobileRequired() && !StringUtils.hasText((String)user.getMobile())) {
            return "\u8bf7\u5148\u8865\u5145\u624b\u673a\u53f7\u540e\u518d\u542f\u7528\u77ed\u4fe1\u9a8c\u8bc1\u7801";
        }
        if (binding == null || !binding.enabled() || !binding.bound()) {
            return "\u672a\u7ed1\u5b9a";
        }
        return "\u5df2\u7ed1\u5b9a\uff0c\u53ef\u7528\u4e8e\u767b\u5f55";
    }

    private String defaultMaskedContact(Long userId, String factorCode) {
        SysUserEntity user = this.requireUser(userId);
        if (FACTOR_SMS.equals(factorCode)) {
            return this.maskMobile(user.getMobile());
        }
        return this.maskEmail(user.getEmail());
    }

    private SysUserEntity requireUser(Long userId) {
        return this.userDomainService.findById(userId).orElseThrow(() -> new BizException(ErrorCode.ACCOUNT_NOT_FOUND, "\u7528\u6237\u4e0d\u5b58\u5728"));
    }

    private SysUserEntity requireUserIdentity(Long userId, String userUuid) {
        SysUserEntity user = this.requireUser(userId);
        user = this.requireTrustedActiveUser(user);
        String normalizedUserUuid = this.normalizeUserUuid(userUuid);
        if (!StringUtils.hasText((String)user.getUuid()) || !Objects.equals(user.getUuid(), normalizedUserUuid)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Verification user identity mismatch");
        }
        return user;
    }

    private SysUserEntity requireTrustedActiveUser(SysUserEntity user) {
        if (user == null || user.getId() == null || user.getId() <= 0L) {
            throw new BizException(ErrorCode.ACCOUNT_NOT_FOUND, "\u7528\u6237\u4e0d\u5b58\u5728");
        }
        if (!StringUtils.hasText((String)user.getUuid())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Verification user uuid is required");
        }
        if (!StringUtils.hasText((String)user.getUsername())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Verification username is required");
        }
        if (!StringUtils.hasText((String)user.getStatus()) || !"ENABLED".equalsIgnoreCase(user.getStatus().trim())) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED, "Verification user is disabled: " + user.getUsername(), ErrorCode.ACCOUNT_DISABLED.getDefaultUserMessage());
        }
        return user;
    }

    private String normalizeUserUuid(String userUuid) {
        if (!StringUtils.hasText((String)userUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "User uuid is required");
        }
        return userUuid.trim();
    }

    private void ensureBindSupported(String factorCode) {
        if (!FACTOR_TOTP.equals(factorCode)) {
            throw new BizException(ErrorCode.NOT_FOUND, "\u9a8c\u8bc1\u65b9\u5f0f\u4e0d\u5b58\u5728");
        }
    }

    private void ensureTotpEnabled() {
        if (!this.isTotpEnabled()) {
            throw new BizException(ErrorCode.BIZ_ERROR, "\u8bf7\u5148\u5728\u7cfb\u7edf\u4e2d\u542f\u7528 2FA \u529f\u80fd");
        }
    }

    private void ensureLoginSupported(String factorCode) {
        if (!(FACTOR_TOTP.equals(factorCode) || FACTOR_SMS.equals(factorCode) || FACTOR_EMAIL.equals(factorCode))) {
            throw new BizException(ErrorCode.NOT_FOUND, "\u9a8c\u8bc1\u65b9\u5f0f\u4e0d\u5b58\u5728");
        }
    }

    private VerificationMethodDefinition bindingDefinitionOf(String factorCode) {
        return this.supportedBindingFactors().stream().filter(definition -> definition.factorCode().equals(factorCode)).findFirst().orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "\u9a8c\u8bc1\u65b9\u5f0f\u4e0d\u5b58\u5728"));
    }

    private VerificationMethodDefinition loginDefinitionOf(String factorCode) {
        return this.supportedLoginFactors().stream().filter(definition -> definition.factorCode().equals(factorCode)).findFirst().orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "\u9a8c\u8bc1\u65b9\u5f0f\u4e0d\u5b58\u5728"));
    }

    private List<VerificationMethodDefinition> supportedBindingFactors() {
        return List.of(new VerificationMethodDefinition(FACTOR_TOTP, "2FA", true, false, true));
    }

    private List<VerificationMethodDefinition> supportedLoginFactors() {
        return List.of(new VerificationMethodDefinition(FACTOR_TOTP, "2FA", true, false, true), new VerificationMethodDefinition(FACTOR_SMS, "\u77ed\u4fe1\u9a8c\u8bc1\u7801", false, true, false), new VerificationMethodDefinition(FACTOR_EMAIL, "\u90ae\u7bb1\u9a8c\u8bc1\u7801", true, false, false));
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
        if (!StringUtils.hasText((String)factorCode)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "\u9a8c\u8bc1\u65b9\u5f0f\u4e0d\u80fd\u4e3a\u7a7a");
        }
        String normalized = factorCode.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 16) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "\u9a8c\u8bc1\u65b9\u5f0f\u4e0d\u5408\u6cd5");
        }
        return normalized;
    }

    private String normalizeChallengeId(String challengeId) {
        if (!StringUtils.hasText((String)challengeId)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "\u9a8c\u8bc1\u7801\u4f1a\u8bdd\u4e0d\u80fd\u4e3a\u7a7a");
        }
        String normalized = challengeId.trim();
        if (normalized.length() != 32 || !normalized.chars().allMatch(Character::isLetterOrDigit)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "\u9a8c\u8bc1\u7801\u4f1a\u8bdd\u4e0d\u5408\u6cd5");
        }
        return normalized;
    }

    private String normalizeVerificationCode(String verificationCode) {
        if (!StringUtils.hasText((String)verificationCode)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "\u9a8c\u8bc1\u7801\u4e0d\u80fd\u4e3a\u7a7a");
        }
        String normalized = verificationCode.trim();
        if (normalized.length() > 32) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "\u9a8c\u8bc1\u7801\u4e0d\u5408\u6cd5");
        }
        return normalized;
    }

    private void requireRequest(Object request, String message) {
        if (request == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, message);
        }
    }

    private void requireVerificationWrite(int updated, String message) {
        if (updated <= 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, message);
        }
    }

    private String generateChallengeId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private PendingLoginRegistrationIdentity pendingLoginRegistrationIdentity(String factorCode, String normalizedAccount) {
        UUID challengeOwnerUuid = UUID.nameUUIDFromBytes(("pending-login:" + factorCode + ":" + normalizedAccount).getBytes(StandardCharsets.UTF_8));
        long raw = challengeOwnerUuid.getMostSignificantBits() ^ challengeOwnerUuid.getLeastSignificantBits();
        long stableMagnitude = raw == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(raw);
        long pendingUserId = -Math.max(1L, stableMagnitude);
        return new PendingLoginRegistrationIdentity(
                pendingUserId,
                challengeOwnerUuid.toString(),
                "pending-" + factorCode + "-" + challengeOwnerUuid.toString().substring(0, 12)
        );
    }

    private String generateNumericCode(int digits) {
        int upperBound = (int)Math.pow(10.0, digits);
        return String.format(Locale.ROOT, "%0" + digits + "d", ThreadLocalRandom.current().nextInt(upperBound));
    }

    private String normalizeConfigText(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String normalizeNullableText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private boolean isTotpEnabled() {
        Map<String, String> values = this.loadConfigValuesByKeys(List.of(TOTP_CONFIG_ENABLED_KEY));
        return Boolean.parseBoolean(this.defaultIfBlank(values.get(TOTP_CONFIG_ENABLED_KEY), "true"));
    }

    private boolean isEmailLoginEnabled() {
        Map<String, String> values = this.loadConfigValuesByKeys(List.of(EMAIL_LOGIN_ENABLED_KEY));
        return Boolean.parseBoolean(this.defaultIfBlank(values.get(EMAIL_LOGIN_ENABLED_KEY), String.valueOf(this.properties.isEmailLoginEnabled())));
    }

    private boolean isEmailLoginAvailable() {
        return this.isEmailLoginEnabled() && this.smtpMailService.isConfigured();
    }

    private boolean isSmsLoginAvailable() {
        SmsVerificationSettingsRecord smsSettings = this.loadSmsSettingsRecord();
        return smsSettings.enabled() && smsSettings.configured();
    }

    private String toJson(List<String> values) {
        try {
            return values == null ? null : this.objectMapper.writeValueAsString(values);
        }
        catch (Exception exception) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "\u9a8c\u8bc1\u7801\u6570\u636e\u5e8f\u5217\u5316\u5931\u8d25");
        }
    }

    private String encryptStringList(List<String> values) {
        String json = this.toJson(values);
        if (!StringUtils.hasText((String)json)) {
            return json;
        }
        try {
            return this.objectMapper.writeValueAsString((Object)this.fieldCryptoService.encrypt(json));
        }
        catch (Exception exception) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "\u9a8c\u8bc1\u7801\u6570\u636e\u52a0\u5bc6\u5931\u8d25");
        }
    }

    private List<String> decryptStringList(String value) {
        if (!StringUtils.hasText((String)value)) {
            return List.of();
        }
        String normalized = value.trim();
        if (normalized.startsWith("[")) {
            return this.parseStringList(normalized);
        }
        try {
            String encrypted = (String)this.objectMapper.readValue(normalized, String.class);
            return this.parseStringList(this.fieldCryptoService.decrypt(encrypted));
        }
        catch (Exception exception) {
            return this.parseStringList(this.fieldCryptoService.decrypt(normalized));
        }
    }

    private List<String> parseStringList(String value) {
        if (!StringUtils.hasText((String)value)) {
            return List.of();
        }
        try {
            return (List)this.objectMapper.readValue(value, STRING_LIST);
        }
        catch (Exception exception) {
            return List.of();
        }
    }

    private String maskEmail(String email) {
        if (!StringUtils.hasText((String)email)) {
            return null;
        }
        int atIndex = email.indexOf(64);
        if (atIndex <= 1) {
            return "***" + email.substring(Math.max(atIndex, 0));
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }

    private String maskMobile(String mobile) {
        if (!StringUtils.hasText((String)mobile)) {
            return null;
        }
        if (mobile.length() <= 4) {
            return "****";
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }

    private record SmsVerificationSettingsRecord(boolean enabled, String provider, String signName, String templateCode, String accessKeyId, String accessKeySecret, String endpoint, String region, boolean configured) {
    }

    private record PendingLoginRegistrationIdentity(Long userId, String userUuid, String auditUsername) {
    }

    private record ChallengeRecord(String challengeId, Long userId, String userUuid, String factorCode, String challengeType, String setupSecret, String setupUri, List<String> recoveryCodes, String codeHash, String maskedContact, String debugCode, LocalDateTime expiresAt, boolean consumedFlag) {
    }

    public record PendingLoginCodeVerification(String normalizedAccount, String factorCode, String message) {
    }

    private record VerificationMethodDefinition(String factorCode, String factorName, boolean emailRequired, boolean mobileRequired, boolean bindSupported) {
    }

    private record TotpVerificationResult(boolean recoveryCodeUsed, List<String> recoveryCodes) {
    }

    private record VerificationBindingRecord(Long userId, String userUuid, String factorCode, String factorName, boolean enabled, boolean bound, boolean emailRequired, String maskedContact, String secretKey, List<String> recoveryCodes, LocalDateTime verifiedAt) {
        String contactValue() {
            return this.maskedContact;
        }
    }
}
