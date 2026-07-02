package com.lumira.saas.modules.system.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.domain.event.DomainEventPublisher;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.FieldCryptoService;
import com.lumira.saas.infrastructure.security.service.AuthSessionStore;
import com.lumira.saas.infrastructure.security.service.SecuritySettingsService;
import com.lumira.saas.infrastructure.readmodel.ReadModelVersionService;
import com.lumira.saas.modules.audit.app.LoginAuditService;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.auth.vo.CurrentUserVO;
import com.lumira.saas.modules.iam.service.IamUserAccount;
import com.lumira.saas.modules.iam.service.IamUserService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.permission.SystemPermissionTreeAssembler;
import com.lumira.saas.modules.system.app.OnlineSessionManagementAppService;
import com.lumira.saas.modules.system.dto.ProfileDTO;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.audit.vo.AuditLogVO;
import com.lumira.saas.modules.system.profile.vo.ProfileFieldSettingVO;
import com.lumira.saas.modules.plugin.vo.PluginVO;
import com.lumira.saas.modules.system.plugin.SystemPluginViewService;
import com.lumira.saas.modules.system.role.app.SystemRoleManagementAppService;
import com.lumira.saas.modules.system.user.app.SystemUserManagementAppService;
import com.lumira.saas.modules.system.user.vo.UserDetailVO;
import com.lumira.saas.modules.system.verification.SystemVerificationAppService;
import com.lumira.saas.modules.system.vo.SystemVO;
import com.lumira.saas.modules.user.domain.UserDomainService;
import com.lumira.saas.modules.user.entity.SysUserEntity;
import com.lumira.saas.infrastructure.security.service.PasswordPolicyService;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.beans.factory.annotation.Autowired;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnLumiraControlPlaneEnabled
public class SystemManagementAppService {

    private static final long MAX_PAGE_SIZE = 100L;
    private static final Long DEFAULT_ADMIN_USER_ID = 1001L;
    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String BRANDING_WEBSITE_NAME_KEY = "branding.website-name";
    private static final String BRANDING_WEBSITE_FAVICON_URL_KEY = "branding.website-favicon-url";
    private static final String BRANDING_WEBSITE_LOGO_URL_KEY = "branding.website-logo-url";
    private static final String BRANDING_LOGIN_BACKGROUND_URL_KEY = "branding.login-background-url";
    private static final String BRANDING_GITHUB_LINK_URL_KEY = "branding.github-link-url";
    private static final String BRANDING_HELP_LINK_URL_KEY = "branding.help-link-url";
    private static final String BRANDING_COMPANY_NAME_KEY = "branding.company-name";
    private static final String BRANDING_COPYRIGHT_START_YEAR_KEY = "branding.copyright-start-year";
    private static final String BRANDING_FOOTER_ICP_KEY = "branding.footer-icp";
    private static final String BRANDING_FOOTER_POLICE_BEIAN_KEY = "branding.footer-police-beian";
    private static final String BRANDING_FOOTER_COPYRIGHT_KEY = "branding.footer-copyright";
    private static final String EXTRA_PROFILE_VALUES_KEY = "customProfileValues";
    private static final int CUSTOM_PROFILE_VALUE_MAX_LENGTH = 1000;
    private static final int MENU_COUNT_CACHE_MAX_ENTRIES = 1024;
    private static final long MENU_COUNT_CACHE_TTL_MS = 30_000L;
    private static final String MENU_COUNT_CACHE_KEY = "global-menu-count";
    private static final int PERMISSION_CATALOG_CACHE_MAX_ENTRIES = 1024;
    private static final long PERMISSION_CATALOG_CACHE_TTL_MS = 300_000L;
    private static final int VERSIONED_MENU_TREE_CACHE_MAX_ENTRIES = 32;
    private static final int VERSIONED_PERMISSION_TREE_CACHE_MAX_ENTRIES = 32;
    private static final long VERSIONED_TREE_CACHE_TTL_MS = 300_000L;
    private static final long READ_MODEL_VERSION_CACHE_TTL_MS = 2_000L;
    private static final String MENU_TREE_READ_MODEL_VERSION_CACHE_KEY = "platform:menu-tree";
    private static final String READ_MODEL_CONTEXT_PLATFORM = "platform";
    private static final String READ_MODEL_SCOPE_MENU_TREE = "menu-tree";
    private static final String PERMISSION_TREE_CACHE_KEY_PREFIX = "permission-tree:";
    private static final String PERMISSION_CATALOG_CACHE_KEY_PREFIX = "permission-catalog:";
    private static final Set<String> BUILTIN_ROOT_MENU_CODES = Set.of(
            "dashboard.home",
            "aiadc.root",
            "team.root",
            "user.center.root",
            "user.center.personal",
            "settings.root"
    );
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DomainEventPublisher NOOP_DOMAIN_EVENT_PUBLISHER = event -> {
    };
    private static final java.util.concurrent.Executor BLOCKING_IO_EXECUTOR = command -> Thread.ofVirtual().start(command);
    private static final List<String> BRANDING_CONFIG_KEYS = List.of(
            BRANDING_WEBSITE_NAME_KEY,
            BRANDING_WEBSITE_FAVICON_URL_KEY,
            BRANDING_WEBSITE_LOGO_URL_KEY,
            BRANDING_LOGIN_BACKGROUND_URL_KEY,
            BRANDING_GITHUB_LINK_URL_KEY,
            BRANDING_HELP_LINK_URL_KEY,
            BRANDING_COMPANY_NAME_KEY,
            BRANDING_COPYRIGHT_START_YEAR_KEY,
            BRANDING_FOOTER_ICP_KEY,
            BRANDING_FOOTER_POLICE_BEIAN_KEY,
            BRANDING_FOOTER_COPYRIGHT_KEY
    );

    private static final String AGREEMENT_USER_MARKDOWN_KEY = "agreement.user-agreement-markdown";
    private static final String AGREEMENT_PRIVACY_MARKDOWN_KEY = "agreement.privacy-agreement-markdown";
    private static final List<String> AGREEMENT_CONFIG_KEYS = List.of(
            AGREEMENT_USER_MARKDOWN_KEY,
            AGREEMENT_PRIVACY_MARKDOWN_KEY
    );

    private static final String SMTP_HOST_KEY = "smtp.host";
    private static final String SMTP_ENABLED_KEY = "smtp.enabled";
    private static final String SMTP_PORT_KEY = "smtp.port";
    private static final String SMTP_USERNAME_KEY = "smtp.username";
    private static final String SMTP_PASSWORD_KEY = "smtp.password";
    private static final String WECHAT_LOGIN_APP_SECRET_KEY = "verification.wechat-login.app-secret";
    private static final String MASKED_CONFIG_VALUE = "******";
    private static final Set<String> SENSITIVE_CONFIG_KEYS = Set.of(WECHAT_LOGIN_APP_SECRET_KEY);
    private static final String SMTP_FROM_KEY = "smtp.from";
    private static final String SMTP_AUTH_ENABLED_KEY = "smtp.auth-enabled";
    private static final String SMTP_STARTTLS_ENABLED_KEY = "smtp.starttls-enabled";
    private static final String SMTP_SSL_ENABLED_KEY = "smtp.ssl-enabled";
    private static final List<String> SMTP_CONFIG_KEYS = List.of(
            SMTP_ENABLED_KEY,
            SMTP_HOST_KEY,
            SMTP_PORT_KEY,
            SMTP_USERNAME_KEY,
            SMTP_PASSWORD_KEY,
            SMTP_FROM_KEY,
            SMTP_AUTH_ENABLED_KEY,
            SMTP_STARTTLS_ENABLED_KEY,
            SMTP_SSL_ENABLED_KEY
    );

    private static final String DEFAULT_LOCALE = "zh-CN";
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

    private static final Set<String> SENSITIVE_CONFIG_KEY_SUFFIXES = Set.of(
            ".app-secret",
            ".password",
            ".secret",
            ".access-key-secret",
            ".private-key"
    );
    private static final List<SystemVO.ShortcutVO> DASHBOARD_SHORTCUTS = List.of(
            shortcut("系统管理", "菜单、字典、配置与验证入口", "/settings/menus", "system:menu:view"),
            shortcut("验证管理", "2FA 开关与短信验证码配置", "/settings/verification", "system:verification:view"),
            shortcut("在线用户", "实时会话、踢出和封禁", "/user-center/online-users", "system:online-user:view"),
            shortcut("个性化设置", "站点名称、Logo、Icon 和页脚信息", "/settings/personalization", "system:config:view"),
            shortcut("安全设置", "空闲超时与 token 生命周期", "/settings/security", "system:config:view"),
            shortcut("审计中心", "登录和操作日志", "/settings/audit", "audit:view"),
            shortcut("通知中心", "通知归档与手动发布", "/settings/notifications", "system:notification:view"),
            shortcut("插件管理", "插件安装、启用和运行态", "/settings/plugins", "plugin:management:view")
    );
    private final MyBatisQueryOperations jdbcTemplate;
    private final UserDomainService userDomainService;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemPluginViewService systemPluginViewService;
    private final OnlineSessionManagementAppService onlineSessionManagementAppService;
    private final SystemVerificationAppService systemVerificationAppService;
    private final SystemPlatformSettingsAppService systemPlatformSettingsAppService;
    private final SystemProfileSettingsAppService systemProfileSettingsAppService;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionStore authSessionStore;
    private final LoginAuditService loginAuditService;
    private final OperationAuditService operationAuditService;
    private final SecuritySettingsService securitySettingsService;
    private final PasswordPolicyService passwordPolicyService;
    private final IamUserService iamUserService;
    private final SystemUserManagementAppService systemUserManagementAppService;
    private final SystemRoleManagementAppService systemRoleManagementAppService;
    private final FieldCryptoService fieldCryptoService;
    private final ReadModelVersionService readModelVersionService;
    private final Cache<String, Integer> menuCountCache;
    private final Cache<String, List<SystemVO.PermissionVO>> permissionCatalogCache;
    private final Cache<Long, List<SystemVO.MenuVO>> menuTreeCache;
    private final Cache<String, List<SystemVO.PermissionTreeVO>> permissionTreeCache;
    private final Cache<String, CachedReadModelVersion> readModelVersionCache;
    private final SystemPermissionTreeAssembler permissionTreeAssembler = new SystemPermissionTreeAssembler();

    @Autowired
    public SystemManagementAppService(
            MyBatisQueryOperations jdbcTemplate,
            UserDomainService userDomainService,
            PermissionSnapshotService permissionSnapshotService,
            SystemPluginViewService systemPluginViewService,
            OnlineSessionManagementAppService onlineSessionManagementAppService,
            SystemVerificationAppService systemVerificationAppService,
            SystemPlatformSettingsAppService systemPlatformSettingsAppService,
            SystemProfileSettingsAppService systemProfileSettingsAppService,
            PasswordEncoder passwordEncoder,
            AuthSessionStore authSessionStore,
            LoginAuditService loginAuditService,
            OperationAuditService operationAuditService,
            SecuritySettingsService securitySettingsService,
            PasswordPolicyService passwordPolicyService,
            IamUserService iamUserService,
            SystemUserManagementAppService systemUserManagementAppService,
            SystemRoleManagementAppService systemRoleManagementAppService,
            FieldCryptoService fieldCryptoService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.userDomainService = userDomainService;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemPluginViewService = systemPluginViewService;
        this.onlineSessionManagementAppService = onlineSessionManagementAppService;
        this.systemVerificationAppService = systemVerificationAppService;
        this.systemPlatformSettingsAppService = systemPlatformSettingsAppService;
        this.systemProfileSettingsAppService = systemProfileSettingsAppService;
        this.passwordEncoder = passwordEncoder;
        this.authSessionStore = authSessionStore;
        this.loginAuditService = loginAuditService;
        this.operationAuditService = operationAuditService;
        this.securitySettingsService = securitySettingsService;
        this.passwordPolicyService = passwordPolicyService;
        this.iamUserService = iamUserService;
        this.systemUserManagementAppService = systemUserManagementAppService;
        this.systemRoleManagementAppService = systemRoleManagementAppService;
        this.fieldCryptoService = fieldCryptoService;
        this.readModelVersionService = new ReadModelVersionService(jdbcTemplate);
        this.menuCountCache = CacheBuilder.newBuilder()
                .maximumSize(MENU_COUNT_CACHE_MAX_ENTRIES)
                .expireAfterWrite(MENU_COUNT_CACHE_TTL_MS, TimeUnit.MILLISECONDS)
                .build();
        this.permissionCatalogCache = CacheBuilder.newBuilder()
                .maximumSize(PERMISSION_CATALOG_CACHE_MAX_ENTRIES)
                .expireAfterWrite(PERMISSION_CATALOG_CACHE_TTL_MS, TimeUnit.MILLISECONDS)
                .build();
        this.menuTreeCache = CacheBuilder.newBuilder()
                .maximumSize(VERSIONED_MENU_TREE_CACHE_MAX_ENTRIES)
                .expireAfterWrite(VERSIONED_TREE_CACHE_TTL_MS, TimeUnit.MILLISECONDS)
                .build();
        this.permissionTreeCache = CacheBuilder.newBuilder()
                .maximumSize(VERSIONED_PERMISSION_TREE_CACHE_MAX_ENTRIES)
                .expireAfterWrite(VERSIONED_TREE_CACHE_TTL_MS, TimeUnit.MILLISECONDS)
                .build();
        this.readModelVersionCache = CacheBuilder.newBuilder()
                .maximumSize(VERSIONED_MENU_TREE_CACHE_MAX_ENTRIES)
                .expireAfterWrite(READ_MODEL_VERSION_CACHE_TTL_MS, TimeUnit.MILLISECONDS)
                .build();
    }

