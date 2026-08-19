package com.lumira.saas.modules.system.controller;

import com.lumira.api.auth.LoginCodeChallengeDTO;
import com.lumira.api.auth.LoginCodeCompleteRequest;
import com.lumira.api.auth.LoginResponseDTO;
import com.lumira.api.auth.PasswordResetChallengeRequest;
import com.lumira.api.auth.PasswordResetCompleteRequest;
import com.lumira.api.auth.SecondFactorCompleteRequest;
import com.lumira.api.auth.VerificationBindRequest;
import com.lumira.api.system.CaptchaValidationRequestDTO;
import com.lumira.api.system.CurrentUserRoleOptionDTO;
import com.lumira.api.system.LoginAuditRecordRequestDTO;
import com.lumira.api.system.LoginCapabilitiesDTO;
import com.lumira.api.system.MaintenanceLoginPolicyDTO;
import com.lumira.api.system.MenuNodeDTO;
import com.lumira.api.system.PasswordLoginVerificationDTO;
import com.lumira.api.system.OperationAuditRecordRequestDTO;
import com.lumira.api.system.PasskeyCredentialAssertionDTO;
import com.lumira.api.system.PasskeyCredentialDescriptorDTO;
import com.lumira.api.system.PasskeyCredentialDTO;
import com.lumira.api.system.PasskeyCredentialSaveRequestDTO;
import com.lumira.api.system.PasskeyCredentialUsageRequestDTO;
import com.lumira.api.system.PasskeySettingsDTO;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.PluginPermissionRegistrationRequestDTO;
import com.lumira.api.system.SecuritySettingsDTO;
import com.lumira.api.system.SystemRoleSnapshotDTO;
import com.lumira.api.system.SystemUserEmailRecipientDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.api.system.SystemUserWechatRecipientDTO;
import com.lumira.api.system.VerificationBindingChallengeDTO;
import com.lumira.api.system.VerificationChallengeDTO;
import com.lumira.api.system.VerificationProviderDTO;
import com.lumira.api.system.VerificationVerificationDTO;
import com.lumira.api.system.WechatLoginSettingsDTO;
import com.lumira.api.system.WechatLoginUserRequestDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AiConfigAccessPolicy;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.saas.infrastructure.readmodel.ReadModelVersionService;
import com.lumira.saas.infrastructure.security.service.AuthSessionStore;
import com.lumira.saas.modules.system.verification.WechatLoginSettingsService;
import com.lumira.saas.infrastructure.security.service.SecuritySettingsService;
import com.lumira.saas.infrastructure.security.service.CaptchaService;
import com.lumira.saas.infrastructure.security.service.PasswordPolicyService;
import com.lumira.saas.modules.audit.app.LoginAuditService;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.iam.service.IamUserService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.passkey.PasskeyCredentialAppService;
import com.lumira.saas.modules.system.app.MaintenanceLoginPolicyService;
import com.lumira.saas.modules.system.internal.app.InternalSystemApplicationService;
import com.lumira.saas.modules.system.user.support.UserUidGenerator;
import com.lumira.saas.modules.system.verification.SystemVerificationAppService;
import com.lumira.saas.modules.user.domain.UserDomainService;
import com.lumira.saas.modules.user.entity.SysUserEntity;
import com.lumira.saas.modules.auth.vo.LoginCodeChallengeVO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.LinkedHashSet;

@RestController
@RequestMapping("/internal/system")
public class InternalSystemController {

    private static final Logger log = LoggerFactory.getLogger(InternalSystemController.class);

    private static final String DEFAULT_REGISTRATION_ROLE_CODE_KEY = "auth.default-registration-role-code";
    private static final String DEFAULT_REGISTRATION_ROLE_CODE = "commonuser";
    private static final Set<String> DEFAULT_REGISTRATION_FORBIDDEN_ROLE_TYPES = Set.of("SYSTEM");
    private static final Set<String> ADMIN_ONLY_ROLE_PERMISSION_PREFIXES = Set.of(
            "ai:",
            "audit:",
            "localization:",
            "payment:",
            "plugin:management:",
            "system:config:",
            "system:dict:",
            "system:file:manage",
            "system:menu:",
            "system:monitor:",
            "system:notification:",
            "system:profile-field:",
            "system:profile_field:",
            "system:security:",
            "system:update:",
            "system:verification:"
    );
    private static final Set<String> ADMIN_ONLY_ROLE_PERMISSION_KEYS = Set.of(
            "plugin:management:view",
            "audit:view",
            "localization:view",
            "system:file:manage",
            "system:monitor:view"
    );
    private static final Set<String> DEFAULT_REGISTRATION_FORBIDDEN_PERMISSION_PREFIXES = Set.of(
            "payment:",
            "plugin:",
            "system:department:",
            "system:online-user:",
            "system:role:",
            "system:user:",
            "workflow:"
    );
    private static final String FACTOR_SMS = "sms";
    private static final String FACTOR_EMAIL = "email";
    private static final int MAX_INTERNAL_ID_BATCH = 200;
    private static final List<String> INTERNAL_SMTP_RUNTIME_CONFIG_KEYS = List.of(
            "smtp.host",
            "smtp.port",
            "smtp.username",
            "smtp.password",
            "smtp.from",
            "smtp.auth-enabled",
            "smtp.starttls-enabled",
            "smtp.ssl-enabled"
    );
    private static final List<String> INTERNAL_WECHAT_OFFICIAL_RUNTIME_CONFIG_KEYS = List.of(
            "notification.wechat-official.enabled",
            "notification.wechat-official.app-id",
            "notification.wechat-official.app-secret",
            "notification.wechat-official.template-id",
            "notification.wechat-official.detail-url"
    );

    private final UserDomainService userDomainService;
    private final IamUserService iamUserService;
    private final PermissionSnapshotService permissionSnapshotService;
    private final CaptchaService captchaService;
    private final SystemVerificationAppService verificationAppService;
    private final WechatLoginSettingsService wechatLoginSettingsService;
    private final PasskeyCredentialAppService passkeyCredentialAppService;
    private final InternalSystemApplicationService internalSystemApplicationService;
    private final PasswordEncoder passwordEncoder;
    private final LoginAuditService loginAuditService;
    private final OperationAuditService operationAuditService;
    private final SecuritySettingsService securitySettingsService;
    private final PasswordPolicyService passwordPolicyService;
    private final AuthSessionStore authSessionStore;
    private final ReadModelVersionService readModelVersionService;
    private MaintenanceLoginPolicyService maintenanceLoginPolicyService;

    public InternalSystemController(
            UserDomainService userDomainService,
            IamUserService iamUserService,
            PermissionSnapshotService permissionSnapshotService,
            CaptchaService captchaService,
            SystemVerificationAppService verificationAppService,
            WechatLoginSettingsService wechatLoginSettingsService,
            PasskeyCredentialAppService passkeyCredentialAppService,
            InternalSystemApplicationService internalSystemApplicationService,
            PasswordEncoder passwordEncoder,
            LoginAuditService loginAuditService,
            OperationAuditService operationAuditService,
            SecuritySettingsService securitySettingsService,
            PasswordPolicyService passwordPolicyService,
            AuthSessionStore authSessionStore,
            ReadModelVersionService readModelVersionService
    ) {
        this.userDomainService = userDomainService;
        this.iamUserService = iamUserService;
        this.permissionSnapshotService = permissionSnapshotService;
        this.captchaService = captchaService;
        this.verificationAppService = verificationAppService;
        this.wechatLoginSettingsService = wechatLoginSettingsService;
        this.passkeyCredentialAppService = passkeyCredentialAppService;
        this.internalSystemApplicationService = internalSystemApplicationService;
        this.passwordEncoder = passwordEncoder;
        this.loginAuditService = loginAuditService;
        this.operationAuditService = operationAuditService;
        this.securitySettingsService = securitySettingsService;
        this.passwordPolicyService = passwordPolicyService;
        this.authSessionStore = authSessionStore;
        this.readModelVersionService = readModelVersionService;
    }

    @Autowired(required = false)
    public void setMaintenanceLoginPolicyService(MaintenanceLoginPolicyService maintenanceLoginPolicyService) {
        this.maintenanceLoginPolicyService = maintenanceLoginPolicyService;
    }

    @ModelAttribute
    void requireInternalServicePrincipal() {
        if (!AuthenticationTrustSupport.isInternalServiceAuthentication(SecurityContextHolder.getContext().getAuthentication())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Internal service token is required");
        }
    }

