package com.legendary.invention.saas.modules.system.app;

import com.legendary.invention.saas.common.enums.ErrorCode;
import com.legendary.invention.saas.common.exception.BizException;
import com.legendary.invention.saas.common.vo.PageResponse;
import com.legendary.invention.saas.infrastructure.security.CurrentUser;
import com.legendary.invention.saas.infrastructure.security.service.SecuritySettingsService;
import com.legendary.invention.saas.modules.audit.app.LoginAuditService;
import com.legendary.invention.saas.modules.audit.app.OperationAuditService;
import com.legendary.invention.saas.modules.auth.app.AuthAppService;
import com.legendary.invention.saas.modules.auth.vo.CurrentUserVO;
import com.legendary.invention.saas.modules.iam.service.PermissionSnapshotService;
import com.legendary.invention.saas.modules.plugin.app.PluginManagementAppService;
import com.legendary.invention.saas.modules.system.app.OnlineSessionManagementAppService;
import com.legendary.invention.saas.modules.system.dto.SystemDTO;
import com.legendary.invention.saas.modules.system.vo.SystemVO;
import com.legendary.invention.saas.modules.tenant.domain.TenantDomainService;
import com.legendary.invention.saas.modules.tenant.entity.TenantInfoEntity;
import com.legendary.invention.saas.modules.user.domain.UserDomainService;
import com.legendary.invention.saas.modules.user.entity.SysUserEntity;
import com.legendary.invention.saas.infrastructure.security.service.PasswordPolicyService;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

@Service
public class SystemManagementAppService {

    private static final Long DEFAULT_PUBLIC_TENANT_ID = 1001L;
    private static final Long DEFAULT_ADMIN_USER_ID = 1001L;
    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String BRANDING_WEBSITE_NAME_KEY = "branding.website-name";
    private static final String BRANDING_WEBSITE_FAVICON_URL_KEY = "branding.website-favicon-url";
    private static final String BRANDING_WEBSITE_LOGO_URL_KEY = "branding.website-logo-url";
    private static final String BRANDING_GITHUB_LINK_URL_KEY = "branding.github-link-url";
    private static final String BRANDING_HELP_LINK_URL_KEY = "branding.help-link-url";
    private static final String BRANDING_COMPANY_NAME_KEY = "branding.company-name";
    private static final String BRANDING_COPYRIGHT_START_YEAR_KEY = "branding.copyright-start-year";
    private static final String BRANDING_FOOTER_ICP_KEY = "branding.footer-icp";
    private static final String BRANDING_FOOTER_COPYRIGHT_KEY = "branding.footer-copyright";
    private static final List<String> BRANDING_CONFIG_KEYS = List.of(
            BRANDING_WEBSITE_NAME_KEY,
            BRANDING_WEBSITE_FAVICON_URL_KEY,
            BRANDING_WEBSITE_LOGO_URL_KEY,
            BRANDING_GITHUB_LINK_URL_KEY,
            BRANDING_HELP_LINK_URL_KEY,
            BRANDING_COMPANY_NAME_KEY,
            BRANDING_COPYRIGHT_START_YEAR_KEY,
            BRANDING_FOOTER_ICP_KEY,
            BRANDING_FOOTER_COPYRIGHT_KEY
    );

    private static final String AGREEMENT_USER_MARKDOWN_KEY = "agreement.user-agreement-markdown";
    private static final String AGREEMENT_PRIVACY_MARKDOWN_KEY = "agreement.privacy-agreement-markdown";
    private static final List<String> AGREEMENT_CONFIG_KEYS = List.of(
            AGREEMENT_USER_MARKDOWN_KEY,
            AGREEMENT_PRIVACY_MARKDOWN_KEY
    );

    private static final String SMTP_HOST_KEY = "smtp.host";
    private static final String SMTP_PORT_KEY = "smtp.port";
    private static final String SMTP_USERNAME_KEY = "smtp.username";
    private static final String SMTP_PASSWORD_KEY = "smtp.password";
    private static final String SMTP_FROM_KEY = "smtp.from";
    private static final String SMTP_AUTH_ENABLED_KEY = "smtp.auth-enabled";
    private static final String SMTP_STARTTLS_ENABLED_KEY = "smtp.starttls-enabled";
    private static final String SMTP_SSL_ENABLED_KEY = "smtp.ssl-enabled";
    private static final List<String> SMTP_CONFIG_KEYS = List.of(
            SMTP_HOST_KEY,
            SMTP_PORT_KEY,
            SMTP_USERNAME_KEY,
            SMTP_PASSWORD_KEY,
            SMTP_FROM_KEY,
            SMTP_AUTH_ENABLED_KEY,
            SMTP_STARTTLS_ENABLED_KEY,
            SMTP_SSL_ENABLED_KEY
    );

    private static final String PROFILE_FIELD_AVATAR_VISIBLE_KEY = "profile.field.avatar.visible";
    private static final String PROFILE_FIELD_REAL_NAME_VISIBLE_KEY = "profile.field.real-name.visible";
    private static final String PROFILE_FIELD_MOBILE_VISIBLE_KEY = "profile.field.mobile.visible";
    private static final String PROFILE_FIELD_EMAIL_VISIBLE_KEY = "profile.field.email.visible";
    private static final String PROFILE_FIELD_BIRTH_MONTH_VISIBLE_KEY = "profile.field.birth-month.visible";
    private static final String PROFILE_FIELD_GENDER_VISIBLE_KEY = "profile.field.gender.visible";
    private static final String PROFILE_FIELD_REGION_VISIBLE_KEY = "profile.field.region.visible";
    private static final String PROFILE_FIELD_AVAILABLE_TIME_VISIBLE_KEY = "profile.field.available-time.visible";
    private static final String PROFILE_FIELD_ID_CARD_VISIBLE_KEY = "profile.field.id-card-number.visible";
    private static final List<ProfileFieldDefinition> PROFILE_FIELD_DEFINITIONS = List.of(
            new ProfileFieldDefinition("avatarUrl", "头像", "控制个人中心是否展示头像上传与预览区域", PROFILE_FIELD_AVATAR_VISIBLE_KEY, true),
            new ProfileFieldDefinition("realName", "姓名", "控制个人中心是否展示姓名字段", PROFILE_FIELD_REAL_NAME_VISIBLE_KEY, true),
            new ProfileFieldDefinition("mobile", "手机号", "控制个人中心是否展示手机号字段", PROFILE_FIELD_MOBILE_VISIBLE_KEY, true),
            new ProfileFieldDefinition("email", "邮箱", "控制个人中心是否展示邮箱字段", PROFILE_FIELD_EMAIL_VISIBLE_KEY, true),
            new ProfileFieldDefinition("birthMonth", "出生年月", "控制个人中心是否展示出生年月字段", PROFILE_FIELD_BIRTH_MONTH_VISIBLE_KEY, true),
            new ProfileFieldDefinition("gender", "性别", "控制个人中心是否展示性别字段", PROFILE_FIELD_GENDER_VISIBLE_KEY, true),
            new ProfileFieldDefinition("region", "所在地区", "控制个人中心是否展示所在地区字段", PROFILE_FIELD_REGION_VISIBLE_KEY, true),
            new ProfileFieldDefinition("availableTime", "可工作时间", "控制个人中心是否展示可工作时间字段", PROFILE_FIELD_AVAILABLE_TIME_VISIBLE_KEY, true),
            new ProfileFieldDefinition("idCardNumber", "身份证号码", "控制个人中心是否展示身份证号码字段", PROFILE_FIELD_ID_CARD_VISIBLE_KEY, true)
    );
    private static final List<String> PROFILE_FIELD_CONFIG_KEYS = PROFILE_FIELD_DEFINITIONS.stream()
            .map(ProfileFieldDefinition::configKey)
            .toList();


    private static final String WATERMARK_ENABLED_KEY = "watermark.enabled";
    private static final String WATERMARK_MODE_KEY = "watermark.mode";
    private static final String WATERMARK_TEXT_LINES_KEY = "watermark.text-lines";
    private static final String WATERMARK_IMAGE_URL_KEY = "watermark.image-url";
    private static final String WATERMARK_FONT_COLOR_KEY = "watermark.font-color";
    private static final String WATERMARK_FONT_SIZE_KEY = "watermark.font-size";
    private static final String WATERMARK_FONT_WEIGHT_KEY = "watermark.font-weight";
    private static final String WATERMARK_ROTATE_KEY = "watermark.rotate";
    private static final String WATERMARK_GAP_X_KEY = "watermark.gap-x";
    private static final String WATERMARK_GAP_Y_KEY = "watermark.gap-y";
    private static final String WATERMARK_OFFSET_X_KEY = "watermark.offset-x";
    private static final String WATERMARK_OFFSET_Y_KEY = "watermark.offset-y";
    private static final String WATERMARK_Z_INDEX_KEY = "watermark.z-index";
    private static final String WATERMARK_OPACITY_KEY = "watermark.opacity";
    private static final List<String> WATERMARK_CONFIG_KEYS = List.of(
            WATERMARK_ENABLED_KEY, WATERMARK_MODE_KEY, WATERMARK_TEXT_LINES_KEY, WATERMARK_IMAGE_URL_KEY,
            WATERMARK_FONT_COLOR_KEY, WATERMARK_FONT_SIZE_KEY, WATERMARK_FONT_WEIGHT_KEY, WATERMARK_ROTATE_KEY,
            WATERMARK_GAP_X_KEY, WATERMARK_GAP_Y_KEY, WATERMARK_OFFSET_X_KEY, WATERMARK_OFFSET_Y_KEY,
            WATERMARK_Z_INDEX_KEY, WATERMARK_OPACITY_KEY
    );
    private static final int RECENT_LOGIN_LOG_LIMIT = 5;

    private static final List<SystemVO.ShortcutVO> DASHBOARD_SHORTCUTS = List.of(
            shortcut("系统管理", "菜单、字典、配置与验证入口", "/system/menus", "system:menu:view"),
            shortcut("验证管理", "2FA 开关与短信验证码配置", "/system/verification", "system:verification:view"),
            shortcut("在线用户", "实时会话、踢出和封禁", "/user-center/online-users", "system:online-user:view"),
            shortcut("个性化设置", "站点名称、Logo、Icon 和页脚信息", "/system/personalization", "system:config:view"),
            shortcut("安全设置", "空闲超时与 token 生命周期", "/system/security", "system:config:view"),
            shortcut("租户中心", "当前租户与可访问租户", "/tenant/overview", "tenant:view"),
            shortcut("审计中心", "登录和操作日志", "/system/monitoring/audit", "audit:view"),
            shortcut("站内信归档", "租户站内信归档与手动发布", "/system/notifications", "system:notification:view"),
            shortcut("插件管理", "插件安装、启用和运行态", "/system/plugins", "plugin:management:view")
    );
    private static final String NODE_TYPE_CATALOG = "CATALOG";
    private static final String NODE_TYPE_PAGE = "PAGE";
    private static final String NODE_TYPE_ALIAS = "ALIAS";
    private static final Set<String> LEGACY_PERMISSION_TREE_ALIAS_PATHS = Set.of(
            "/audit/overview",
            "/system/overview",
            "/system/users",
            "/system/online-users",
            "/system/roles",
            "/profile/center",
            "/user-center/permissions",
            "/iam/overview"
    );

    private final JdbcTemplate jdbcTemplate;
    private final AuthAppService authAppService;
    private final TenantDomainService tenantDomainService;
    private final UserDomainService userDomainService;
    private final PermissionSnapshotService permissionSnapshotService;
    private final PluginManagementAppService pluginManagementAppService;
    private final OnlineSessionManagementAppService onlineSessionManagementAppService;
    private final PasswordEncoder passwordEncoder;
    private final LoginAuditService loginAuditService;
    private final OperationAuditService operationAuditService;
    private final SecuritySettingsService securitySettingsService;
    private final PasswordPolicyService passwordPolicyService;

    public SystemManagementAppService(
            JdbcTemplate jdbcTemplate,
            AuthAppService authAppService,
            TenantDomainService tenantDomainService,
            UserDomainService userDomainService,
            PermissionSnapshotService permissionSnapshotService,
            PluginManagementAppService pluginManagementAppService,
            OnlineSessionManagementAppService onlineSessionManagementAppService,
            PasswordEncoder passwordEncoder,
            LoginAuditService loginAuditService,
            OperationAuditService operationAuditService,
            SecuritySettingsService securitySettingsService,
            PasswordPolicyService passwordPolicyService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.authAppService = authAppService;
        this.tenantDomainService = tenantDomainService;
        this.userDomainService = userDomainService;
        this.permissionSnapshotService = permissionSnapshotService;
        this.pluginManagementAppService = pluginManagementAppService;
        this.onlineSessionManagementAppService = onlineSessionManagementAppService;
        this.passwordEncoder = passwordEncoder;
        this.loginAuditService = loginAuditService;
        this.operationAuditService = operationAuditService;
        this.securitySettingsService = securitySettingsService;
        this.passwordPolicyService = passwordPolicyService;
    }