    public SystemManagementAppService(
            MyBatisQueryOperations jdbcTemplate,
            UserDomainService userDomainService,
            PermissionSnapshotService permissionSnapshotService,
            SystemPluginViewService systemPluginViewService,
            OnlineSessionManagementAppService onlineSessionManagementAppService,
            SystemVerificationAppService systemVerificationAppService,
            SystemPlatformSettingsAppService systemPlatformSettingsAppService,
            SystemProfileSettingsAppService systemProfileSettingsAppService,
            PasswordEncoder passwordEncoder,
            AuthSessionStore authSessionStore,
            LoginAuditService loginAuditService,
            OperationAuditService operationAuditService,
            SecuritySettingsService securitySettingsService,
            PasswordPolicyService passwordPolicyService,
            IamUserService iamUserService,
            SystemUserManagementAppService systemUserManagementAppService
    ) {
        this(
                jdbcTemplate,
                userDomainService,
                permissionSnapshotService,
                systemPluginViewService,
                onlineSessionManagementAppService,
                systemVerificationAppService,
                systemPlatformSettingsAppService,
                systemProfileSettingsAppService,
                passwordEncoder,
                authSessionStore,
                loginAuditService,
                operationAuditService,
                securitySettingsService,
                passwordPolicyService,
                iamUserService,
                systemUserManagementAppService,
                defaultRoleManagementAppService(jdbcTemplate, permissionSnapshotService, operationAuditService),
                null
        );
    }

    public SystemManagementAppService(
            MyBatisQueryOperations jdbcTemplate,
            UserDomainService userDomainService,
            PermissionSnapshotService permissionSnapshotService,
            SystemPluginViewService systemPluginViewService,
            OnlineSessionManagementAppService onlineSessionManagementAppService,
            SystemVerificationAppService systemVerificationAppService,
            SystemPlatformSettingsAppService systemPlatformSettingsAppService,
            SystemProfileSettingsAppService systemProfileSettingsAppService,
            PasswordEncoder passwordEncoder,
            AuthSessionStore authSessionStore,
            LoginAuditService loginAuditService,
            OperationAuditService operationAuditService,
            SecuritySettingsService securitySettingsService,
            PasswordPolicyService passwordPolicyService,
            IamUserService iamUserService,
            SystemUserManagementAppService systemUserManagementAppService,
            SystemRoleManagementAppService systemRoleManagementAppService
    ) {
        this(
                jdbcTemplate,
                userDomainService,
                permissionSnapshotService,
                systemPluginViewService,
                onlineSessionManagementAppService,
                systemVerificationAppService,
                systemPlatformSettingsAppService,
                systemProfileSettingsAppService,
                passwordEncoder,
                authSessionStore,
                loginAuditService,
                operationAuditService,
                securitySettingsService,
                passwordPolicyService,
                iamUserService,
                systemUserManagementAppService,
                systemRoleManagementAppService,
                null
        );
    }

    public SystemManagementAppService(
            MyBatisQueryOperations jdbcTemplate,
            UserDomainService userDomainService,
            PermissionSnapshotService permissionSnapshotService,
            SystemPluginViewService systemPluginViewService,
            OnlineSessionManagementAppService onlineSessionManagementAppService,
            SystemVerificationAppService systemVerificationAppService,
            SystemPlatformSettingsAppService systemPlatformSettingsAppService,
            SystemProfileSettingsAppService systemProfileSettingsAppService,
            PasswordEncoder passwordEncoder,
            AuthSessionStore authSessionStore,
            LoginAuditService loginAuditService,
            OperationAuditService operationAuditService,
            SecuritySettingsService securitySettingsService,
            PasswordPolicyService passwordPolicyService,
            IamUserService iamUserService
    ) {
        this(
                jdbcTemplate,
                userDomainService,
                permissionSnapshotService,
                systemPluginViewService,
                onlineSessionManagementAppService,
                systemVerificationAppService,
                systemPlatformSettingsAppService,
                systemProfileSettingsAppService,
                passwordEncoder,
                authSessionStore,
                loginAuditService,
                operationAuditService,
                securitySettingsService,
                passwordPolicyService,
                iamUserService,
                defaultUserManagementAppService(jdbcTemplate, userDomainService, iamUserService, permissionSnapshotService, onlineSessionManagementAppService, operationAuditService, passwordEncoder, passwordPolicyService),
                defaultRoleManagementAppService(jdbcTemplate, permissionSnapshotService, operationAuditService),
                null
        );
    }

    private static SystemUserManagementAppService defaultUserManagementAppService(
            MyBatisQueryOperations jdbcTemplate,
            UserDomainService userDomainService,
            IamUserService iamUserService,
            PermissionSnapshotService permissionSnapshotService,
            OnlineSessionManagementAppService onlineSessionManagementAppService,
            OperationAuditService operationAuditService,
            PasswordEncoder passwordEncoder,
            PasswordPolicyService passwordPolicyService
    ) {
        return new SystemUserManagementAppService(
                jdbcTemplate,
                userDomainService,
                iamUserService,
                permissionSnapshotService,
                onlineSessionManagementAppService,
                operationAuditService,
                passwordEncoder,
                passwordPolicyService
        );
    }

    private static SystemRoleManagementAppService defaultRoleManagementAppService(
            MyBatisQueryOperations jdbcTemplate,
            PermissionSnapshotService permissionSnapshotService,
            OperationAuditService operationAuditService
    ) {
        return new SystemRoleManagementAppService(
                jdbcTemplate,
                permissionSnapshotService,
                operationAuditService,
                NOOP_DOMAIN_EVENT_PUBLISHER
        );
    }

    public SystemVO.DashboardSummaryVO dashboardSummary(CurrentUser currentUser) {
        PermissionSnapshotService.PermissionSnapshot snapshot = resolvePermissionSnapshot(currentUser);
        CompletableFuture<CurrentUserVO> currentUserFuture = CompletableFuture.supplyAsync(() -> buildCurrentUser(currentUser, snapshot), BLOCKING_IO_EXECUTOR);
        CompletableFuture<List<PluginVO.PluginAvailabilityVO>> availablePluginsFuture = CompletableFuture.supplyAsync(
                systemPluginViewService::availablePlugins,
                BLOCKING_IO_EXECUTOR
        );
        CompletableFuture<Integer> menuCountFuture = CompletableFuture.supplyAsync(this::countMenus, BLOCKING_IO_EXECUTOR);
        CompletableFuture<List<AuditLogVO>> recentLoginLogsFuture = CompletableFuture.supplyAsync(
                () -> new ArrayList<>(listCurrentUserSuccessfulLoginLogs(currentUser, 5)),
                BLOCKING_IO_EXECUTOR
        );
        CompletableFuture<List<AuditLogVO>> recentOperationLogsFuture = CompletableFuture.supplyAsync(
                () -> new ArrayList<>(listRecentOperationLogs(currentUser, currentUser.getUsername(), 5)),
                BLOCKING_IO_EXECUTOR
        );

        SystemVO.DashboardSummaryVO summary = new SystemVO.DashboardSummaryVO();
        summary.setCurrentUser(currentUserFuture.join());
        summary.setAvailablePlugins(availablePluginsFuture.join());
        summary.setMenuCount(menuCountFuture.join());
        summary.setPermissionCount(snapshot.getPermissionList().size());
        summary.setRecentLoginLogs(recentLoginLogsFuture.join());
        summary.setRecentOperationLogs(recentOperationLogsFuture.join());
        summary.setShortcuts(new ArrayList<>(DASHBOARD_SHORTCUTS));
        return summary;
    }

    public SystemVO.ProfileSummaryVO profileSummary(CurrentUser currentUser) {
        PermissionSnapshotService.PermissionSnapshot snapshot = resolvePermissionSnapshot(currentUser);
        CompletableFuture<CurrentUserVO> currentUserFuture = CompletableFuture.supplyAsync(() -> buildCurrentUser(currentUser, snapshot), BLOCKING_IO_EXECUTOR);
        CompletableFuture<List<String>> roleNamesFuture = CompletableFuture.supplyAsync(
                () -> listCurrentRoleNames(currentUser.getUserId()),
                BLOCKING_IO_EXECUTOR
        );
        CompletableFuture<List<AuditLogVO>> recentLoginLogsFuture = CompletableFuture.supplyAsync(
                () -> new ArrayList<>(listCurrentUserSuccessfulLoginLogs(currentUser, RECENT_LOGIN_LOG_LIMIT)),
                BLOCKING_IO_EXECUTOR
        );
        CompletableFuture<List<ProfileFieldSettingVO>> profileFieldSettingsFuture = CompletableFuture.supplyAsync(
                () -> new ArrayList<>(systemProfileSettingsAppService.getProfileFieldSettings(currentUser)),
                BLOCKING_IO_EXECUTOR
        );
        CompletableFuture<Boolean> mobileBindAvailableFuture = CompletableFuture.supplyAsync(
                () -> systemVerificationAppService.isContactBindAvailable("mobile"),
                BLOCKING_IO_EXECUTOR
        );
        CompletableFuture<Boolean> emailBindAvailableFuture = CompletableFuture.supplyAsync(
                () -> systemVerificationAppService.isContactBindAvailable("email"),
                BLOCKING_IO_EXECUTOR
        );

        List<ProfileFieldSettingVO> profileFieldSettings = profileFieldSettingsFuture.join();
        boolean mobileBindAvailable = Boolean.TRUE.equals(mobileBindAvailableFuture.join());
        boolean emailBindAvailable = Boolean.TRUE.equals(emailBindAvailableFuture.join());

        SystemVO.ProfileSummaryVO summary = new SystemVO.ProfileSummaryVO();
        summary.setCurrentUser(currentUserFuture.join());
        summary.setRoleNames(roleNamesFuture.join());
        summary.setPermissionCount(snapshot.getPermissionList().size());
        summary.setRecentLoginLogs(recentLoginLogsFuture.join());
        summary.setProfileFieldSettings(profileFieldSettings);
        summary.setMobileBindAvailable(mobileBindAvailable);
        summary.setEmailBindAvailable(emailBindAvailable);
        summary.setMobileBindVerificationRequired(mobileBindAvailable);
        summary.setEmailBindVerificationRequired(emailBindAvailable);
        summary.setProfileCompletion(systemProfileSettingsAppService.buildProfileCompletionSummary(
                summary.getCurrentUser(),
                profileFieldSettings,
                mobileBindAvailable,
                emailBindAvailable
        ));
        return summary;
    }