    @PostMapping("/users/login/verify")
    public PasswordLoginVerificationDTO verifyPasswordLogin(
            @RequestParam("account") String account,
            @RequestParam("password") String password
    ) {
        requireInternalServicePrincipal();
        String normalizedAccount = requireText(account, "account");
        String trustedPassword = requireText(password, "password");
        SysUserEntity user = userDomainService.findLoginUser(normalizedAccount).orElse(null);
        if (user == null) {
            return null;
        }
        requireTrustedSnapshotUser(user);
        boolean passwordMatched = StringUtils.hasText(user.getPasswordHash())
                && passwordEncoder.matches(trustedPassword, user.getPasswordHash());
        boolean requiresPasswordChange = passwordMatched && requiresPasswordChange(user);
        return new PasswordLoginVerificationDTO(
                toProfileSnapshot(user),
                passwordMatched,
                requiresPasswordChange
        );
    }

    @GetMapping("/users/{id}/identity")
    public SystemUserSnapshotDTO findUserIdentityById(@PathVariable("id") Long id) {
        requireInternalServicePrincipal();
        return userDomainService.findById(requirePositiveId(id, "id")).map(this::toIdentitySnapshot).orElse(null);
    }

    @GetMapping("/users/{id}/profile")
    public SystemUserSnapshotDTO findUserProfileById(@PathVariable("id") Long id) {
        requireInternalServicePrincipal();
        return userDomainService.findById(requirePositiveId(id, "id")).map(this::toProfileSnapshot).orElse(null);
    }

    @GetMapping("/users/{id}/email-available")
    public Boolean userHasEmail(
            @PathVariable("id") Long id,
            @RequestParam("userUuid") String userUuid
    ) {
        requireInternalServicePrincipal();
        Long normalizedUserId = requirePositiveId(id, "id");
        if (!StringUtils.hasText(userUuid)) {
            return Boolean.FALSE;
        }
        SysUserEntity user = userDomainService.findById(normalizedUserId).orElse(null);
        if (user == null) {
            return Boolean.FALSE;
        }
        requireTrustedSnapshotUser(user);
        String trustedUserUuid = user.getUuid();
        if (!StringUtils.hasText(trustedUserUuid) || !trustedUserUuid.trim().equals(userUuid.trim())) {
            return Boolean.FALSE;
        }
        String trustedStatus = user.getStatus();
        if (!StringUtils.hasText(trustedStatus) || !"ENABLED".equalsIgnoreCase(trustedStatus.trim())) {
            return Boolean.FALSE;
        }
        return StringUtils.hasText(user.getEmail());
    }

    @GetMapping("/users/{id}/requires-password-change")
    public Boolean requiresInitialPasswordChange(
            @PathVariable("id") Long id,
            @RequestParam("userUuid") String userUuid
    ) {
        requireInternalServicePrincipal();
        SysUserEntity user = requireTrustedInternalUserEntity(id, userUuid);
        return requiresPasswordChange(user);
    }

    @GetMapping("/users/{id}")
    public SystemUserSnapshotDTO findUserById(@PathVariable("id") Long id) {
        requireInternalServicePrincipal();
        return userDomainService.findById(requirePositiveId(id, "id")).map(this::toIdentitySnapshot).orElse(null);
    }

    @GetMapping("/users/{id}/target-uuid")
    public String findTargetUserUuidById(@PathVariable("id") Long id) {
        requireInternalServicePrincipal();
        return userDomainService.findById(requirePositiveId(id, "id"))
                .filter(this::isTrustedActiveSnapshotUser)
                .map(SysUserEntity::getUuid)
                .map(String::trim)
                .orElse(null);
    }

    @GetMapping("/users/identities-by-ids")
    public List<SystemUserSnapshotDTO> userIdentitiesByIds(
            @RequestParam("ids") List<Long> userIds
    ) {
        requireInternalServicePrincipal();
        List<Long> normalizedIds = normalizeInternalIdBatch(userIds, "ids");
        if (normalizedIds.isEmpty()) {
            return List.of();
        }
        return internalSystemApplicationService.findEnabledUserIdentities(normalizedIds);
    }

    @GetMapping("/users/{id}/role-options")
    public List<CurrentUserRoleOptionDTO> userRoleOptions(
            @PathVariable("id") Long userId,
            @RequestParam("userUuid") String userUuid
    ) {
        requireInternalServicePrincipal();
        SysUserEntity user = requireTrustedInternalUserEntity(userId, userUuid);
        return loadRoleOptions(user.getId(), user.getUuid());
    }

    @GetMapping("/roles/names-by-ids")
    public List<SystemRoleSnapshotDTO> roleNamesByIds(
            @RequestParam("ids") List<Long> roleIds
    ) {
        requireInternalServicePrincipal();
        List<Long> normalizedIds = normalizeInternalIdBatch(roleIds, "ids");
        if (normalizedIds.isEmpty()) {
            return List.of();
        }
        return internalSystemApplicationService.findRoleNames(normalizedIds);
    }

    @GetMapping("/roles/{roleId}/identities")
    public List<SystemUserSnapshotDTO> roleUserIdentities(@PathVariable("roleId") Long roleId) {
        requireInternalServicePrincipal();
        return internalSystemApplicationService.findEnabledRoleUserIdentities(requirePositiveId(roleId, "roleId"));
    }

    @GetMapping("/users/email-recipients")
    public List<SystemUserEmailRecipientDTO> userEmailRecipientsByIds(
            @RequestParam("ids") List<Long> userIds
    ) {
        requireInternalServicePrincipal();
        List<Long> normalizedIds = normalizeInternalIdBatch(userIds, "ids");
        if (normalizedIds.isEmpty()) {
            return List.of();
        }
        return internalSystemApplicationService.findEmailRecipientsByUserIds(normalizedIds);
    }

    @GetMapping("/users/wechat-recipients")
    public List<SystemUserWechatRecipientDTO> userWechatRecipientsByIds(
            @RequestParam("ids") List<Long> userIds
    ) {
        requireInternalServicePrincipal();
        List<Long> normalizedIds = normalizeInternalIdBatch(userIds, "ids");
        if (normalizedIds.isEmpty()) {
            return List.of();
        }
        return internalSystemApplicationService.findWechatRecipientsByUserIds(normalizedIds);
    }

    @GetMapping("/roles/{roleId}/email-recipients")
    public List<SystemUserEmailRecipientDTO> userEmailRecipientsByRole(
            @PathVariable("roleId") Long roleId
    ) {
        requireInternalServicePrincipal();
        return internalSystemApplicationService.findEmailRecipientsByRoleId(requirePositiveId(roleId, "roleId"));
    }

    @GetMapping("/roles/{roleId}/wechat-recipients")
    public List<SystemUserWechatRecipientDTO> userWechatRecipientsByRole(
            @PathVariable("roleId") Long roleId
    ) {
        requireInternalServicePrincipal();
        return internalSystemApplicationService.findWechatRecipientsByRoleId(requirePositiveId(roleId, "roleId"));
    }

    @GetMapping("/platform/email-recipients")
    public List<SystemUserEmailRecipientDTO> platformUserEmailRecipients() {
        requireInternalServicePrincipal();
        return internalSystemApplicationService.findPlatformEmailRecipients();
    }

    @GetMapping("/platform/wechat-recipients")
    public List<SystemUserWechatRecipientDTO> platformUserWechatRecipients() {
        requireInternalServicePrincipal();
        return internalSystemApplicationService.findPlatformWechatRecipients();
    }

    @PostMapping("/users/wechat-login")
    @Transactional
    public SystemUserSnapshotDTO resolveWechatLoginUser(@RequestBody WechatLoginUserRequestDTO request) {
        requireInternalServicePrincipal();
        WechatLoginUserRequestDTO normalizedRequest = normalizeWechatRequest(request);
        SysUserEntity user = findWechatBoundUser(normalizedRequest.unionid(), normalizedRequest.openid());
        if (user == null) {
            requireWechatBindingAvailableForRegistration(normalizedRequest.unionid(), normalizedRequest.openid());
            user = registerWechatUser(normalizedRequest);
        }
        upsertWechatBinding(user, normalizedRequest);
        refreshWechatProfile(user, normalizedRequest);
        SysUserEntity refreshedUser = userDomainService.findById(user.getId()).orElse(user);
        iamUserService.updateProfile(refreshedUser);
        return toProfileSnapshot(refreshedUser);
    }

    @GetMapping("/permissions/snapshot")
    public PermissionSnapshotDTO permissionSnapshot(
            @RequestParam("userId") Long userId,
            @RequestParam("userUuid") String userUuid
    ) {
        requireInternalServicePrincipal();
        PermissionSnapshotService.PermissionSnapshot snapshot = requireTrustedPermissionSnapshot(
                permissionSnapshotService.loadSnapshot(requireTrustedInternalUser(userId, userUuid), userUuid),
                "Trusted user permission snapshot is unavailable"
        );
        return new PermissionSnapshotDTO(
                snapshot.getVersion(),
                snapshot.getPermissionList(),
                snapshot.getRoleIds().stream().toList(),
                snapshot.getPrimaryDeptId(),
                snapshot.getDeptIds().stream().toList(),
                snapshot.getDescendantDeptIds().stream().toList(),
                snapshot.getDataScopes(),
                snapshot.getDefaultHomePath()
        );
    }

