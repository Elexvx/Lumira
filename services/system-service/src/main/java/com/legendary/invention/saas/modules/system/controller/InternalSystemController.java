package com.legendary.invention.saas.modules.system.controller;

import com.legendary.invention.api.auth.LoginCodeChallengeDTO;
import com.legendary.invention.api.auth.LoginCodeCompleteRequest;
import com.legendary.invention.api.auth.LoginResponseDTO;
import com.legendary.invention.api.auth.SecondFactorCompleteRequest;
import com.legendary.invention.api.system.CaptchaValidationRequestDTO;
import com.legendary.invention.api.system.LoginAuditRecordRequestDTO;
import com.legendary.invention.api.system.LoginCapabilitiesDTO;
import com.legendary.invention.api.system.MenuNodeDTO;
import com.legendary.invention.api.system.PasskeyCredentialDTO;
import com.legendary.invention.api.system.PasskeyCredentialSaveRequestDTO;
import com.legendary.invention.api.system.PasskeyCredentialUsageRequestDTO;
import com.legendary.invention.api.system.PasskeySettingsDTO;
import com.legendary.invention.api.system.PermissionSnapshotDTO;
import com.legendary.invention.api.system.SecuritySettingsDTO;
import com.legendary.invention.api.system.SystemUserSnapshotDTO;
import com.legendary.invention.api.system.VerificationChallengeDTO;
import com.legendary.invention.api.system.VerificationProviderDTO;
import com.legendary.invention.api.system.VerificationVerificationDTO;
import com.legendary.invention.api.system.WechatLoginSettingsDTO;
import com.legendary.invention.api.system.WechatLoginUserRequestDTO;
import com.legendary.invention.saas.common.enums.ErrorCode;
import com.legendary.invention.saas.common.exception.BizException;
import com.legendary.invention.saas.modules.system.verification.WechatLoginSettingsService;
import com.legendary.invention.saas.infrastructure.security.service.SecuritySettingsService;
import com.legendary.invention.saas.modules.system.app.SystemRouteCatalog;
import com.legendary.invention.saas.infrastructure.security.service.CaptchaService;
import com.legendary.invention.saas.modules.audit.app.LoginAuditService;
import com.legendary.invention.saas.modules.iam.service.IamUserService;
import com.legendary.invention.saas.modules.iam.service.PermissionSnapshotService;
import com.legendary.invention.saas.modules.system.passkey.PasskeyCredentialAppService;
import com.legendary.invention.saas.modules.system.verification.SystemVerificationAppService;
import com.legendary.invention.saas.modules.user.domain.UserDomainService;
import com.legendary.invention.saas.modules.user.entity.SysUserEntity;
import jakarta.validation.Valid;
import com.legendary.invention.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/internal/system")
public class InternalSystemController {

    private static final String DEFAULT_REGISTRATION_ROLE_CODE_KEY = "auth.default-registration-role-code";
    private static final String DEFAULT_REGISTRATION_ROLE_CODE = "commonuser";
    private static final String FACTOR_SMS = "sms";
    private static final String FACTOR_EMAIL = "email";

    private final UserDomainService userDomainService;
    private final IamUserService iamUserService;
    private final PermissionSnapshotService permissionSnapshotService;
    private final CaptchaService captchaService;
    private final SystemVerificationAppService verificationAppService;
    private final WechatLoginSettingsService wechatLoginSettingsService;
    private final PasskeyCredentialAppService passkeyCredentialAppService;
    private final MyBatisQueryOperations jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final LoginAuditService loginAuditService;
    private final SecuritySettingsService securitySettingsService;

