package com.lumira.saas.modules.system.controller;

import com.lumira.api.auth.LoginCodeChallengeDTO;
import com.lumira.api.auth.LoginCodeCompleteRequest;
import com.lumira.api.auth.LoginResponseDTO;
import com.lumira.api.auth.SecondFactorCompleteRequest;
import com.lumira.api.system.CaptchaValidationRequestDTO;
import com.lumira.api.system.LoginAuditRecordRequestDTO;
import com.lumira.api.system.LoginCapabilitiesDTO;
import com.lumira.api.system.MenuNodeDTO;
import com.lumira.api.system.OperationAuditRecordRequestDTO;
import com.lumira.api.system.PasskeyCredentialDTO;
import com.lumira.api.system.PasskeyCredentialSaveRequestDTO;
import com.lumira.api.system.PasskeyCredentialUsageRequestDTO;
import com.lumira.api.system.PasskeySettingsDTO;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.PluginPermissionRegistrationRequestDTO;
import com.lumira.api.system.SecuritySettingsDTO;
import com.lumira.api.system.SystemRoleSnapshotDTO;
import com.lumira.api.system.SystemUserContactSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.api.system.VerificationChallengeDTO;
import com.lumira.api.system.VerificationProviderDTO;
import com.lumira.api.system.VerificationVerificationDTO;
import com.lumira.api.system.WechatLoginSettingsDTO;
import com.lumira.api.system.WechatLoginUserRequestDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.infrastructure.readmodel.ReadModelVersionService;
import com.lumira.saas.modules.system.verification.WechatLoginSettingsService;
import com.lumira.saas.infrastructure.security.service.SecuritySettingsService;
import com.lumira.saas.infrastructure.security.service.CaptchaService;
import com.lumira.saas.modules.audit.app.LoginAuditService;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.iam.service.IamUserService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.passkey.PasskeyCredentialAppService;
import com.lumira.saas.modules.system.user.support.UserUidGenerator;
import com.lumira.saas.modules.system.verification.SystemVerificationAppService;
import com.lumira.saas.modules.user.domain.UserDomainService;
import com.lumira.saas.modules.user.entity.SysUserEntity;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import jakarta.validation.Valid;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.SqlRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/system")
public class InternalSystemController implements com.lumira.api.client.SystemInternalApi {