    public SystemVO.DashboardSummaryVO dashboardSummary(CurrentUser currentUser) {
        SystemVO.DashboardSummaryVO summary = new SystemVO.DashboardSummaryVO();
        summary.setCurrentUser(authAppService.currentUser(currentUser));
        TenantInfoEntity tenantInfo = tenantDomainService.findTenantById(currentTenantId(currentUser)).orElse(null);
        summary.setCurrentTenant(tenantDomainService.toTenantSummary(tenantInfo));
        summary.setTenantPlugins(pluginManagementAppService.availablePlugins(currentTenantId(currentUser)));
        summary.setMenuCount(countMenus(currentTenantId(currentUser)));
        summary.setPermissionCount(permissionSnapshotService.loadSnapshot(currentTenantId(currentUser), currentUser.getUserId()).getPermissionList().size());
        summary.setRecentLoginLogs(listLoginLogs(currentUser, currentUser.getUsername(), currentTenantId(currentUser), null, null, null, 1, 5).getRecords());
        summary.setRecentOperationLogs(listOperationLogs(currentUser, currentUser.getUsername(), currentTenantId(currentUser), null, null, 1, 5).getRecords());
        summary.setShortcuts(DASHBOARD_SHORTCUTS);
        return summary;
    }

    public SystemVO.ProfileSummaryVO profileSummary(CurrentUser currentUser) {
        SystemVO.ProfileSummaryVO summary = new SystemVO.ProfileSummaryVO();
        summary.setCurrentUser(authAppService.currentUser(currentUser));
        TenantInfoEntity tenantInfo = tenantDomainService.findTenantById(currentTenantId(currentUser)).orElse(null);
        summary.setCurrentTenant(tenantDomainService.toTenantSummary(tenantInfo));
        summary.setRoleNames(listCurrentTenantRoleNames(currentUser.getUserId(), currentTenantId(currentUser)));
        summary.setPermissionCount(permissionSnapshotService.loadSnapshot(currentTenantId(currentUser), currentUser.getUserId()).getPermissionList().size());
        summary.setRecentLoginLogs(listLoginLogs(
                currentUser,
                currentUser.getUsername(),
                currentTenantId(currentUser),
                null,
                null,
                null,
                1,
                RECENT_LOGIN_LOG_LIMIT
        ).getRecords());
        summary.setProfileFieldSettings(loadProfileFieldSettings(currentTenantId(currentUser)));
        return summary;
    }

    @Transactional
    public CurrentUserVO updateCurrentUserProfile(CurrentUser currentUser, com.legendary.invention.saas.modules.system.dto.ProfileDTO.BasicInfoUpdateRequest request) {
        SysUserEntity user = userDomainService.findById(currentUser.getUserId())
                .orElseThrow(() -> new BizException(ErrorCode.ACCOUNT_NOT_FOUND, "用户不存在"));
        jdbcTemplate.update(
                """
                        update sys_user
                        set avatar_url = ?, nickname = ?, real_name = ?, mobile = ?, email = ?, birth_month = ?, gender = ?, region = ?,
                            available_time = ?, id_card_number = ?, updated_by = ?, updated_at = ?
                        where id = ? and deleted = 0
                        """,
                normalizeNullableText(request.getAvatarUrl()),
                normalizeNullableText(request.getNickname()),
                normalizeNullableText(request.getRealName()),
                normalizeNullableText(request.getMobile()),
                normalizeNullableText(request.getEmail()),
                normalizeNullableText(request.getBirthMonth()),
                normalizeNullableText(request.getGender()),
                normalizeNullableText(request.getRegion()),
                normalizeNullableText(request.getAvailableTime()),
                normalizeNullableText(request.getIdCardNumber()),
                currentUser.getUserId(),
                LocalDateTime.now(),
                user.getId()
        );
        operationAuditService.log(currentTenantId(currentUser), currentUser.getUserId(), currentUser.getUsername(), "profile", "update", "UPDATE", "SUCCESS", "更新个人资料");
        return authAppService.currentUser(currentUser);
    }

    @Transactional
    public CurrentUserVO updateCurrentUserEmail(CurrentUser currentUser, String email) {
        SysUserEntity user = userDomainService.findById(currentUser.getUserId())
                .orElseThrow(() -> new BizException(ErrorCode.ACCOUNT_NOT_FOUND, "用户不存在"));
        jdbcTemplate.update(
                """
                        update sys_user
                        set email = ?, updated_by = ?, updated_at = ?
                        where id = ? and deleted = 0
                        """,
                email,
                currentUser.getUserId(),
                LocalDateTime.now(),
                user.getId()
        );
        return authAppService.currentUser(currentUser);
    }

    public List<SystemVO.ProfileFieldSettingVO> getProfileFieldSettings(CurrentUser currentUser) {
        return loadProfileFieldSettings(currentTenantId(currentUser));
    }