    public InternalSystemController(
            UserDomainService userDomainService,
            IamUserService iamUserService,
            PermissionSnapshotService permissionSnapshotService,
            CaptchaService captchaService,
            SystemVerificationAppService verificationAppService,
            WechatLoginSettingsService wechatLoginSettingsService,
            PasskeyCredentialAppService passkeyCredentialAppService,
            MyBatisQueryOperations jdbcTemplate,
            PasswordEncoder passwordEncoder,
            LoginAuditService loginAuditService,
            SecuritySettingsService securitySettingsService
    ) {
        this.userDomainService = userDomainService;
        this.iamUserService = iamUserService;
        this.permissionSnapshotService = permissionSnapshotService;
        this.captchaService = captchaService;
        this.verificationAppService = verificationAppService;
        this.wechatLoginSettingsService = wechatLoginSettingsService;
        this.passkeyCredentialAppService = passkeyCredentialAppService;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.loginAuditService = loginAuditService;
        this.securitySettingsService = securitySettingsService;
    }

    @GetMapping("/users/login/{account}")
    public SystemUserSnapshotDTO findLoginUser(@PathVariable("account") String account) {
        return userDomainService.findLoginUser(account).map(this::toSnapshot).orElse(null);
    }

    @GetMapping("/users/{id}")
    public SystemUserSnapshotDTO findUserById(@PathVariable("id") Long id) {
        return userDomainService.findById(id).map(this::toSnapshot).orElse(null);
    }

    @PostMapping("/users/wechat-login")
    public SystemUserSnapshotDTO resolveWechatLoginUser(@RequestBody WechatLoginUserRequestDTO request) {
        SysUserEntity user = findWechatBoundUser(request.unionid(), request.openid());
        if (user == null) {
            user = registerWechatUser(request);
        }
        upsertWechatBinding(user.getId(), request);
        return toSnapshot(user);
    }

    @GetMapping("/permissions/snapshot")
    public PermissionSnapshotDTO permissionSnapshot(@RequestParam("tenantId") Long tenantId, @RequestParam("userId") Long userId) {
        PermissionSnapshotService.PermissionSnapshot snapshot = permissionSnapshotService.loadSnapshot(tenantId, userId);
        return new PermissionSnapshotDTO(
                snapshot.getVersion(),
                snapshot.getPermissionList(),
                snapshot.getRoleIds().stream().toList(),
                snapshot.getPrimaryDeptId(),
                snapshot.getDeptIds().stream().toList(),
                snapshot.getDescendantDeptIds().stream().toList(),
                snapshot.getDataScopes()
        );
    }

    @PostMapping("/permissions/invalidate")
    public Boolean invalidatePermissionSnapshot(@RequestParam("tenantId") Long tenantId) {
        permissionSnapshotService.invalidateTenant(tenantId);
        return Boolean.TRUE;
    }

    @PostMapping("/captcha/validate")
    public Boolean validateCaptcha(@Valid @RequestBody CaptchaValidationRequestDTO request) {
        captchaService.validateCaptcha(request.captchaId(), request.captchaCode(), request.captchaProof());
        return Boolean.TRUE;
    }

    @PostMapping("/audit/login")
    public Boolean recordLoginAudit(@RequestBody LoginAuditRecordRequestDTO request) {
        loginAuditService.log(
                request.userId(),
                request.tenantId(),
                request.username(),
                request.loginType(),
                request.loginResult(),
                request.failReason(),
                request.loginIp(),
                request.userAgent()
        );
        return Boolean.TRUE;
    }