    private static final Logger log = LoggerFactory.getLogger(InternalSystemController.class);

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
    private final OperationAuditService operationAuditService;
    private final SecuritySettingsService securitySettingsService;
    private final ReadModelVersionService readModelVersionService;

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
            OperationAuditService operationAuditService,
            SecuritySettingsService securitySettingsService,
            ReadModelVersionService readModelVersionService
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
        this.operationAuditService = operationAuditService;
        this.securitySettingsService = securitySettingsService;
        this.readModelVersionService = readModelVersionService;
    }

    @GetMapping("/users/login/{account}")
    public SystemUserSnapshotDTO findLoginUser(@PathVariable("account") String account) {
        return userDomainService.findLoginUser(account).map(this::toSnapshot).orElse(null);
    }

    @GetMapping("/users/{id}")
    public SystemUserSnapshotDTO findUserById(@PathVariable("id") Long id) {
        return userDomainService.findById(id).map(this::toSnapshot).orElse(null);
    }

    @GetMapping("/users")
    public List<SystemUserSnapshotDTO> usersByIds(
            @RequestParam("ids") List<Long> userIds
    ) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        List<Long> normalizedIds = userIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .limit(200)
                .toList();
        if (normalizedIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", normalizedIds.stream().map(ignored -> "?").toList());
        List<Object> params = new java.util.ArrayList<>();
        params.addAll(normalizedIds);
        return jdbcTemplate.query(
                """
                        select u.id,
                               u.uuid,
                               u.username,
                               u.password_hash,
                               u.status,
                               u.mobile,
                               u.email,
                               u.nickname,
                               u.real_name,
                               u.avatar_url,
                               u.birth_month,
                               u.gender,
                               u.region,
                               u.available_time,
                               u.id_card_number
                        from sys_user u
                        where u.deleted = 0
                          and u.id in (
                        """ + placeholders + """
                          )
                        order by u.id asc
                        """,
                (rs, rowNum) -> new SystemUserSnapshotDTO(
                        rs.getLong("id"),
                        rs.getString("uuid"),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("status"),
                        rs.getString("mobile"),
                        rs.getString("email"),
                        rs.getString("nickname"),
                        rs.getString("real_name"),
                        rs.getString("avatar_url"),
                        rs.getString("birth_month"),
                        rs.getString("gender"),
                        rs.getString("region"),
                        rs.getString("available_time"),
                        rs.getString("id_card_number"),
                        null
                ),
                params.toArray()
        );
    }

    @GetMapping("/roles")
    public List<SystemRoleSnapshotDTO> rolesByIds(
            @RequestParam("ids") List<Long> roleIds
    ) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        List<Long> normalizedIds = roleIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .limit(200)
                .toList();
        if (normalizedIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", normalizedIds.stream().map(ignored -> "?").toList());
        List<Object> params = new java.util.ArrayList<>();
        params.addAll(normalizedIds);
        return jdbcTemplate.query(
                """
                        select id, role_code, role_name
                        from sys_role
                        where deleted = 0
                          and id in (
                        """ + placeholders + """
                          )
                        order by id asc
                        """,
                (rs, rowNum) -> new SystemRoleSnapshotDTO(
                        rs.getLong("id"),
                        rs.getString("role_code"),
                        rs.getString("role_name")
                ),
                params.toArray()
        );
    }

    @GetMapping("/roles/{roleId}/users")
    public List<Long> userIdsByRole(@PathVariable("roleId") Long roleId) {
        if (roleId == null) {
            return List.of();
        }
        return jdbcTemplate.queryForList(
                """
                        select distinct user_id
                        from sys_user_role
                        where role_id = ?
                          and deleted = 0
                        order by user_id asc
                        """,
                Long.class,
                roleId
        );
    }

    @GetMapping("/users/contacts")
    public List<SystemUserContactSnapshotDTO> userContactsByIds(
            @RequestParam("ids") List<Long> userIds
    ) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        List<Long> normalizedIds = userIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .limit(200)
                .toList();
        if (normalizedIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", normalizedIds.stream().map(ignored -> "?").toList());
        List<Object> params = new java.util.ArrayList<>();
        params.addAll(normalizedIds);
        return jdbcTemplate.query(
                """
                        select u.id as user_id, u.username, u.email, wb.openid as wechat_openid
                        from sys_user u
                        left join sys_user_wechat_binding wb
                          on wb.user_id = u.id
                         and wb.deleted = 0
                        where u.deleted = 0
                          and u.status = 'ENABLED'
                          and u.id in (
                        """ + placeholders + """
                          )
                        order by u.id asc
                        """,
                this::toContactSnapshot,
                params.toArray()
        );
    }

    @GetMapping("/roles/{roleId}/user-contacts")
    public List<SystemUserContactSnapshotDTO> userContactsByRole(
            @PathVariable("roleId") Long roleId
    ) {
        if (roleId == null) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                        select distinct u.id as user_id, u.username, u.email, wb.openid as wechat_openid
                        from sys_user u
                        join sys_user_role ur
                          on ur.user_id = u.id
                         and ur.role_id = ?
                         and ur.deleted = 0
                        left join sys_user_wechat_binding wb
                          on wb.user_id = u.id
                         and wb.deleted = 0
                        where u.deleted = 0
                          and u.status = 'ENABLED'
                        order by u.id asc
                        """,
                this::toContactSnapshot,
                roleId
        );
    }

    @Override
    public List<SystemUserContactSnapshotDTO> platformUserContacts() {
        return jdbcTemplate.query(
                """
                        select distinct u.id as user_id, u.username, u.email, wb.openid as wechat_openid
                        from sys_user u
                        left join sys_user_wechat_binding wb
                          on wb.user_id = u.id
                         and wb.deleted = 0
                        where u.deleted = 0
                          and u.status = 'ENABLED'
                        order by u.id asc
                        """,
                this::toContactSnapshot
        );
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
    public PermissionSnapshotDTO permissionSnapshot(@RequestParam("userId") Long userId) {
        PermissionSnapshotService.PermissionSnapshot snapshot = permissionSnapshotService.loadSnapshot(userId);
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
        permissionSnapshotService.invalidatePermissions();
        return Boolean.TRUE;
    }

    @PostMapping("/permissions/plugin")
    @Transactional
    public Boolean registerPluginPermissions(@Valid @RequestBody PluginPermissionRegistrationRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.pluginCode())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "插件权限注册参数不完整");
        }
        if (request.permissions() == null || request.permissions().isEmpty()) {
            return Boolean.TRUE;
        }
        for (PluginPermissionRegistrationRequestDTO.Permission permission : request.permissions()) {
            if (permission == null || !StringUtils.hasText(permission.permissionKey())) {
                continue;
            }
            jdbcTemplate.update(
                    """
                            insert into sys_permission (
                                permission_key, permission_name, permission_group, source_type,
                                plugin_code, created_by, updated_by, deleted
                            ) values (?, ?, ?, 'PLUGIN', ?, 0, 0, 0)
                            on duplicate key update
                                permission_name = values(permission_name),
                                permission_group = values(permission_group),
                                plugin_code = values(plugin_code),
                                updated_at = current_timestamp,
                                deleted = 0
                            """,
                    permission.permissionKey(),
                    StringUtils.hasText(permission.permissionName()) ? permission.permissionName() : permission.permissionKey(),
                    StringUtils.hasText(permission.permissionGroup()) ? permission.permissionGroup() : request.pluginCode(),
                    request.pluginCode()
            );
        }
        List<Long> adminRoleIds = jdbcTemplate.queryForList(
                """
                        select id
                        from sys_role
                        where role_code = 'ADMIN'
                          and deleted = 0
                        """,
                Long.class
        );
        for (Long roleId : adminRoleIds) {
            for (PluginPermissionRegistrationRequestDTO.Permission permission : request.permissions()) {
                if (permission == null || !StringUtils.hasText(permission.permissionKey())) {
                    continue;
                }
                jdbcTemplate.update(
                        """
                                insert into sys_role_permission (
                                    role_id, permission_key, created_by, updated_by, deleted
                                ) values (?, ?, 0, 0, 0)
                                on duplicate key update
                                    updated_at = current_timestamp,
                                    deleted = 0
                                """,
                        roleId,
                        permission.permissionKey()
                );
            }
        }
        permissionSnapshotService.invalidatePermissions();
        return Boolean.TRUE;
    }

    @PostMapping("/read-model-version/bump")
    public Boolean bumpReadModelVersion(
            @RequestParam("contextName") String contextName,
            @RequestParam("scope") String scope,
            @RequestParam(value = "eventKey", required = false) String eventKey
    ) {
        readModelVersionService.bump(contextName, scope, eventKey);
        return Boolean.TRUE;
    }

    @GetMapping("/read-model-version")
    public Long readModelVersion(
            @RequestParam("contextName") String contextName,
            @RequestParam("scope") String scope
    ) {
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
        captchaService.validateCaptcha(request.captchaId(), request.captchaCode(), request.captchaProof());
        return Boolean.TRUE;
    }

    @PostMapping("/audit/login")
    public Boolean recordLoginAudit(@RequestBody LoginAuditRecordRequestDTO request) {
        loginAuditService.log(
                request.userId(),
                request.userUuid(),
                request.username(),
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
        operationAuditService.log(
                request.userId(),
                request.userUuid(),
                request.username(),
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

    @GetMapping("/config/platform-values")
    public Map<String, String> platformConfigValues(
            @RequestParam("keys") List<String> keys
    ) {
        if (keys == null || keys.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", keys.stream().map(ignored -> "?").toList());
        List<Object> params = new java.util.ArrayList<>();
        params.addAll(keys);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                        select config_key, config_value
                        from sys_config
                        where config_scope = 'PLATFORM'
                          and deleted = 0
                          and config_key in (
                        """ + placeholders + """
                          )
                        order by id desc
                        """,
                params.toArray()
        );
        Map<String, String> values = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String key = row.get("config_key") == null ? null : String.valueOf(row.get("config_key"));
            String value = row.get("config_value") == null ? null : String.valueOf(row.get("config_value"));
            if (StringUtils.hasText(key) && value != null && !values.containsKey(key)) {
                values.put(key, value);
            }
        }
        return values;
    }

    @GetMapping("/verification/wechat-settings")
    public WechatLoginSettingsDTO wechatLoginSettings() {
        return wechatLoginSettingsService.getInternalSettings();
    }

    @GetMapping("/verification/passkey-settings")
    public PasskeySettingsDTO passkeySettings() {
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

    @GetMapping("/passkeys/credential")
    public PasskeyCredentialDTO passkeyCredentialByCredentialId(@RequestParam("credentialId") String credentialId) {
        return passkeyCredentialAppService.findByCredentialId(credentialId);
    }

    @GetMapping("/passkeys")
    public List<PasskeyCredentialDTO> passkeyCredentials(@RequestParam("userId") Long userId) {
        return passkeyCredentialAppService.list(userId);
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
            @RequestParam("userId") Long userId,
            @RequestParam("label") String label
    ) {
        return passkeyCredentialAppService.rename(id, userId, label);
    }

    @PostMapping("/passkeys/{id}/delete")
    public Boolean deletePasskeyCredential(@PathVariable("id") Long id, @RequestParam("userId") Long userId) {
        return passkeyCredentialAppService.delete(id, userId);
    }

    @GetMapping("/verification/providers")
    public List<VerificationProviderDTO> listVerificationProviders(@RequestParam("userId") Long userId) {
        return verificationAppService.listProviders(userId).stream().map(this::toProvider).toList();
    }

    @GetMapping("/verification/login-options")
    public List<LoginResponseDTO.SecondFactorOptionDTO> listLoginSecondFactorOptions(@RequestParam("userId") Long userId) {
        return userDomainService.findById(userId)
                .map(user -> verificationAppService.listLoginOptions(user).stream().map(this::toSecondFactorOption).toList())
                .orElseGet(List::of);
    }

    @GetMapping("/verification/providers/{factorCode}")
    public VerificationProviderDTO verificationProvider(
            @RequestParam("userId") Long userId,
            @PathVariable("factorCode") String factorCode
    ) {
        return toProvider(verificationAppService.provider(userId, factorCode));
    }

    @PostMapping("/verification/providers/{factorCode}/bind")
    public VerificationChallengeDTO bindVerificationProvider(
            @RequestParam("userId") Long userId,
            @PathVariable("factorCode") String factorCode
    ) {
        return toChallenge(verificationAppService.bind(userId, factorCode));
    }

    @PostMapping("/verification/providers/{factorCode}/unbind")
    public Boolean unbindVerificationProvider(
            @RequestParam("userId") Long userId,
            @PathVariable("factorCode") String factorCode
    ) {
        return verificationAppService.unbind(userId, factorCode);
    }

    @PostMapping("/verification/providers/{factorCode}/challenge")
    public VerificationChallengeDTO verificationChallenge(
            @RequestParam("userId") Long userId,
            @PathVariable("factorCode") String factorCode
    ) {
        return toChallenge(verificationAppService.challenge(userId, factorCode));
    }

    @PostMapping("/verification/providers/{factorCode}/verify")
    public VerificationVerificationDTO verificationVerify(
            @RequestParam("userId") Long userId,
            @PathVariable("factorCode") String factorCode,
            @RequestParam("challengeId") String challengeId,
            @RequestParam("verificationCode") String verificationCode
    ) {
        return toVerification(verificationAppService.completeBind(userId, factorCode, challengeId, verificationCode), factorCode);
    }

    @PostMapping("/verification/login-code/challenge")
    @Transactional
    public LoginCodeChallengeDTO loginCodeChallenge(
            @RequestParam("account") String account,
            @RequestParam("loginType") String loginType
    ) {
        SysUserEntity user = userDomainService.findLoginUser(account)
                .orElseGet(() -> registerLoginCodeUser(account, loginType));
        var challenge = verificationAppService.startLoginCodeChallenge(user, loginType);
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
        com.lumira.saas.modules.auth.dto.LoginCodeCompleteRequest backendRequest = new com.lumira.saas.modules.auth.dto.LoginCodeCompleteRequest();
        backendRequest.setChallengeId(request.challengeId());
        backendRequest.setVerificationCode(request.verificationCode());
        return toVerification(verificationAppService.completeLoginCodeLogin(backendRequest), null);
    }

    @PostMapping("/verification/second-factor/complete")
    public VerificationVerificationDTO completeSecondFactorLogin(@Valid @RequestBody SecondFactorCompleteRequest request) {
        com.lumira.saas.modules.auth.dto.SecondFactorCompleteRequest backendRequest = new com.lumira.saas.modules.auth.dto.SecondFactorCompleteRequest();
        backendRequest.setFactorCode(request.factorCode());
        backendRequest.setChallengeId(request.challengeId());
        backendRequest.setVerificationCode(request.verificationCode());
        return toVerification(verificationAppService.completeSecondFactorLogin(backendRequest, null, null), request.factorCode());
    }

    @GetMapping("/menus/builtin")
    public List<MenuNodeDTO> builtinMenus() {
        return listSystemMenusFromDatabase().stream().map(this::toMenuNode).toList();
    }

    private List<com.lumira.saas.modules.system.vo.SystemVO.MenuVO> listSystemMenusFromDatabase() {
        List<com.lumira.saas.modules.system.vo.SystemVO.MenuVO> menus = jdbcTemplate.query(
                """
                        select id, parent_id as parentId, menu_code as menuCode,
                               menu_name as menuName, menu_type as menuType, path, component, icon, sort_no as sortNo,
                               permission_key as permissionKey, status
                        from sys_menu
                        where deleted = 0 and status = 'ENABLED'
                        order by sort_no asc, id asc
                        """,
                new BeanPropertyRowMapper<>(com.lumira.saas.modules.system.vo.SystemVO.MenuVO.class)
        ).stream()
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

    private SystemUserSnapshotDTO toSnapshot(SysUserEntity user) {
        return new SystemUserSnapshotDTO(
                user.getId(),
                user.getUuid(),
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

    private SystemUserContactSnapshotDTO toContactSnapshot(SqlRow row, int rowNum) {
        return new SystemUserContactSnapshotDTO(
                row.getLong("user_id"),
                row.getString("username"),
                row.getString("email"),
                row.getString("wechat_openid")
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
                            uuid, username, password_hash, mobile, nickname, real_name, avatar_url, email, birth_month, gender, region,
                            available_time, id_card_number, status,
                            created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                UserUidGenerator.nextNumericUid(),
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
        grantDefaultLoginRole(user.getId());
        permissionSnapshotService.invalidatePermissions();
        return user;
    }

    private SysUserEntity registerLoginCodeUser(String account, String loginType) {
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
                            uuid, username, password_hash, mobile, nickname, real_name, avatar_url, email, birth_month, gender, region,
                            available_time, id_card_number, status,
                            created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ENABLED', 0, 0, 0)
                        """,
                UserUidGenerator.nextNumericUid(),
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
        grantDefaultLoginRole(user.getId());
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

    private void grantDefaultLoginRole(Long userId) {
        String roleCode = resolveDefaultRegistrationRoleCode();
        Long roleId = jdbcTemplate.query(
                """
                        select id
                        from sys_role
                        where role_code = ? and deleted = 0
                        order by id desc
                        limit 1
                        """,
                rs -> rs.next() ? rs.getLong("id") : null,
                roleCode
        );
        if (roleId == null) {
            roleId = jdbcTemplate.query(
                    """
                            select id
                            from sys_role
                            where role_code = ? and deleted = 0
                            order by id desc
                            limit 1
                            """,
                    rs -> rs.next() ? rs.getLong("id") : null,
                    DEFAULT_REGISTRATION_ROLE_CODE
            );
        }
        if (roleId == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "默认注册角色不存在，请先创建可用角色");
        }
        jdbcTemplate.update(
                """
                        insert into sys_user_role (user_id, role_id, created_by, updated_by, deleted)
                        values (?, ?, 0, 0, 0)
                        on duplicate key update updated_by = 0, updated_at = current_timestamp, deleted = 0
                        """,
                userId,
                roleId
        );
    }

    private String resolveDefaultRegistrationRoleCode() {
        String roleCode = jdbcTemplate.query(
                """
                        select config_value
                        from sys_config
                        where deleted = 0
                          and config_scope = 'PLATFORM'
                          and config_key = ?
                        order by id desc
                        limit 1
                        """,
                rs -> rs.next() ? rs.getString("config_value") : null,
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
        dto.setDebugCode(challenge.getDebugCode());
        return dto;
    }

    private VerificationVerificationDTO toVerification(com.lumira.saas.modules.system.vo.SystemVO.VerificationVerificationVO verification, String factorCode) {
        return new VerificationVerificationDTO(
                Boolean.TRUE.equals(verification.getVerified()),
                verification.getMessage(),
                verification.getUserId(),
                factorCode
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
}