    @Transactional
    public List<SystemVO.ProfileFieldSettingVO> updateProfileFieldSettings(CurrentUser currentUser, SystemDTO.ProfileFieldSettingsRequest request) {
        Long tenantId = currentTenantId(currentUser);
        Map<String, Boolean> requestedVisibility = new LinkedHashMap<>();
        request.getItems().forEach(item -> requestedVisibility.put(item.getFieldKey(), Boolean.TRUE.equals(item.getVisible())));
        PROFILE_FIELD_DEFINITIONS.forEach(definition -> upsertConfigValue(
                tenantId,
                definition.configKey(),
                definition.fieldLabel() + "展示开关",
                String.valueOf(requestedVisibility.getOrDefault(definition.fieldKey(), definition.defaultVisible())),
                definition.fieldDescription(),
                currentUser.getUserId()
        ));
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "profile-field", "update", "UPDATE", "SUCCESS", "更新个人中心字段展示设置");
        return loadProfileFieldSettings(tenantId);
    }

    public PageResponse<SystemVO.UserVO> listUsers(CurrentUser currentUser, String username, String mobile, String status, long pageNo, long pageSize) {
        Long tenantId = currentTenantId(currentUser);
        String baseSql = """
                from sys_user u
                join sys_user_tenant ut
                  on ut.user_id = u.id
                 and ut.tenant_id = ?
                 and ut.deleted = 0
                 and ut.status = 'ENABLED'
                where u.deleted = 0
                """;
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        if (StringUtils.hasText(username)) {
            baseSql += " and u.username like ?";
            params.add(like(username));
        }
        if (StringUtils.hasText(mobile)) {
            baseSql += " and u.mobile like ?";
            params.add(like(mobile));
        }
        if (StringUtils.hasText(status)) {
            baseSql += " and u.status = ?";
            params.add(status);
        }
        String selectSql = """
                select u.id, u.username, u.mobile, u.id_card_number as idCardNumber, u.nickname, u.real_name as realName,
                       u.avatar_url as avatarUrl, u.email, u.birth_month as birthMonth, u.gender, u.region,
                       u.available_time as availableTime, u.status, u.created_at as createdAt, u.updated_at as updatedAt
                """ + baseSql + """
                order by u.id desc
                """;
        PageResponse<SystemVO.UserVO> page = pageQuery(selectSql, "select count(1) " + baseSql, SystemVO.UserVO.class, pageNo, pageSize, params);
        page.setRecords(page.getRecords().stream().map(user -> {
            user.setTenantNames(listUserTenantNames(user.getId()));
            user.setRoleNames(listUserRoleNames(user.getId(), tenantId));
            return user;
        }).toList());
        return page;
    }

    public SystemVO.UserDetailVO getUser(CurrentUser currentUser, Long userId) {
        SystemVO.UserVO user = queryUser(currentTenantId(currentUser), userId);
        SystemVO.UserDetailVO detail = new SystemVO.UserDetailVO();
        copyUser(detail, user);
        Long tenantId = currentTenantId(currentUser);
        detail.setRoleIds(listUserRoleIds(userId, tenantId));
        detail.setTenantIds(listUserTenantIds(userId));
        return detail;
    }

    @Transactional
    public SystemVO.UserDetailVO createUser(CurrentUser currentUser, SystemDTO.UserUpsertRequest request) {
        Long tenantId = currentTenantId(currentUser);
        Long userId = insertOrUpdateUser(null, request, currentUser.getUserId());
        upsertUserTenantRelation(userId, tenantId, true, currentUser.getUserId());
        replaceUserRoles(userId, tenantId, request.getRoleIds(), currentUser.getUserId());
        permissionSnapshotService.invalidateTenant(tenantId);
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "user", "create", "CREATE", "SUCCESS", "创建用户: " + request.getUsername());
        return getUser(currentUser, userId);
    }

    @Transactional
    public SystemVO.UserDetailVO updateUser(CurrentUser currentUser, Long userId, SystemDTO.UserUpsertRequest request) {
        Long tenantId = currentTenantId(currentUser);
        insertOrUpdateUser(userId, request, currentUser.getUserId());
        replaceUserRoles(userId, tenantId, request.getRoleIds(), currentUser.getUserId());
        permissionSnapshotService.invalidateTenant(tenantId);
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "user", "update", "UPDATE", "SUCCESS", "更新用户: " + request.getUsername());
        return getUser(currentUser, userId);
    }

    @Transactional
    public boolean updateUserStatus(CurrentUser currentUser, Long userId, String status) {
        if (isProtectedAdminAccount(userId, null) && "DISABLED".equalsIgnoreCase(status)) {
            throw new BizException(ErrorCode.FORBIDDEN, "默认管理员账户不允许被禁用");
        }
        jdbcTemplate.update(
                "update sys_user set status = ?, updated_by = ?, updated_at = ? where id = ? and deleted = 0",
                status,
                currentUser.getUserId(),
                LocalDateTime.now(),
                userId
        );
        if ("DISABLED".equalsIgnoreCase(status)) {
            onlineSessionManagementAppService.revokeUserSessions(userId);
        }
        operationAuditService.log(currentTenantId(currentUser), currentUser.getUserId(), currentUser.getUsername(), "user", "status", "UPDATE", "SUCCESS", "更新用户状态: " + userId + " -> " + status);
        return true;
    }

    public List<SystemVO.RoleVO> listUserRoles(CurrentUser currentUser, Long userId) {
        Long tenantId = currentTenantId(currentUser);
        return jdbcTemplate.query(
                """
                        select r.id, r.tenant_id as tenantId, r.role_code as roleCode, r.role_name as roleName,
                               r.role_type as roleType, r.created_at as createdAt, r.updated_at as updatedAt
                        from sys_user_role ur
                        join sys_role r on r.id = ur.role_id and r.tenant_id = ur.tenant_id and r.deleted = 0
                        where ur.tenant_id = ? and ur.user_id = ? and ur.deleted = 0
                        order by r.id desc
                        """,
                new BeanPropertyRowMapper<>(SystemVO.RoleVO.class),
                tenantId,
                userId
        );
    }

    public PageResponse<SystemVO.RoleVO> listRoles(CurrentUser currentUser, String roleCode, String roleName, String roleType, long pageNo, long pageSize) {
        Long tenantId = currentTenantId(currentUser);
        String baseSql = """
                from sys_role r
                where r.tenant_id = ? and r.deleted = 0
                """;
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        if (StringUtils.hasText(roleCode)) {
            baseSql += " and r.role_code like ?";
            params.add(like(roleCode));
        }
        if (StringUtils.hasText(roleName)) {
            baseSql += " and r.role_name like ?";
            params.add(like(roleName));
        }
        if (StringUtils.hasText(roleType)) {
            baseSql += " and r.role_type = ?";
            params.add(roleType);
        }
        String selectSql = """
                select r.id, r.tenant_id as tenantId, r.role_code as roleCode, r.role_name as roleName,
                       r.role_type as roleType, r.created_at as createdAt, r.updated_at as updatedAt
                """ + baseSql + " order by r.id desc";
        PageResponse<SystemVO.RoleVO> page = pageQuery(selectSql, "select count(1) " + baseSql, SystemVO.RoleVO.class, pageNo, pageSize, params);
        page.setRecords(page.getRecords().stream().map(role -> {
            role.setPermissionCount(countRolePermissions(role.getId(), tenantId));
            role.setUserCount(countRoleUsers(role.getId(), tenantId));
            return role;
        }).toList());
        return page;
    }

    public SystemVO.RoleDetailVO getRole(CurrentUser currentUser, Long roleId) {
        Long tenantId = currentTenantId(currentUser);
        SystemVO.RoleVO role = queryOne(
                """
                        select r.id, r.tenant_id as tenantId, r.role_code as roleCode, r.role_name as roleName,
                               r.role_type as roleType, r.created_at as createdAt, r.updated_at as updatedAt
                        from sys_role r
                        where r.id = ? and r.tenant_id = ? and r.deleted = 0
                        """,
                SystemVO.RoleVO.class,
                roleId,
                tenantId
        );
        if (role == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        SystemVO.RoleDetailVO detail = new SystemVO.RoleDetailVO();
        copyRole(detail, role);
        detail.setPermissionCount(countRolePermissions(roleId, tenantId));
        detail.setUserCount(countRoleUsers(roleId, tenantId));
        detail.setPermissionKeys(listRolePermissionKeys(roleId, tenantId));
        return detail;
    }

    @Transactional
    public SystemVO.RoleDetailVO createRole(CurrentUser currentUser, SystemDTO.RoleUpsertRequest request) {
        Long tenantId = currentTenantId(currentUser);
        Long roleId = upsertRole(null, tenantId, request, currentUser.getUserId());
        replaceRolePermissions(tenantId, roleId, request.getPermissionKeys(), currentUser.getUserId());
        permissionSnapshotService.invalidateTenant(tenantId);
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "role", "create", "CREATE", "SUCCESS", "创建角色: " + request.getRoleName());
        return getRole(currentUser, roleId);
    }

    @Transactional
    public SystemVO.RoleDetailVO updateRole(CurrentUser currentUser, Long roleId, SystemDTO.RoleUpsertRequest request) {
        Long tenantId = currentTenantId(currentUser);
        upsertRole(roleId, tenantId, request, currentUser.getUserId());
        replaceRolePermissions(tenantId, roleId, request.getPermissionKeys(), currentUser.getUserId());
        permissionSnapshotService.invalidateTenant(tenantId);
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "role", "update", "UPDATE", "SUCCESS", "更新角色: " + request.getRoleName());
        return getRole(currentUser, roleId);
    }

    @Transactional
    public boolean updateRolePermissions(CurrentUser currentUser, Long roleId, List<String> permissionKeys) {
        Long tenantId = currentTenantId(currentUser);
        replaceRolePermissions(tenantId, roleId, permissionKeys, currentUser.getUserId());
        permissionSnapshotService.invalidateTenant(tenantId);
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "role", "permissions", "UPDATE", "SUCCESS", "更新角色权限: " + roleId);
        return true;
    }

    public List<SystemVO.PermissionVO> listPermissions(CurrentUser currentUser) {
        Long tenantId = currentTenantId(currentUser);
        return jdbcTemplate.query(
                """
                        select permission_key as permissionKey, permission_name as permissionName,
                               permission_group as permissionGroup, source_type as sourceType, plugin_code as pluginCode
                        from sys_permission
                        where tenant_id = ? and deleted = 0
                        order by permission_group asc, permission_key asc
                        """,
                new BeanPropertyRowMapper<>(SystemVO.PermissionVO.class),
                tenantId
        );
    }

    public List<SystemVO.PermissionTreeVO> listPermissionTree(CurrentUser currentUser) {
        List<SystemVO.MenuVO> menus = listMenus(currentUser);
        List<SystemVO.PermissionVO> permissions = listPermissions(currentUser);
        Map<String, List<SystemVO.PermissionActionVO>> actionPermissionsByPageKey = buildActionPermissionsByPageKey(permissions);
        return buildPermissionTree(menus, actionPermissionsByPageKey);
    }

    public List<SystemVO.MenuVO> listMenus(CurrentUser currentUser) {
        Long tenantId = currentTenantId(currentUser);
        List<SystemVO.MenuVO> menus = jdbcTemplate.query(
                """
                        select id, tenant_id as tenantId, parent_id as parentId, menu_code as menuCode,
                               menu_name as menuName, menu_type as menuType, path, component, icon, sort_no as sortNo,
                               permission_key as permissionKey, status
                        from sys_menu
                        where tenant_id = ? and deleted = 0
                        order by sort_no asc, id asc
                        """,
                new BeanPropertyRowMapper<>(SystemVO.MenuVO.class),
                tenantId
        );
        return buildMenuTree(menus);
    }

    private List<SystemVO.PermissionTreeVO> buildPermissionTree(
            List<SystemVO.MenuVO> menus,
            Map<String, List<SystemVO.PermissionActionVO>> actionPermissionsByPageKey
    ) {
        if (CollectionUtils.isEmpty(menus)) {
            return List.of();
        }
        List<SystemVO.PermissionTreeVO> tree = new ArrayList<>();
        for (SystemVO.MenuVO menu : menus) {
            SystemVO.PermissionTreeVO node = buildPermissionTreeNode(menu, actionPermissionsByPageKey);
            if (node != null) {
                tree.add(node);
            }
        }
        return tree;
    }

    private SystemVO.PermissionTreeVO buildPermissionTreeNode(
            SystemVO.MenuVO menu,
            Map<String, List<SystemVO.PermissionActionVO>> actionPermissionsByPageKey
    ) {
        if (menu == null || "BUTTON".equalsIgnoreCase(menu.getMenuType())) {
            return null;
        }

        List<SystemVO.PermissionTreeVO> children = new ArrayList<>();
        if (!CollectionUtils.isEmpty(menu.getChildren())) {
            for (SystemVO.MenuVO child : menu.getChildren()) {
                SystemVO.PermissionTreeVO childNode = buildPermissionTreeNode(child, actionPermissionsByPageKey);
                if (childNode != null) {
                    children.add(childNode);
                }
            }
        }

        String nodeType = resolvePermissionTreeNodeType(menu);
        boolean selectable = NODE_TYPE_PAGE.equals(nodeType) && StringUtils.hasText(menu.getPermissionKey());
        if (!selectable && children.isEmpty() && !NODE_TYPE_CATALOG.equals(nodeType)) {
            return null;
        }

        SystemVO.PermissionTreeVO node = new SystemVO.PermissionTreeVO();
        node.setPageKey(menu.getId() != null ? String.valueOf(menu.getId()) : StringUtils.hasText(menu.getPath()) ? menu.getPath() : menu.getMenuCode());
        node.setPageName(menu.getMenuName());
        node.setNodeType(nodeType);
        node.setRoutePath(NODE_TYPE_PAGE.equals(nodeType) ? menu.getPath() : null);
        node.setIcon(menu.getIcon());
        node.setPermissionKey(menu.getPermissionKey());
        node.setSelectable(selectable);
        node.setChildren(children.isEmpty() ? null : children);
        if (selectable) {
            node.setPermissionGroup(resolvePermissionGroup(menu.getPermissionKey()));
            node.setSourceType(resolvePermissionSourceType(menu.getPermissionKey()));
            node.setActionPermissions(actionPermissionsByPageKey.getOrDefault(menu.getPermissionKey(), List.of()));
        }
        return node;
    }

    private String resolvePermissionTreeNodeType(SystemVO.MenuVO menu) {
        if (menu == null) {
            return NODE_TYPE_ALIAS;
        }
        if ("CATALOG".equalsIgnoreCase(menu.getMenuType())) {
            return NODE_TYPE_CATALOG;
        }
        if (isLegacyPermissionTreeAliasPath(menu.getPath()) || isRedirectComponent(menu.getComponent())) {
            return NODE_TYPE_ALIAS;
        }
        return NODE_TYPE_PAGE;
    }

    private boolean isLegacyPermissionTreeAliasPath(String path) {
        return StringUtils.hasText(path) && LEGACY_PERMISSION_TREE_ALIAS_PATHS.contains(path);
    }

    private boolean isRedirectComponent(String component) {
        return StringUtils.hasText(component) && component.startsWith("redirect:");
    }

    private Map<String, List<SystemVO.PermissionActionVO>> buildActionPermissionsByPageKey(List<SystemVO.PermissionVO> permissions) {
        if (CollectionUtils.isEmpty(permissions)) {
            return Map.of();
        }

        Map<String, List<SystemVO.PermissionActionVO>> result = new LinkedHashMap<>();
        Map<String, SystemVO.PermissionVO> permissionMap = permissions.stream()
                .filter(permission -> StringUtils.hasText(permission.getPermissionKey()))
                .collect(Collectors.toMap(SystemVO.PermissionVO::getPermissionKey, permission -> permission, (left, right) -> left, LinkedHashMap::new));

        for (SystemVO.PermissionVO permission : permissions) {
            String permissionKey = permission.getPermissionKey();
            if (!StringUtils.hasText(permissionKey) || permissionKey.chars().filter(ch -> ch == ':').count() < 2) {
                continue;
            }
            String pagePermissionKey = resolvePagePermissionKey(permissionKey);
            if (!StringUtils.hasText(pagePermissionKey)) {
                continue;
            }
            String actionPrefix = resolveActionPrefix(pagePermissionKey);
            List<SystemVO.PermissionActionVO> actions = permissionMap.values().stream()
                    .filter(candidate -> !permissionKey.equals(candidate.getPermissionKey()))
                    .filter(candidate -> StringUtils.hasText(candidate.getPermissionKey()) && candidate.getPermissionKey().startsWith(actionPrefix))
                    .map(candidate -> {
                        SystemVO.PermissionActionVO action = new SystemVO.PermissionActionVO();
                        action.setPermissionKey(candidate.getPermissionKey());
                        action.setPermissionName(candidate.getPermissionName());
                        action.setPermissionGroup(candidate.getPermissionGroup());
                        action.setSourceType(candidate.getSourceType());
                        return action;
                    })
                    .sorted(Comparator.comparing(SystemVO.PermissionActionVO::getPermissionKey))
                    .toList();
            if (!actions.isEmpty()) {
                result.put(pagePermissionKey, actions);
            }
        }

        return result;
    }

    private String resolvePagePermissionKey(String permissionKey) {
        if (!StringUtils.hasText(permissionKey) || !permissionKey.endsWith(":view")) {
            return null;
        }
        return permissionKey;
    }

    private String resolveActionPrefix(String pagePermissionKey) {
        if (!StringUtils.hasText(pagePermissionKey) || !pagePermissionKey.endsWith(":view")) {
            return null;
        }
        return pagePermissionKey.substring(0, pagePermissionKey.length() - ":view".length()) + ":";
    }

    private String resolvePermissionGroup(String permissionKey) {
        if (!StringUtils.hasText(permissionKey)) {
            return null;
        }
        int firstColon = permissionKey.indexOf(':');
        return firstColon > 0 ? permissionKey.substring(0, firstColon) : permissionKey;
    }

    private String resolvePermissionSourceType(String permissionKey) {
        if (!StringUtils.hasText(permissionKey)) {
            return null;
        }
        return permissionKey.startsWith("plugin:") ? "PLUGIN" : "CORE";
    }

    @Transactional
    public boolean reorderMenus(CurrentUser currentUser, SystemDTO.MenuReorderRequest request) {
        Long tenantId = currentTenantId(currentUser);
        LocalDateTime now = LocalDateTime.now();
        if (request == null || CollectionUtils.isEmpty(request.getItems())) {
            return true;
        }

        for (SystemDTO.MenuOrderItem item : request.getItems()) {
            if (item == null || item.getId() == null || item.getSortNo() == null) {
                continue;
            }
            jdbcTemplate.update(
                    """
                            update sys_menu
                            set parent_id = ?, sort_no = ?, updated_by = ?, updated_at = ?
                            where id = ? and tenant_id = ? and deleted = 0
                            """,
                    item.getParentId() == null ? 0L : item.getParentId(),
                    item.getSortNo(),
                    currentUser.getUserId(),
                    now,
                    item.getId(),
                    tenantId
            );
        }

        operationAuditService.log(
                tenantId,
                currentUser.getUserId(),
                currentUser.getUsername(),
                "menu",
                "reorder",
                "UPDATE",
                "SUCCESS",
                "调整菜单顺序"
        );
        return true;
    }

    public SystemVO.MenuVO getMenu(CurrentUser currentUser, Long menuId) {
        Long tenantId = currentTenantId(currentUser);
        SystemVO.MenuVO menu = queryOne(
                """
                        select id, tenant_id as tenantId, parent_id as parentId, menu_code as menuCode,
                               menu_name as menuName, menu_type as menuType, path, component, icon, sort_no as sortNo,
                               permission_key as permissionKey, status
                        from sys_menu
                        where id = ? and tenant_id = ? and deleted = 0
                        """,
                SystemVO.MenuVO.class,
                menuId,
                tenantId
        );
        if (menu == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "菜单不存在");
        }
        return menu;
    }

    @Transactional
    public SystemVO.MenuVO createMenu(CurrentUser currentUser, SystemDTO.MenuUpsertRequest request) {
        Long tenantId = currentTenantId(currentUser);
        Long menuId = insertMenu(null, tenantId, request, currentUser.getUserId());
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "menu", "create", "CREATE", "SUCCESS", "创建菜单: " + request.getMenuName());
        return getMenu(currentUser, menuId);
    }

    @Transactional
    public SystemVO.MenuVO updateMenu(CurrentUser currentUser, Long menuId, SystemDTO.MenuUpsertRequest request) {
        Long tenantId = currentTenantId(currentUser);
        insertMenu(menuId, tenantId, request, currentUser.getUserId());
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "menu", "update", "UPDATE", "SUCCESS", "更新菜单: " + request.getMenuName());
        return getMenu(currentUser, menuId);
    }

    @Transactional
    public boolean updateMenuStatus(CurrentUser currentUser, Long menuId, String status) {
        jdbcTemplate.update(
                "update sys_menu set status = ?, updated_by = ?, updated_at = ? where id = ? and tenant_id = ? and deleted = 0",
                status,
                currentUser.getUserId(),
                LocalDateTime.now(),
                menuId,
                currentTenantId(currentUser)
        );
        operationAuditService.log(currentTenantId(currentUser), currentUser.getUserId(), currentUser.getUsername(), "menu", "status", "UPDATE", "SUCCESS", "更新菜单状态: " + menuId + " -> " + status);
        return true;
    }

    public PageResponse<SystemVO.DictTypeVO> listDictTypes(CurrentUser currentUser, String dictCode, String dictName, String status, long pageNo, long pageSize) {
        Long tenantId = currentTenantId(currentUser);
        String baseSql = """
                from sys_dict_type t
                where t.deleted = 0 and (t.tenant_id is null or t.tenant_id = ?)
                """;
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        if (StringUtils.hasText(dictCode)) {
            baseSql += " and t.dict_code like ?";
            params.add(like(dictCode));
        }
        if (StringUtils.hasText(dictName)) {
            baseSql += " and t.dict_name like ?";
            params.add(like(dictName));
        }
        if (StringUtils.hasText(status)) {
            baseSql += " and t.status = ?";
            params.add(status);
        }
        String selectSql = """
                select t.id, t.tenant_id as tenantId, t.dict_code as dictCode, t.dict_name as dictName,
                       t.status, t.is_system as isSystem, t.remark
                """ + baseSql + " order by t.is_system desc, t.id desc";
        PageResponse<SystemVO.DictTypeVO> page = pageQuery(selectSql, "select count(1) " + baseSql, SystemVO.DictTypeVO.class, pageNo, pageSize, params);
        return page;
    }

    public SystemVO.DictTypeVO getDictType(CurrentUser currentUser, Long id) {
        Long tenantId = currentTenantId(currentUser);
        SystemVO.DictTypeVO type = queryOne(
                """
                        select id, tenant_id as tenantId, dict_code as dictCode, dict_name as dictName,
                               status, is_system as isSystem, remark
                        from sys_dict_type
                        where id = ? and deleted = 0 and (tenant_id is null or tenant_id = ?)
                        """,
                SystemVO.DictTypeVO.class,
                id,
                tenantId
        );
        if (type == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "字典类型不存在");
        }
        return type;
    }

    @Transactional
    public SystemVO.DictTypeVO createDictType(CurrentUser currentUser, SystemDTO.DictTypeUpsertRequest request) {
        Long tenantId = currentTenantId(currentUser);
        Long id = upsertDictType(null, tenantId, request, currentUser.getUserId());
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "dict", "create", "CREATE", "SUCCESS", "创建字典类型: " + request.getDictCode());
        return getDictType(currentUser, id);
    }

    @Transactional
    public SystemVO.DictTypeVO updateDictType(CurrentUser currentUser, Long id, SystemDTO.DictTypeUpsertRequest request) {
        Long tenantId = currentTenantId(currentUser);
        upsertDictType(id, tenantId, request, currentUser.getUserId());
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "dict", "update", "UPDATE", "SUCCESS", "更新字典类型: " + request.getDictCode());
        return getDictType(currentUser, id);
    }

    public List<SystemVO.DictItemVO> listDictItems(CurrentUser currentUser, Long dictTypeId) {
        return jdbcTemplate.query(
                """
                        select id, dict_type_id as dictTypeId, item_label as itemLabel, item_value as itemValue,
                               sort_no as sortNo, status, remark
                        from sys_dict_item
                        where dict_type_id = ? and deleted = 0
                        order by sort_no asc, id asc
                        """,
                new BeanPropertyRowMapper<>(SystemVO.DictItemVO.class),
                dictTypeId
        );
    }

    public SystemVO.DictItemVO getDictItem(Long dictTypeId, Long itemId) {
        SystemVO.DictItemVO item = queryOne(
                """
                        select id, dict_type_id as dictTypeId, item_label as itemLabel, item_value as itemValue,
                               sort_no as sortNo, status, remark
                        from sys_dict_item
                        where id = ? and dict_type_id = ? and deleted = 0
                        """,
                SystemVO.DictItemVO.class,
                itemId,
                dictTypeId
        );
        if (item == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "字典项不存在");
        }
        return item;
    }

    @Transactional
    public SystemVO.DictItemVO createDictItem(CurrentUser currentUser, Long dictTypeId, SystemDTO.DictItemUpsertRequest request) {
        Long id = upsertDictItem(null, dictTypeId, request, currentUser.getUserId());
        operationAuditService.log(currentTenantId(currentUser), currentUser.getUserId(), currentUser.getUsername(), "dict", "item-create", "CREATE", "SUCCESS", "创建字典项: " + request.getItemLabel());
        return getDictItem(dictTypeId, id);
    }

    @Transactional
    public SystemVO.DictItemVO updateDictItem(CurrentUser currentUser, Long dictTypeId, Long itemId, SystemDTO.DictItemUpsertRequest request) {
        upsertDictItem(itemId, dictTypeId, request, currentUser.getUserId());
        operationAuditService.log(currentTenantId(currentUser), currentUser.getUserId(), currentUser.getUsername(), "dict", "item-update", "UPDATE", "SUCCESS", "更新字典项: " + request.getItemLabel());
        return getDictItem(dictTypeId, itemId);
    }

    public PageResponse<SystemVO.ConfigVO> listConfigs(CurrentUser currentUser, String configKey, String configName, String configScope, long pageNo, long pageSize) {
        Long tenantId = currentTenantId(currentUser);
        String baseSql = """
                from sys_config c
                where c.deleted = 0 and (c.tenant_id is null or c.tenant_id = ?)
                """;
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        if (StringUtils.hasText(configKey)) {
            baseSql += " and c.config_key like ?";
            params.add(like(configKey));
        }
        if (StringUtils.hasText(configName)) {
            baseSql += " and c.config_name like ?";
            params.add(like(configName));
        }
        if (StringUtils.hasText(configScope)) {
            baseSql += " and c.config_scope = ?";
            params.add(configScope);
        }
        String selectSql = """
                select c.id, c.tenant_id as tenantId, c.config_key as configKey, c.config_name as configName,
                       c.config_value as configValue, c.config_scope as configScope, c.is_system as isSystem, c.remark
                """ + baseSql + " order by c.is_system desc, c.id desc";
        return pageQuery(selectSql, "select count(1) " + baseSql, SystemVO.ConfigVO.class, pageNo, pageSize, params);
    }

    public SystemVO.ConfigVO getConfig(CurrentUser currentUser, Long id) {
        Long tenantId = currentTenantId(currentUser);
        SystemVO.ConfigVO config = queryOne(
                """
                        select id, tenant_id as tenantId, config_key as configKey, config_name as configName,
                               config_value as configValue, config_scope as configScope, is_system as isSystem, remark
                        from sys_config
                        where id = ? and deleted = 0 and (tenant_id is null or tenant_id = ?)
                        """,
                SystemVO.ConfigVO.class,
                id,
                tenantId
        );
        if (config == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "配置不存在");
        }
        return config;
    }

    @Transactional
    public SystemVO.ConfigVO updateConfig(CurrentUser currentUser, Long id, SystemDTO.ConfigUpsertRequest request) {
        Long tenantId = currentTenantId(currentUser);
        jdbcTemplate.update(
                """
                        update sys_config
                        set config_key = ?, config_name = ?, config_value = ?, config_scope = ?, remark = ?,
                            updated_by = ?, updated_at = ?
                        where id = ? and deleted = 0 and (tenant_id is null or tenant_id = ?)
                        """,
                request.getConfigKey(),
                request.getConfigName(),
                request.getConfigValue(),
                request.getConfigScope(),
                request.getRemark(),
                currentUser.getUserId(),
                LocalDateTime.now(),
                id,
                tenantId
        );
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "config", "update", "UPDATE", "SUCCESS", "更新配置: " + request.getConfigKey());
        return getConfig(currentUser, id);
    }

    public SystemVO.SmtpSettingsVO getSmtpSettings(CurrentUser currentUser) {
        return loadSmtpSettings(currentTenantId(currentUser));
    }

    @Transactional
    public SystemVO.SmtpSettingsVO updateSmtpSettings(CurrentUser currentUser, SystemDTO.SmtpSettingsRequest request) {
        Long tenantId = currentTenantId(currentUser);
        Long operatorId = currentUser.getUserId();
        Map<String, String> currentValues = loadConfigValuesByKeys(tenantId, SMTP_CONFIG_KEYS);
        SystemVO.SmtpSettingsVO current = loadSmtpSettings(tenantId);
        String host = sanitizeText(request.getHost(), current.getHost());
        Integer port = request.getPort() == null ? current.getPort() : request.getPort();
        String username = sanitizeText(request.getUsername(), current.getUsername());
        String existingPassword = defaultIfBlank(currentValues.get(SMTP_PASSWORD_KEY), "");
        String password = StringUtils.hasText(request.getPassword()) ? request.getPassword() : existingPassword;
        String from = sanitizeText(request.getFrom(), current.getFrom());
        boolean authEnabled = request.getAuthEnabled() == null ? Boolean.TRUE.equals(current.getAuthEnabled()) : request.getAuthEnabled();
        boolean startTlsEnabled = request.getStartTlsEnabled() == null ? Boolean.TRUE.equals(current.getStartTlsEnabled()) : request.getStartTlsEnabled();
        boolean sslEnabled = request.getSslEnabled() == null ? Boolean.TRUE.equals(current.getSslEnabled()) : request.getSslEnabled();

        upsertPlatformConfig(tenantId, SMTP_HOST_KEY, "SMTP 主机", host, "邮件服务器地址", operatorId);
        upsertPlatformConfig(tenantId, SMTP_PORT_KEY, "SMTP 端口", String.valueOf(port == null ? 25 : port), "邮件服务器端口", operatorId);
        upsertPlatformConfig(tenantId, SMTP_USERNAME_KEY, "SMTP 用户名", username, "SMTP 登录用户名", operatorId);
        upsertPlatformConfig(tenantId, SMTP_PASSWORD_KEY, "SMTP 密码", password, "SMTP 登录密码", operatorId);
        upsertPlatformConfig(tenantId, SMTP_FROM_KEY, "发件人地址", from, "SMTP 默认发件人", operatorId);
        upsertPlatformConfig(tenantId, SMTP_AUTH_ENABLED_KEY, "SMTP 认证", String.valueOf(authEnabled), "是否启用 SMTP AUTH", operatorId);
        upsertPlatformConfig(tenantId, SMTP_STARTTLS_ENABLED_KEY, "SMTP STARTTLS", String.valueOf(startTlsEnabled), "是否启用 STARTTLS", operatorId);
        upsertPlatformConfig(tenantId, SMTP_SSL_ENABLED_KEY, "SMTP SSL", String.valueOf(sslEnabled), "是否启用 SSL", operatorId);

        operationAuditService.log(tenantId, operatorId, currentUser.getUsername(), "smtp", "update", "UPDATE", "SUCCESS", "更新 SMTP 配置");
        return loadSmtpSettings(tenantId);
    }

    @Transactional
    public SystemVO.SmtpTestVO testSmtpSettings(CurrentUser currentUser, SystemDTO.SmtpTestRequest request) {
        Long tenantId = currentTenantId(currentUser);
        Map<String, String> values = loadConfigValuesByKeys(tenantId, SMTP_CONFIG_KEYS);
        JavaMailSenderImpl mailSender = buildSmtpSender(values);
        String from = defaultIfBlank(values.get(SMTP_FROM_KEY), values.get(SMTP_USERNAME_KEY));
        if (!StringUtils.hasText(from)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "请先补充 SMTP 发件人地址");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(request.getToEmail());
        message.setFrom(from);
        message.setSubject(defaultIfBlank(request.getSubject(), "SMTP 测试邮件"));
        message.setText(defaultIfBlank(request.getContent(), "这是一封来自系统的 SMTP 测试邮件。"));
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "SMTP 测试发送失败: " + exception.getMessage());
        }
        SystemVO.SmtpTestVO result = new SystemVO.SmtpTestVO();
        result.setSuccess(Boolean.TRUE);
        result.setMessage("SMTP 测试邮件已发送");
        result.setToEmail(request.getToEmail());
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "smtp", "test", "CREATE", "SUCCESS", "SMTP 测试发送至 " + request.getToEmail());
        return result;
    }

    @Transactional
    public SystemVO.ConfigVO createConfig(CurrentUser currentUser, SystemDTO.ConfigUpsertRequest request) {
        Long tenantId = "TENANT".equalsIgnoreCase(request.getConfigScope()) ? currentTenantId(currentUser) : null;
        jdbcTemplate.update(
                """
                        insert into sys_config (
                            tenant_id, config_key, config_name, config_value, config_scope, is_system, remark,
                            created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, 0, ?, ?, ?, 0)
                        """,
                tenantId,
                request.getConfigKey(),
                request.getConfigName(),
                request.getConfigValue(),
                request.getConfigScope(),
                request.getRemark(),
                currentUser.getUserId(),
                currentUser.getUserId()
        );
        operationAuditService.log(currentTenantId(currentUser), currentUser.getUserId(), currentUser.getUsername(), "config", "create", "CREATE", "SUCCESS", "创建配置: " + request.getConfigKey());
        return jdbcTemplate.queryForObject(
                """
                        select id, tenant_id as tenantId, config_key as configKey, config_name as configName,
                               config_value as configValue, config_scope as configScope, is_system as isSystem, remark
                        from sys_config
                        where config_key = ? and deleted = 0 and tenant_id <=> ?
                        order by id desc
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(SystemVO.ConfigVO.class),
                request.getConfigKey(),
                tenantId
        );
    }

    public SystemVO.SecuritySettingsVO getSecuritySettings() {
        return toSecuritySettingsVO(securitySettingsService.loadSettings());
    }

    @Transactional
    public SystemVO.SecuritySettingsVO updateSecuritySettings(CurrentUser currentUser, SystemDTO.SecuritySettingsRequest request) {
        SecuritySettingsService.SecuritySettingsSnapshot updated = securitySettingsService.updateSettings(
                toSnapshot(securitySettingsService.loadSettings(), request)
        );
        if (!updated.isAllowMultiDeviceLogin()) {
            onlineSessionManagementAppService.retainLatestSessionForEachUser();
        }
        operationAuditService.log(
                currentTenantId(currentUser),
                currentUser.getUserId(),
                currentUser.getUsername(),
                "security",
                "update",
                "UPDATE",
                "SUCCESS",
                "更新安全设置"
        );
        return toSecuritySettingsVO(updated);
    }

    public SystemVO.BrandingSettingsVO getBrandingSettings(CurrentUser currentUser) {
        return loadBrandingSettings(currentTenantId(currentUser));
    }

    public SystemVO.BrandingSettingsVO getPublicBrandingSettings(Long preferredTenantId) {
        Long tenantId = preferredTenantId == null ? DEFAULT_PUBLIC_TENANT_ID : preferredTenantId;
        return loadBrandingSettings(tenantId);
    }

    public SystemVO.SecuritySettingsVO getPublicSecuritySettings() {
        return toSecuritySettingsVO(securitySettingsService.loadSettings());
    }

    public SystemVO.AgreementSettingsVO getAgreementSettings() {
        return loadAgreementSettings(DEFAULT_PUBLIC_TENANT_ID);
    }

    public SystemVO.AgreementSettingsVO getPublicAgreementSettings() {
        return loadAgreementSettings(DEFAULT_PUBLIC_TENANT_ID);
    }

    @Transactional
    public SystemVO.BrandingSettingsVO updateBrandingSettings(CurrentUser currentUser, SystemDTO.BrandingSettingsRequest request) {
        Long tenantId = currentTenantId(currentUser);
        Long operatorId = currentUser.getUserId();
        String websiteName = sanitizeBrandingText(request.getWebsiteName(), "宏翔商道");
        String companyName = sanitizeBrandingText(request.getCompanyName(), websiteName);
        Integer copyrightStartYear = request.getCopyrightStartYear() == null ? LocalDate.now().getYear() : request.getCopyrightStartYear();
        upsertBrandingConfig(
                tenantId,
                BRANDING_WEBSITE_NAME_KEY,
                "站点名称",
                websiteName,
                "控制台顶部与浏览器标题展示名称",
                operatorId
        );
        upsertBrandingConfig(
                tenantId,
                BRANDING_WEBSITE_FAVICON_URL_KEY,
                "站点图标地址",
                sanitizeBrandingText(request.getWebsiteFaviconUrl(), ""),
                "浏览器标签页 icon 地址",
                operatorId
        );
        upsertBrandingConfig(
                tenantId,
                BRANDING_WEBSITE_LOGO_URL_KEY,
                "站点 Logo 地址",
                sanitizeBrandingText(request.getWebsiteLogoUrl(), ""),
                "控制台左上角品牌 Logo 地址",
                operatorId
        );
        upsertBrandingConfig(
                tenantId,
                BRANDING_GITHUB_LINK_URL_KEY,
                "GitHub 链接",
                sanitizeBrandingText(request.getGithubLinkUrl(), ""),
                "顶部 GitHub 图标跳转地址",
                operatorId
        );
        upsertBrandingConfig(
                tenantId,
                BRANDING_HELP_LINK_URL_KEY,
                "帮助链接",
                sanitizeBrandingText(request.getHelpLinkUrl(), ""),
                "顶部帮助图标跳转地址",
                operatorId
        );
        upsertBrandingConfig(
                tenantId,
                BRANDING_COMPANY_NAME_KEY,
                "公司名称",
                companyName,
                "页脚版权主体名称",
                operatorId
        );
        upsertBrandingConfig(
                tenantId,
                BRANDING_COPYRIGHT_START_YEAR_KEY,
                "版权起始年份",
                String.valueOf(copyrightStartYear),
                "页脚版权起始年份",
                operatorId
        );
        upsertBrandingConfig(
                tenantId,
                BRANDING_FOOTER_ICP_KEY,
                "页脚 ICP 备案",
                sanitizeBrandingText(request.getFooterIcp(), ""),
                "页脚备案信息",
                operatorId
        );
        upsertBrandingConfig(
                tenantId,
                BRANDING_FOOTER_COPYRIGHT_KEY,
                "页脚版权声明",
                buildCopyrightText(companyName, copyrightStartYear),
                "页脚版权声明（由公司名称和起始年份生成）",
                operatorId
        );

        operationAuditService.log(
                tenantId,
                currentUser.getUserId(),
                currentUser.getUsername(),
                "system",
                "branding-update",
                "UPDATE",
                "SUCCESS",
                "更新个性化设置"
        );
        return loadBrandingSettings(tenantId);
    }

    @Transactional
    public SystemVO.AgreementSettingsVO updateAgreementSettings(CurrentUser currentUser, SystemDTO.AgreementSettingsRequest request) {
        Long tenantId = DEFAULT_PUBLIC_TENANT_ID;
        Long operatorId = currentUser.getUserId();
        upsertConfigValue(
                tenantId,
                AGREEMENT_USER_MARKDOWN_KEY,
                "用户协议",
                normalizeMarkdownText(request.getUserAgreementMarkdown()),
                "用户协议 Markdown",
                operatorId
        );
        upsertConfigValue(
                tenantId,
                AGREEMENT_PRIVACY_MARKDOWN_KEY,
                "隐私协议",
                normalizeMarkdownText(request.getPrivacyAgreementMarkdown()),
                "隐私协议 Markdown",
                operatorId
        );
        operationAuditService.log(
                currentTenantId(currentUser),
                currentUser.getUserId(),
                currentUser.getUsername(),
                "system",
                "agreement-update",
                "UPDATE",
                "SUCCESS",
                "更新协议设置"
        );
        return loadAgreementSettings(tenantId);
    }


    public SystemVO.WatermarkSettingsVO getWatermarkSettings(CurrentUser currentUser) {
        return loadWatermarkSettings(currentTenantId(currentUser));
    }

    @Transactional
    public SystemVO.WatermarkSettingsVO updateWatermarkSettings(CurrentUser currentUser, SystemDTO.WatermarkSettingsRequest request) {
        Long tenantId = currentTenantId(currentUser);
        Long operatorId = currentUser.getUserId();
        upsertBrandingConfig(tenantId, WATERMARK_ENABLED_KEY, "水印开关", String.valueOf(Boolean.TRUE.equals(request.getEnabled())), "全局水印开关", operatorId);
        upsertBrandingConfig(tenantId, WATERMARK_MODE_KEY, "水印模式", defaultIfBlank(request.getMode(), "TEXT"), "TEXT/IMAGE", operatorId);
        upsertBrandingConfig(tenantId, WATERMARK_TEXT_LINES_KEY, "水印文本", String.join("\n", request.getTextLines() == null ? List.of("宏翔商道", "后台管理系统") : request.getTextLines()), "多行文本水印", operatorId);
        upsertBrandingConfig(tenantId, WATERMARK_IMAGE_URL_KEY, "水印图片", defaultIfBlank(request.getImageUrl(), ""), "图片水印 URL", operatorId);
        upsertBrandingConfig(tenantId, WATERMARK_FONT_COLOR_KEY, "字体颜色", defaultIfBlank(request.getFontColor(), "rgba(0,0,0,0.15)"), "字体颜色", operatorId);
        upsertBrandingConfig(tenantId, WATERMARK_FONT_SIZE_KEY, "字体大小", String.valueOf(request.getFontSize() == null ? 14 : request.getFontSize()), "字体大小", operatorId);
        upsertBrandingConfig(tenantId, WATERMARK_FONT_WEIGHT_KEY, "字体粗细", defaultIfBlank(request.getFontWeight(), "normal"), "字体粗细", operatorId);
        upsertBrandingConfig(tenantId, WATERMARK_ROTATE_KEY, "旋转角度", String.valueOf(request.getRotate() == null ? -22 : request.getRotate()), "旋转角度", operatorId);
        upsertBrandingConfig(tenantId, WATERMARK_GAP_X_KEY, "横向间距", String.valueOf(request.getGapX() == null ? 100 : request.getGapX()), "横向间距", operatorId);
        upsertBrandingConfig(tenantId, WATERMARK_GAP_Y_KEY, "纵向间距", String.valueOf(request.getGapY() == null ? 100 : request.getGapY()), "纵向间距", operatorId);
        upsertBrandingConfig(tenantId, WATERMARK_OFFSET_X_KEY, "横向偏移", String.valueOf(request.getOffsetX() == null ? 0 : request.getOffsetX()), "横向偏移", operatorId);
        upsertBrandingConfig(tenantId, WATERMARK_OFFSET_Y_KEY, "纵向偏移", String.valueOf(request.getOffsetY() == null ? 0 : request.getOffsetY()), "纵向偏移", operatorId);
        upsertBrandingConfig(tenantId, WATERMARK_Z_INDEX_KEY, "层级", String.valueOf(request.getZIndex() == null ? 9 : request.getZIndex()), "z-index", operatorId);
        upsertBrandingConfig(tenantId, WATERMARK_OPACITY_KEY, "透明度", String.valueOf(request.getOpacity() == null ? 0.15D : request.getOpacity()), "透明度", operatorId);
        return loadWatermarkSettings(tenantId);
    }

    private SystemVO.WatermarkSettingsVO loadWatermarkSettings(Long tenantId) {
        Map<String, String> valueByKey = loadConfigValuesByKeys(tenantId, WATERMARK_CONFIG_KEYS);
        SystemVO.WatermarkSettingsVO settings = new SystemVO.WatermarkSettingsVO();
        settings.setEnabled(Boolean.parseBoolean(defaultIfBlank(valueByKey.get(WATERMARK_ENABLED_KEY), "false")));
        settings.setMode(defaultIfBlank(valueByKey.get(WATERMARK_MODE_KEY), "TEXT"));
        settings.setTextLines(List.of(defaultIfBlank(valueByKey.get(WATERMARK_TEXT_LINES_KEY), "宏翔商道\n后台管理系统").split("\n")));
        settings.setImageUrl(defaultIfBlank(valueByKey.get(WATERMARK_IMAGE_URL_KEY), ""));
        settings.setFontColor(defaultIfBlank(valueByKey.get(WATERMARK_FONT_COLOR_KEY), "rgba(0,0,0,0.15)"));
        settings.setFontSize(Integer.parseInt(defaultIfBlank(valueByKey.get(WATERMARK_FONT_SIZE_KEY), "14")));
        settings.setFontWeight(defaultIfBlank(valueByKey.get(WATERMARK_FONT_WEIGHT_KEY), "normal"));
        settings.setRotate(Integer.parseInt(defaultIfBlank(valueByKey.get(WATERMARK_ROTATE_KEY), "-22")));
        settings.setGapX(Integer.parseInt(defaultIfBlank(valueByKey.get(WATERMARK_GAP_X_KEY), "100")));
        settings.setGapY(Integer.parseInt(defaultIfBlank(valueByKey.get(WATERMARK_GAP_Y_KEY), "100")));
        settings.setOffsetX(Integer.parseInt(defaultIfBlank(valueByKey.get(WATERMARK_OFFSET_X_KEY), "0")));
        settings.setOffsetY(Integer.parseInt(defaultIfBlank(valueByKey.get(WATERMARK_OFFSET_Y_KEY), "0")));
        settings.setZIndex(Integer.parseInt(defaultIfBlank(valueByKey.get(WATERMARK_Z_INDEX_KEY), "9")));
        settings.setOpacity(Double.parseDouble(defaultIfBlank(valueByKey.get(WATERMARK_OPACITY_KEY), "0.15")));
        return settings;
    }

    public PageResponse<SystemVO.AuditLogVO> listLoginLogs(CurrentUser currentUser, String username, Long tenantId, long pageNo, long pageSize) {
        return listLoginLogs(currentUser, username, tenantId, null, null, null, pageNo, pageSize);
    }

    public PageResponse<SystemVO.AuditLogVO> listLoginLogs(
            CurrentUser currentUser,
            String username,
            Long tenantId,
            String loginType,
            String startTime,
            String endTime,
            long pageNo,
            long pageSize
    ) {
        Long effectiveTenantId = tenantId == null ? currentTenantId(currentUser) : tenantId;
        String baseSql = """
                from audit_login_log l
                where 1 = 1
                """;
        List<Object> params = new ArrayList<>();
        if (StringUtils.hasText(username)) {
            baseSql += " and l.username like ?";
            params.add(like(username));
        }
        if (StringUtils.hasText(loginType)) {
            baseSql += " and l.login_type = ?";
            params.add(loginType);
        }
        if (effectiveTenantId != null) {
            baseSql += " and l.tenant_id = ?";
            params.add(effectiveTenantId);
        }
        if (StringUtils.hasText(startTime)) {
            baseSql += " and l.created_at >= ?";
            params.add(parseDateTime(startTime));
        }
        if (StringUtils.hasText(endTime)) {
            baseSql += " and l.created_at <= ?";
            params.add(parseDateTime(endTime));
        }
        String selectSql = """
                select l.id, l.tenant_id as tenantId, l.user_id as userId, l.username, l.login_type as logType,
                       l.login_result as logResult, l.fail_reason as failReason, l.login_ip as loginIp,
                       l.user_agent as userAgent, l.request_id as requestId, l.trace_id as traceId, l.created_at as createdAt
                """ + baseSql + " order by l.id desc";
        return pageQuery(selectSql, "select count(1) " + baseSql, SystemVO.AuditLogVO.class, pageNo, pageSize, params);
    }

    public PageResponse<SystemVO.AuditLogVO> listOperationLogs(CurrentUser currentUser, String username, Long tenantId, long pageNo, long pageSize) {
        return listOperationLogs(currentUser, username, tenantId, null, null, pageNo, pageSize);
    }

    public PageResponse<SystemVO.AuditLogVO> listOperationLogs(
            CurrentUser currentUser,
            String username,
            Long tenantId,
            String startTime,
            String endTime,
            long pageNo,
            long pageSize
    ) {
        Long effectiveTenantId = tenantId == null ? currentTenantId(currentUser) : tenantId;
        String baseSql = """
                from audit_operation_log l
                where 1 = 1
                """;
        List<Object> params = new ArrayList<>();
        if (StringUtils.hasText(username)) {
            baseSql += " and l.username like ?";
            params.add(like(username));
        }
        if (effectiveTenantId != null) {
            baseSql += " and l.tenant_id = ?";
            params.add(effectiveTenantId);
        }
        if (StringUtils.hasText(startTime)) {
            baseSql += " and l.created_at >= ?";
            params.add(parseDateTime(startTime));
        }
        if (StringUtils.hasText(endTime)) {
            baseSql += " and l.created_at <= ?";
            params.add(parseDateTime(endTime));
        }
        String selectSql = """
                select l.id, l.tenant_id as tenantId, l.user_id as userId, l.username, l.module_name as moduleName,
                       l.action_name as actionName, l.operation_type as operationType, l.result_status as logResult,
                       l.detail_message as detailMessage, l.request_id as requestId, l.trace_id as traceId,
                       l.created_at as createdAt
                """ + baseSql + " order by l.id desc";
        return pageQuery(selectSql, "select count(1) " + baseSql, SystemVO.AuditLogVO.class, pageNo, pageSize, params);
    }

    public Integer countMenus(Long tenantId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(1) from sys_menu where tenant_id = ? and deleted = 0 and status = 'ENABLED'",
                Long.class,
                tenantId
        );
        return count == null ? 0 : count.intValue();
    }

    private SystemVO.BrandingSettingsVO loadBrandingSettings(Long tenantId) {
        Map<String, String> valueByKey = loadConfigValuesByKeys(tenantId, BRANDING_CONFIG_KEYS);

        SystemVO.BrandingSettingsVO settings = new SystemVO.BrandingSettingsVO();
        settings.setWebsiteName(defaultIfBlank(valueByKey.get(BRANDING_WEBSITE_NAME_KEY), "宏翔商道"));
        settings.setWebsiteFaviconUrl(defaultIfBlank(valueByKey.get(BRANDING_WEBSITE_FAVICON_URL_KEY), ""));
        settings.setWebsiteLogoUrl(defaultIfBlank(valueByKey.get(BRANDING_WEBSITE_LOGO_URL_KEY), ""));
        settings.setGithubLinkUrl(defaultIfBlank(valueByKey.get(BRANDING_GITHUB_LINK_URL_KEY), ""));
        settings.setHelpLinkUrl(defaultIfBlank(valueByKey.get(BRANDING_HELP_LINK_URL_KEY), ""));
        settings.setCompanyName(defaultIfBlank(valueByKey.get(BRANDING_COMPANY_NAME_KEY), settings.getWebsiteName()));
        settings.setCopyrightStartYear(parseInteger(valueByKey.get(BRANDING_COPYRIGHT_START_YEAR_KEY), LocalDate.now().getYear()));
        settings.setFooterIcp(defaultIfBlank(valueByKey.get(BRANDING_FOOTER_ICP_KEY), ""));
        settings.setFooterCopyright(defaultIfBlank(
                valueByKey.get(BRANDING_FOOTER_COPYRIGHT_KEY),
                buildCopyrightText(settings.getCompanyName(), settings.getCopyrightStartYear())
        ));
        return settings;
    }

    private SystemVO.AgreementSettingsVO loadAgreementSettings(Long tenantId) {
        Map<String, String> valueByKey = loadConfigValuesByKeys(tenantId, AGREEMENT_CONFIG_KEYS, false);

        SystemVO.AgreementSettingsVO settings = new SystemVO.AgreementSettingsVO();
        settings.setUserAgreementMarkdown(defaultIfBlank(valueByKey.get(AGREEMENT_USER_MARKDOWN_KEY), ""));
        settings.setPrivacyAgreementMarkdown(defaultIfBlank(valueByKey.get(AGREEMENT_PRIVACY_MARKDOWN_KEY), ""));
        return settings;
    }

    private SystemVO.SmtpSettingsVO loadSmtpSettings(Long tenantId) {
        Map<String, String> valueByKey = loadConfigValuesByKeys(tenantId, SMTP_CONFIG_KEYS);
        SystemVO.SmtpSettingsVO settings = new SystemVO.SmtpSettingsVO();
        settings.setHost(defaultIfBlank(valueByKey.get(SMTP_HOST_KEY), ""));
        settings.setPort(parseInteger(valueByKey.get(SMTP_PORT_KEY), 25));
        settings.setUsername(defaultIfBlank(valueByKey.get(SMTP_USERNAME_KEY), ""));
        settings.setPassword("");
        settings.setFrom(defaultIfBlank(valueByKey.get(SMTP_FROM_KEY), ""));
        settings.setAuthEnabled(Boolean.parseBoolean(defaultIfBlank(valueByKey.get(SMTP_AUTH_ENABLED_KEY), "true")));
        settings.setStartTlsEnabled(Boolean.parseBoolean(defaultIfBlank(valueByKey.get(SMTP_STARTTLS_ENABLED_KEY), "true")));
        settings.setSslEnabled(Boolean.parseBoolean(defaultIfBlank(valueByKey.get(SMTP_SSL_ENABLED_KEY), "false")));
        settings.setConfigured(
                StringUtils.hasText(settings.getHost())
                        && settings.getPort() != null
                        && settings.getPort() > 0
                        && StringUtils.hasText(settings.getFrom())
        );
        return settings;
    }

    private void upsertPlatformConfig(
            Long tenantId,
            String configKey,
            String configName,
            String configValue,
            String remark,
            Long operatorId
    ) {
        upsertBrandingConfig(tenantId, configKey, configName, configValue, remark, operatorId);
    }

    private JavaMailSenderImpl buildSmtpSender(Map<String, String> values) {
        String host = defaultIfBlank(values.get(SMTP_HOST_KEY), "");
        Integer port = parseInteger(values.get(SMTP_PORT_KEY), 25);
        String username = defaultIfBlank(values.get(SMTP_USERNAME_KEY), "");
        String password = defaultIfBlank(values.get(SMTP_PASSWORD_KEY), "");
        if (!StringUtils.hasText(host)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "请先配置 SMTP 主机");
        }
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port == null ? 25 : port);
        sender.setUsername(username);
        sender.setPassword(password);
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", String.valueOf(Boolean.parseBoolean(defaultIfBlank(values.get(SMTP_AUTH_ENABLED_KEY), "true"))));
        properties.put("mail.smtp.starttls.enable", String.valueOf(Boolean.parseBoolean(defaultIfBlank(values.get(SMTP_STARTTLS_ENABLED_KEY), "true"))));
        properties.put("mail.smtp.ssl.enable", String.valueOf(Boolean.parseBoolean(defaultIfBlank(values.get(SMTP_SSL_ENABLED_KEY), "false"))));
        properties.put("mail.smtp.connectiontimeout", "5000");
        properties.put("mail.smtp.timeout", "5000");
        properties.put("mail.smtp.writetimeout", "5000");
        return sender;
    }


    private Map<String, String> loadConfigValuesByKeys(Long tenantId, List<String> keys) {
        return loadConfigValuesByKeys(tenantId, keys, true);
    }

    private Map<String, String> loadConfigValuesByKeys(Long tenantId, List<String> keys, boolean trimValues) {
        Long effectiveTenantId = tenantId == null ? DEFAULT_PUBLIC_TENANT_ID : tenantId;
        String placeholders = keys.stream().map(item -> "?").collect(Collectors.joining(", "));
        String sql = """
                select tenant_id as tenantId, config_key as configKey, config_value as configValue
                from sys_config
                where deleted = 0
                  and config_scope = 'PLATFORM'
                  and config_key in (%s)
                  and (tenant_id = ? or tenant_id is null)
                order by case when tenant_id = ? then 0 else 1 end, id desc
                """.formatted(placeholders);
        List<Object> params = new ArrayList<>(keys);
        params.add(effectiveTenantId);
        params.add(effectiveTenantId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
        Map<String, String> valueByKey = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String configKey = String.valueOf(row.get("configKey"));
            if (!valueByKey.containsKey(configKey)) {
                valueByKey.put(
                        configKey,
                        trimValues ? normalizeConfigText(row.get("configValue")) : normalizeConfigTextRaw(row.get("configValue"))
                );
            }
        }
        return valueByKey;
    }

    private void upsertBrandingConfig(
            Long tenantId,
            String configKey,
            String configName,
            String configValue,
            String remark,
            Long operatorId
    ) {
        Long existingId = queryConfigId(configKey, tenantId);
        upsertConfigRecord(existingId, tenantId, configKey, configName, configValue, remark, operatorId);
    }

    private void upsertConfigValue(
            Long tenantId,
            String configKey,
            String configName,
            String configValue,
            String remark,
            Long operatorId
    ) {
        Long existingId = queryConfigId(configKey, tenantId);
        upsertConfigRecord(existingId, tenantId, configKey, configName, configValue, remark, operatorId);
    }

    private void upsertConfigRecord(
            Long existingId,
            Long tenantId,
            String configKey,
            String configName,
            String configValue,
            String remark,
            Long operatorId
    ) {
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

    private Long queryBrandingConfigId(String configKey, Long tenantId) {
        return queryConfigId(configKey, tenantId);
    }

    private Long queryConfigId(String configKey, Long tenantId) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select id
                            from sys_config
                            where config_key = ? and tenant_id <=> ?
                            order by id desc
                            limit 1
                            """,
                    Long.class,
                    configKey,
                    tenantId
            );
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private String sanitizeBrandingText(String value, String fallback) {
        String normalized = normalizeConfigText(value);
        return StringUtils.hasText(normalized) ? normalized : fallback;
    }

    private String sanitizeText(String value, String fallback) {
        String normalized = normalizeConfigText(value);
        return StringUtils.hasText(normalized) ? normalized : fallback;
    }

    private List<SystemVO.ProfileFieldSettingVO> loadProfileFieldSettings(Long tenantId) {
        Map<String, String> valueByKey = loadConfigValuesByKeys(tenantId, PROFILE_FIELD_CONFIG_KEYS);
        return PROFILE_FIELD_DEFINITIONS.stream().map(definition -> {
            SystemVO.ProfileFieldSettingVO item = new SystemVO.ProfileFieldSettingVO();
            item.setFieldKey(definition.fieldKey());
            item.setFieldLabel(definition.fieldLabel());
            item.setFieldDescription(definition.fieldDescription());
            item.setVisible(Boolean.parseBoolean(defaultIfBlank(valueByKey.get(definition.configKey()), String.valueOf(definition.defaultVisible()))));
            return item;
        }).toList();
    }

    private String normalizeNullableText(String value) {
        String normalized = normalizeConfigText(value);
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private String buildCopyrightText(String companyName, Integer copyrightStartYear) {
        int currentYear = LocalDate.now().getYear();
        int startYear = copyrightStartYear == null ? currentYear : copyrightStartYear;
        String yearLabel = startYear < currentYear ? startYear + "-" + currentYear : String.valueOf(startYear);
        String owner = StringUtils.hasText(companyName) ? companyName : "宏翔商道";
        return "Copyright © " + yearLabel + " " + owner + " All Rights Reserved";
    }

    private static final class ProfileFieldDefinition {
        private final String fieldKey;
        private final String fieldLabel;
        private final String fieldDescription;
        private final String configKey;
        private final boolean defaultVisible;

        private ProfileFieldDefinition(String fieldKey, String fieldLabel, String fieldDescription, String configKey, boolean defaultVisible) {
            this.fieldKey = fieldKey;
            this.fieldLabel = fieldLabel;
            this.fieldDescription = fieldDescription;
            this.configKey = configKey;
            this.defaultVisible = defaultVisible;
        }

        private String fieldKey() {
            return fieldKey;
        }

        private String fieldLabel() {
            return fieldLabel;
        }

        private String fieldDescription() {
            return fieldDescription;
        }

        private String configKey() {
            return configKey;
        }

        private boolean defaultVisible() {
            return defaultVisible;
        }
    }

    private Integer parseInteger(String value, Integer fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String normalizeConfigText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String normalizeConfigTextRaw(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String normalizeMarkdownText(String value) {
        return value == null ? "" : value;
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private SecuritySettingsService.SecuritySettingsSnapshot toSnapshot(
            SecuritySettingsService.SecuritySettingsSnapshot current,
            SystemDTO.SecuritySettingsRequest request
    ) {
        SecuritySettingsService.SecuritySettingsSnapshot snapshot = new SecuritySettingsService.SecuritySettingsSnapshot();
        snapshot.setIdleTimeoutSeconds(request.getIdleTimeoutSeconds());
        snapshot.setAccessTokenExpireSeconds(request.getAccessTokenExpireSeconds());
        snapshot.setRefreshTokenExpireSeconds(request.getRefreshTokenExpireSeconds());
        snapshot.setAllowMultiDeviceLogin(Boolean.TRUE.equals(request.getAllowMultiDeviceLogin()));
        snapshot.setCaptchaEnabled(Boolean.TRUE.equals(request.getCaptchaEnabled()));
        snapshot.setCaptchaType(defaultIfBlank(request.getCaptchaType(), "IMAGE").trim().toUpperCase());
        snapshot.setLoginDefenseWindowMinutes(current.getLoginDefenseWindowMinutes());
        snapshot.setLoginMaxValidationAttempts(current.getLoginMaxValidationAttempts());
        snapshot.setLoginMaxFailureCount(current.getLoginMaxFailureCount());
        snapshot.setPasswordMinLength(current.getPasswordMinLength());
        snapshot.setPasswordRequireUppercase(current.isPasswordRequireUppercase());
        snapshot.setPasswordRequireLowercase(current.isPasswordRequireLowercase());
        snapshot.setPasswordRequireSpecialCharacter(current.isPasswordRequireSpecialCharacter());
        snapshot.setPasswordAllowConsecutiveCharacters(current.isPasswordAllowConsecutiveCharacters());
        return snapshot;
    }

    private SystemVO.SecuritySettingsVO toSecuritySettingsVO(SecuritySettingsService.SecuritySettingsSnapshot snapshot) {
        SystemVO.SecuritySettingsVO vo = new SystemVO.SecuritySettingsVO();
        vo.setIdleTimeoutSeconds(snapshot.getIdleTimeoutSeconds());
        vo.setAccessTokenExpireSeconds(snapshot.getAccessTokenExpireSeconds());
        vo.setRefreshTokenExpireSeconds(snapshot.getRefreshTokenExpireSeconds());
        vo.setAllowMultiDeviceLogin(snapshot.isAllowMultiDeviceLogin());
        vo.setCaptchaEnabled(snapshot.isCaptchaEnabled());
        vo.setCaptchaType(defaultIfBlank(snapshot.getCaptchaType(), "IMAGE").trim().toUpperCase());
        vo.setLoginDefenseWindowMinutes(snapshot.getLoginDefenseWindowMinutes());
        vo.setLoginMaxValidationAttempts(snapshot.getLoginMaxValidationAttempts());
        vo.setLoginMaxFailureCount(snapshot.getLoginMaxFailureCount());
        vo.setPasswordMinLength(snapshot.getPasswordMinLength());
        vo.setPasswordRequireUppercase(snapshot.isPasswordRequireUppercase());
        vo.setPasswordRequireLowercase(snapshot.isPasswordRequireLowercase());
        vo.setPasswordRequireSpecialCharacter(snapshot.isPasswordRequireSpecialCharacter());
        vo.setPasswordAllowConsecutiveCharacters(snapshot.isPasswordAllowConsecutiveCharacters());
        return vo;
    }

    private Long currentTenantId(CurrentUser currentUser) {
        if (currentUser.getCurrentTenantId() != null) {
            return currentUser.getCurrentTenantId();
        }
        return tenantDomainService.listUserTenantAccess(currentUser.getUserId()).stream()
                .filter(access -> access.getTenant() != null)
                .findFirst()
                .map(access -> access.getTenant().getId())
                .orElseThrow(() -> new BizException(ErrorCode.TENANT_NOT_BOUND, "当前账号没有可用租户"));
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private List<String> listCurrentTenantRoleNames(Long userId, Long tenantId) {
        return jdbcTemplate.queryForList(
                """
                        select r.role_name
                        from sys_user_role ur
                        join sys_role r on r.id = ur.role_id and r.tenant_id = ur.tenant_id and r.deleted = 0
                        where ur.user_id = ? and ur.tenant_id = ? and ur.deleted = 0
                        order by r.id asc
                        """,
                String.class,
                userId,
                tenantId
        );
    }

    private List<String> listUserTenantNames(Long userId) {
        return jdbcTemplate.queryForList(
                """
                        select t.tenant_name
                        from sys_user_tenant ut
                        join tenant_info t on t.id = ut.tenant_id and t.deleted = 0
                        where ut.user_id = ? and ut.deleted = 0
                        order by t.id asc
                        """,
                String.class,
                userId
        );
    }

    private List<Long> listUserTenantIds(Long userId) {
        return jdbcTemplate.queryForList(
                """
                        select ut.tenant_id
                        from sys_user_tenant ut
                        where ut.user_id = ? and ut.deleted = 0
                        order by ut.tenant_id asc
                        """,
                Long.class,
                userId
        );
    }

    private List<Long> listUserRoleIds(Long userId, Long tenantId) {
        return jdbcTemplate.queryForList(
                """
                        select ur.role_id
                        from sys_user_role ur
                        where ur.user_id = ? and ur.tenant_id = ? and ur.deleted = 0
                        order by ur.role_id asc
                        """,
                Long.class,
                userId,
                tenantId
        );
    }

    private List<String> listUserRoleNames(Long userId, Long tenantId) {
        return jdbcTemplate.queryForList(
                """
                        select r.role_name
                        from sys_user_role ur
                        join sys_role r on r.id = ur.role_id and r.tenant_id = ur.tenant_id and r.deleted = 0
                        where ur.user_id = ? and ur.tenant_id = ? and ur.deleted = 0
                        order by r.id asc
                        """,
                String.class,
                userId,
                tenantId
        );
    }

    private Integer countRolePermissions(Long roleId, Long tenantId) {
        Long count = jdbcTemplate.queryForObject(
                """
                        select count(1)
                        from sys_role_permission
                        where role_id = ? and tenant_id = ? and deleted = 0
                        """,
                Long.class,
                roleId,
                tenantId
        );
        return count == null ? 0 : count.intValue();
    }

    private Integer countRoleUsers(Long roleId, Long tenantId) {
        Long count = jdbcTemplate.queryForObject(
                """
                        select count(1)
                        from sys_user_role
                        where role_id = ? and tenant_id = ? and deleted = 0
                        """,
                Long.class,
                roleId,
                tenantId
        );
        return count == null ? 0 : count.intValue();
    }

    private List<String> listRolePermissionKeys(Long roleId, Long tenantId) {
        return jdbcTemplate.queryForList(
                """
                        select permission_key
                        from sys_role_permission
                        where role_id = ? and tenant_id = ? and deleted = 0
                        order by permission_key asc
                        """,
                String.class,
                roleId,
                tenantId
        );
    }

    private SystemVO.UserVO queryUser(Long tenantId, Long userId) {
        SystemVO.UserVO user = queryOne(
                """
                        select u.id, u.username, u.mobile, u.id_card_number as idCardNumber, u.nickname, u.real_name as realName, u.avatar_url as avatarUrl,
                               u.email, u.birth_month as birthMonth, u.gender, u.region, u.available_time as availableTime,
                               u.status, u.created_at as createdAt, u.updated_at as updatedAt
                        from sys_user u
                        join sys_user_tenant ut on ut.user_id = u.id and ut.tenant_id = ? and ut.deleted = 0
                        where u.id = ? and u.deleted = 0
                        """,
                SystemVO.UserVO.class,
                tenantId,
                userId
        );
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        user.setTenantNames(listUserTenantNames(user.getId()));
        user.setRoleNames(listUserRoleNames(user.getId(), tenantId));
        return user;
    }

    private Long insertOrUpdateUser(Long userId, SystemDTO.UserUpsertRequest request, Long operatorId) {
        if (userId != null && isProtectedAdminAccount(userId, request.getUsername()) && "DISABLED".equalsIgnoreCase(request.getStatus())) {
            throw new BizException(ErrorCode.FORBIDDEN, "默认管理员账户不允许被禁用");
        }
        if (userId == null) {
            if (!StringUtils.hasText(request.getPassword())) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "初始密码不能为空");
            }
            String password = request.getPassword();
            passwordPolicyService.validatePassword(password);
            jdbcTemplate.update(
                    """
                            insert into sys_user (
                                username, password_hash, mobile, nickname, real_name, avatar_url, email, birth_month, gender, region,
                                available_time, id_card_number, status,
                                created_by, updated_by, deleted
                            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                            """,
                    request.getUsername(),
                    passwordEncoder.encode(password),
                    normalizeNullableText(request.getMobile()),
                    normalizeNullableText(request.getNickname()),
                    normalizeNullableText(request.getRealName()),
                    normalizeNullableText(request.getAvatarUrl()),
                    normalizeNullableText(request.getEmail()),
                    normalizeNullableText(request.getBirthMonth()),
                    normalizeNullableText(request.getGender()),
                    normalizeNullableText(request.getRegion()),
                    normalizeNullableText(request.getAvailableTime()),
                    normalizeNullableText(request.getIdCardNumber()),
                    request.getStatus(),
                    operatorId,
                    operatorId
            );
            return jdbcTemplate.queryForObject("select id from sys_user where username = ? and deleted = 0 order by id desc limit 1", Long.class, request.getUsername());
        }
        jdbcTemplate.update(
                """
                        update sys_user
                        set username = ?, mobile = ?, nickname = ?, real_name = ?, avatar_url = ?, email = ?,
                            birth_month = ?, gender = ?, region = ?, available_time = ?, id_card_number = ?, status = ?,
                            updated_by = ?, updated_at = ?
                        where id = ? and deleted = 0
                        """,
                request.getUsername(),
                normalizeNullableText(request.getMobile()),
                normalizeNullableText(request.getNickname()),
                normalizeNullableText(request.getRealName()),
                normalizeNullableText(request.getAvatarUrl()),
                normalizeNullableText(request.getEmail()),
                normalizeNullableText(request.getBirthMonth()),
                normalizeNullableText(request.getGender()),
                normalizeNullableText(request.getRegion()),
                normalizeNullableText(request.getAvailableTime()),
                normalizeNullableText(request.getIdCardNumber()),
                request.getStatus(),
                operatorId,
                LocalDateTime.now(),
                userId
        );
        if (StringUtils.hasText(request.getPassword())) {
            passwordPolicyService.validatePassword(request.getPassword());
            jdbcTemplate.update(
                    "update sys_user set password_hash = ?, updated_by = ?, updated_at = ? where id = ? and deleted = 0",
                    passwordEncoder.encode(request.getPassword()),
                    operatorId,
                    LocalDateTime.now(),
                    userId
            );
        }
        return userId;
    }

    private boolean isProtectedAdminAccount(Long userId, String username) {
        return DEFAULT_ADMIN_USER_ID.equals(userId)
                || (StringUtils.hasText(username) && DEFAULT_ADMIN_USERNAME.equalsIgnoreCase(username));
    }

    private void upsertUserTenantRelation(Long userId, Long tenantId, boolean isDefault, Long operatorId) {
        jdbcTemplate.update(
                """
                        insert into sys_user_tenant (tenant_id, user_id, is_default, status, created_by, updated_by, deleted)
                        values (?, ?, ?, 'ENABLED', ?, ?, 0)
                        on duplicate key update is_default = values(is_default), status = values(status),
                                                 updated_by = values(updated_by), updated_at = current_timestamp, deleted = 0
                        """,
                tenantId,
                userId,
                isDefault ? 1 : 0,
                operatorId,
                operatorId
        );
    }

    private void replaceUserRoles(Long userId, Long tenantId, List<Long> roleIds, Long operatorId) {
        jdbcTemplate.update(
                "delete from sys_user_role where tenant_id = ? and user_id = ?",
                tenantId,
                userId
        );
        if (CollectionUtils.isEmpty(roleIds)) {
            return;
        }
        for (Long roleId : new LinkedHashSet<>(roleIds)) {
            jdbcTemplate.update(
                    """
                            insert into sys_user_role (tenant_id, user_id, role_id, created_by, updated_by, deleted)
                            values (?, ?, ?, ?, ?, 0)
                            """,
                    tenantId,
                    userId,
                    roleId,
                    operatorId,
                    operatorId
            );
        }
    }

    private Long upsertRole(Long roleId, Long tenantId, SystemDTO.RoleUpsertRequest request, Long operatorId) {
        if (roleId == null) {
            jdbcTemplate.update(
                    """
                            insert into sys_role (tenant_id, role_code, role_name, role_type, created_by, updated_by, deleted)
                            values (?, ?, ?, ?, ?, ?, 0)
                            """,
                    tenantId,
                    request.getRoleCode(),
                    request.getRoleName(),
                    request.getRoleType(),
                    operatorId,
                    operatorId
            );
            return jdbcTemplate.queryForObject(
                    "select id from sys_role where tenant_id = ? and role_code = ? and deleted = 0 order by id desc limit 1",
                    Long.class,
                    tenantId,
                    request.getRoleCode()
            );
        }
        jdbcTemplate.update(
                """
                        update sys_role
                        set role_code = ?, role_name = ?, role_type = ?, updated_by = ?, updated_at = ?
                        where id = ? and tenant_id = ? and deleted = 0
                        """,
                request.getRoleCode(),
                request.getRoleName(),
                request.getRoleType(),
                operatorId,
                LocalDateTime.now(),
                roleId,
                tenantId
        );
        return roleId;
    }

    private void replaceRolePermissions(Long tenantId, Long roleId, List<String> permissionKeys, Long operatorId) {
        jdbcTemplate.update(
                "delete from sys_role_permission where tenant_id = ? and role_id = ?",
                tenantId,
                roleId
        );
        if (CollectionUtils.isEmpty(permissionKeys)) {
            return;
        }
        for (String permissionKey : new LinkedHashSet<>(permissionKeys)) {
            jdbcTemplate.update(
                    """
                            insert into sys_role_permission (tenant_id, role_id, permission_key, created_by, updated_by, deleted)
                            values (?, ?, ?, ?, ?, 0)
                            """,
                    tenantId,
                    roleId,
                    permissionKey,
                    operatorId,
                    operatorId
            );
        }
    }

    private Long insertMenu(Long menuId, Long tenantId, SystemDTO.MenuUpsertRequest request, Long operatorId) {
        if (menuId == null) {
            jdbcTemplate.update(
                    """
                            insert into sys_menu (
                                tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no,
                                permission_key, status, created_by, updated_by, deleted
                            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                            """,
                    tenantId,
                    request.getParentId() == null ? 0L : request.getParentId(),
                    request.getMenuCode(),
                    request.getMenuName(),
                    request.getMenuType(),
                    request.getPath(),
                    request.getComponent(),
                    request.getIcon(),
                    request.getSortNo() == null ? 0 : request.getSortNo(),
                    request.getPermissionKey(),
                    request.getStatus(),
                    operatorId,
                    operatorId
            );
            return jdbcTemplate.queryForObject(
                    "select id from sys_menu where tenant_id = ? and menu_code = ? and deleted = 0 order by id desc limit 1",
                    Long.class,
                    tenantId,
                    request.getMenuCode()
            );
        }
        jdbcTemplate.update(
                """
                        update sys_menu
                        set parent_id = ?, menu_code = ?, menu_name = ?, menu_type = ?, path = ?, component = ?,
                            icon = ?, sort_no = ?, permission_key = ?, status = ?, updated_by = ?, updated_at = ?
                        where id = ? and tenant_id = ? and deleted = 0
                        """,
                request.getParentId() == null ? 0L : request.getParentId(),
                request.getMenuCode(),
                request.getMenuName(),
                request.getMenuType(),
                request.getPath(),
                request.getComponent(),
                request.getIcon(),
                request.getSortNo() == null ? 0 : request.getSortNo(),
                request.getPermissionKey(),
                request.getStatus(),
                operatorId,
                LocalDateTime.now(),
                menuId,
                tenantId
        );
        return menuId;
    }

    private Long upsertDictType(Long id, Long tenantId, SystemDTO.DictTypeUpsertRequest request, Long operatorId) {
        if (id == null) {
            jdbcTemplate.update(
                    """
                            insert into sys_dict_type (tenant_id, dict_code, dict_name, status, is_system, remark, created_by, updated_by, deleted)
                            values (?, ?, ?, ?, 0, ?, ?, ?, 0)
                            """,
                    tenantId,
                    request.getDictCode(),
                    request.getDictName(),
                    request.getStatus(),
                    request.getRemark(),
                    operatorId,
                    operatorId
            );
            return jdbcTemplate.queryForObject(
                    "select id from sys_dict_type where tenant_id <=> ? and dict_code = ? and deleted = 0 order by id desc limit 1",
                    Long.class,
                    tenantId,
                    request.getDictCode()
            );
        }
        jdbcTemplate.update(
                """
                        update sys_dict_type
                        set dict_code = ?, dict_name = ?, status = ?, remark = ?, updated_by = ?, updated_at = ?
                        where id = ? and deleted = 0
                        """,
                request.getDictCode(),
                request.getDictName(),
                request.getStatus(),
                request.getRemark(),
                operatorId,
                LocalDateTime.now(),
                id
        );
        return id;
    }

    private Long upsertDictItem(Long id, Long dictTypeId, SystemDTO.DictItemUpsertRequest request, Long operatorId) {
        if (id == null) {
            jdbcTemplate.update(
                    """
                            insert into sys_dict_item (dict_type_id, item_label, item_value, sort_no, status, remark, created_by, updated_by, deleted)
                            values (?, ?, ?, ?, ?, ?, ?, ?, 0)
                            """,
                    dictTypeId,
                    request.getItemLabel(),
                    request.getItemValue(),
                    request.getSortNo(),
                    request.getStatus(),
                    request.getRemark(),
                    operatorId,
                    operatorId
            );
            return jdbcTemplate.queryForObject(
                    "select id from sys_dict_item where dict_type_id = ? and item_value = ? and deleted = 0 order by id desc limit 1",
                    Long.class,
                    dictTypeId,
                    request.getItemValue()
            );
        }
        jdbcTemplate.update(
                """
                        update sys_dict_item
                        set item_label = ?, item_value = ?, sort_no = ?, status = ?, remark = ?, updated_by = ?, updated_at = ?
                        where id = ? and dict_type_id = ? and deleted = 0
                        """,
                request.getItemLabel(),
                request.getItemValue(),
                request.getSortNo(),
                request.getStatus(),
                request.getRemark(),
                operatorId,
                LocalDateTime.now(),
                id,
                dictTypeId
        );
        return id;
    }

    private <T> PageResponse<T> pageQuery(String selectSql, String countSql, Class<T> voClass, long pageNo, long pageSize, List<Object> params) {
        long safePageNo = pageNo <= 0 ? 1 : pageNo;
        long safePageSize = pageSize <= 0 ? 10 : pageSize;
        long offset = (safePageNo - 1) * safePageSize;
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(safePageSize);
        queryParams.add(offset);
        String pagedSql = selectSql + " limit ? offset ?";
        List<T> records = jdbcTemplate.query(pagedSql, new BeanPropertyRowMapper<>(voClass), queryParams.toArray());
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());
        PageResponse<T> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(total == null ? 0 : total);
        response.setPageNo(safePageNo);
        response.setPageSize(safePageSize);
        return response;
    }

    private List<SystemVO.MenuVO> buildMenuTree(List<SystemVO.MenuVO> flatMenus) {
        Map<Long, SystemVO.MenuVO> index = new LinkedHashMap<>();
        List<SystemVO.MenuVO> roots = new ArrayList<>();
        for (SystemVO.MenuVO menu : flatMenus) {
            menu.setChildren(new ArrayList<>());
            index.put(menu.getId(), menu);
        }
        for (SystemVO.MenuVO menu : flatMenus) {
            Long parentId = menu.getParentId();
            if (parentId == null || parentId == 0 || !index.containsKey(parentId)) {
                roots.add(menu);
                continue;
            }
            index.get(parentId).getChildren().add(menu);
        }
        Comparator<SystemVO.MenuVO> comparator = Comparator.comparingInt(item -> item.getSortNo() == null ? 0 : item.getSortNo());
        roots.sort(comparator);
        for (SystemVO.MenuVO root : roots) {
            sortChildren(root, comparator);
        }
        return roots;
    }

    private void sortChildren(SystemVO.MenuVO menu, Comparator<SystemVO.MenuVO> comparator) {
        if (menu.getChildren() == null || menu.getChildren().isEmpty()) {
            return;
        }
        menu.getChildren().sort(comparator);
        for (SystemVO.MenuVO child : menu.getChildren()) {
            sortChildren(child, comparator);
        }
    }

    private List<String> splitCsv(String csv) {
        if (!StringUtils.hasText(csv)) {
            return List.of();
        }
        return List.of(csv.split(","));
    }

    private void copyUser(SystemVO.UserDetailVO target, SystemVO.UserVO source) {
        target.setId(source.getId());
        target.setUsername(source.getUsername());
        target.setMobile(source.getMobile());
        target.setIdCardNumber(source.getIdCardNumber());
        target.setNickname(source.getNickname());
        target.setRealName(source.getRealName());
        target.setAvatarUrl(source.getAvatarUrl());
        target.setEmail(source.getEmail());
        target.setBirthMonth(source.getBirthMonth());
        target.setGender(source.getGender());
        target.setRegion(source.getRegion());
        target.setAvailableTime(source.getAvailableTime());
        target.setStatus(source.getStatus());
        target.setTenantNames(source.getTenantNames());
        target.setRoleNames(source.getRoleNames());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
    }

    private void copyRole(SystemVO.RoleDetailVO target, SystemVO.RoleVO source) {
        target.setId(source.getId());
        target.setTenantId(source.getTenantId());
        target.setRoleCode(source.getRoleCode());
        target.setRoleName(source.getRoleName());
        target.setRoleType(source.getRoleType());
        target.setPermissionCount(source.getPermissionCount());
        target.setUserCount(source.getUserCount());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
    }

    private static SystemVO.ShortcutVO shortcut(String title, String description, String path, String permission) {
        SystemVO.ShortcutVO shortcut = new SystemVO.ShortcutVO();
        shortcut.setTitle(title);
        shortcut.setDescription(description);
        shortcut.setPath(path);
        shortcut.setPermission(permission);
        return shortcut;
    }

    private String like(String value) {
        return "%" + value.trim() + "%";
    }

    private LocalDateTime parseDateTime(String value) {
        return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private <T> T queryOne(String sql, Class<T> voClass, Object... params) {
        try {
            return jdbcTemplate.queryForObject(sql, new BeanPropertyRowMapper<>(voClass), params);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }
}