    @GetMapping("/permissions/role-snapshot")
    public PermissionSnapshotDTO permissionRoleSnapshot(
            @RequestParam("userId") Long userId,
            @RequestParam("userUuid") String userUuid
    ) {
        requireInternalServicePrincipal();
        PermissionSnapshotService.PermissionSnapshot snapshot = requireTrustedPermissionSnapshot(
                permissionSnapshotService.loadSnapshot(requireTrustedInternalUser(userId, userUuid), userUuid),
                "Trusted user permission snapshot is unavailable"
        );
        return new PermissionSnapshotDTO(
                snapshot.getVersion(),
                List.of(),
                snapshot.getRoleIds().stream().toList(),
                null,
                List.of(),
                List.of(),
                List.of(),
                null
        );
    }

    @GetMapping("/permissions/simulated-role-snapshot")
    public PermissionSnapshotDTO simulatedRolePermissionSnapshot(
            @RequestParam("userId") Long userId,
            @RequestParam("userUuid") String userUuid,
            @RequestParam("roleId") Long roleId
    ) {
        requireInternalServicePrincipal();
        SysUserEntity user = requireTrustedInternalUserEntity(userId, userUuid);
        Long trustedRoleId = requirePositiveId(roleId, "roleId");
        PermissionSnapshotService.PermissionSnapshot snapshot = requireTrustedPermissionSnapshot(
                permissionSnapshotService.loadGrantedRoleSnapshot(user.getId(), user.getUuid(), trustedRoleId),
                "Trusted role permission snapshot is unavailable"
        );
        return new PermissionSnapshotDTO(
                snapshot.getVersion(),
                snapshot.getPermissionList(),
                snapshot.getRoleIds().stream().toList(),
                snapshot.getPrimaryDeptId(),
                snapshot.getDeptIds().stream().toList(),
                snapshot.getDescendantDeptIds().stream().toList(),
                snapshot.getDataScopes(),
                snapshot.getDefaultHomePath()
        );
    }

    @PostMapping("/permissions/invalidate")
    public Boolean invalidatePermissionSnapshot() {
        requireInternalServicePrincipal();
        permissionSnapshotService.invalidatePermissions();
        return Boolean.TRUE;
    }