    @Transactional
    public CurrentUserVO updateCurrentUserProfile(CurrentUser currentUser, com.lumira.saas.modules.system.dto.ProfileDTO.BasicInfoUpdateRequest request) {
        SysUserEntity user = userDomainService.findById(currentUser.getUserId())
                .orElseThrow(() -> new BizException(ErrorCode.ACCOUNT_NOT_FOUND, "用户不存在"));
        String nextMobile = normalizeNullableText(request.getMobile());
        String nextEmail = normalizeNullableText(request.getEmail());
        if (contactValueChanged(user.getMobile(), nextMobile)) {
            if (!systemVerificationAppService.isContactBindAvailable("mobile")) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "当前未启用短信验证码，暂不允许绑定手机号");
            }
            throw new BizException(ErrorCode.VALIDATION_ERROR, "手机号绑定需要验证码，请在已绑定登录方式中修改");
        }
        if (contactValueChanged(user.getEmail(), nextEmail)) {
            if (!systemVerificationAppService.isContactBindAvailable("email")) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "当前未启用邮箱验证码，暂不允许绑定邮箱");
            }
            throw new BizException(ErrorCode.VALIDATION_ERROR, "邮箱绑定需要验证码，请在已绑定登录方式中修改");
        }
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
                nextMobile,
                nextEmail,
                normalizeNullableText(request.getBirthMonth()),
                normalizeNullableText(request.getGender()),
                normalizeNullableText(request.getRegion()),
                normalizeNullableText(request.getAvailableTime()),
                normalizeNullableText(request.getIdCardNumber()),
                currentUser.getUserId(),
                LocalDateTime.now(),
                user.getId()
        );
        userDomainService.findById(user.getId()).ifPresent(iamUserService::updateProfile);
        if (request.getExtraProfileValues() != null) {
            updateCurrentUserExtraProfileValues(currentUser, user.getId(), request.getExtraProfileValues());
        }
        operationAuditService.log(currentUser.getUserId(), currentUser.getUsername(), "profile", "update", "UPDATE", "SUCCESS", "更新个人资料");
        return buildCurrentUser(currentUser);
    }

    @Transactional
    public CurrentUserVO updateCurrentUserAvatar(CurrentUser currentUser, String avatarUrl) {
        if (!StringUtils.hasText(avatarUrl)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "头像地址不能为空");
        }
        SysUserEntity user = userDomainService.findById(currentUser.getUserId())
                .orElseThrow(() -> new BizException(ErrorCode.ACCOUNT_NOT_FOUND, "用户不存在"));
        String normalizedAvatarUrl = normalizeNullableText(avatarUrl);
        jdbcTemplate.update(
                """
                        update sys_user
                        set avatar_url = ?, updated_by = ?, updated_at = ?
                        where id = ? and deleted = 0
                        """,
                normalizedAvatarUrl,
                currentUser.getUserId(),
                LocalDateTime.now(),
                user.getId()
        );
        userDomainService.findById(user.getId()).ifPresent(iamUserService::updateProfile);
        operationAuditService.log(currentUser.getUserId(), currentUser.getUsername(), "profile", "avatar", "UPDATE", "SUCCESS", "更新个人头像");
        return buildCurrentUser(currentUser);
    }

    @Transactional
    public CurrentUserVO updateCurrentUserEmail(CurrentUser currentUser, ProfileDTO.EmailUpdateRequest request) {
        SysUserEntity user = userDomainService.findById(currentUser.getUserId())
                .orElseThrow(() -> new BizException(ErrorCode.ACCOUNT_NOT_FOUND, "用户不存在"));
        String email = normalizeContactValue("email", request.getEmail());
        if (!systemVerificationAppService.isContactBindAvailable("email")) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "当前未启用邮箱验证码，暂不允许绑定邮箱");
        }
        if (!StringUtils.hasText(request.getChallengeId()) || !StringUtils.hasText(request.getVerificationCode())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "请先获取验证码");
        }
        systemVerificationAppService.completeContactBind(
                currentUser.getUserId(),
                "email",
                request.getChallengeId(),
                request.getVerificationCode(),
                email
        );
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
        userDomainService.findById(user.getId()).ifPresent(iamUserService::updateProfile);
        return buildCurrentUser(currentUser);
    }

    public SystemVO.VerificationChallengeVO startCurrentUserContactBindChallenge(CurrentUser currentUser, ProfileDTO.ContactBindChallengeRequest request) {
        String contactType = normalizeContactType(request.getContactType());
        String value = normalizeContactValue(contactType, request.getValue());
        return systemVerificationAppService.startContactBindChallenge(currentUser.getUserId(), contactType, value);
    }

    @Transactional
    public CurrentUserVO updateCurrentUserContactBinding(CurrentUser currentUser, ProfileDTO.ContactBindRequest request) {
        SysUserEntity user = userDomainService.findById(currentUser.getUserId())
                .orElseThrow(() -> new BizException(ErrorCode.ACCOUNT_NOT_FOUND, "用户不存在"));
        String contactType = normalizeContactType(request.getContactType());
        String value = normalizeContactValue(contactType, request.getValue());

        if (!systemVerificationAppService.isContactBindAvailable(contactType)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "当前未启用该绑定方式");
        }
        if (!StringUtils.hasText(request.getChallengeId()) || !StringUtils.hasText(request.getVerificationCode())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "请先获取验证码");
        }
        systemVerificationAppService.completeContactBind(
                currentUser.getUserId(),
                contactType,
                request.getChallengeId(),
                request.getVerificationCode(),
                value
        );

        if ("mobile".equals(contactType)) {
            jdbcTemplate.update(
                    """
                            update sys_user
                            set mobile = ?, updated_by = ?, updated_at = ?
                            where id = ? and deleted = 0
                            """,
                    value,
                    currentUser.getUserId(),
                    LocalDateTime.now(),
                    user.getId()
            );
        } else if ("email".equals(contactType)) {
            jdbcTemplate.update(
                    """
                            update sys_user
                            set email = ?, updated_by = ?, updated_at = ?
                            where id = ? and deleted = 0
                            """,
                    value,
                    currentUser.getUserId(),
                    LocalDateTime.now(),
                    user.getId()
            );
        } else {
            throw new BizException(ErrorCode.NOT_FOUND, "绑定类型不存在");
        }

        userDomainService.findById(user.getId()).ifPresent(iamUserService::updateProfile);
        operationAuditService.log(currentUser.getUserId(), currentUser.getUsername(), "profile", "bind", "UPDATE", "SUCCESS", "更新绑定信息");
        return buildCurrentUser(currentUser);
    }

    @Transactional
    public CurrentUserVO updateCurrentUserLocale(CurrentUser currentUser, ProfileDTO.LocaleUpdateRequest request) {
        SysUserEntity user = userDomainService.findById(currentUser.getUserId())
                .orElseThrow(() -> new BizException(ErrorCode.ACCOUNT_NOT_FOUND, "用户不存在"));
        String locale = normalizeLocale(request.getLocale());
        jdbcTemplate.update(
                """
                        insert into iam_user_profile (
                            user_id, nickname, real_name, gender, birth_month, region, locale, timezone, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, 'Asia/Shanghai', 0)
                        on duplicate key update
                            locale = values(locale),
                            deleted = 0,
                            updated_at = current_timestamp
                        """,
                user.getId(),
                user.getNickname(),
                user.getRealName(),
                user.getGender(),
                user.getBirthMonth(),
                user.getRegion(),
                locale
        );
        operationAuditService.log(currentUser.getUserId(), currentUser.getUsername(), "profile", "update-locale", "UPDATE", "SUCCESS", "更新语言偏好");
        return buildCurrentUser(currentUser);
    }

    @Transactional
    public boolean updateCurrentUserPassword(CurrentUser currentUser, ProfileDTO.PasswordUpdateRequest request) {
        SysUserEntity user = userDomainService.findById(currentUser.getUserId())
                .orElseThrow(() -> new BizException(ErrorCode.ACCOUNT_NOT_FOUND, "用户不存在"));

        String currentPassword = normalizeNullableText(request.getCurrentPassword());
        String newPassword = normalizeNullableText(request.getNewPassword());
        String confirmPassword = normalizeNullableText(request.getConfirmPassword());

        if (!StringUtils.hasText(currentPassword) || !StringUtils.hasText(newPassword) || !StringUtils.hasText(confirmPassword)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "请输入完整的密码信息");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "两次输入的新密码不一致");
        }
        String currentPasswordHash = iamUserService.findActiveCredential(user.getId(), "PASSWORD")
                .map(IamUserAccount.CredentialView::getCredentialSecret)
                .orElse(null);
        boolean fallbackToSysUserPassword = !StringUtils.hasText(currentPasswordHash);
        if (fallbackToSysUserPassword) {
            currentPasswordHash = user.getPasswordHash();
        }
        if (!StringUtils.hasText(currentPasswordHash) || !passwordEncoder.matches(currentPassword, currentPasswordHash)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "当前密码不正确");
        }
        if (fallbackToSysUserPassword) {
            iamUserService.upsertPasswordCredential(user.getId(), user.getPasswordHash());
        }

        passwordPolicyService.validatePassword(newPassword);
        String encodedPassword = passwordEncoder.encode(newPassword);
        jdbcTemplate.update(
                "update sys_user set password_hash = ?, updated_by = ?, updated_at = ? where id = ? and deleted = 0",
                encodedPassword,
                currentUser.getUserId(),
                LocalDateTime.now(),
                user.getId()
        );
        iamUserService.upsertPasswordCredential(user.getId(), encodedPassword);
        authSessionStore.markPasswordChangeResolved(currentUser.getUserId(), currentUser.getSessionId(), true);
        authSessionStore.revokeUserSessionsExcept(currentUser.getUserId(), currentUser.getSessionId(), true);
        operationAuditService.log(currentUser.getUserId(), currentUser.getUsername(), "profile", "password", "UPDATE", "SUCCESS", "修改登录密码");
        return true;
    }

    public List<ProfileFieldSettingVO> getProfileFieldSettings(CurrentUser currentUser) {
        return systemProfileSettingsAppService.getProfileFieldSettings(currentUser);
    }

    public List<ProfileFieldSettingVO> getProfileFieldSettings(CurrentUser currentUser, String pageKey) {
        return systemProfileSettingsAppService.getProfileFieldSettings(currentUser, pageKey);
    }

    @Transactional
    public List<ProfileFieldSettingVO> updateProfileFieldSettings(CurrentUser currentUser, SystemDTO.ProfileFieldSettingsRequest request) {
        return systemProfileSettingsAppService.updateProfileFieldSettings(currentUser, request);
    }

    @Transactional
    public List<ProfileFieldSettingVO> updateProfileFieldSettings(CurrentUser currentUser, SystemDTO.ProfileFieldSettingsRequest request, String pageKey) {
        return systemProfileSettingsAppService.updateProfileFieldSettings(currentUser, request, pageKey);
    }

    public PageResponse<SystemVO.UserVO> listUsers(
            CurrentUser currentUser,
            Long userId,
            String uid,
            String username,
            String mobile,
            String email,
            Long deptId,
            String status,
            String source,
            String registeredStart,
            String registeredEnd,
            String lastLoginStart,
            String lastLoginEnd,
            Long cursorId,
            String cursorCreatedAt,
            long pageNo,
            long pageSize
    ) {
        return systemUserManagementAppService.listUsers(
                currentUser, userId, uid, username, mobile, email, deptId, status, source,
                registeredStart, registeredEnd, lastLoginStart, lastLoginEnd,
                cursorId, cursorCreatedAt, pageNo, pageSize
        );
    }

    public SystemVO.UserDetailVO getUser(CurrentUser currentUser, Long userId) {
        return systemUserManagementAppService.getUser(currentUser, userId);
    }

    @Transactional
    public SystemVO.UserDetailVO createUser(CurrentUser currentUser, SystemDTO.UserUpsertRequest request) {
        return systemUserManagementAppService.createUser(currentUser, request);
    }

    @Transactional
    public SystemVO.UserDetailVO updateUser(CurrentUser currentUser, Long userId, SystemDTO.UserUpsertRequest request) {
        return systemUserManagementAppService.updateUser(currentUser, userId, request);
    }

    @Transactional
    public boolean updateUserStatus(CurrentUser currentUser, Long userId, String status) {
        return systemUserManagementAppService.updateUserStatus(currentUser, userId, status);
    }

    @Transactional
    public boolean deleteUser(CurrentUser currentUser, Long userId) {
        return systemUserManagementAppService.deleteUser(currentUser, userId);
    }

    public List<SystemVO.RoleVO> listUserRoles(CurrentUser currentUser, Long userId) {
        return systemUserManagementAppService.listUserRoles(currentUser, userId);
    }

    public PageResponse<SystemVO.RoleVO> listRoles(CurrentUser currentUser, String roleCode, String roleName, String roleType, long pageNo, long pageSize) {
        return systemRoleManagementAppService.listRoles(currentUser, roleCode, roleName, roleType, pageNo, pageSize);
    }

    public SystemVO.RoleDetailVO getRole(CurrentUser currentUser, Long roleId) {
        return systemRoleManagementAppService.getRole(currentUser, roleId);
    }

    public SystemVO.DefaultRegistrationRoleVO getDefaultRegistrationRole(CurrentUser currentUser) {
        return systemRoleManagementAppService.getDefaultRegistrationRole(currentUser);
    }

    public SystemVO.DefaultRegistrationRoleVO updateDefaultRegistrationRole(CurrentUser currentUser, Long roleId) {
        return systemRoleManagementAppService.updateDefaultRegistrationRole(currentUser, roleId);
    }

    public SystemVO.RoleDetailVO createRole(CurrentUser currentUser, SystemDTO.RoleUpsertRequest request) {
        return systemRoleManagementAppService.createRole(currentUser, request);
    }

    public SystemVO.RoleDetailVO updateRole(CurrentUser currentUser, Long roleId, SystemDTO.RoleUpsertRequest request) {
        return systemRoleManagementAppService.updateRole(currentUser, roleId, request);
    }

    public boolean updateRolePermissions(CurrentUser currentUser, Long roleId, List<String> permissionKeys) {
        return systemRoleManagementAppService.updateRolePermissions(currentUser, roleId, permissionKeys);
    }

    public boolean deleteRole(CurrentUser currentUser, Long roleId) {
        return systemRoleManagementAppService.deleteRole(currentUser, roleId);
    }

    public List<SystemVO.PermissionVO> listPermissions(CurrentUser currentUser) {
        return listPermissionsByVersion(currentPermissionCatalogVersion());
    }

    private List<SystemVO.PermissionVO> listPermissionsByVersion(String permissionCatalogVersion) {
        pruneStalePermissionCatalogCache(permissionCatalogVersion);
        try {
            return copyPermissions(permissionCatalogCache.get(
                    permissionCatalogCacheKey(permissionCatalogVersion),
                    this::loadPermissionCatalog
            ));
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Failed to load permission catalog", cause);
        }
    }

    private List<SystemVO.PermissionVO> loadPermissionCatalog() {
        List<SystemVO.PermissionVO> permissions = new ArrayList<>(jdbcTemplate.query(
                """
                        select permission_key as permissionKey, permission_name as permissionName,
                               permission_group as permissionGroup, source_type as sourceType, plugin_code as pluginCode
                        from sys_permission
                        where deleted = 0
                        order by permission_group asc, permission_key asc
                        """,
                new BeanPropertyRowMapper<>(SystemVO.PermissionVO.class)
        ).stream()
                .filter(permission -> !isAiPermission(permission))
                .toList());
        permissions.sort(Comparator.comparing(SystemVO.PermissionVO::getPermissionGroup, Comparator.nullsLast(String::compareTo))
                .thenComparing(SystemVO.PermissionVO::getPermissionKey, Comparator.nullsLast(String::compareTo)));
        return List.copyOf(permissions);
    }

    public List<SystemVO.PermissionTreeVO> listPermissionTree(CurrentUser currentUser) {
        long menuTreeVersion = currentMenuTreeVersion();
        String permissionCatalogVersion = currentPermissionCatalogVersion();
        String cacheKey = permissionTreeCacheKey(menuTreeVersion, permissionCatalogVersion);
        pruneStalePermissionTreeCache(cacheKey);
        List<SystemVO.PermissionTreeVO> cached = permissionTreeCache.getIfPresent(cacheKey);
        if (cached != null) {
            return copyPermissionTrees(cached);
        }

        List<SystemVO.PermissionTreeVO> permissionTree = permissionTreeAssembler.build(
                loadMenusByVersion(currentUser, menuTreeVersion),
                listPermissionsByVersion(permissionCatalogVersion)
        );
        List<SystemVO.PermissionTreeVO> snapshot = copyPermissionTrees(permissionTree);
        permissionTreeCache.put(cacheKey, snapshot);
        return copyPermissionTrees(snapshot);
    }

    public List<SystemVO.MenuVO> listMenus(CurrentUser currentUser) {
        return loadMenusByVersion(currentUser, currentMenuTreeVersion());
    }

    private List<SystemVO.MenuVO> listPersistedMenus(CurrentUser currentUser) {
        List<SystemVO.MenuVO> menus = jdbcTemplate.query(
                """
                        select id, parent_id as parentId, menu_code as menuCode,
                               menu_name as menuName, menu_type as menuType, path, component, icon, sort_no as sortNo,
                               permission_key as permissionKey, status
                        from sys_menu
                        where deleted = 0
                        order by sort_no asc, id asc
                """,
                new BeanPropertyRowMapper<>(SystemVO.MenuVO.class)
        ).stream()
                .filter(menu -> !isAiMenu(menu))
                .toList();
        normalizeBuiltinRootMenuParents(menus);
        return buildMenuTree(menus);
    }

    private boolean isAiPermission(SystemVO.PermissionVO permission) {
        if (permission == null) {
            return false;
        }
        return isAiPermissionKey(permission.getPermissionKey())
                || "ai".equalsIgnoreCase(permission.getPermissionGroup());
    }

    private boolean isAiPermissionKey(String permissionKey) {
        return StringUtils.hasText(permissionKey)
                && permissionKey.trim().toLowerCase(Locale.ROOT).startsWith("ai:");
    }

    private boolean isAiMenu(SystemVO.MenuVO menu) {
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

    private void normalizeBuiltinRootMenuParents(List<SystemVO.MenuVO> menus) {
        for (SystemVO.MenuVO menu : menus) {
            if (menu == null || menu.getMenuCode() == null) {
                continue;
            }
            if (BUILTIN_ROOT_MENU_CODES.contains(menu.getMenuCode())) {
                menu.setParentId(0L);
            }
        }
    }

    private List<SystemVO.MenuVO> loadMenusByVersion(CurrentUser currentUser, long menuTreeVersion) {
        pruneStaleMenuTreeCache(menuTreeVersion);
        List<SystemVO.MenuVO> cached = menuTreeCache.getIfPresent(menuTreeVersion);
        if (cached != null) {
            return copyMenus(cached);
        }

        List<SystemVO.MenuVO> menus = listPersistedMenus(currentUser);
        List<SystemVO.MenuVO> snapshot = copyMenus(menus);
        menuTreeCache.put(menuTreeVersion, snapshot);
        return copyMenus(snapshot);
    }

    private long currentMenuTreeVersion() {
        CachedReadModelVersion cached = readModelVersionCache.getIfPresent(MENU_TREE_READ_MODEL_VERSION_CACHE_KEY);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAtEpochMillis() > now) {
            return cached.version();
        }

        long version = 0L;
        Long storedVersion = readModelVersionService.currentVersion(READ_MODEL_CONTEXT_PLATFORM, READ_MODEL_SCOPE_MENU_TREE);
        if (storedVersion != null && storedVersion > 0) {
            version = storedVersion;
        }
        readModelVersionCache.put(
                MENU_TREE_READ_MODEL_VERSION_CACHE_KEY,
                new CachedReadModelVersion(version, now + READ_MODEL_VERSION_CACHE_TTL_MS)
        );
        return version;
    }

    private String currentPermissionCatalogVersion() {
        String version = permissionSnapshotService.currentPermissionSnapshotVersion();
        return StringUtils.hasText(version) ? version.trim() : "v0";
    }

    private String permissionCatalogCacheKey(String permissionCatalogVersion) {
        return PERMISSION_CATALOG_CACHE_KEY_PREFIX + permissionCatalogVersion;
    }

    private String permissionTreeCacheKey(long menuTreeVersion, String permissionCatalogVersion) {
        return PERMISSION_TREE_CACHE_KEY_PREFIX + menuTreeVersion + ":" + permissionCatalogVersion;
    }

    private void bumpMenuTreeReadModelVersion(String eventKey) {
        long version = readModelVersionService.bump(READ_MODEL_CONTEXT_PLATFORM, READ_MODEL_SCOPE_MENU_TREE, eventKey);
        readModelVersionCache.put(
                MENU_TREE_READ_MODEL_VERSION_CACHE_KEY,
                new CachedReadModelVersion(version, System.currentTimeMillis() + READ_MODEL_VERSION_CACHE_TTL_MS)
        );
        invalidateVersionedMenuCaches();
    }

    private void invalidateVersionedMenuCaches() {
        menuTreeCache.invalidateAll();
        permissionTreeCache.invalidateAll();
    }

    private void pruneStaleMenuTreeCache(long menuTreeVersion) {
        menuTreeCache.asMap().keySet().removeIf(version -> version != menuTreeVersion);
    }

    private void pruneStalePermissionCatalogCache(String permissionCatalogVersion) {
        String activeCacheKey = permissionCatalogCacheKey(permissionCatalogVersion);
        permissionCatalogCache.asMap().keySet().removeIf(cacheKey -> !activeCacheKey.equals(cacheKey));
    }

    private void pruneStalePermissionTreeCache(String activeCacheKey) {
        permissionTreeCache.asMap().keySet().removeIf(cacheKey -> !activeCacheKey.equals(cacheKey));
    }

    @Transactional
    public boolean reorderMenus(CurrentUser currentUser, SystemDTO.MenuReorderRequest request) {
        LocalDateTime now = LocalDateTime.now();
        if (request == null || CollectionUtils.isEmpty(request.getItems())) {
            return true;
        }

        for (SystemDTO.MenuOrderItem item : request.getItems()) {
            if (item == null || item.getId() == null || item.getSortNo() == null) {
                continue;
            }
            if (item.getId() <= 0) {
                continue;
            }
            ensureEditableMenu(item.getId());
            ensureEditableParentMenu(item.getParentId());
            jdbcTemplate.update(
                    """
                            update sys_menu
                            set parent_id = ?, sort_no = ?, updated_by = ?, updated_at = ?
                            where id = ? and deleted = 0
                            """,
                    item.getParentId() == null ? 0L : item.getParentId(),
                    item.getSortNo(),
                    currentUser.getUserId(),
                    now,
                    item.getId()
            );
        }

        operationAuditService.log(
                currentUser.getUserId(),
                currentUser.getUsername(),
                "menu",
                "reorder",
                "UPDATE",
                "SUCCESS",
                "调整菜单顺序"
        );
        bumpMenuTreeReadModelVersion("system.menu.reorder");
        invalidateMenuCountCache();
        return true;
    }

    public SystemVO.MenuVO getMenu(CurrentUser currentUser, Long menuId) {
        SystemVO.MenuVO menu = queryOne(
                """
                        select id, parent_id as parentId, menu_code as menuCode,
                               menu_name as menuName, menu_type as menuType, path, component, icon, sort_no as sortNo,
                               permission_key as permissionKey, status
                        from sys_menu
                        where id = ? and deleted = 0
                        """,
                SystemVO.MenuVO.class,
                menuId
        );
        if (menu == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "菜单不存在");
        }
        ensureEditableMenu(menu);
        return menu;
    }

    @Transactional
    public SystemVO.MenuVO createMenu(CurrentUser currentUser, SystemDTO.MenuUpsertRequest request) {
        ensureEditableMenuRequest(request);
        ensureEditableParentMenu(request.getParentId());
        Long menuId = insertMenu(null, request, currentUser.getUserId());
        operationAuditService.log(currentUser.getUserId(), currentUser.getUsername(), "menu", "create", "CREATE", "SUCCESS", "创建菜单: " + request.getMenuName());
        bumpMenuTreeReadModelVersion("system.menu.create");
        invalidateMenuCountCache();
        return getMenu(currentUser, menuId);
    }

    @Transactional
    public SystemVO.MenuVO updateMenu(CurrentUser currentUser, Long menuId, SystemDTO.MenuUpsertRequest request) {
        ensureEditableMenu(menuId);
        ensureEditableParentMenu(request.getParentId());
        insertMenu(menuId, request, currentUser.getUserId());
        operationAuditService.log(currentUser.getUserId(), currentUser.getUsername(), "menu", "update", "UPDATE", "SUCCESS", "更新菜单: " + request.getMenuName());
        bumpMenuTreeReadModelVersion("system.menu.update");
        invalidateMenuCountCache();
        return getMenu(currentUser, menuId);
    }

    @Transactional
    public boolean updateMenuStatus(CurrentUser currentUser, Long menuId, String status) {
        ensureEditableMenu(menuId);
        jdbcTemplate.update(
                "update sys_menu set status = ?, updated_by = ?, updated_at = ? where id = ? and deleted = 0",
                status,
                currentUser.getUserId(),
                LocalDateTime.now(),
                menuId
        );
        operationAuditService.log(currentUser.getUserId(), currentUser.getUsername(), "menu", "status", "UPDATE", "SUCCESS", "更新菜单状态: " + menuId + " -> " + status);
        bumpMenuTreeReadModelVersion("system.menu.status");
        invalidateMenuCountCache();
        return true;
    }

    @Transactional
    public boolean deleteMenu(CurrentUser currentUser, Long menuId) {
        ensureEditableMenu(menuId);
        boolean hasChildMenu = jdbcTemplate.exists(
                "select 1 from sys_menu where parent_id = ? and deleted = 0 limit 1",
                menuId
        );
        if (hasChildMenu) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "请先删除子菜单后再删除当前菜单");
        }
        SystemVO.MenuVO menu = getMenu(currentUser, menuId);
        if (StringUtils.hasText(menu.getPermissionKey())) {
            boolean permissionReferenced = jdbcTemplate.exists(
                    "select 1 from sys_role_permission where permission_key = ? and deleted = 0 limit 1",
                    menu.getPermissionKey()
            );
            if (permissionReferenced) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "菜单权限仍被角色引用，请先调整角色权限");
            }
        }
        jdbcTemplate.update(
                "update sys_menu set deleted = 1, updated_by = ?, updated_at = ? where id = ? and deleted = 0",
                currentUser.getUserId(),
                LocalDateTime.now(),
                menuId
        );
        operationAuditService.log(currentUser.getUserId(), currentUser.getUsername(), "menu", "delete", "DELETE", "SUCCESS", "删除菜单: " + menu.getMenuName());
        bumpMenuTreeReadModelVersion("system.menu.delete");
        permissionSnapshotService.invalidatePermissions();
        invalidateMenuCountCache();
        return true;
    }

    public PageResponse<SystemVO.DictTypeVO> listDictTypes(CurrentUser currentUser, String dictCode, String dictName, String status, long pageNo, long pageSize) {
        String baseSql = """
                from sys_dict_type t
                where t.deleted = 0
                """;
        List<Object> params = new ArrayList<>();
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
                select t.id, t.dict_code as dictCode, t.dict_name as dictName,
                       t.status, t.is_system as isSystem, t.remark
                """ + baseSql + " order by t.is_system desc, t.id desc";
        PageResponse<SystemVO.DictTypeVO> page = pageQuery(selectSql, "select count(1) " + baseSql, SystemVO.DictTypeVO.class, pageNo, pageSize, params);
        return page;
    }

    public SystemVO.DictTypeVO getDictType(CurrentUser currentUser, Long id) {
        SystemVO.DictTypeVO type = queryOne(
                """
                        select id, dict_code as dictCode, dict_name as dictName,
                               status, is_system as isSystem, remark
                        from sys_dict_type
                        where id = ? and deleted = 0
                        """,
                SystemVO.DictTypeVO.class,
                id
        );
        if (type == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "字典类型不存在");
        }
        return type;
    }

    @Transactional
    public SystemVO.DictTypeVO createDictType(CurrentUser currentUser, SystemDTO.DictTypeUpsertRequest request) {
        Long id = upsertDictType(null, request, currentUser.getUserId());
        operationAuditService.log(currentUser.getUserId(), currentUser.getUsername(), "dict", "create", "CREATE", "SUCCESS", "创建字典类型: " + request.getDictCode());
        return getDictType(currentUser, id);
    }

    @Transactional
    public SystemVO.DictTypeVO updateDictType(CurrentUser currentUser, Long id, SystemDTO.DictTypeUpsertRequest request) {
        upsertDictType(id, request, currentUser.getUserId());
        operationAuditService.log(currentUser.getUserId(), currentUser.getUsername(), "dict", "update", "UPDATE", "SUCCESS", "更新字典类型: " + request.getDictCode());
        return getDictType(currentUser, id);
    }

    @Transactional
    public boolean deleteDictType(CurrentUser currentUser, Long id) {
        SystemVO.DictTypeVO type = getDictType(currentUser, id);
        if (isSystemDictType(type)) {
            throw new BizException(ErrorCode.FORBIDDEN, "系统字典不允许删除");
        }
        jdbcTemplate.update(
                """
                        update sys_dict_item
                        set deleted = 1,
                            item_value = concat(left(item_value, greatest(0, 64 - char_length(concat('__deleted_', id)))), '__deleted_', id),
                            updated_by = ?,
                            updated_at = ?
                        where dict_type_id = ? and deleted = 0
                        """,
                currentUser.getUserId(),
                LocalDateTime.now(),
                id
        );
        jdbcTemplate.update(
                """
                        update sys_dict_type
                        set deleted = 1,
                            dict_code = concat(left(dict_code, greatest(0, 64 - char_length(concat('__deleted_', id)))), '__deleted_', id),
                            updated_by = ?,
                            updated_at = ?
                        where id = ? and deleted = 0
                        """,
                currentUser.getUserId(),
                LocalDateTime.now(),
                id
        );
        operationAuditService.log(currentUser.getUserId(), currentUser.getUsername(), "dict", "delete", "DELETE", "SUCCESS", "删除字典类型: " + type.getDictCode());
        return true;
    }

    public List<SystemVO.DictItemVO> listDictItems(CurrentUser currentUser, Long dictTypeId) {
        getDictType(currentUser, dictTypeId);
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

    public List<SystemVO.DictItemVO> listEnabledDictItemsByCode(CurrentUser currentUser, String dictCode) {
        if (!StringUtils.hasText(dictCode)) {
            return List.of();
        }
        String normalizedCode = dictCode.trim();
        return jdbcTemplate.query(
                """
                        select i.id, i.dict_type_id as dictTypeId, i.item_label as itemLabel, i.item_value as itemValue,
                               i.sort_no as sortNo, i.status, i.remark
                        from sys_dict_type t
                        join sys_dict_item i
                          on i.dict_type_id = t.id
                         and i.deleted = 0
                        where t.dict_code = ?
                          and t.deleted = 0
                          and t.status = 'ENABLED'
                          and i.status = 'ENABLED'
                        order by i.sort_no asc, i.id asc
                        """,
                new BeanPropertyRowMapper<>(SystemVO.DictItemVO.class),
                normalizedCode
        );
    }

    public SystemVO.DictItemVO getDictItem(CurrentUser currentUser, Long dictTypeId, Long itemId) {
        getDictType(currentUser, dictTypeId);
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
        getDictType(currentUser, dictTypeId);
        Long id = upsertDictItem(null, dictTypeId, request, currentUser.getUserId());
        operationAuditService.log(currentUser.getUserId(), currentUser.getUsername(), "dict", "item-create", "CREATE", "SUCCESS", "创建字典项: " + request.getItemLabel());
        return getDictItem(currentUser, dictTypeId, id);
    }

    @Transactional
    public SystemVO.DictItemVO updateDictItem(CurrentUser currentUser, Long dictTypeId, Long itemId, SystemDTO.DictItemUpsertRequest request) {
        upsertDictItem(itemId, dictTypeId, request, currentUser.getUserId());
        operationAuditService.log(currentUser.getUserId(), currentUser.getUsername(), "dict", "item-update", "UPDATE", "SUCCESS", "更新字典项: " + request.getItemLabel());
        return getDictItem(currentUser, dictTypeId, itemId);
    }

    @Transactional
    public boolean deleteDictItem(CurrentUser currentUser, Long dictTypeId, Long itemId) {
        SystemVO.DictItemVO item = getDictItem(currentUser, dictTypeId, itemId);
        jdbcTemplate.update(
                """
                        update sys_dict_item
                        set deleted = 1,
                            item_value = concat(left(item_value, greatest(0, 64 - char_length(concat('__deleted_', id)))), '__deleted_', id),
                            updated_by = ?,
                            updated_at = ?
                        where id = ? and dict_type_id = ? and deleted = 0
                        """,
                currentUser.getUserId(),
                LocalDateTime.now(),
                itemId,
                dictTypeId
        );
        operationAuditService.log(currentUser.getUserId(), currentUser.getUsername(), "dict", "item-delete", "DELETE", "SUCCESS", "删除字典项: " + item.getItemLabel());
        return true;
    }

    public PageResponse<SystemVO.ConfigVO> listConfigs(CurrentUser currentUser, String configKey, String configName, long pageNo, long pageSize) {
        String baseSql = """
                from sys_config c
                where c.deleted = 0
                """;
        List<Object> params = new ArrayList<>();
        if (StringUtils.hasText(configKey)) {
            baseSql += " and c.config_key like ?";
            params.add(like(configKey));
        }
        if (StringUtils.hasText(configName)) {
            baseSql += " and c.config_name like ?";
            params.add(like(configName));
        }
        String selectSql = """
                select c.id, c.config_key as configKey, c.config_name as configName,
                       c.config_value as configValue, c.is_system as isSystem, c.remark
                """ + baseSql + " order by c.is_system desc, c.id desc";
        PageResponse<SystemVO.ConfigVO> page = pageQuery(selectSql, "select count(1) " + baseSql, SystemVO.ConfigVO.class, pageNo, pageSize, params);
        maskSensitiveConfigValues(page.getRecords());
        return page;
    }

    public SystemVO.ConfigVO getConfig(CurrentUser currentUser, Long id) {
        SystemVO.ConfigVO config = queryOne(
                """
                        select id, config_key as configKey, config_name as configName,
                               config_value as configValue, is_system as isSystem, remark
                        from sys_config
                        where id = ? and deleted = 0
                        """,
                SystemVO.ConfigVO.class,
                id
        );
        if (config == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "配置不存在");
        }
        maskSensitiveConfigValue(config);
        return config;
    }

    static boolean isSensitiveConfigKey(String configKey) {
        if (!StringUtils.hasText(configKey)) {
            return false;
        }
        String normalizedKey = configKey.trim().toLowerCase(Locale.ROOT);
        return SENSITIVE_CONFIG_KEY_SUFFIXES.stream().anyMatch(normalizedKey::endsWith);
    }

    private static void maskSensitiveConfigValues(List<SystemVO.ConfigVO> configs) {
        if (configs == null) {
            return;
        }
        configs.forEach(SystemManagementAppService::maskSensitiveConfigValue);
    }

    static void maskSensitiveConfigValue(SystemVO.ConfigVO config) {
        if (config != null && isSensitiveConfigKey(config.getConfigKey()) && StringUtils.hasText(config.getConfigValue())) {
            config.setConfigValue(MASKED_CONFIG_VALUE);
        }
    }

    @Transactional
    public SystemVO.ConfigVO updateConfig(CurrentUser currentUser, Long id, SystemDTO.ConfigUpsertRequest request) {
        jdbcTemplate.update(
                """
                        update sys_config
                        set config_key = ?, config_name = ?, config_value = ?, config_scope = 'PLATFORM', remark = ?,
                            updated_by = ?, updated_at = ?
                        where id = ? and deleted = 0
                        """,
                request.getConfigKey(),
                request.getConfigName(),
                resolveStoredConfigValue(id, request.getConfigKey(), request.getConfigValue()),
                request.getRemark(),
                currentUser.getUserId(),
                LocalDateTime.now(),
                id
        );
        operationAuditService.log(currentUser.getUserId(), currentUser.getUsername(), "config", "update", "UPDATE", "SUCCESS", "更新配置: " + request.getConfigKey());
        return getConfig(currentUser, id);
    }

    public SystemVO.SmtpSettingsVO getSmtpSettings(CurrentUser currentUser) {
        return systemPlatformSettingsAppService.getSmtpSettings(currentUser);
    }

    public SystemVO.WechatOfficialAccountSettingsVO getWechatOfficialAccountSettings(CurrentUser currentUser) {
        return systemPlatformSettingsAppService.getWechatOfficialAccountSettings(currentUser);
    }

    @Transactional
    public SystemVO.SmtpSettingsVO updateSmtpSettings(CurrentUser currentUser, SystemDTO.SmtpSettingsRequest request) {
        return systemPlatformSettingsAppService.updateSmtpSettings(currentUser, request);
    }

    public SystemVO.SmtpSettingsVO resetSmtpSettings(CurrentUser currentUser) {
        return systemPlatformSettingsAppService.resetSmtpSettings(currentUser);
    }

    @Transactional
    public SystemVO.WechatOfficialAccountSettingsVO updateWechatOfficialAccountSettings(CurrentUser currentUser, SystemDTO.WechatOfficialAccountSettingsRequest request) {
        return systemPlatformSettingsAppService.updateWechatOfficialAccountSettings(currentUser, request);
    }

    @Transactional
    public SystemVO.SmtpTestVO testSmtpSettings(CurrentUser currentUser, SystemDTO.SmtpTestRequest request) {
        return systemPlatformSettingsAppService.testSmtpSettings(currentUser, request);
    }

    @Transactional
    public SystemVO.ConfigVO createConfig(CurrentUser currentUser, SystemDTO.ConfigUpsertRequest request) {
        jdbcTemplate.update(
                """
                        insert into sys_config (
                            config_key, config_name, config_value, config_scope, is_system, remark,
                            created_by, updated_by, deleted
                        ) values (?, ?, ?, 'PLATFORM', 0, ?, ?, ?, 0)
                        """,
                request.getConfigKey(),
                request.getConfigName(),
                encryptConfigValue(request.getConfigKey(), request.getConfigValue()),
                request.getRemark(),
                currentUser.getUserId(),
                currentUser.getUserId()
        );
        operationAuditService.log(currentUser.getUserId(), currentUser.getUsername(), "config", "create", "CREATE", "SUCCESS", "创建配置: " + request.getConfigKey());
        SystemVO.ConfigVO config = jdbcTemplate.queryForObject(
                """
                        select id, config_key as configKey, config_name as configName,
                               config_value as configValue, is_system as isSystem, remark
                        from sys_config
                        where config_key = ? and deleted = 0
                        order by id desc
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(SystemVO.ConfigVO.class),
                request.getConfigKey()
        );
        maskSensitiveConfigValue(config);
        return config;
    }

    private String resolveStoredConfigValue(Long id, String configKey, String requestedValue) {
        if (isSensitiveConfigKey(configKey) && MASKED_CONFIG_VALUE.equals(requestedValue)) {
            String currentValue = jdbcTemplate.queryForObject(
                    """
                            select config_value
                            from sys_config
                            where id = ? and deleted = 0
                            """,
                    String.class,
                    id
            );
            return currentValue;
        }
        return encryptConfigValue(configKey, requestedValue);
    }

    private String encryptConfigValue(String configKey, String configValue) {
        if (!isSensitiveConfigKey(configKey) || fieldCryptoService == null) {
            return configValue;
        }
        return fieldCryptoService.encrypt(configValue);
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
        return systemPlatformSettingsAppService.getBrandingSettings(currentUser);
    }

    public SystemVO.RuntimeAppearanceSettingsVO getRuntimeAppearanceSettings(CurrentUser currentUser) {
        SystemVO.RuntimeAppearanceSettingsVO settings = new SystemVO.RuntimeAppearanceSettingsVO();
        settings.setBrandingSettings(getBrandingSettings(currentUser));
        settings.setWatermarkSettings(getWatermarkSettings(currentUser));
        settings.setFloatingWindowSettings(getFloatingWindowSettings(currentUser));
        return settings;
    }

    public SystemVO.BrandingSettingsVO getPublicBrandingSettings() {
        return systemPlatformSettingsAppService.getPublicBrandingSettings();
    }

    public SystemVO.SecuritySettingsVO getPublicSecuritySettings() {
        return toSecuritySettingsVO(securitySettingsService.loadSettingsFresh());
    }

    public SystemVO.AgreementSettingsVO getAgreementSettings() {
        return systemPlatformSettingsAppService.getAgreementSettings();
    }

    public SystemVO.AgreementSettingsVO getPublicAgreementSettings() {
        return systemPlatformSettingsAppService.getPublicAgreementSettings();
    }

    @Transactional
    public SystemVO.BrandingSettingsVO updateBrandingSettings(CurrentUser currentUser, SystemDTO.BrandingSettingsRequest request) {
        return systemPlatformSettingsAppService.updateBrandingSettings(currentUser, request);
    }

    @Transactional
    public SystemVO.AgreementSettingsVO updateAgreementSettings(CurrentUser currentUser, SystemDTO.AgreementSettingsRequest request) {
        return systemPlatformSettingsAppService.updateAgreementSettings(currentUser, request);
    }


    public SystemVO.WatermarkSettingsVO getWatermarkSettings(CurrentUser currentUser) {
        return systemPlatformSettingsAppService.getWatermarkSettings(currentUser);
    }

    @Transactional
    public SystemVO.WatermarkSettingsVO updateWatermarkSettings(CurrentUser currentUser, SystemDTO.WatermarkSettingsRequest request) {
        return systemPlatformSettingsAppService.updateWatermarkSettings(currentUser, request);
    }

    public SystemVO.FloatingWindowSettingsVO getFloatingWindowSettings(CurrentUser currentUser) {
        return systemPlatformSettingsAppService.getFloatingWindowSettings(currentUser);
    }

    @Transactional
    public SystemVO.FloatingWindowSettingsVO updateFloatingWindowSettings(CurrentUser currentUser, SystemDTO.FloatingWindowSettingsRequest request) {
        return systemPlatformSettingsAppService.updateFloatingWindowSettings(currentUser, request);
    }

    public PageResponse<SystemVO.AuditLogVO> listLoginLogs(CurrentUser currentUser, String username, long pageNo, long pageSize) {
        return listLoginLogs(currentUser, username, null, null, null, pageNo, pageSize);
    }

    public PageResponse<SystemVO.AuditLogVO> listLoginLogs(
            CurrentUser currentUser,
            String username,
            String loginType,
            String startTime,
            String endTime,
            long pageNo,
            long pageSize
    ) {
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
        if (StringUtils.hasText(startTime)) {
            baseSql += " and l.created_at >= ?";
            params.add(parseDateTime(startTime));
        }
        if (StringUtils.hasText(endTime)) {
            baseSql += " and l.created_at <= ?";
            params.add(parseDateTime(endTime));
        }
        String selectSql = """
                select l.id, l.user_id as userId, l.username, l.login_type as logType,
                       l.login_result as logResult, l.fail_reason as failReason, l.login_ip as loginIp,
                       l.user_agent as userAgent, l.request_id as requestId, l.trace_id as traceId, l.created_at as createdAt
                """ + baseSql + " order by l.id desc";
        return pageQuery(selectSql, "select count(1) " + baseSql, SystemVO.AuditLogVO.class, pageNo, pageSize, params);
    }

    private List<AuditLogVO> listCurrentUserSuccessfulLoginLogs(CurrentUser currentUser, long pageSize) {
        String selectSql = """
                select l.id, l.user_id as userId, l.username, l.login_type as logType,
                       l.login_result as logResult, l.fail_reason as failReason, l.login_ip as loginIp,
                       l.user_agent as userAgent, l.request_id as requestId, l.trace_id as traceId, l.created_at as createdAt
                from audit_login_log l
                where l.user_id = ?
                  and l.login_result = 'SUCCESS'
                  and l.login_type <> 'LOGOUT'
                order by l.created_at desc, l.id desc
                """;
        return jdbcTemplate.query(
                selectSql + " limit ?",
                new BeanPropertyRowMapper<>(AuditLogVO.class),
                currentUser.getUserId(),
                Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE))
        );
    }

    private List<AuditLogVO> listRecentOperationLogs(CurrentUser currentUser, String username, long pageSize) {
        String baseSql = """
                from audit_operation_log l
                where 1 = 1
                """;
        List<Object> params = new ArrayList<>();
        if (StringUtils.hasText(username)) {
            baseSql += " and l.username like ?";
            params.add(like(username));
        }
        String selectSql = """
                select l.id, l.user_id as userId, l.username, l.module_name as moduleName,
                       l.action_name as actionName, l.operation_type as operationType, l.result_status as logResult,
                       l.detail_message as detailMessage, l.request_id as requestId, l.trace_id as traceId,
                       l.created_at as createdAt
                """ + baseSql + " order by l.created_at desc, l.id desc limit ?";
        params.add(Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE)));
        return jdbcTemplate.query(selectSql, new BeanPropertyRowMapper<>(AuditLogVO.class), params.toArray());
    }

    public PageResponse<SystemVO.AuditLogVO> listOperationLogs(CurrentUser currentUser, String username, long pageNo, long pageSize) {
        return listOperationLogs(currentUser, username, null, null, pageNo, pageSize);
    }

    public PageResponse<SystemVO.AuditLogVO> listOperationLogs(
            CurrentUser currentUser,
            String username,
            String startTime,
            String endTime,
            long pageNo,
            long pageSize
    ) {
        String baseSql = """
                from audit_operation_log l
                where 1 = 1
                """;
        List<Object> params = new ArrayList<>();
        if (StringUtils.hasText(username)) {
            baseSql += " and l.username like ?";
            params.add(like(username));
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
                select l.id, l.user_id as userId, l.username, l.module_name as moduleName,
                       l.action_name as actionName, l.operation_type as operationType, l.result_status as logResult,
                       l.detail_message as detailMessage, l.request_id as requestId, l.trace_id as traceId,
                       l.created_at as createdAt
                """ + baseSql + " order by l.id desc";
        return pageQuery(selectSql, "select count(1) " + baseSql, SystemVO.AuditLogVO.class, pageNo, pageSize, params);
    }

    public PageResponse<SystemVO.AuditLogVO> listVerificationLogs(
            CurrentUser currentUser,
            String channel,
            String scene,
            String resultStatus,
            String startTime,
            String endTime,
            long pageNo,
            long pageSize
    ) {
        String baseSql = """
                from audit_operation_log l
                where l.deleted = 0
                  and l.module_name = 'verification'
                """;
        List<Object> params = new ArrayList<>();
        if (StringUtils.hasText(channel)) {
            baseSql += " and l.operation_type = ?";
            params.add(channel.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(scene)) {
            baseSql += " and l.action_name = ?";
            params.add(scene.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(resultStatus)) {
            baseSql += " and l.result_status = ?";
            params.add(resultStatus.trim().toUpperCase(Locale.ROOT));
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
                select l.id, l.user_id as userId, l.username, l.module_name as moduleName,
                       l.action_name as actionName, l.operation_type as operationType, l.result_status as logResult,
                       l.detail_message as detailMessage, l.request_id as requestId, l.trace_id as traceId,
                       l.created_at as createdAt
                """ + baseSql + " order by l.id desc";
        return pageQuery(selectSql, "select count(1) " + baseSql, SystemVO.AuditLogVO.class, pageNo, pageSize, params);
    }

    public PageResponse<SystemVO.AuditLogVO> listAiCallLogs(
            CurrentUser currentUser,
            Long employeeId,
            String skillCode,
            String resultStatus,
            String startTime,
            String endTime,
            long pageNo,
            long pageSize
    ) {
        String baseSql = """
                from ai_tool_audit_log l
                where l.is_deleted = 0
                """;
        List<Object> params = new ArrayList<>();
        if (employeeId != null) {
            baseSql += " and l.employee_id = ?";
            params.add(employeeId);
        }
        if (StringUtils.hasText(skillCode)) {
            baseSql += " and l.skill_code like ?";
            params.add(like(skillCode));
        }
        if (StringUtils.hasText(resultStatus)) {
            baseSql += " and l.result_status = ?";
            params.add(resultStatus);
        }
        if (StringUtils.hasText(startTime)) {
            baseSql += " and l.create_time >= ?";
            params.add(parseDateTime(startTime));
        }
        if (StringUtils.hasText(endTime)) {
            baseSql += " and l.create_time <= ?";
            params.add(parseDateTime(endTime));
        }
        String selectSql = """
                select l.id, l.conversation_id as conversationId,
                       l.employee_id as employeeId, l.skill_code as skillCode, l.tool_name as toolName,
                       l.permission_mode as permissionMode, l.confirm_required as confirmRequired,
                       l.confirm_result as confirmResult, l.result_status as logResult,
                       l.detail_message as detailMessage, l.request_payload_json as requestPayloadJson,
                       l.response_payload_json as responsePayloadJson, l.create_time as createdAt,
                       'AI' as moduleName, l.tool_name as actionName, 'CALL' as operationType
                """ + baseSql + " order by l.id desc";
        return pageQuery(selectSql, "select count(1) " + baseSql, SystemVO.AuditLogVO.class, pageNo, pageSize, params);
    }

    public Integer countMenus() {
        Integer cached = menuCountCache.getIfPresent(MENU_COUNT_CACHE_KEY);
        if (cached != null) {
            return cached;
        }
        int result = (int) jdbcTemplate.query(
                """
                        select menu_code as menuCode, path, component, permission_key as permissionKey
                        from sys_menu
                        where deleted = 0 and status = 'ENABLED'
                        """,
                new BeanPropertyRowMapper<>(SystemVO.MenuVO.class)
        ).stream()
                .filter(menu -> !isAiMenu(menu))
                .count();
        menuCountCache.put(MENU_COUNT_CACHE_KEY, result);
        return result;
    }

    private void invalidateMenuCountCache() {
        menuCountCache.invalidate(MENU_COUNT_CACHE_KEY);
    }

    private String normalizeNullableText(String value) {
        String normalized = normalizeConfigText(value);
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private boolean canViewSensitiveUserInfo(CurrentUser currentUser) {
        return currentUser != null
                && currentUser.getPermissions() != null
                && (currentUser.getPermissions().contains("*")
                || currentUser.getPermissions().contains("system:user:sensitive:view"));
    }

    private void maskSensitiveUsers(List<SystemVO.UserVO> users, boolean canViewSensitive) {
        if (canViewSensitive || users == null || users.isEmpty()) {
            return;
        }
        users.forEach(this::maskSensitiveUser);
    }

    private void maskSensitiveUser(SystemVO.UserVO user) {
        if (user == null) {
            return;
        }
        user.setMobile(maskMobile(user.getMobile()));
        user.setEmail(maskEmail(user.getEmail()));
        user.setIdCardNumber(maskIdCard(user.getIdCardNumber()));
    }

    private void decorateIamUserDetail(SystemVO.UserDetailVO detail, Long userId, boolean canViewSensitive) {
        detail.setIdentities(iamUserService.listIdentities(userId).stream()
                .map(identity -> toUserIdentityVO(identity, canViewSensitive))
                .toList());
        detail.setRecentDevices(iamUserService.listRecentDevices(userId, 10).stream()
                .map(device -> toUserDeviceVO(device, canViewSensitive))
                .toList());
        detail.setSecuritySetting(iamUserService.findSecuritySetting(userId)
                .map(this::toUserSecuritySettingVO)
                .orElse(null));
    }

    private UserDetailVO.UserIdentityVO toUserIdentityVO(IamUserAccount.IdentityView identity, boolean canViewSensitive) {
        UserDetailVO.UserIdentityVO vo = new UserDetailVO.UserIdentityVO();
        vo.setId(identity.getId());
        vo.setIdentityType(identity.getIdentityType());
        vo.setIdentifier(canViewSensitive ? identity.getIdentifier() : maskIdentity(identity.getIdentityType(), identity.getIdentifier()));
        vo.setVerified(identity.getVerified());
        vo.setPrimaryIdentity(identity.getPrimaryIdentity());
        vo.setStatus(identity.getStatus());
        return vo;
    }

    private UserDetailVO.UserDeviceVO toUserDeviceVO(IamUserAccount.DeviceView device, boolean canViewSensitive) {
        UserDetailVO.UserDeviceVO vo = new UserDetailVO.UserDeviceVO();
        vo.setId(device.getId());
        vo.setDeviceId(canViewSensitive ? device.getDeviceId() : maskDeviceId(device.getDeviceId()));
        vo.setDeviceName(device.getDeviceName());
        vo.setDeviceType(device.getDeviceType());
        vo.setOs(device.getOs());
        vo.setBrowser(device.getBrowser());
        vo.setLastIp(canViewSensitive ? device.getLastIp() : maskIp(device.getLastIp()));
        vo.setLastActiveAt(device.getLastActiveAt());
        vo.setTrusted(device.getTrusted());
        return vo;
    }

    private UserDetailVO.UserSecuritySettingVO toUserSecuritySettingVO(IamUserAccount.SecuritySettingView setting) {
        UserDetailVO.UserSecuritySettingVO vo = new UserDetailVO.UserSecuritySettingVO();
        vo.setMfaEnabled(setting.getMfaEnabled());
        vo.setPasswordLoginEnabled(setting.getPasswordLoginEnabled());
        vo.setSmsLoginEnabled(setting.getSmsLoginEnabled());
        vo.setEmailLoginEnabled(setting.getEmailLoginEnabled());
        vo.setPasskeyEnabled(setting.getPasskeyEnabled());
        vo.setLoginNotifyEnabled(setting.getLoginNotifyEnabled());
        return vo;
    }

    private String maskIdentity(String identityType, String identifier) {
        if (!StringUtils.hasText(identifier)) {
            return identifier;
        }
        String type = identityType == null ? "" : identityType.toUpperCase(Locale.ROOT);
        if (IamUserService.IDENTITY_MOBILE.equals(type)) {
            return maskMobile(identifier);
        }
        if (IamUserService.IDENTITY_EMAIL.equals(type)) {
            return maskEmail(identifier);
        }
        if (IamUserService.IDENTITY_USERNAME.equals(type)) {
            return maskUsername(identifier);
        }
        return maskLongIdentifier(identifier);
    }

    private String maskMobile(String mobile) {
        if (!StringUtils.hasText(mobile) || mobile.length() < 7) {
            return mobile;
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }

    private String maskEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return email;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***" + (atIndex >= 0 ? email.substring(atIndex) : "");
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }

    private String maskIdCard(String idCardNumber) {
        if (!StringUtils.hasText(idCardNumber) || idCardNumber.length() < 8) {
            return idCardNumber;
        }
        return idCardNumber.substring(0, 4) + "********" + idCardNumber.substring(idCardNumber.length() - 4);
    }

    private String maskUsername(String username) {
        if (!StringUtils.hasText(username) || username.length() <= 2) {
            return "***";
        }
        return username.charAt(0) + "***" + username.charAt(username.length() - 1);
    }

    private String maskLongIdentifier(String identifier) {
        if (!StringUtils.hasText(identifier) || identifier.length() <= 8) {
            return "***";
        }
        return identifier.substring(0, 4) + "****" + identifier.substring(identifier.length() - 4);
    }

    private String maskDeviceId(String deviceId) {
        return maskLongIdentifier(deviceId);
    }

    private String maskIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return ip;
        }
        if (ip.contains(".")) {
            int lastDot = ip.lastIndexOf('.');
            return lastDot > 0 ? ip.substring(0, lastDot + 1) + "*" : "*";
        }
        if (ip.contains(":")) {
            int idx = ip.indexOf(':');
            return idx > 0 ? ip.substring(0, idx) + ":****" : "****";
        }
        return "*";
    }

    private LocalDateTime parseDateTimeParam(String value, boolean endOfDay) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        try {
            if (normalized.length() == 10) {
                LocalDate date = LocalDate.parse(normalized);
                return endOfDay ? date.atTime(23, 59, 59) : date.atStartOfDay();
            }
            return LocalDateTime.parse(normalized.replace(" ", "T"));
        } catch (RuntimeException exception) {
            throw new BizException(ErrorCode.UNPROCESSABLE_ENTITY, "时间参数格式不正确，请使用 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss");
        }
    }

    private String normalizeLocale(String locale) {
        if (!StringUtils.hasText(locale)) {
            return DEFAULT_LOCALE;
        }

        String normalized = locale.trim();
        if ("zh".equalsIgnoreCase(normalized) || "zh-CN".equalsIgnoreCase(normalized)) {
            return "zh-CN";
        }
        if ("en".equalsIgnoreCase(normalized) || "en-US".equalsIgnoreCase(normalized)) {
            return "en-US";
        }
        return DEFAULT_LOCALE;
    }

    private boolean contactValueChanged(String currentValue, String nextValue) {
        String current = normalizeNullableText(currentValue);
        if (current == null) {
            return nextValue != null;
        }
        return !current.equals(nextValue);
    }

    private String buildCopyrightText(String companyName, Integer copyrightStartYear) {
        int currentYear = LocalDate.now().getYear();
        int startYear = copyrightStartYear == null ? currentYear : copyrightStartYear;
        String yearLabel = startYear < currentYear ? startYear + "-" + currentYear : String.valueOf(startYear);
        String owner = StringUtils.hasText(companyName) ? companyName : "宏翔商道";
        return "Copyright © " + yearLabel + " " + owner + " All Rights Reserved";
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

    private List<String> parseWatermarkTextLines(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return value.lines()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
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
        snapshot.setLoginDefenseWindowMinutes(request.getLoginDefenseWindowMinutes());
        snapshot.setLoginMaxValidationAttempts(request.getLoginMaxValidationAttempts());
        snapshot.setLoginMaxFailureCount(request.getLoginMaxFailureCount());
        snapshot.setVerificationCodeExpireSeconds(request.getVerificationCodeExpireSeconds());
        snapshot.setVerificationCodeCooldownSeconds(request.getVerificationCodeCooldownSeconds());
        snapshot.setPasswordMinLength(request.getPasswordMinLength());
        snapshot.setPasswordRequireUppercase(Boolean.TRUE.equals(request.getPasswordRequireUppercase()));
        snapshot.setPasswordRequireLowercase(Boolean.TRUE.equals(request.getPasswordRequireLowercase()));
        snapshot.setPasswordRequireSpecialCharacter(Boolean.TRUE.equals(request.getPasswordRequireSpecialCharacter()));
        snapshot.setPasswordAllowConsecutiveCharacters(Boolean.TRUE.equals(request.getPasswordAllowConsecutiveCharacters()));
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
        vo.setVerificationCodeExpireSeconds(snapshot.getVerificationCodeExpireSeconds());
        vo.setVerificationCodeCooldownSeconds(snapshot.getVerificationCodeCooldownSeconds());
        vo.setPasswordMinLength(snapshot.getPasswordMinLength());
        vo.setPasswordRequireUppercase(snapshot.isPasswordRequireUppercase());
        vo.setPasswordRequireLowercase(snapshot.isPasswordRequireLowercase());
        vo.setPasswordRequireSpecialCharacter(snapshot.isPasswordRequireSpecialCharacter());
        vo.setPasswordAllowConsecutiveCharacters(snapshot.isPasswordAllowConsecutiveCharacters());
        return vo;
    }

    private CurrentUserVO buildCurrentUser(CurrentUser currentUser) {
        return buildCurrentUser(currentUser, resolvePermissionSnapshot(currentUser));
    }

    private CurrentUserVO buildCurrentUser(CurrentUser currentUser, PermissionSnapshotService.PermissionSnapshot snapshot) {
        SysUserEntity user = userDomainService.findById(currentUser.getUserId())
                .orElseThrow(() -> new BizException(
                        ErrorCode.SESSION_EXPIRED,
                        "会话关联用户不存在: " + currentUser.getUserId(),
                        ErrorCode.SESSION_EXPIRED.getDefaultUserMessage()
                ));
        CompletableFuture<Map<String, String>> extraProfileValuesFuture = CompletableFuture.supplyAsync(() -> loadExtraProfileValues(user.getId()), BLOCKING_IO_EXECUTOR);
        CompletableFuture<String> localeFuture = CompletableFuture.supplyAsync(() -> resolveLocale(user.getId()), BLOCKING_IO_EXECUTOR);
        CompletableFuture<List<CurrentUserVO.RoleOptionVO>> availableRolesFuture = CompletableFuture.supplyAsync(
                () -> listAvailableRoles(currentUser.getUserId()),
                BLOCKING_IO_EXECUTOR
        );
        CurrentUserVO response = new CurrentUserVO();
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setRealName(user.getRealName());
        response.setAvatarUrl(user.getAvatarUrl());
        response.setMobile(user.getMobile());
        response.setEmail(user.getEmail());
        response.setBirthMonth(user.getBirthMonth());
        response.setGender(user.getGender());
        response.setRegion(user.getRegion());
        response.setAvailableTime(user.getAvailableTime());
        response.setIdCardNumber(user.getIdCardNumber());
        response.setExtraProfileValues(extraProfileValuesFuture.join());
        response.setLocale(localeFuture.join());
        response.setSimulatedRoleId(currentUser.getSimulatedRoleId());
        response.setAvailableRoles(availableRolesFuture.join());
        response.setSessionId(currentUser.getSessionId());
        response.setPermissionsVersion(snapshot.getVersion());
        response.setSessionVersion(currentUser.getSessionVersion());
        response.setPermissions(snapshot.getPermissionList());
        response.setDefaultHomePath(snapshot.getDefaultHomePath());
        return response;
    }

    private void updateCurrentUserExtraProfileValues(CurrentUser currentUser, Long userId, Map<String, String> requestedValues) {
        Map<String, String> sanitizedValues = sanitizeExtraProfileValues(currentUser, requestedValues);
        try {
            String extraJson = OBJECT_MAPPER.writeValueAsString(Map.of(EXTRA_PROFILE_VALUES_KEY, sanitizedValues));
            jdbcTemplate.update(
                    """
                            insert into iam_user_profile (user_id, extra_json, deleted)
                            values (?, ?, 0)
                            on duplicate key update extra_json = json_merge_patch(coalesce(extra_json, json_object()), values(extra_json)),
                                                    deleted = 0,
                                                    updated_at = current_timestamp
                            """,
                    userId,
                    extraJson
            );
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "自定义资料序列化失败");
        }
    }

    private Map<String, String> sanitizeExtraProfileValues(CurrentUser currentUser, Map<String, String> requestedValues) {
        Set<String> allowedKeys = systemProfileSettingsAppService.getProfileFieldSettings(currentUser).stream()
                .filter(item -> Boolean.TRUE.equals(item.getCustom()))
                .map(ProfileFieldSettingVO::getFieldKey)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, String> sanitizedValues = new LinkedHashMap<>();
        if (requestedValues == null || requestedValues.isEmpty() || allowedKeys.isEmpty()) {
            return sanitizedValues;
        }
        for (String fieldKey : allowedKeys) {
            String value = normalizeNullableText(requestedValues.get(fieldKey));
            if (!StringUtils.hasText(value)) {
                continue;
            }
            sanitizedValues.put(fieldKey, value.length() > CUSTOM_PROFILE_VALUE_MAX_LENGTH
                    ? value.substring(0, CUSTOM_PROFILE_VALUE_MAX_LENGTH)
                    : value);
        }
        return sanitizedValues;
    }

    private Map<String, String> loadExtraProfileValues(Long userId) {
        String extraJson;
        try {
            extraJson = jdbcTemplate.queryForObject(
                    """
                            select extra_json
                            from iam_user_profile
                            where user_id = ? and deleted = 0
                            limit 1
                            """,
                    String.class,
                    userId
            );
        } catch (EmptyResultDataAccessException ignored) {
            return Map.of();
        }
        if (!StringUtils.hasText(extraJson)) {
            return Map.of();
        }
        try {
            Map<String, Object> payload = OBJECT_MAPPER.readValue(extraJson, new TypeReference<>() {});
            Object rawValues = payload.get(EXTRA_PROFILE_VALUES_KEY);
            if (!(rawValues instanceof Map<?, ?> rawMap)) {
                return Map.of();
            }
            Map<String, String> values = new LinkedHashMap<>();
            rawMap.forEach((key, value) -> {
                if (key instanceof String fieldKey && value instanceof String fieldValue && StringUtils.hasText(fieldValue)) {
                    values.put(fieldKey, fieldValue);
                }
            });
            return values;
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    private PermissionSnapshotService.PermissionSnapshot resolvePermissionSnapshot(CurrentUser currentUser) {
        if (currentUser == null) {
            return PermissionSnapshotService.PermissionSnapshot.empty();
        }
        return resolvePermissionSnapshot(currentUser.getUserId(), currentUser.getSimulatedRoleId(), currentUser);
    }

    private PermissionSnapshotService.PermissionSnapshot resolvePermissionSnapshot(Long userId, Long simulatedRoleId, CurrentUser currentUser) {
        if (userId == null) {
            return PermissionSnapshotService.PermissionSnapshot.empty();
        }
        if (simulatedRoleId != null) {
            return permissionSnapshotService.loadRoleSnapshot(simulatedRoleId);
        }
        PermissionSnapshotService.PermissionSnapshot snapshotFromCurrentUser = snapshotFromCurrentUser(currentUser);
        if (snapshotFromCurrentUser != null) {
            return snapshotFromCurrentUser;
        }
        return permissionSnapshotService.loadSnapshot(userId);
    }

    private PermissionSnapshotService.PermissionSnapshot snapshotFromCurrentUser(CurrentUser currentUser) {
        if (currentUser == null || !StringUtils.hasText(currentUser.getPermissionsVersion())) {
            return null;
        }
        return new PermissionSnapshotService.PermissionSnapshot(
                currentUser.getPermissionsVersion(),
                currentUser.getPermissions(),
                currentUser.getRoleIds(),
                currentUser.getPrimaryDeptId(),
                currentUser.getDeptIds(),
                currentUser.getDescendantDeptIds(),
                currentUser.getDataScopes(),
                currentUser.getDefaultHomePath()
        );
    }

    private List<CurrentUserVO.RoleOptionVO> listAvailableRoles(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                        select r.id as id,
                               r.role_code as roleCode,
                               r.role_name as roleName,
                               r.role_type as roleType,
                               count(rp.permission_key) as permissionCount
                        from sys_user_role ur
                        join sys_role r on r.id = ur.role_id and r.deleted = 0
                        left join sys_role_permission rp on rp.role_id = r.id and rp.deleted = 0
                        where ur.user_id = ? and ur.deleted = 0
                        group by r.id, r.role_code, r.role_name, r.role_type
                        order by r.id desc
                        """,
                (rs, rowNum) -> {
                    CurrentUserVO.RoleOptionVO role = new CurrentUserVO.RoleOptionVO();
                    role.setId(rs.getLong("id"));
                    role.setRoleCode(rs.getString("roleCode"));
                    role.setRoleName(rs.getString("roleName"));
                    role.setRoleType(rs.getString("roleType"));
                    role.setPermissionCount(rs.getInt("permissionCount"));
                    return role;
                },
                userId
        );
    }

    private String resolveLocale(Long userId) {
        if (userId == null) {
            return DEFAULT_LOCALE;
        }
        try {
            String locale = jdbcTemplate.queryForObject(
                    """
                            select locale
                            from iam_user_profile
                            where user_id = ? and deleted = 0
                            limit 1
                            """,
                    String.class,
                    userId
            );
            return normalizeLocale(locale);
        } catch (EmptyResultDataAccessException ex) {
            return DEFAULT_LOCALE;
        }
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private List<String> listCurrentRoleNames(Long userId) {
        return jdbcTemplate.queryForList(
                """
                        select r.role_name
                        from sys_user_role ur
                        join sys_role r on r.id = ur.role_id and r.deleted = 0
                        where ur.user_id = ? and ur.deleted = 0
                        order by r.id asc
                        """,
                String.class,
                userId
        );
    }

    private void decorateUsers(List<SystemVO.UserVO> users) {
        List<Long> userIds = users.stream()
                .map(SystemVO.UserVO::getId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return;
        }

        Map<Long, List<String>> roleNames = listUserRoleNames(userIds);
        users.forEach(user -> {
            user.setRoleNames(roleNames.getOrDefault(user.getId(), List.of()));
        });
    }

    private List<Long> listUserRoleIds(Long userId) {
        return jdbcTemplate.queryForList(
                """
                        select ur.role_id
                        from sys_user_role ur
                        where ur.user_id = ? and ur.deleted = 0
                        order by ur.role_id asc
                        """,
                Long.class,
                userId
        );
    }

    private List<String> listUserRoleNames(Long userId) {
        return jdbcTemplate.queryForList(
                """
                        select r.role_name
                        from sys_user_role ur
                        join sys_role r on r.id = ur.role_id and r.deleted = 0
                        where ur.user_id = ? and ur.deleted = 0
                        order by r.id asc
                        """,
                String.class,
                userId
        );
    }

    private Map<Long, List<String>> listUserRoleNames(List<Long> userIds) {
        String placeholders = placeholders(userIds.size());
        List<Object> params = new ArrayList<>();
        params.addAll(userIds);
        return jdbcTemplate.query(
                """
                        select ur.user_id as userId, r.role_name as roleName
                        from sys_user_role ur
                        join sys_role r on r.id = ur.role_id and r.deleted = 0
                        where ur.user_id in (%s) and ur.deleted = 0
                        order by ur.user_id asc, r.id asc
                        """.formatted(placeholders),
                rs -> {
                    Map<Long, List<String>> result = new LinkedHashMap<>();
                    while (rs.next()) {
                        result.computeIfAbsent(rs.getLong("userId"), ignored -> new ArrayList<>())
                                .add(rs.getString("roleName"));
                    }
                    return result;
                },
                params.toArray()
        );
    }

    private String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }

    private SystemVO.UserVO queryUser(Long userId) {
        SystemVO.UserVO user = queryOne(
                """
                        select u.id, u.username, u.mobile, u.id_card_number as idCardNumber, u.nickname, u.real_name as realName, u.avatar_url as avatarUrl,
                               u.email, u.birth_month as birthMonth, u.gender, u.region, u.available_time as availableTime,
                               u.status, iu.user_no as userNo, iu.source,
                               coalesce(iu.registered_at, u.created_at) as registeredAt,
                               iu.last_login_at as lastLoginAt,
                               u.created_at as createdAt, u.updated_at as updatedAt
                        from sys_user u
                        left join iam_user iu on iu.id = u.id and iu.deleted = 0
                        where u.id = ? and u.deleted = 0
                        """,
                SystemVO.UserVO.class,
                userId
        );
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        user.setRoleNames(listUserRoleNames(user.getId()));
        return user;
    }

    private Long insertOrUpdateUser(Long userId, SystemDTO.UserUpsertRequest request, Long operatorId) {
        String normalizedStatus = normalizeUserStatus(request.getStatus());
        if (userId != null && isProtectedAdminAccount(userId, request.getUsername()) && "DISABLED".equals(normalizedStatus)) {
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
                                uuid, username, password_hash, mobile, nickname, real_name, avatar_url, email, birth_month, gender, region,
                                available_time, id_card_number, status,
                                created_by, updated_by, deleted
                            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                            """,
                    UUID.randomUUID().toString(),
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
                    normalizedStatus,
                    operatorId,
                    operatorId
            );
            Long createdUserId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
            userDomainService.findById(createdUserId).ifPresent(user -> {
                iamUserService.createUserWithIdentity(user, request.getUsername(), "ADMIN_CREATE");
                iamUserService.recordUserRegistered(user.getId(), "ADMIN_CREATE", null, null);
            });
            return createdUserId;
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
                normalizedStatus,
                operatorId,
                LocalDateTime.now(),
                userId
        );
        if (StringUtils.hasText(request.getPassword())) {
            passwordPolicyService.validatePassword(request.getPassword());
            String encodedPassword = passwordEncoder.encode(request.getPassword());
            jdbcTemplate.update(
                    "update sys_user set password_hash = ?, updated_by = ?, updated_at = ? where id = ? and deleted = 0",
                    encodedPassword,
                    operatorId,
                    LocalDateTime.now(),
                    userId
            );
            iamUserService.upsertPasswordCredential(userId, encodedPassword);
        }
        userDomainService.findById(userId).ifPresent(iamUserService::updateProfile);
        return userId;
    }

    private boolean isProtectedAdminAccount(Long userId, String username) {
        return DEFAULT_ADMIN_USER_ID.equals(userId)
                || (StringUtils.hasText(username) && DEFAULT_ADMIN_USERNAME.equalsIgnoreCase(username));
    }

    private void replaceUserRoles(Long userId, List<Long> roleIds, Long operatorId) {
        if (CollectionUtils.isEmpty(roleIds)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "用户角色不能为空");
        }
        List<Long> distinctRoleIds = new ArrayList<>(new LinkedHashSet<>(roleIds));
        Long existingRoleCount = jdbcTemplate.queryForObject(
                "select count(1) from sys_role where deleted = 0 and id in (" + placeholders(distinctRoleIds.size()) + ")",
                Long.class,
                distinctRoleIds.toArray()
        );
        if (existingRoleCount == null || existingRoleCount != distinctRoleIds.size()) {
            throw new BizException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        jdbcTemplate.update(
                "delete from sys_user_role where user_id = ?",
                userId
        );
        for (Long roleId : distinctRoleIds) {
            jdbcTemplate.update(
                    """
                            insert into sys_user_role (user_id, role_id, created_by, updated_by, deleted)
                            values (?, ?, ?, ?, 0)
                            """,
                    userId,
                    roleId,
                    operatorId,
                    operatorId
            );
        }
    }

    private String normalizeUserStatus(String status) {
        if (!StringUtils.hasText(status)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "用户状态不能为空");
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ENABLED", "DISABLED").contains(normalized)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "用户状态只能是 ENABLED 或 DISABLED");
        }
        return normalized;
    }

    private Long insertMenu(Long menuId, SystemDTO.MenuUpsertRequest request, Long operatorId) {
        if (menuId == null) {
            jdbcTemplate.update(
                    """
                            insert into sys_menu (
                                parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no,
                                permission_key, status, created_by, updated_by, deleted
                            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
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
                    operatorId
            );
            return jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        }
        jdbcTemplate.update(
                """
                        update sys_menu
                        set parent_id = ?, menu_code = ?, menu_name = ?, menu_type = ?, path = ?, component = ?,
                            icon = ?, sort_no = ?, permission_key = ?, status = ?, updated_by = ?, updated_at = ?
                        where id = ? and deleted = 0
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
                menuId
        );
        return menuId;
    }

    private void ensureEditableMenu(Long menuId) {
        if (menuId == null || menuId <= 0) {
            return;
        }
        SystemVO.MenuVO menu = queryOne(
                """
                        select id, parent_id as parentId, menu_code as menuCode,
                               menu_name as menuName, menu_type as menuType, path, component, icon, sort_no as sortNo,
                               permission_key as permissionKey, status
                        from sys_menu
                        where id = ? and deleted = 0
                        """,
                SystemVO.MenuVO.class,
                menuId
        );
        if (menu == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "菜单不存在");
        }
        ensureEditableMenu(menu);
    }

    private void ensureEditableMenu(SystemVO.MenuVO menu) {
        if (menu == null || menu.getId() == null || menu.getId() <= 0 || menu.isBuiltin()) {
            throw new BizException(ErrorCode.FORBIDDEN, "内置设置菜单不允许修改");
        }
    }

    private void ensureEditableParentMenu(Long parentId) {
        if (parentId == null || parentId == 0) {
            return;
        }
        if (parentId < 0) {
            throw new BizException(ErrorCode.FORBIDDEN, "内置设置菜单不允许修改");
        }
        ensureEditableMenu(parentId);
    }

    private void ensureEditableMenuRequest(SystemDTO.MenuUpsertRequest request) {
        if (request == null) {
            return;
        }
        if (SystemRouteCatalog.isBuiltInMenuPath(request.getPath()) || SystemRouteCatalog.isBuiltInMenuComponent(request.getComponent())) {
            throw new BizException(ErrorCode.FORBIDDEN, "内置设置菜单不允许修改");
        }
    }

    private Long upsertDictType(Long id, SystemDTO.DictTypeUpsertRequest request, Long operatorId) {
        if (id == null) {
            jdbcTemplate.update(
                    """
                            insert into sys_dict_type (dict_code, dict_name, status, is_system, remark, created_by, updated_by, deleted)
                            values (?, ?, ?, 0, ?, ?, ?, 0)
                            """,
                    request.getDictCode(),
                    request.getDictName(),
                    request.getStatus(),
                    request.getRemark(),
                    operatorId,
                    operatorId
            );
            return jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
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
            return jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
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

    private boolean isSystemDictType(SystemVO.DictTypeVO type) {
        return type != null && type.getIsSystem() != null && type.getIsSystem() != 0;
    }

    private <T> PageResponse<T> pageQuery(String selectSql, String countSql, Class<T> voClass, long pageNo, long pageSize, List<Object> params) {
        long safePageNo = pageNo <= 0 ? 1 : pageNo;
        long safePageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        long offset = (safePageNo - 1) * safePageSize;
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(safePageSize);
        queryParams.add(offset);
        String pagedSql = selectSql + " limit ? offset ?";
        List<T> records = jdbcTemplate.query(pagedSql, new BeanPropertyRowMapper<>(voClass), queryParams.toArray());
        long total = safePageNo == 1 && records.size() < safePageSize
                ? records.size()
                : nullToZero(jdbcTemplate.queryForObject(countSql, Long.class, params.toArray()));
        PageResponse<T> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(total);
        response.setPageNo(safePageNo);
        response.setPageSize(safePageSize);
        return response;
    }

    private long nullToZero(Long value) {
        return value == null ? 0 : value;
    }

    private <T> PageResponse<T> cursorQuery(String selectSql, Class<T> voClass, long pageSize, List<Object> params) {
        long safePageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(safePageSize + 1);
        List<T> records = jdbcTemplate.query(selectSql + " limit ?", new BeanPropertyRowMapper<>(voClass), queryParams.toArray());
        boolean hasMore = records.size() > safePageSize;
        if (hasMore) {
            records = new ArrayList<>(records.subList(0, (int) safePageSize));
        }
        PageResponse<T> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(-1);
        response.setPageNo(1);
        response.setPageSize(safePageSize);
        response.setHasMore(hasMore);
        if (!records.isEmpty() && records.get(records.size() - 1) instanceof SystemVO.UserVO user) {
            response.setNextCursorId(user.getId());
            response.setNextCursorCreatedAt(user.getRegisteredAt() == null ? null : user.getRegisteredAt().toString());
        }
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
        Comparator<SystemVO.MenuVO> comparator = Comparator
                .comparingInt((SystemVO.MenuVO item) -> item.getSortNo() == null ? 0 : item.getSortNo())
                .thenComparing(item -> item.getId() == null ? 0L : item.getId());
        sortMenuTree(roots, comparator);
        return roots;
    }

    private void sortMenuTree(List<SystemVO.MenuVO> roots) {
        Comparator<SystemVO.MenuVO> comparator = Comparator
                .comparingInt((SystemVO.MenuVO item) -> item.getSortNo() == null ? 0 : item.getSortNo())
                .thenComparing(item -> item.getId() == null ? 0L : item.getId());
        sortMenuTree(roots, comparator);
    }

    private void sortMenuTree(List<SystemVO.MenuVO> roots, Comparator<SystemVO.MenuVO> comparator) {
        roots.sort(comparator);
        for (SystemVO.MenuVO root : roots) {
            sortChildren(root, comparator);
        }
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

    private List<SystemVO.MenuVO> copyMenus(List<SystemVO.MenuVO> menus) {
        if (menus == null || menus.isEmpty()) {
            return List.of();
        }
        List<SystemVO.MenuVO> copied = new ArrayList<>(menus.size());
        for (SystemVO.MenuVO menu : menus) {
            copied.add(copyMenu(menu));
        }
        return copied;
    }

    private SystemVO.MenuVO copyMenu(SystemVO.MenuVO source) {
        SystemVO.MenuVO target = new SystemVO.MenuVO();
        target.setId(source.getId());
        target.setParentId(source.getParentId());
        target.setMenuCode(source.getMenuCode());
        target.setMenuName(source.getMenuName());
        target.setMenuType(source.getMenuType());
        target.setPath(source.getPath());
        target.setComponent(source.getComponent());
        target.setIcon(source.getIcon());
        target.setSortNo(source.getSortNo());
        target.setPermissionKey(source.getPermissionKey());
        target.setStatus(source.getStatus());
        target.setBuiltin(source.isBuiltin());
        target.setChildren(copyMenus(source.getChildren()));
        return target;
    }

    private List<SystemVO.PermissionVO> copyPermissions(List<SystemVO.PermissionVO> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return List.of();
        }
        List<SystemVO.PermissionVO> copied = new ArrayList<>(permissions.size());
        for (SystemVO.PermissionVO permission : permissions) {
            SystemVO.PermissionVO item = new SystemVO.PermissionVO();
            item.setPermissionKey(permission.getPermissionKey());
            item.setPermissionName(permission.getPermissionName());
            item.setPermissionGroup(permission.getPermissionGroup());
            item.setSourceType(permission.getSourceType());
            item.setPluginCode(permission.getPluginCode());
            copied.add(item);
        }
        return copied;
    }

    private List<SystemVO.PermissionTreeVO> copyPermissionTrees(List<SystemVO.PermissionTreeVO> permissionTrees) {
        if (permissionTrees == null || permissionTrees.isEmpty()) {
            return List.of();
        }
        List<SystemVO.PermissionTreeVO> copied = new ArrayList<>(permissionTrees.size());
        for (SystemVO.PermissionTreeVO permissionTree : permissionTrees) {
            copied.add(copyPermissionTree(permissionTree));
        }
        return copied;
    }

    private SystemVO.PermissionTreeVO copyPermissionTree(SystemVO.PermissionTreeVO source) {
        SystemVO.PermissionTreeVO target = new SystemVO.PermissionTreeVO();
        target.setNodeType(source.getNodeType());
        target.setPageKey(source.getPageKey());
        target.setPageName(source.getPageName());
        target.setRoutePath(source.getRoutePath());
        target.setIcon(source.getIcon());
        target.setPermissionKey(source.getPermissionKey());
        target.setPermissionGroup(source.getPermissionGroup());
        target.setSourceType(source.getSourceType());
        target.setSelectable(source.isSelectable());
        target.setChildren(copyPermissionTrees(source.getChildren()));
        target.setActionPermissions(copyPermissionActions(source.getActionPermissions()));
        return target;
    }

    private List<SystemVO.PermissionActionVO> copyPermissionActions(List<SystemVO.PermissionActionVO> actionPermissions) {
        if (actionPermissions == null || actionPermissions.isEmpty()) {
            return List.of();
        }
        List<SystemVO.PermissionActionVO> copied = new ArrayList<>(actionPermissions.size());
        for (SystemVO.PermissionActionVO action : actionPermissions) {
            SystemVO.PermissionActionVO item = new SystemVO.PermissionActionVO();
            item.setPermissionKey(action.getPermissionKey());
            item.setPermissionName(action.getPermissionName());
            item.setPermissionGroup(action.getPermissionGroup());
            item.setSourceType(action.getSourceType());
            copied.add(item);
        }
        return copied;
    }

    private List<String> splitCsv(String csv) {
        if (!StringUtils.hasText(csv)) {
            return List.of();
        }
        return List.of(csv.split(","));
    }

    private void copyUser(SystemVO.UserDetailVO target, SystemVO.UserVO source) {
        target.setId(source.getId());
        target.setUserNo(source.getUserNo());
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
        target.setSource(source.getSource());
        target.setRegisteredAt(source.getRegisteredAt());
        target.setLastLoginAt(source.getLastLoginAt());
        target.setRoleNames(source.getRoleNames());
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

    private String normalizeContactType(String contactType) {
        if (!StringUtils.hasText(contactType)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "绑定类型不能为空");
        }
        String normalized = contactType.trim().toLowerCase(Locale.ROOT);
        if (!"mobile".equals(normalized) && !"email".equals(normalized)) {
            throw new BizException(ErrorCode.NOT_FOUND, "绑定类型不存在");
        }
        return normalized;
    }

    private String normalizeContactValue(String contactType, String value) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "请输入绑定信息");
        }
        String normalized = value.trim();
        if ("mobile".equals(contactType) && !normalized.matches("^1[3-9]\\d{9}$")) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "请输入有效手机号");
        }
        if ("email".equals(contactType) && !normalized.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "请输入有效邮箱地址");
        }
        return normalized;
    }
    private record CachedReadModelVersion(long version, long expiresAtEpochMillis) {
    }
}