    @GetMapping("/verification/login-capabilities")
    public LoginCapabilitiesDTO loginCapabilities(@RequestParam("tenantId") Long tenantId) {
        var capabilities = verificationAppService.loadLoginCapabilities(tenantId);
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
    public SecuritySettingsDTO securitySettings(@RequestParam("tenantId") Long tenantId) {
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

    @GetMapping("/verification/wechat-settings")
    public WechatLoginSettingsDTO wechatLoginSettings(@RequestParam("tenantId") Long tenantId) {
        return wechatLoginSettingsService.getInternalSettings(tenantId);
    }

    @GetMapping("/verification/passkey-settings")
    public PasskeySettingsDTO passkeySettings(@RequestParam("tenantId") Long tenantId) {
        var settings = verificationAppService.getPasskeySettings(tenantId);
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

    @GetMapping("/passkeys/credential")
    public PasskeyCredentialDTO passkeyCredentialByCredentialId(@RequestParam("credentialId") String credentialId) {
        return passkeyCredentialAppService.findByCredentialId(credentialId);
    }

    @GetMapping("/passkeys")
    public List<PasskeyCredentialDTO> passkeyCredentials(@RequestParam("tenantId") Long tenantId, @RequestParam("userId") Long userId) {
        return passkeyCredentialAppService.list(tenantId, userId);
    }

    @PostMapping("/passkeys")
    public PasskeyCredentialDTO savePasskeyCredential(@RequestBody PasskeyCredentialSaveRequestDTO request) {
        return passkeyCredentialAppService.create(request);
    }

    @PostMapping("/passkeys/usage")
    public Boolean updatePasskeyCredentialUsage(@RequestBody PasskeyCredentialUsageRequestDTO request) {
        return passkeyCredentialAppService.updateUsage(request);
    }

    @PostMapping("/passkeys/{id}/label")
    public PasskeyCredentialDTO renamePasskeyCredential(
            @PathVariable("id") Long id,
            @RequestParam("tenantId") Long tenantId,
            @RequestParam("userId") Long userId,
            @RequestParam("label") String label
    ) {
        return passkeyCredentialAppService.rename(id, tenantId, userId, label);
    }

    @PostMapping("/passkeys/{id}/delete")
    public Boolean deletePasskeyCredential(@PathVariable("id") Long id, @RequestParam("tenantId") Long tenantId, @RequestParam("userId") Long userId) {
        return passkeyCredentialAppService.delete(id, tenantId, userId);
    }

    @GetMapping("/verification/providers")
    public List<VerificationProviderDTO> listVerificationProviders(@RequestParam("tenantId") Long tenantId, @RequestParam("userId") Long userId) {
        return verificationAppService.listProviders(tenantId, userId).stream().map(this::toProvider).toList();
    }

    @GetMapping("/verification/login-options")
    public List<LoginResponseDTO.SecondFactorOptionDTO> listLoginSecondFactorOptions(@RequestParam("tenantId") Long tenantId, @RequestParam("userId") Long userId) {
        return userDomainService.findById(userId)
                .map(user -> verificationAppService.listLoginOptions(user, tenantId).stream().map(this::toSecondFactorOption).toList())
                .orElseGet(List::of);
    }

    @GetMapping("/verification/providers/{factorCode}")
    public VerificationProviderDTO verificationProvider(
            @RequestParam("tenantId") Long tenantId,
            @RequestParam("userId") Long userId,
            @PathVariable("factorCode") String factorCode
    ) {
        return toProvider(verificationAppService.provider(tenantId, userId, factorCode));
    }

    @PostMapping("/verification/providers/{factorCode}/bind")
    public VerificationChallengeDTO bindVerificationProvider(
            @RequestParam("tenantId") Long tenantId,
            @RequestParam("userId") Long userId,
            @PathVariable("factorCode") String factorCode
    ) {
        return toChallenge(verificationAppService.bind(tenantId, userId, factorCode));
    }

    @PostMapping("/verification/providers/{factorCode}/unbind")
    public Boolean unbindVerificationProvider(
            @RequestParam("tenantId") Long tenantId,
            @RequestParam("userId") Long userId,
            @PathVariable("factorCode") String factorCode
    ) {
        return verificationAppService.unbind(tenantId, userId, factorCode);
    }

    @PostMapping("/verification/providers/{factorCode}/challenge")
    public VerificationChallengeDTO verificationChallenge(
            @RequestParam("tenantId") Long tenantId,
            @RequestParam("userId") Long userId,
            @PathVariable("factorCode") String factorCode
    ) {
        return toChallenge(verificationAppService.challenge(tenantId, userId, factorCode));
    }

    @PostMapping("/verification/providers/{factorCode}/verify")
    public VerificationVerificationDTO verificationVerify(
            @RequestParam("tenantId") Long tenantId,
            @RequestParam("userId") Long userId,
            @PathVariable("factorCode") String factorCode,
            @RequestParam("challengeId") String challengeId,
            @RequestParam("verificationCode") String verificationCode
    ) {
        return toVerification(verificationAppService.completeBind(tenantId, userId, factorCode, challengeId, verificationCode), factorCode);
    }

    @PostMapping("/verification/login-code/challenge")
    @Transactional
    public LoginCodeChallengeDTO loginCodeChallenge(
            @RequestParam("tenantId") Long tenantId,
            @RequestParam("account") String account,
            @RequestParam("loginType") String loginType
    ) {
        SysUserEntity user = userDomainService.findLoginUser(account)
                .orElseGet(() -> registerLoginCodeUser(tenantId, account, loginType));
        var challenge = verificationAppService.startLoginCodeChallenge(user, tenantId, loginType);
        LoginCodeChallengeDTO dto = new LoginCodeChallengeDTO();
        dto.setLoginType(challenge.getLoginType());
        dto.setFactorName(challenge.getFactorName());
        dto.setChallengeId(challenge.getChallengeId());
        dto.setMaskedContact(challenge.getMaskedContact());
        dto.setPromptMessage(challenge.getPromptMessage());
        dto.setExpiresInSeconds(challenge.getExpiresInSeconds());
        dto.setCooldownSeconds(challenge.getCooldownSeconds());
        dto.setDebugCode(challenge.getDebugCode());
        return dto;
    }

    @PostMapping("/verification/login-code/complete")
    public VerificationVerificationDTO completeLoginCodeLogin(@Valid @RequestBody LoginCodeCompleteRequest request) {
        com.legendary.invention.saas.modules.auth.dto.LoginCodeCompleteRequest backendRequest = new com.legendary.invention.saas.modules.auth.dto.LoginCodeCompleteRequest();
        backendRequest.setChallengeId(request.challengeId());
        backendRequest.setVerificationCode(request.verificationCode());
        return toVerification(verificationAppService.completeLoginCodeLogin(backendRequest), null);
    }

    @PostMapping("/verification/second-factor/complete")
    public VerificationVerificationDTO completeSecondFactorLogin(@Valid @RequestBody SecondFactorCompleteRequest request) {
        com.legendary.invention.saas.modules.auth.dto.SecondFactorCompleteRequest backendRequest = new com.legendary.invention.saas.modules.auth.dto.SecondFactorCompleteRequest();
        backendRequest.setFactorCode(request.factorCode());
        backendRequest.setChallengeId(request.challengeId());
        backendRequest.setVerificationCode(request.verificationCode());
        return toVerification(verificationAppService.completeSecondFactorLogin(backendRequest, null, null), request.factorCode());
    }

    @GetMapping("/menus/builtin")
    public List<MenuNodeDTO> builtinMenus() {
        return SystemRouteCatalog.buildBuiltinPermissionMenus().stream().map(this::toMenuNode).toList();
    }

    private SystemUserSnapshotDTO toSnapshot(SysUserEntity user) {
        return new SystemUserSnapshotDTO(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
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

    private SysUserEntity findWechatBoundUser(String unionid, String openid) {
        if (!StringUtils.hasText(openid)) {
            throw new IllegalArgumentException("微信 openid 不能为空");
        }
        String normalizedUnionid = StringUtils.hasText(unionid) ? unionid.trim() : "";
        List<Long> userIds = jdbcTemplate.query(
                """
                        select u.id
                        from sys_user_wechat_binding b
                        join sys_user u on u.id = b.user_id and u.deleted = 0
                        where b.deleted = 0
                          and ((? <> '' and b.unionid = ?) or b.openid = ?)
                        order by case when ? <> '' and b.unionid = ? then 0 else 1 end, b.id desc
                        limit 1
                        """,
                (rs, rowNum) -> rs.getLong("id"),
                normalizedUnionid,
                normalizedUnionid,
                openid,
                normalizedUnionid,
                normalizedUnionid
        );
        return userIds.isEmpty() ? null : userDomainService.findById(userIds.get(0)).orElse(null);
    }

    private SysUserEntity registerWechatUser(WechatLoginUserRequestDTO request) {
        String username = nextWechatUsername(request);
        jdbcTemplate.update(
                """
                        insert into sys_user (
                            username, password_hash, mobile, nickname, real_name, avatar_url, email, birth_month, gender, region,
                            available_time, id_card_number, status,
                            created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                username,
                passwordEncoder.encode(UUID.randomUUID().toString()),
                null,
                "微信用户",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "ENABLED",
                0L,
                0L
        );
        SysUserEntity user = userDomainService.findLoginUser(username)
                .orElseThrow(() -> new IllegalStateException("微信登录自动注册用户失败"));
        upsertUserTenantRelation(user.getId(), com.legendary.invention.common.constant.PlatformConstants.PLATFORM_TENANT_ID);
        grantDefaultLoginRole(user.getId(), com.legendary.invention.common.constant.PlatformConstants.PLATFORM_TENANT_ID);
        permissionSnapshotService.invalidateTenant(com.legendary.invention.common.constant.PlatformConstants.PLATFORM_TENANT_ID);
        return user;
    }

    private SysUserEntity registerLoginCodeUser(Long tenantId, String account, String loginType) {
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
        jdbcTemplate.update(
                """
                        insert into sys_user (
                            username, password_hash, mobile, nickname, real_name, avatar_url, email, birth_month, gender, region,
                            available_time, id_card_number, status,
                            created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ENABLED', 0, 0, 0)
                        """,
                username,
                randomPassword,
                FACTOR_SMS.equals(normalizedLoginType) ? normalizedAccount : null,
                nickname,
                null,
                null,
                FACTOR_EMAIL.equals(normalizedLoginType) ? normalizedAccount : null,
                null,
                null,
                null,
                null,
                null
        );
        Long createdUserId = jdbcTemplate.queryForObject(
                "select id from sys_user where username = ? and deleted = 0 order by id desc limit 1",
                Long.class,
                username
        );
        SysUserEntity user = userDomainService.findById(createdUserId)
                .orElseThrow(() -> new IllegalStateException("验证码登录自动注册用户失败"));
        iamUserService.createUserWithIdentity(user, normalizedAccount, "LOGIN_CODE_REGISTER");
        iamUserService.recordUserRegistered(user.getId(), "LOGIN_CODE_REGISTER", null, null);
        upsertUserTenantRelation(user.getId(), tenantId);
        grantDefaultLoginRole(user.getId(), tenantId);
        permissionSnapshotService.invalidateTenant(tenantId);
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

    private void upsertWechatBinding(Long userId, WechatLoginUserRequestDTO request) {
        jdbcTemplate.update(
                """
                        insert into sys_user_wechat_binding (
                            user_id, openid, unionid, scope, created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, 0)
                        on duplicate key update user_id = values(user_id),
                                                unionid = values(unionid),
                                                scope = values(scope),
                                                updated_by = values(updated_by),
                                                updated_at = current_timestamp,
                                                deleted = 0
                        """,
                userId,
                request.openid(),
                StringUtils.hasText(request.unionid()) ? request.unionid() : null,
                request.scope(),
                0L,
                0L
        );
    }

    private void upsertUserTenantRelation(Long userId, Long tenantId) {
        jdbcTemplate.update(
                """
                        insert into sys_user_tenant (tenant_id, user_id, is_default, status, created_by, updated_by, deleted)
                        values (?, ?, 1, 'ENABLED', 0, 0, 0)
                        on duplicate key update is_default = 1,
                                                 status = 'ENABLED',
                                                 updated_by = 0,
                                                 updated_at = current_timestamp,
                                                 deleted = 0
                        """,
                tenantId,
                userId
        );
    }

    private void grantDefaultLoginRole(Long userId, Long tenantId) {
        String roleCode = resolveDefaultRegistrationRoleCode(tenantId);
        Long roleId = jdbcTemplate.query(
                """
                        select id
                        from sys_role
                        where tenant_id = ? and role_code = ? and deleted = 0
                        order by id desc
                        limit 1
                        """,
                rs -> rs.next() ? rs.getLong("id") : null,
                tenantId,
                roleCode
        );
        if (roleId == null) {
            roleId = jdbcTemplate.query(
                    """
                            select id
                            from sys_role
                            where tenant_id = ? and role_code = ? and deleted = 0
                            order by id desc
                            limit 1
                            """,
                    rs -> rs.next() ? rs.getLong("id") : null,
                    tenantId,
                    DEFAULT_REGISTRATION_ROLE_CODE
            );
        }
        if (roleId == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "默认注册角色不存在，请先创建可用角色");
        }
        jdbcTemplate.update(
                """
                        insert into sys_user_role (tenant_id, user_id, role_id, created_by, updated_by, deleted)
                        values (?, ?, ?, 0, 0, 0)
                        on duplicate key update updated_by = 0, updated_at = current_timestamp, deleted = 0
                        """,
                tenantId,
                userId,
                roleId
        );
    }

    private String resolveDefaultRegistrationRoleCode(Long tenantId) {
        String roleCode = jdbcTemplate.query(
                """
                        select config_value
                        from sys_config
                        where deleted = 0
                          and config_scope = 'PLATFORM'
                          and config_key = ?
                          and (tenant_id = ? or tenant_id is null)
                        order by case when tenant_id = ? then 0 else 1 end, id desc
                        limit 1
                        """,
                rs -> rs.next() ? rs.getString("config_value") : null,
                DEFAULT_REGISTRATION_ROLE_CODE_KEY,
                tenantId,
                tenantId
        );
        return StringUtils.hasText(roleCode) ? roleCode.trim() : DEFAULT_REGISTRATION_ROLE_CODE;
    }

    private LoginResponseDTO.SecondFactorOptionDTO toSecondFactorOption(com.legendary.invention.saas.modules.auth.vo.LoginResponseVO.SecondFactorOptionVO option) {
        LoginResponseDTO.SecondFactorOptionDTO dto = new LoginResponseDTO.SecondFactorOptionDTO();
        dto.setFactorCode(option.getFactorCode());
        dto.setFactorName(option.getFactorName());
        dto.setChallengeId(option.getChallengeId());
        dto.setMaskedContact(option.getMaskedContact());
        dto.setPromptMessage(option.getPromptMessage());
        return dto;
    }

    private VerificationProviderDTO toProvider(com.legendary.invention.saas.modules.system.vo.SystemVO.VerificationProviderVO provider) {
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

    private VerificationChallengeDTO toChallenge(com.legendary.invention.saas.modules.system.vo.SystemVO.VerificationChallengeVO challenge) {
        VerificationChallengeDTO dto = new VerificationChallengeDTO();
        dto.setFactorCode(challenge.getFactorCode());
        dto.setFactorName(challenge.getFactorName());
        dto.setChallengeId(challenge.getChallengeId());
        dto.setMaskedContact(challenge.getMaskedContact());
        dto.setPromptMessage(challenge.getPromptMessage());
        dto.setExpiresInSeconds(null);
        dto.setDebugCode(challenge.getDebugCode());
        return dto;
    }

    private VerificationVerificationDTO toVerification(com.legendary.invention.saas.modules.system.vo.SystemVO.VerificationVerificationVO verification, String factorCode) {
        return new VerificationVerificationDTO(
                Boolean.TRUE.equals(verification.getVerified()),
                verification.getMessage(),
                verification.getUserId(),
                verification.getTenantId(),
                factorCode
        );
    }

    private MenuNodeDTO toMenuNode(com.legendary.invention.saas.modules.system.vo.SystemVO.MenuVO menu) {
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
}