    @PostMapping("/permissions/plugin")
    @Transactional
    public Boolean registerPluginPermissions(@Valid @RequestBody PluginPermissionRegistrationRequestDTO request) {
        requireInternalServicePrincipal();
        if (request == null || !StringUtils.hasText(request.pluginCode())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "插件权限注册参数不完整");
        }
        if (request.permissions() == null || request.permissions().isEmpty()) {
            return Boolean.TRUE;
        }
        internalSystemApplicationService.registerPluginPermissions(request);
        permissionSnapshotService.invalidatePermissions();
        return Boolean.TRUE;
    }

    @PostMapping("/read-model-version/bump")
    public Boolean bumpReadModelVersion(
            @RequestParam("contextName") String contextName,
            @RequestParam("scope") String scope,
            @RequestParam(value = "eventKey", required = false) String eventKey
    ) {
        requireInternalServicePrincipal();
        readModelVersionService.bump(contextName, scope, eventKey);
        return Boolean.TRUE;
    }

    @GetMapping("/read-model-version")
    public Long readModelVersion(
            @RequestParam("contextName") String contextName,
            @RequestParam("scope") String scope
    ) {
        requireInternalServicePrincipal();
        try {
            Long version = readModelVersionService.currentVersion(contextName, scope);
            return version == null ? 0L : version;
        } catch (Exception exception) {
            log.warn("Failed to read current read-model version for context={} scope={}", contextName, scope, exception);
            return 0L;
        }
    }

    @PostMapping("/captcha/validate")
    public Boolean validateCaptcha(@Valid @RequestBody CaptchaValidationRequestDTO request) {
        requireInternalServicePrincipal();
        captchaService.validateCaptcha(request.captchaId(), request.captchaCode(), request.captchaProof());
        return Boolean.TRUE;
    }

    @PostMapping("/audit/login")
    public Boolean recordLoginAudit(@RequestBody LoginAuditRecordRequestDTO request) {
        requireInternalServicePrincipal();
        requireRequest(request, "login audit request");
        AuditSubject auditSubject = resolveAuditSubject(request.userId(), request.userUuid(), request.username());
        loginAuditService.log(
                auditSubject.userId(),
                auditSubject.userUuid(),
                auditSubject.username(),
                request.loginType(),
                request.loginResult(),
                request.failReason(),
                request.loginIp(),
                request.userAgent()
        );
        return Boolean.TRUE;
    }

    @PostMapping("/audit/operation")
    public Boolean recordOperationAudit(@RequestBody OperationAuditRecordRequestDTO request) {
        requireInternalServicePrincipal();
        requireRequest(request, "operation audit request");
        AuditSubject auditSubject = resolveAuditSubject(request.userId(), request.userUuid(), request.username());
        operationAuditService.log(
                auditSubject.userId(),
                auditSubject.userUuid(),
                auditSubject.username(),
                request.moduleName(),
                request.actionName(),
                request.operationType(),
                request.resultStatus(),
                request.detailMessage()
        );
        return Boolean.TRUE;
    }

    @GetMapping("/verification/login-capabilities")
    public LoginCapabilitiesDTO loginCapabilities() {
        requireInternalServicePrincipal();
        var capabilities = verificationAppService.loadLoginCapabilitiesFresh();
        return new LoginCapabilitiesDTO(
                Boolean.TRUE.equals(capabilities.getPasswordLoginAvailable()),
                Boolean.TRUE.equals(capabilities.getSmsLoginAvailable()),
                Boolean.TRUE.equals(capabilities.getEmailLoginAvailable()),
                Boolean.TRUE.equals(capabilities.getWechatLoginAvailable()),
                Boolean.TRUE.equals(capabilities.getPasskeyLoginAvailable()),
                Boolean.TRUE.equals(capabilities.getPasskeyPasswordlessAvailable()),
                capabilities.getLoginModeOrder()
        );
    }

    @GetMapping("/security/settings")
    public SecuritySettingsDTO securitySettings() {
        requireInternalServicePrincipal();
        var settings = securitySettingsService.loadSettings();
        return new SecuritySettingsDTO(
                settings.getIdleTimeoutSeconds(),
                settings.getAccessTokenExpireSeconds(),
                settings.getRefreshTokenExpireSeconds(),
                settings.isAllowMultiDeviceLogin(),
                settings.isCaptchaEnabled(),
                settings.getCaptchaType(),
                settings.getLoginDefenseWindowMinutes(),
                settings.getLoginMaxValidationAttempts(),
                settings.getLoginMaxFailureCount(),
                settings.getVerificationCodeExpireSeconds(),
                settings.getVerificationCodeCooldownSeconds()
        );
    }

    @GetMapping("/config/runtime/smtp")
    public Map<String, String> smtpRuntimeConfigValues() {
        requireInternalServicePrincipal();
        return queryPlatformConfigValues(INTERNAL_SMTP_RUNTIME_CONFIG_KEYS);
    }

    @GetMapping("/config/runtime/wechat-official")
    public Map<String, String> wechatOfficialRuntimeConfigValues() {
        requireInternalServicePrincipal();
        return queryPlatformConfigValues(INTERNAL_WECHAT_OFFICIAL_RUNTIME_CONFIG_KEYS);
    }

    @GetMapping("/config/ai-platform-values")
    public Map<String, String> aiPlatformConfigValues(@RequestParam(name = "keys", required = false) List<String> keys) {
        requireInternalServicePrincipal();
        List<String> normalizedKeys = normalizeAiConfigKeys(keys);
        if (normalizedKeys.isEmpty()) {
            return Map.of();
        }
        return queryPlatformConfigValues(normalizedKeys);
    }

    private Map<String, String> queryPlatformConfigValues(List<String> normalizedKeys) {
        return internalSystemApplicationService.platformConfigValues(normalizedKeys);
    }

    private List<String> normalizeAiConfigKeys(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalizedKeys = new LinkedHashSet<>();
        for (String key : keys) {
            if (!StringUtils.hasText(key)) {
                continue;
            }
            String normalized = key.trim();
            if (AiConfigAccessPolicy.isAiManageableConfigKey(normalized)) {
                normalizedKeys.add(normalized);
            }
        }
        return List.copyOf(normalizedKeys);
    }

    @GetMapping("/verification/wechat-settings")
    public WechatLoginSettingsDTO wechatLoginSettings() {
        requireInternalServicePrincipal();
        return wechatLoginSettingsService.getInternalSettings();
    }

    @GetMapping("/maintenance-login-policy")
    public MaintenanceLoginPolicyDTO maintenanceLoginPolicy() {
        requireInternalServicePrincipal();
        if (maintenanceLoginPolicyService == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "Maintenance login policy service is unavailable");
        }
        return maintenanceLoginPolicyService.loadEffectivePolicy();
    }

    @GetMapping("/verification/passkey-settings")
    public PasskeySettingsDTO passkeySettings() {
        requireInternalServicePrincipal();
        var settings = verificationAppService.getPasskeySettings();
        return new PasskeySettingsDTO(
                settings.getEnabled(),
                settings.getPasswordlessEnabled(),
                settings.getSelfBindingEnabled(),
                settings.getRpId(),
                settings.getRpName(),
                settings.getAllowedOrigins(),
                settings.getChallengeTtlSeconds()
        );
    }

    @GetMapping("/passkeys/assertion")
    public PasskeyCredentialAssertionDTO passkeyCredentialAssertion(@RequestParam("credentialId") String credentialId) {
        requireInternalServicePrincipal();
        return passkeyCredentialAppService.findAssertionByCredentialId(requireText(credentialId, "credentialId"));
    }

    @GetMapping("/passkeys/descriptors")
    public List<PasskeyCredentialDescriptorDTO> passkeyCredentialDescriptors(
            @RequestParam("userId") Long userId,
            @RequestParam("userUuid") String userUuid
    ) {
        requireInternalServicePrincipal();
        Long trustedUserId = requireTrustedInternalUser(userId, userUuid);
        return passkeyCredentialAppService.listDescriptors(trustedUserId, userUuid);
    }

    @GetMapping("/passkeys")
    public List<PasskeyCredentialDTO> passkeyCredentials(
            @RequestParam("userId") Long userId,
            @RequestParam("userUuid") String userUuid
    ) {
        requireInternalServicePrincipal();
        Long trustedUserId = requireTrustedInternalUser(userId, userUuid);
        return passkeyCredentialAppService.list(trustedUserId, userUuid);
    }

    @PostMapping("/passkeys")
    public PasskeyCredentialDTO savePasskeyCredential(@RequestBody PasskeyCredentialSaveRequestDTO request) {
        requireInternalServicePrincipal();
        requireRequest(request, "passkey credential request");
        requirePositiveId(request.userId(), "userId");
        requireTrustedInternalUser(request.userId(), request.userUuid());
        requireText(request.userHandle(), "userHandle");
        requireText(request.credentialId(), "credentialId");
        requireText(request.publicKeyCose(), "publicKeyCose");
        requireNonNegative(request.signCount(), "signCount");
        return passkeyCredentialAppService.create(request);
    }

    @PostMapping("/passkeys/usage")
    public Boolean updatePasskeyCredentialUsage(@RequestBody PasskeyCredentialUsageRequestDTO request) {
        requireInternalServicePrincipal();
        requireRequest(request, "passkey credential usage request");
        requirePositiveId(request.credentialId(), "credentialId");
        requirePositiveId(request.userId(), "userId");
        requireTrustedInternalUser(request.userId(), request.userUuid());
        requireNonNegative(request.signCount(), "signCount");
        return passkeyCredentialAppService.updateUsage(request);
    }

    @PostMapping("/passkeys/{id}/label")
    public PasskeyCredentialDTO renamePasskeyCredential(
            @PathVariable("id") Long id,
            @RequestParam("userId") Long userId,
            @RequestParam("userUuid") String userUuid,
            @RequestParam("label") String label
    ) {
        requireInternalServicePrincipal();
        return passkeyCredentialAppService.rename(
                requirePositiveId(id, "id"),
                requireTrustedInternalUser(userId, userUuid),
                userUuid,
                label
        );
    }

    @PostMapping("/passkeys/{id}/delete")
    public Boolean deletePasskeyCredential(
            @PathVariable("id") Long id,
            @RequestParam("userId") Long userId,
            @RequestParam("userUuid") String userUuid
    ) {
        requireInternalServicePrincipal();
        return passkeyCredentialAppService.delete(requirePositiveId(id, "id"), requireTrustedInternalUser(userId, userUuid), userUuid);
    }

    @GetMapping("/verification/providers")
    public List<VerificationProviderDTO> listVerificationProviders(
            @RequestParam("userId") Long userId,
            @RequestParam("userUuid") String userUuid
    ) {
        requireInternalServicePrincipal();
        return verificationAppService.listProviders(requireTrustedInternalUser(userId, userUuid), userUuid).stream().map(this::toProvider).toList();
    }

    @GetMapping("/verification/login-options")
    public List<LoginResponseDTO.SecondFactorOptionDTO> listLoginSecondFactorOptions(
            @RequestParam("userId") Long userId,
            @RequestParam("userUuid") String userUuid
    ) {
        requireInternalServicePrincipal();
        return userDomainService.findById(requireTrustedInternalUser(userId, userUuid))
                .map(user -> verificationAppService.listLoginOptions(user).stream().map(this::toSecondFactorOption).toList())
                .orElseGet(List::of);
    }

    @GetMapping("/verification/providers/{factorCode}")
    public VerificationProviderDTO verificationProvider(
            @RequestParam("userId") Long userId,
            @RequestParam("userUuid") String userUuid,
            @PathVariable("factorCode") String factorCode
    ) {
        requireInternalServicePrincipal();
        return toProvider(verificationAppService.provider(requireTrustedInternalUser(userId, userUuid), userUuid, factorCode));
    }

    @PostMapping("/verification/providers/{factorCode}/bind")
    public VerificationBindingChallengeDTO bindVerificationProvider(
            @RequestParam("userId") Long userId,
            @RequestParam("userUuid") String userUuid,
            @PathVariable("factorCode") String factorCode,
            @RequestBody(required = false) @Valid VerificationBindRequest request
    ) {
        requireInternalServicePrincipal();
        return toBindingChallenge(verificationAppService.bind(
                requireTrustedInternalUser(userId, userUuid),
                userUuid,
                factorCode,
                request == null ? null : request.currentPassword(),
                request == null ? null : request.currentFactorCode(),
                request == null ? null : request.currentChallengeId(),
                request == null ? null : request.currentVerificationCode()
        ));
    }

    @PostMapping("/verification/providers/{factorCode}/unbind")
    public Boolean unbindVerificationProvider(
            @RequestParam("userId") Long userId,
            @RequestParam("userUuid") String userUuid,
            @PathVariable("factorCode") String factorCode,
            @Valid @RequestBody SecondFactorCompleteRequest request
    ) {
        requireInternalServicePrincipal();
        if (!factorCode.equalsIgnoreCase(request.factorCode())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "验证方式不匹配");
        }
        return verificationAppService.unbind(
                requireTrustedInternalUser(userId, userUuid),
                userUuid,
                factorCode,
                request.challengeId(),
                request.verificationCode()
        );
    }

    @PostMapping("/verification/providers/{factorCode}/challenge")
    public VerificationChallengeDTO verificationChallenge(
            @RequestParam("userId") Long userId,
            @RequestParam("userUuid") String userUuid,
            @PathVariable("factorCode") String factorCode
    ) {
        requireInternalServicePrincipal();
        return toChallenge(verificationAppService.challenge(requireTrustedInternalUser(userId, userUuid), userUuid, factorCode));
    }

    @PostMapping("/verification/providers/{factorCode}/verify")
    public VerificationVerificationDTO verificationVerify(
            @RequestParam("userId") Long userId,
            @RequestParam("userUuid") String userUuid,
            @PathVariable("factorCode") String factorCode,
            @RequestParam("challengeId") String challengeId,
            @RequestParam("verificationCode") String verificationCode
    ) {
        requireInternalServicePrincipal();
        return toVerification(
                verificationAppService.completeBind(requireTrustedInternalUser(userId, userUuid), userUuid, factorCode, challengeId, verificationCode),
                factorCode
        );
    }

    @PostMapping("/verification/login-code/challenge")
    @Transactional
    public LoginCodeChallengeDTO loginCodeChallenge(
            @RequestParam("account") String account,
            @RequestParam("loginType") String loginType
    ) {
        requireInternalServicePrincipal();
        String normalizedLoginType = normalizeLoginCodeType(loginType);
        String normalizedAccount = normalizeLoginCodeAccount(account, normalizedLoginType);
        var existingUser = userDomainService.findLoginUser(normalizedAccount);
        if (existingUser.isEmpty()) {
            if (FACTOR_SMS.equals(normalizedLoginType)) {
                requireRegistrationEnabled();
                return toChallenge(verificationAppService.startPendingLoginCodeChallenge(normalizedAccount, normalizedLoginType));
            }
            throw loginCodeChallengeAccountMismatch();
        }
        SysUserEntity user = resolveLoginCodeChallengeUser(existingUser.get());
        var challenge = verificationAppService.startLoginCodeChallenge(user, normalizedLoginType);
        return toChallenge(challenge);
    }

    private LoginCodeChallengeDTO toChallenge(LoginCodeChallengeVO challenge) {
        LoginCodeChallengeDTO dto = new LoginCodeChallengeDTO();
        dto.setLoginType(challenge.getLoginType());
        dto.setFactorName(challenge.getFactorName());
        dto.setChallengeId(challenge.getChallengeId());
        dto.setMaskedContact(challenge.getMaskedContact());
        dto.setPromptMessage(challenge.getPromptMessage());
        dto.setExpiresInSeconds(challenge.getExpiresInSeconds());
        dto.setCooldownSeconds(challenge.getCooldownSeconds());
        return dto;
    }

    @PostMapping("/verification/login-code/complete")
    public VerificationVerificationDTO completeLoginCodeLogin(@Valid @RequestBody LoginCodeCompleteRequest request) {
        requireInternalServicePrincipal();
        com.lumira.saas.modules.auth.dto.LoginCodeCompleteRequest backendRequest = new com.lumira.saas.modules.auth.dto.LoginCodeCompleteRequest();
        backendRequest.setChallengeId(request.challengeId());
        backendRequest.setVerificationCode(request.verificationCode());
        Optional<SystemVerificationAppService.PendingLoginCodeVerification> pendingVerification =
                verificationAppService.completePendingLoginCodeLoginIfPresent(backendRequest);
        if (pendingVerification.isPresent()) {
            SystemVerificationAppService.PendingLoginCodeVerification verification = pendingVerification.get();
            SysUserEntity user = resolveVerifiedLoginCodeUser(verification.normalizedAccount(), verification.factorCode());
            return new VerificationVerificationDTO(
                    true,
                    verification.message(),
                    user.getId(),
                    user.getUuid(),
                    verification.factorCode(),
                    null
            );
        }
        return toVerification(verificationAppService.completeLoginCodeLogin(backendRequest), null);
    }

    @PostMapping("/verification/password-reset/challenge")
    @Transactional
    public LoginCodeChallengeDTO passwordResetChallenge(@Valid @RequestBody PasswordResetChallengeRequest request) {
        requireInternalServicePrincipal();
        String normalizedType = normalizeLoginCodeType(request.contactType());
        String normalizedAccount = requireText(request.account(), "account");
        String normalizedContact = normalizeLoginCodeAccount(request.contact(), normalizedType);
        SysUserEntity user;
        try {
            user = userDomainService.findLoginUser(normalizedAccount)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "账号不存在"));
            requireTrustedSnapshotUser(user);
        if (!"ENABLED".equalsIgnoreCase(user.getStatus())) {
            throw new BizException(ErrorCode.FORBIDDEN, "账号不可用");
        }
        String boundContact = FACTOR_SMS.equals(normalizedType) ? user.getMobile() : user.getEmail();
        String normalizedBoundContact = normalizeLoginCodeAccount(boundContact, normalizedType);
        if (!normalizedContact.equals(normalizedBoundContact)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "绑定邮箱或手机号不匹配");
        }
        } catch (BizException exception) {
            throw passwordResetChallengeMismatch();
        }
        var challenge = verificationAppService.startLoginCodeChallenge(user, normalizedType);
        LoginCodeChallengeDTO dto = new LoginCodeChallengeDTO();
        dto.setLoginType(challenge.getLoginType());
        dto.setFactorName(challenge.getFactorName());
        dto.setChallengeId(challenge.getChallengeId());
        dto.setMaskedContact(challenge.getMaskedContact());
        dto.setPromptMessage(challenge.getPromptMessage());
        dto.setExpiresInSeconds(challenge.getExpiresInSeconds());
        dto.setCooldownSeconds(challenge.getCooldownSeconds());
        return dto;
    }

    private SysUserEntity resolveLoginCodeChallengeUser(SysUserEntity user) {
        try {
            requireTrustedSnapshotUser(user);
            if (!"ENABLED".equalsIgnoreCase(user.getStatus())) {
                throw new BizException(ErrorCode.FORBIDDEN, "账号不可用");
            }
            return user;
        } catch (BizException exception) {
            throw loginCodeChallengeAccountMismatch();
        }
    }

    private SysUserEntity resolveVerifiedLoginCodeUser(String normalizedAccount, String normalizedLoginType) {
        Optional<SysUserEntity> existingUser = userDomainService.findLoginUser(normalizedAccount);
        if (existingUser.isPresent()) {
            SysUserEntity user = existingUser.get();
            requireTrustedSnapshotUser(user);
            if (!"ENABLED".equalsIgnoreCase(user.getStatus())) {
                throw new BizException(ErrorCode.ACCOUNT_DISABLED, "登录失败，账号已禁用");
            }
            return user;
        }
        if (FACTOR_SMS.equals(normalizedLoginType)) {
            return registerLoginCodeUser(normalizedAccount, normalizedLoginType);
        }
        throw loginCodeChallengeAccountMismatch();
    }

    private BizException loginCodeChallengeAccountMismatch() {
        return new BizException(ErrorCode.VALIDATION_ERROR, "账号不存在或暂不支持该登录方式");
    }

    private void requireRegistrationEnabled() {
        if (!securitySettingsService.isRegistrationEnabled()) {
            throw new BizException(ErrorCode.FORBIDDEN, "用户注册通道未开放");
        }
    }

    private BizException passwordResetChallengeMismatch() {
        return new BizException(ErrorCode.VALIDATION_ERROR, "账号或绑定的联系方式不匹配");
    }

    @PostMapping("/verification/password-reset/complete")
    @Transactional
    public Boolean completePasswordReset(@Valid @RequestBody PasswordResetCompleteRequest request) {
        requireInternalServicePrincipal();
        com.lumira.saas.modules.auth.dto.LoginCodeCompleteRequest backendRequest = new com.lumira.saas.modules.auth.dto.LoginCodeCompleteRequest();
        backendRequest.setChallengeId(request.challengeId());
        backendRequest.setVerificationCode(request.verificationCode());
        VerificationVerificationDTO verification = toVerification(verificationAppService.completeLoginCodeLogin(backendRequest), null);
        if (!Boolean.TRUE.equals(verification.verified()) || verification.userId() == null || !StringUtils.hasText(verification.userUuid())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "验证码无效");
        }
        passwordPolicyService.validatePassword(request.newPassword());
        String encodedPassword = passwordEncoder.encode(request.newPassword());
        int updated = internalSystemApplicationService.updatePassword(
                encodedPassword,
                verification.userId(),
                verification.userUuid()
        );
        if (updated != 1) {
            throw new BizException(ErrorCode.BIZ_ERROR, "密码重置失败，请重试");
        }
        iamUserService.upsertPasswordCredential(
                verification.userId(),
                verification.userUuid(),
                encodedPassword
        );
        authSessionStore.revokeUserSessions(verification.userId(), verification.userUuid(), true);
        return Boolean.TRUE;
    }

    @PostMapping("/verification/second-factor/complete")
    public VerificationVerificationDTO completeSecondFactorLogin(@Valid @RequestBody SecondFactorCompleteRequest request) {
        requireInternalServicePrincipal();
        com.lumira.saas.modules.auth.dto.SecondFactorCompleteRequest backendRequest = new com.lumira.saas.modules.auth.dto.SecondFactorCompleteRequest();
        backendRequest.setFactorCode(request.factorCode());
        backendRequest.setChallengeId(request.challengeId());
        backendRequest.setVerificationCode(request.verificationCode());
        return toVerification(verificationAppService.completeSecondFactorLogin(backendRequest, null, null), request.factorCode());
    }

    @GetMapping("/menus/builtin")
    public List<MenuNodeDTO> builtinMenus() {
        requireInternalServicePrincipal();
        return listSystemMenusFromDatabase().stream().map(this::toMenuNode).toList();
    }

    @GetMapping("/menus/ai-visible")
    public List<MenuNodeDTO> aiVisibleBuiltinMenus(
            @RequestParam("userId") Long userId,
            @RequestParam("userUuid") String userUuid
    ) {
        requireInternalServicePrincipal();
        PermissionSnapshotService.PermissionSnapshot snapshot = requireTrustedPermissionSnapshot(
                permissionSnapshotService.loadSnapshot(
                        requireTrustedInternalUser(userId, userUuid),
                        userUuid
                ),
                "Trusted user permission snapshot is unavailable"
        );
        return filterVisibleMenus(
                listSystemMenusFromDatabase().stream().map(this::toMenuNode).toList(),
                snapshot.getPermissions()
        );
    }

    private PermissionSnapshotService.PermissionSnapshot requireTrustedPermissionSnapshot(
            PermissionSnapshotService.PermissionSnapshot snapshot,
            String message
    ) {
        if (snapshot == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, message);
        }
        return snapshot;
    }

    private List<com.lumira.saas.modules.system.vo.SystemVO.MenuVO> listSystemMenusFromDatabase() {
        List<com.lumira.saas.modules.system.vo.SystemVO.MenuVO> menus = internalSystemApplicationService
                .findEnabledSystemMenus().stream()
                .filter(menu -> !isAiMenu(menu))
                .toList();
        return buildSystemMenuTree(menus);
    }

    private boolean isAiMenu(com.lumira.saas.modules.system.vo.SystemVO.MenuVO menu) {
        if (menu == null) {
            return false;
        }
        return isAiMenuCode(menu.getMenuCode())
                || isAiMenuPath(menu.getPath())
                || isAiMenuComponent(menu.getComponent())
                || isAiPermissionKey(menu.getPermissionKey());
    }

    private boolean isAiMenuCode(String menuCode) {
        if (!StringUtils.hasText(menuCode)) {
            return false;
        }
        String normalized = menuCode.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("ai.") || "settings.ai-employees".equals(normalized);
    }

    private boolean isAiMenuPath(String path) {
        if (!StringUtils.hasText(path)) {
            return false;
        }
        String normalized = path.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("/ai") || "/settings/ai-employees".equals(normalized);
    }

    private boolean isAiMenuComponent(String component) {
        if (!StringUtils.hasText(component)) {
            return false;
        }
        String normalized = component.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("@/pages/ai/")
                || normalized.equals("@/pages/settings/ai-employees")
                || normalized.equals("redirect:/ai/assistant");
    }

    private boolean isAiPermissionKey(String permissionKey) {
        return StringUtils.hasText(permissionKey)
                && permissionKey.trim().toLowerCase(Locale.ROOT).startsWith("ai:");
    }

    private List<com.lumira.saas.modules.system.vo.SystemVO.MenuVO> buildSystemMenuTree(List<com.lumira.saas.modules.system.vo.SystemVO.MenuVO> flatMenus) {
        Map<Long, com.lumira.saas.modules.system.vo.SystemVO.MenuVO> index = new LinkedHashMap<>();
        List<com.lumira.saas.modules.system.vo.SystemVO.MenuVO> roots = new ArrayList<>();
        for (com.lumira.saas.modules.system.vo.SystemVO.MenuVO menu : flatMenus) {
            menu.setBuiltin(true);
            menu.setChildren(new ArrayList<>());
            index.put(menu.getId(), menu);
        }
        for (com.lumira.saas.modules.system.vo.SystemVO.MenuVO menu : flatMenus) {
            Long parentId = menu.getParentId();
            if (parentId == null || parentId == 0 || !index.containsKey(parentId)) {
                roots.add(menu);
            } else {
                index.get(parentId).getChildren().add(menu);
            }
        }
        sortSystemMenuTree(roots);
        return roots;
    }

    private void sortSystemMenuTree(List<com.lumira.saas.modules.system.vo.SystemVO.MenuVO> roots) {
        Comparator<com.lumira.saas.modules.system.vo.SystemVO.MenuVO> comparator = Comparator
                .comparingInt((com.lumira.saas.modules.system.vo.SystemVO.MenuVO item) -> item.getSortNo() == null ? 0 : item.getSortNo())
                .thenComparing(item -> item.getId() == null ? 0L : item.getId());
        roots.sort(comparator);
        for (com.lumira.saas.modules.system.vo.SystemVO.MenuVO root : roots) {
            if (root.getChildren() != null) {
                sortSystemMenuTree(root.getChildren());
            }
        }
    }

    private List<MenuNodeDTO> filterVisibleMenus(List<MenuNodeDTO> menus, Set<String> permissions) {
        if (menus == null || menus.isEmpty()) {
            return List.of();
        }
        List<MenuNodeDTO> visibleMenus = new ArrayList<>();
        for (MenuNodeDTO menu : menus) {
            MenuNodeDTO visibleMenu = filterVisibleMenu(menu, permissions);
            if (visibleMenu != null) {
                visibleMenus.add(visibleMenu);
            }
        }
        return visibleMenus;
    }

    private MenuNodeDTO filterVisibleMenu(MenuNodeDTO menu, Set<String> permissions) {
        if (menu == null) {
            return null;
        }
        List<MenuNodeDTO> visibleChildren = filterVisibleMenus(menu.getChildren(), permissions);
        if (!isMenuAllowed(menu, permissions) && visibleChildren.isEmpty()) {
            return null;
        }
        MenuNodeDTO visibleMenu = new MenuNodeDTO();
        visibleMenu.setId(menu.getId());
        visibleMenu.setParentId(menu.getParentId());
        visibleMenu.setMenuCode(menu.getMenuCode());
        visibleMenu.setName(menu.getName());
        visibleMenu.setPath(menu.getPath());
        visibleMenu.setComponent(menu.getComponent());
        visibleMenu.setIcon(menu.getIcon());
        visibleMenu.setPermissionKey(menu.getPermissionKey());
        visibleMenu.setPluginCode(menu.getPluginCode());
        visibleMenu.setSortNo(menu.getSortNo());
        visibleMenu.setChildren(visibleChildren);
        return visibleMenu;
    }

    private boolean isMenuAllowed(MenuNodeDTO menu, Set<String> permissions) {
        if (menu == null || !StringUtils.hasText(menu.getPermissionKey())) {
            return true;
        }
        return permissions != null
                && (permissions.contains("*") || permissions.contains(menu.getPermissionKey().trim()));
    }

    private SystemUserSnapshotDTO toProfileSnapshot(SysUserEntity user) {
        requireTrustedSnapshotUser(user);
        return new SystemUserSnapshotDTO(
                user.getId(),
                user.getUuid(),
                user.getUsername(),
                null,
                user.getStatus(),
                user.getMobile(),
                user.getEmail(),
                user.getNickname(),
                user.getRealName(),
                user.getAvatarUrl(),
                user.getBirthMonth(),
                user.getGender(),
                user.getRegion(),
                user.getAvailableTime(),
                user.getIdCardNumber(),
                null
        );
    }

    private SystemUserSnapshotDTO toIdentitySnapshot(SysUserEntity user) {
        requireTrustedSnapshotUser(user);
        return new SystemUserSnapshotDTO(
                user.getId(),
                user.getUuid(),
                user.getUsername(),
                null,
                user.getStatus(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private void requireTrustedSnapshotUser(SysUserEntity user) {
        if (user == null || user.getId() == null || user.getId() <= 0) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "user id is required");
        }
        if (!StringUtils.hasText(user.getUuid())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "user uuid is required");
        }
        if (!StringUtils.hasText(user.getUsername())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "username is required");
        }
        if (!StringUtils.hasText(user.getStatus())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "user status is required");
        }
    }

    private boolean isTrustedActiveSnapshotUser(SysUserEntity user) {
        try {
            requireTrustedSnapshotUser(user);
        } catch (RuntimeException exception) {
            return false;
        }
        return "ENABLED".equalsIgnoreCase(user.getStatus().trim());
    }

    private boolean requiresPasswordChange(SysUserEntity user) {
        if (user == null) {
            return false;
        }
        return iamUserService.requiresPasswordChange(user.getId(), user.getUuid());
    }

    private List<CurrentUserRoleOptionDTO> loadRoleOptions(Long userId, String userUuid) {
        if (userId == null || !StringUtils.hasText(userUuid)) {
            return List.of();
        }
        return internalSystemApplicationService.findRoleOptions(userId, userUuid);
    }

    private Long requireTrustedInternalUser(Long userId, String userUuid) {
        return requireTrustedInternalUserEntity(userId, userUuid).getId();
    }

    private SysUserEntity requireTrustedInternalUserEntity(Long userId, String userUuid) {
        Long normalizedUserId = requirePositiveId(userId, "userId");
        if (!StringUtils.hasText(userUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "userUuid must not be blank");
        }
        SysUserEntity user = userDomainService.findById(normalizedUserId)
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "User does not exist"));
        requireTrustedSnapshotUser(user);
        if (!user.getUuid().trim().equals(userUuid.trim())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "User identity mismatch");
        }
        if (!"ENABLED".equalsIgnoreCase(user.getStatus().trim())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "User is disabled");
        }
        return user;
    }

    private Long requirePositiveId(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, fieldName + " must be positive");
        }
        return value;
    }

    private String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, fieldName + " must not be blank");
        }
        return value.trim();
    }

    private Long requireNonNegative(Long value, String fieldName) {
        if (value != null && value < 0) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, fieldName + " must not be negative");
        }
        return value;
    }

    private <T> T requireRequest(T request, String fieldName) {
        if (request == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, fieldName + " must not be null");
        }
        return request;
    }

    private Long normalizeOptionalPositiveUserId(Long userId) {
        if (userId == null) {
            return null;
        }
        return requirePositiveId(userId, "userId");
    }

    private AuditSubject resolveAuditSubject(Long userId, String userUuid, String username) {
        Long normalizedUserId = normalizeOptionalPositiveUserId(userId);
        if (normalizedUserId == null) {
            return new AuditSubject(null, null, sanitizeOptionalText(username));
        }
        SysUserEntity user = userDomainService.findById(normalizedUserId)
                .orElseThrow(() -> new BizException(ErrorCode.VALIDATION_ERROR, "audit user does not exist"));
        if (!StringUtils.hasText(user.getUsername())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "audit user username is required");
        }
        if (!StringUtils.hasText(user.getUuid())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "audit user uuid is required");
        }
        if (!StringUtils.hasText(userUuid) || !user.getUuid().trim().equals(userUuid.trim())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "audit user uuid mismatch");
        }
        return new AuditSubject(user.getId(), sanitizeOptionalText(user.getUuid()), user.getUsername().trim());
    }

    private String sanitizeOptionalText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private List<Long> normalizeInternalIdBatch(List<Long> ids, String fieldName) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        if (ids.size() > MAX_INTERNAL_ID_BATCH) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, fieldName + " exceeds max batch size");
        }
        return ids.stream()
                .filter(java.util.Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .toList();
    }

    private WechatLoginUserRequestDTO normalizeWechatRequest(WechatLoginUserRequestDTO request) {
        requireRequest(request, "wechat login request");
        if (!StringUtils.hasText(request.openid())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "wechat openid must not be blank");
        }
        String openid = request.openid().trim();
        String unionid = StringUtils.hasText(request.unionid()) ? request.unionid().trim() : null;
        String scope = StringUtils.hasText(request.scope()) ? request.scope().trim() : null;
        String nickname = normalizeWechatText(request.nickname(), 128);
        String avatarUrl = normalizeWechatText(request.avatarUrl(), 255);
        String country = normalizeWechatText(request.country(), 64);
        String province = normalizeWechatText(request.province(), 64);
        String city = normalizeWechatText(request.city(), 64);
        return new WechatLoginUserRequestDTO(openid, unionid, scope, nickname, avatarUrl, country, province, city, request.sex());
    }

    private SysUserEntity findWechatBoundUser(String unionid, String openid) {
        if (!StringUtils.hasText(openid)) {
            throw new IllegalArgumentException("微信 openid 不能为空");
        }
        String normalizedUnionid = StringUtils.hasText(unionid) ? unionid.trim() : "";
        Long userId = internalSystemApplicationService.findEnabledUserIdByWechatBinding(
                normalizedUnionid,
                openid
        );
        return userId == null ? null : userDomainService.findById(userId).orElse(null);
    }

    private void requireWechatBindingAvailableForRegistration(String unionid, String openid) {
        String normalizedUnionid = StringUtils.hasText(unionid) ? unionid.trim() : "";
        boolean bindingExists = internalSystemApplicationService.hasActiveWechatBinding(normalizedUnionid, openid);
        if (bindingExists) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Wechat account is unavailable");
        }
    }

    private SysUserEntity registerWechatUser(WechatLoginUserRequestDTO request) {
        String username = nextWechatUsername(request);
        int inserted = internalSystemApplicationService.insertWechatUser(
                UserUidGenerator.nextNumericUid(),
                username,
                passwordEncoder.encode(UUID.randomUUID().toString())
        );
        if (inserted != 1) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Wechat user changed, please retry");
        }
        SysUserEntity user = userDomainService.findLoginUser(username)
                .orElseThrow(() -> new IllegalStateException("微信登录自动注册用户失败"));
        iamUserService.syncSysUser(user, "WECHAT_REGISTER");
        iamUserService.recordUserRegistered(user.getId(), user.getUuid(), "WECHAT_REGISTER", null, null);
        grantDefaultLoginRole(user);
        permissionSnapshotService.invalidatePermissions();
        return user;
    }

    private SysUserEntity registerLoginCodeUser(String account, String loginType) {
        requireRegistrationEnabled();
        String normalizedLoginType = normalizeLoginCodeType(loginType);
        String identityType = iamUserService.detectIdentityType(account);
        if (FACTOR_SMS.equals(normalizedLoginType) && !IamUserService.IDENTITY_MOBILE.equals(identityType)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "短信验证码登录请使用手机号");
        }
        if (FACTOR_EMAIL.equals(normalizedLoginType) && !IamUserService.IDENTITY_EMAIL.equals(identityType)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "邮箱验证码登录请使用邮箱地址");
        }

        String normalizedAccount = iamUserService.normalizeIdentifier(identityType, account);
        String username = nextLoginCodeUsername(normalizedLoginType, normalizedAccount);
        String randomPassword = passwordEncoder.encode(UUID.randomUUID().toString());
        String nickname = FACTOR_SMS.equals(normalizedLoginType) ? "短信注册用户" : "邮箱注册用户";
        int inserted = internalSystemApplicationService.insertLoginCodeUser(
                UserUidGenerator.nextNumericUid(),
                username,
                randomPassword,
                FACTOR_SMS.equals(normalizedLoginType) ? normalizedAccount : null,
                nickname,
                FACTOR_EMAIL.equals(normalizedLoginType) ? normalizedAccount : null
        );
        if (inserted != 1) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Login code user changed, please retry");
        }
        Long createdUserId = internalSystemApplicationService.findActiveUserIdByUsername(username);
        SysUserEntity user = userDomainService.findById(createdUserId)
                .orElseThrow(() -> new IllegalStateException("验证码登录自动注册用户失败"));
        iamUserService.createUserWithIdentity(user, normalizedAccount, "LOGIN_CODE_REGISTER");
        iamUserService.recordUserRegistered(user.getId(), user.getUuid(), "LOGIN_CODE_REGISTER", null, null);
        grantDefaultLoginRole(user);
        permissionSnapshotService.invalidatePermissions();
        return user;
    }

    private String normalizeLoginCodeType(String loginType) {
        if (!StringUtils.hasText(loginType)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "登录方式不能为空");
        }
        String normalized = loginType.trim().toLowerCase(Locale.ROOT);
        if (!FACTOR_SMS.equals(normalized) && !FACTOR_EMAIL.equals(normalized)) {
            throw new BizException(ErrorCode.NOT_FOUND, "验证码登录方式不存在");
        }
        return normalized;
    }

    private String normalizeLoginCodeAccount(String account, String normalizedLoginType) {
        String identityType = iamUserService.detectIdentityType(account);
        if (FACTOR_SMS.equals(normalizedLoginType) && !IamUserService.IDENTITY_MOBILE.equals(identityType)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "sms login requires a mobile account");
        }
        if (FACTOR_EMAIL.equals(normalizedLoginType) && !IamUserService.IDENTITY_EMAIL.equals(identityType)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "email login requires an email account");
        }
        String normalizedAccount = iamUserService.normalizeIdentifier(identityType, account);
        if (!StringUtils.hasText(normalizedAccount)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "account must not be blank");
        }
        return normalizedAccount;
    }

    private String nextLoginCodeUsername(String loginType, String normalizedAccount) {
        String prefix = FACTOR_SMS.equals(loginType) ? "sms" : "email";
        String safeAccount = StringUtils.hasText(normalizedAccount)
                ? normalizedAccount.replaceAll("[^A-Za-z0-9_]", "_").replaceAll("_+", "_")
                : UUID.randomUUID().toString().replace("-", "");
        if (!StringUtils.hasText(safeAccount)) {
            safeAccount = UUID.randomUUID().toString().replace("-", "");
        }
        int maxAccountLength = 48 - prefix.length() - 1;
        if (safeAccount.length() > maxAccountLength) {
            safeAccount = safeAccount.substring(0, maxAccountLength);
        }
        String baseUsername = prefix + "_" + safeAccount;
        String username = baseUsername;
        int suffix = 1;
        while (userDomainService.findLoginUser(username).isPresent()) {
            String suffixText = "_" + suffix;
            int maxBaseLength = 64 - suffixText.length();
            username = baseUsername.length() > maxBaseLength
                    ? baseUsername.substring(0, maxBaseLength) + suffixText
                    : baseUsername + suffixText;
            suffix++;
        }
        return username;
    }

    private String nextWechatUsername(WechatLoginUserRequestDTO request) {
        String sourceId = StringUtils.hasText(request.unionid()) ? request.unionid() : request.openid();
        String normalized = sourceId == null ? UUID.randomUUID().toString() : sourceId.replaceAll("[^A-Za-z0-9_]", "");
        if (normalized.length() > 24) {
            normalized = normalized.substring(0, 24);
        }
        String baseUsername = "wx_" + (StringUtils.hasText(normalized) ? normalized : UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        String username = baseUsername;
        int suffix = 1;
        while (userDomainService.findLoginUser(username).isPresent()) {
            username = baseUsername + "_" + suffix;
            suffix++;
        }
        return username;
    }

    private void upsertWechatBinding(SysUserEntity user, WechatLoginUserRequestDTO request) {
        String userUuid = requireUserUuid(user);
        internalSystemApplicationService.upsertWechatBinding(
                user.getId(),
                userUuid,
                request.openid(),
                StringUtils.hasText(request.unionid()) ? request.unionid() : null,
                request.scope()
        );
        requireWechatBindingOwnedByUser(user.getId(), userUuid, request);
    }

    private void requireWechatBindingOwnedByUser(Long userId, String userUuid, WechatLoginUserRequestDTO request) {
        Long ownerId = requirePositiveId(userId, "userId");
        String ownerUuid = requireUserUuidText(userUuid);
        String unionid = StringUtils.hasText(request.unionid()) ? request.unionid() : null;
        boolean linked = internalSystemApplicationService.hasActiveWechatBindingOwnedByUser(
                ownerId,
                ownerUuid,
                request.openid(),
                unionid
        );
        if (!linked) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Wechat binding changed, please retry");
        }
    }

    private String requireUserUuidText(String userUuid) {
        if (!StringUtils.hasText(userUuid)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "user uuid is required");
        }
        return userUuid.trim();
    }

    private void refreshWechatProfile(SysUserEntity user, WechatLoginUserRequestDTO request) {
        if (!StringUtils.hasText(request.nickname()) && !StringUtils.hasText(request.avatarUrl())) {
            return;
        }
        String userUuid = requireUserUuid(user);
        int updated = internalSystemApplicationService.updateWechatProfile(
                user.getId(),
                userUuid,
                request.nickname(),
                request.avatarUrl()
        );
        if (updated != 1) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Wechat profile changed, please retry");
        }
    }

    private String normalizeWechatText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\p{Cntrl}", "");
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if (normalized.length() > maxLength) {
            return normalized.substring(0, maxLength);
        }
        return normalized;
    }

    private void grantDefaultLoginRole(SysUserEntity user) {
        String userUuid = requireUserUuid(user);
        String roleCode = resolveDefaultRegistrationRoleCode();
        Long roleId = resolveSafeDefaultRegistrationRoleId(roleCode);
        if (roleId == null && !DEFAULT_REGISTRATION_ROLE_CODE.equalsIgnoreCase(roleCode)) {
            roleId = resolveSafeDefaultRegistrationRoleId(DEFAULT_REGISTRATION_ROLE_CODE);
        }
        if (roleId == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "默认注册角色不存在，请先创建可用角色");
        }
        internalSystemApplicationService.upsertUserRole(user.getId(), userUuid, roleId);
        requireDefaultRoleGranted(user.getId(), userUuid, roleId);
    }

    private void requireDefaultRoleGranted(Long userId, String userUuid, Long roleId) {
        boolean granted = internalSystemApplicationService.hasActiveUserRole(
                requirePositiveId(userId, "userId"),
                requireUserUuidText(userUuid),
                requirePositiveId(roleId, "roleId")
        );
        if (!granted) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Default role binding changed, please retry");
        }
    }

    private String requireUserUuid(SysUserEntity user) {
        if (user == null || user.getId() == null || !StringUtils.hasText(user.getUuid())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "用户身份缺少可信 UUID");
        }
        return user.getUuid().trim();
    }

    private Long resolveSafeDefaultRegistrationRoleId(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            return null;
        }
        InternalSystemApplicationService.RegistrationRole candidate = internalSystemApplicationService
                .findActiveRoleByCode(roleCode.trim());
        if (candidate == null || !isSafeDefaultRegistrationRole(candidate)) {
            return null;
        }
        return candidate.id();
    }

    private boolean isSafeDefaultRegistrationRole(InternalSystemApplicationService.RegistrationRole role) {
        String roleCode = role.roleCode() == null ? "" : role.roleCode().trim();
        String roleType = role.roleType() == null ? "" : role.roleType().trim().toUpperCase(Locale.ROOT);
        if ("ADMIN".equalsIgnoreCase(roleCode) || DEFAULT_REGISTRATION_FORBIDDEN_ROLE_TYPES.contains(roleType)) {
            return false;
        }
        List<String> permissionKeys = internalSystemApplicationService.findActiveRolePermissionKeys(role.id());
        return permissionKeys.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .noneMatch(permissionKey -> "*".equals(permissionKey) || !isDefaultRegistrationAssignablePermissionKey(permissionKey));
    }

    private boolean isDefaultRegistrationAssignablePermissionKey(String permissionKey) {
        if (!isRoleAssignablePermissionKey(permissionKey)) {
            return false;
        }
        String normalizedKey = permissionKey.trim();
        for (String prefix : DEFAULT_REGISTRATION_FORBIDDEN_PERMISSION_PREFIXES) {
            if (normalizedKey.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    private boolean isRoleAssignablePermissionKey(String permissionKey) {
        if (!StringUtils.hasText(permissionKey)) {
            return false;
        }
        String normalizedKey = permissionKey.trim();
        if ("*".equals(normalizedKey)) {
            return false;
        }
        if (ADMIN_ONLY_ROLE_PERMISSION_KEYS.contains(normalizedKey)) {
            return false;
        }
        for (String prefix : ADMIN_ONLY_ROLE_PERMISSION_PREFIXES) {
            if (normalizedKey.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    private String resolveDefaultRegistrationRoleCode() {
        String roleCode = internalSystemApplicationService.findLatestPlatformConfigValue(
                DEFAULT_REGISTRATION_ROLE_CODE_KEY
        );
        return StringUtils.hasText(roleCode) ? roleCode.trim() : DEFAULT_REGISTRATION_ROLE_CODE;
    }

    private LoginResponseDTO.SecondFactorOptionDTO toSecondFactorOption(com.lumira.saas.modules.auth.vo.LoginResponseVO.SecondFactorOptionVO option) {
        LoginResponseDTO.SecondFactorOptionDTO dto = new LoginResponseDTO.SecondFactorOptionDTO();
        dto.setFactorCode(option.getFactorCode());
        dto.setFactorName(option.getFactorName());
        dto.setChallengeId(option.getChallengeId());
        dto.setMaskedContact(option.getMaskedContact());
        dto.setPromptMessage(option.getPromptMessage());
        return dto;
    }

    private VerificationProviderDTO toProvider(com.lumira.saas.modules.system.vo.SystemVO.VerificationProviderVO provider) {
        VerificationProviderDTO dto = new VerificationProviderDTO();
        dto.setFactorCode(provider.getFactorCode());
        dto.setFactorName(provider.getFactorName());
        dto.setEnabled(Boolean.TRUE.equals(provider.getEnabled()));
        dto.setBound(Boolean.TRUE.equals(provider.getBound()));
        dto.setStatus(provider.getStatusMessage());
        dto.setPromptMessage(provider.getStatusMessage());
        dto.setMaskedContact(provider.getMaskedContact());
        return dto;
    }

    private VerificationChallengeDTO toChallenge(com.lumira.saas.modules.system.vo.SystemVO.VerificationChallengeVO challenge) {
        VerificationChallengeDTO dto = new VerificationChallengeDTO();
        dto.setFactorCode(challenge.getFactorCode());
        dto.setFactorName(challenge.getFactorName());
        dto.setChallengeId(challenge.getChallengeId());
        dto.setMaskedContact(challenge.getMaskedContact());
        dto.setPromptMessage(challenge.getPromptMessage());
        dto.setExpiresInSeconds(null);
        return dto;
    }

    private VerificationBindingChallengeDTO toBindingChallenge(com.lumira.saas.modules.system.vo.SystemVO.VerificationChallengeVO challenge) {
        VerificationBindingChallengeDTO dto = new VerificationBindingChallengeDTO();
        dto.setFactorCode(challenge.getFactorCode());
        dto.setFactorName(challenge.getFactorName());
        dto.setChallengeId(challenge.getChallengeId());
        dto.setMaskedContact(challenge.getMaskedContact());
        dto.setPromptMessage(challenge.getPromptMessage());
        dto.setExpiresInSeconds(null);
        dto.setSetupUri(challenge.getSetupUri());
        dto.setSetupSecret(challenge.getSetupSecret());
        return dto;
    }

    private VerificationVerificationDTO toVerification(com.lumira.saas.modules.system.vo.SystemVO.VerificationVerificationVO verification, String factorCode) {
        return new VerificationVerificationDTO(
                Boolean.TRUE.equals(verification.getVerified()),
                verification.getMessage(),
                verification.getUserId(),
                verification.getUserUuid(),
                factorCode,
                verification.getRecoveryCodes()
        );
    }

    private MenuNodeDTO toMenuNode(com.lumira.saas.modules.system.vo.SystemVO.MenuVO menu) {
        MenuNodeDTO dto = new MenuNodeDTO();
        dto.setId(menu.getId());
        dto.setParentId(menu.getParentId());
        dto.setMenuCode(menu.getMenuCode());
        dto.setName(menu.getMenuName());
        dto.setPath(menu.getPath());
        dto.setComponent(menu.getComponent());
        dto.setIcon(menu.getIcon());
        dto.setPermissionKey(menu.getPermissionKey());
        dto.setSortNo(menu.getSortNo());
        dto.setChildren(menu.getChildren() == null ? List.of() : menu.getChildren().stream().map(this::toMenuNode).toList());
        return dto;
    }

    private record AuditSubject(Long userId, String userUuid, String username) {
    }
}
