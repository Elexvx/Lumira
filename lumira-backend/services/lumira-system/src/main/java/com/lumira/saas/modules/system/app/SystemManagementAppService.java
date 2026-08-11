package com.lumira.saas.modules.system.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.domain.event.DomainEventPublisher;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.FieldCryptoService;
import com.lumira.saas.infrastructure.security.service.AuthSessionStore;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
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
import com.lumira.saas.modules.system.config.app.SystemConfigVersioningService;
import com.lumira.saas.modules.system.config.repository.SystemConfigurationManagementRepository;
import com.lumira.saas.modules.system.dict.repository.SystemDictionaryManagementRepository;
import com.lumira.saas.modules.system.infrastructure.SystemManagementPersistenceAdapters;
import com.lumira.saas.modules.system.infrastructure.SystemManagementPersistenceDependencies;
import com.lumira.saas.modules.system.menu.repository.SystemMenuManagementRepository;
import com.lumira.saas.modules.system.profile.repository.SystemCurrentUserProfileRepository;
import com.lumira.saas.modules.system.audit.repository.SystemAuditQueryRepository;
import com.lumira.saas.modules.system.audit.vo.AuditLogVO;
import com.lumira.saas.modules.system.profile.vo.ProfileFieldSettingVO;
import com.lumira.saas.modules.plugin.vo.PluginVO;
import com.lumira.saas.modules.system.plugin.SystemPluginViewService;
import com.lumira.saas.modules.system.role.app.SystemRoleManagementAppService;
import com.lumira.saas.modules.system.user.app.SystemUserManagementAppService;
import com.lumira.saas.modules.system.user.vo.UserDetailVO;
import com.lumira.saas.modules.system.user.support.UserAvatarDefaults;
import com.lumira.saas.modules.system.verification.SystemVerificationAppService;
import com.lumira.saas.modules.system.vo.SystemVO;
import com.lumira.saas.modules.user.domain.UserDomainService;
import com.lumira.saas.modules.user.entity.SysUserEntity;
import com.lumira.saas.infrastructure.security.service.PasswordPolicyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
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
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnLumiraControlPlaneEnabled
public class SystemManagementAppService {

    private static final String STATUS_ENABLED = "ENABLED";
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
            ".private-key",
            ".credential",
            ".token",
            "-credential",
            "-token"
    );
    private static final List<SystemVO.ShortcutVO> DASHBOARD_SHORTCUTS = List.of(
            shortcut("Menu settings", "Manage menus and route permissions", "/settings/menus", "system:menu:view"),
            shortcut("Verification settings", "Configure 2FA, login verification, and trust checks", "/settings/verification", "system:verification:view"),
            shortcut("Online users", "View current online users and session activity", "/user-center/online-users", "system:online-user:view"),
            shortcut("Personalization", "Configure branding, logo, and personal appearance", "/settings/personalization", "system:config:view"),
            shortcut("Security center", "Review tokens, passwords, and security settings", "/settings/security", "system:config:view"),
            shortcut("Audit logs", "Inspect audit records and operation history", "/settings/audit", "audit:view"),
            shortcut("Notifications", "Configure notification channels and delivery rules", "/settings/notifications", "system:notification:view"),
            shortcut("Plugin settings", "Manage installed plugins and runtime access", "/settings/plugins", "plugin:management:view")
    );
    private final UserDomainService userDomainService;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;
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
    private final SystemCurrentUserProfileRepository currentUserProfileRepository;
    private final SystemMenuManagementRepository menuRepository;
    private final SystemDictionaryManagementRepository dictionaryRepository;
    private final SystemConfigurationManagementRepository configurationRepository;
    private final SystemAuditQueryRepository auditQueryRepository;
    private final ReadModelVersionService readModelVersionService;
    private boolean enforceTrustedUserResolution;
    private SystemConfigVersioningService configVersioningService;
    private final Cache<String, Integer> menuCountCache;
    private final Cache<String, List<SystemVO.PermissionVO>> permissionCatalogCache;
    private final Cache<Long, List<SystemVO.MenuVO>> menuTreeCache;
    private final Cache<String, List<SystemVO.PermissionTreeVO>> permissionTreeCache;
    private final Cache<String, CachedReadModelVersion> readModelVersionCache;
    private final SystemPermissionTreeAssembler permissionTreeAssembler = new SystemPermissionTreeAssembler();

    @Autowired
    public void setConfigVersioningService(SystemConfigVersioningService configVersioningService) {
        this.configVersioningService = configVersioningService;
    }

    @Autowired
    public SystemManagementAppService(
            SystemManagementPersistenceDependencies persistence,
            UserDomainService userDomainService,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService,
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
        this.userDomainService = userDomainService;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
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
        this.currentUserProfileRepository = persistence.currentUserProfiles();
        this.menuRepository = persistence.menus();
        this.dictionaryRepository = persistence.dictionaries();
        this.configurationRepository = persistence.configurations();
        this.auditQueryRepository = persistence.auditQueries();
        this.readModelVersionService = persistence.readModelVersions();
        this.enforceTrustedUserResolution = true;
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

    /**
     * Source-compatible test constructor. Production wiring always uses the
     * typed persistence bundle above; isolated tests can still provide their
     * recording MyBatis operations through the infrastructure adapter.
     */
    public SystemManagementAppService(
            Object persistence,
            UserDomainService userDomainService,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService,
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
        this(
                SystemManagementPersistenceAdapters.from(persistence),
                userDomainService,
                permissionSnapshotService,
                systemInternalApi,
                sessionAuthenticationService,
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
                fieldCryptoService
        );
    }

    public SystemManagementAppService(
            Object persistence,
            UserDomainService userDomainService,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService,
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
        this(
                SystemManagementPersistenceAdapters.from(persistence),
                userDomainService,
                permissionSnapshotService,
                null,
                sessionAuthenticationService,
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
                fieldCryptoService
        );
        this.enforceTrustedUserResolution = false;
    }

    public SystemManagementAppService(
            Object persistence,
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
        this(
                SystemManagementPersistenceAdapters.from(persistence),
                userDomainService,
                permissionSnapshotService,
                null,
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
                fieldCryptoService
        );
        this.enforceTrustedUserResolution = false;
    }

    public SystemManagementAppService(
            Object persistence,
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
                SystemManagementPersistenceAdapters.from(persistence),
                userDomainService,
                permissionSnapshotService,
                null,
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
                defaultRoleManagementAppService(persistence, permissionSnapshotService, operationAuditService),
                null
        );
        this.enforceTrustedUserResolution = false;
    }

    public SystemManagementAppService(
            Object persistence,
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
                SystemManagementPersistenceAdapters.from(persistence),
                userDomainService,
                permissionSnapshotService,
                null,
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
        this.enforceTrustedUserResolution = false;
    }

    public SystemManagementAppService(
            Object persistence,
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
                SystemManagementPersistenceAdapters.from(persistence),
                userDomainService,
                permissionSnapshotService,
                null,
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
                defaultUserManagementAppService(persistence, userDomainService, iamUserService, permissionSnapshotService, onlineSessionManagementAppService, operationAuditService, passwordEncoder, passwordPolicyService),
                defaultRoleManagementAppService(persistence, permissionSnapshotService, operationAuditService),
                null
        );
        this.enforceTrustedUserResolution = false;
    }

    private static SystemUserManagementAppService defaultUserManagementAppService(
            Object persistence,
            UserDomainService userDomainService,
            IamUserService iamUserService,
            PermissionSnapshotService permissionSnapshotService,
            OnlineSessionManagementAppService onlineSessionManagementAppService,
            OperationAuditService operationAuditService,
            PasswordEncoder passwordEncoder,
            PasswordPolicyService passwordPolicyService
    ) {
        return new SystemUserManagementAppService(
                persistence,
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
            Object persistence,
            PermissionSnapshotService permissionSnapshotService,
            OperationAuditService operationAuditService
    ) {
        return new SystemRoleManagementAppService(
                persistence,
                permissionSnapshotService,
                operationAuditService,
                NOOP_DOMAIN_EVENT_PUBLISHER
        );
    }

    public SystemVO.DashboardSummaryVO dashboardSummary(CurrentUser currentUser) {
        requireAuthenticatedUser(currentUser);
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
        requireAuthenticatedUser(currentUser);
        PermissionSnapshotService.PermissionSnapshot snapshot = resolvePermissionSnapshot(currentUser);
        CompletableFuture<CurrentUserVO> currentUserFuture = CompletableFuture.supplyAsync(() -> buildCurrentUser(currentUser, snapshot), BLOCKING_IO_EXECUTOR);
        CompletableFuture<List<String>> roleNamesFuture = CompletableFuture.supplyAsync(
                () -> listCurrentRoleNames(currentUser.getUserId(), currentUser.getUserUuid()),
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
        SysUserEntity user = requireAuthenticatedUserEntity(currentUser);
        Long currentUserId = user.getId();
        requireRequest(request, "Profile update request is required");
        String requestedMobile = normalizeNullableText(request.getMobile());
        String requestedEmail = normalizeNullableText(request.getEmail());
        if (request.getMobile() != null && contactValueChanged(user.getMobile(), requestedMobile)) {
            if (!systemVerificationAppService.isContactBindAvailable("mobile")) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "Mobile binding is not enabled on this platform");
            }
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Mobile number changed, please complete mobile verification first");
        }
        if (request.getEmail() != null && contactValueChanged(user.getEmail(), requestedEmail)) {
            if (!systemVerificationAppService.isContactBindAvailable("email")) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "Email binding is not enabled on this platform");
            }
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Email changed, please complete email verification first");
        }
        String persistedMobile = normalizeNullableText(user.getMobile());
        String persistedEmail = normalizeNullableText(user.getEmail());
        int updated = currentUserProfileRepository.updateBasicProfile(new SystemCurrentUserProfileRepository.BasicProfile(
                user.getId(),
                currentUser.getUserUuid(),
                normalizeOptionalProfileUrl(request.getAvatarUrl(), "Avatar URL"),
                normalizeNullableText(request.getNickname()),
                normalizeNullableText(request.getRealName()),
                persistedMobile,
                persistedEmail,
                normalizeNullableText(request.getBirthMonth()),
                normalizeNullableText(request.getGender()),
                normalizeNullableText(request.getRegion()),
                normalizeNullableText(request.getAvailableTime()),
                normalizeNullableText(request.getIdCardNumber()),
                currentUserProfileActor(currentUser),
                LocalDateTime.now()
        ));
        requireSystemWrite(updated, "User profile changed, please retry");
        userDomainService.findById(user.getId()).ifPresent(iamUserService::updateProfile);
        if (request.getExtraProfileValues() != null) {
            updateCurrentUserExtraProfileValues(currentUser, user.getId(), currentUser.getUserUuid(), request.getExtraProfileValues());
        }
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "profile", "update", "UPDATE", "SUCCESS", "Update personal profile");
        return buildCurrentUser(currentUser);
    }

    @Transactional
    public CurrentUserVO updateCurrentUserAvatar(CurrentUser currentUser, String avatarUrl) {
        if (!StringUtils.hasText(avatarUrl)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Avatar URL cannot be blank");
        }
        SysUserEntity user = requireAuthenticatedUserEntity(currentUser);
        String normalizedAvatarUrl = normalizeOptionalProfileUrl(avatarUrl, "Avatar URL");
        int updated = currentUserProfileRepository.updateAvatar(
                user.getId(), currentUser.getUserUuid(), normalizedAvatarUrl, currentUserProfileActor(currentUser), LocalDateTime.now()
        );
        requireSystemWrite(updated, "User profile changed, please retry");
        userDomainService.findById(user.getId()).ifPresent(iamUserService::updateProfile);
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "profile", "avatar", "UPDATE", "SUCCESS", "Update profile avatar");
        return buildCurrentUser(currentUser);
    }

    @Transactional
    public CurrentUserVO updateCurrentUserEmail(CurrentUser currentUser, ProfileDTO.EmailUpdateRequest request) {
        requireRequest(request, "Email update request is required");
        ProfileDTO.ContactBindRequest contactBindRequest = new ProfileDTO.ContactBindRequest();
        contactBindRequest.setContactType("email");
        contactBindRequest.setValue(request.getEmail());
        contactBindRequest.setChallengeId(request.getChallengeId());
        contactBindRequest.setVerificationCode(request.getVerificationCode());
        String email = normalizeContactValue("email", request.getEmail());
        if (!systemVerificationAppService.isContactBindAvailable("email")) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Email binding is not enabled on this platform");
        }
        if (!StringUtils.hasText(request.getChallengeId()) || !StringUtils.hasText(request.getVerificationCode())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Verification challenge and code are required");
        }
        contactBindRequest.setValue(email);
        return updateCurrentUserContactBinding(currentUser, contactBindRequest);
    }

    public SystemVO.VerificationChallengeVO startCurrentUserContactBindChallenge(CurrentUser currentUser, ProfileDTO.ContactBindChallengeRequest request) {
        SysUserEntity user = requireAuthenticatedUserEntity(currentUser);
        requireRequest(request, "Contact bind challenge request is required");
        String contactType = normalizeContactType(request.getContactType());
        String value = normalizeContactValue(contactType, request.getValue());
        requireSensitiveContactBindPasswordWhenNoCurrentFactor(currentUser, user, request);
        return systemVerificationAppService.startContactBindChallenge(
                user.getId(),
                currentUser.getUserUuid(),
                contactType,
                value,
                request.getCurrentFactorCode(),
                request.getCurrentChallengeId(),
                request.getCurrentVerificationCode()
        );
    }

    private void requireSensitiveContactBindPasswordWhenNoCurrentFactor(
            CurrentUser currentUser,
            SysUserEntity user,
            ProfileDTO.ContactBindChallengeRequest request
    ) {
        if (StringUtils.hasText(request.getCurrentFactorCode())
                || StringUtils.hasText(request.getCurrentChallengeId())
                || StringUtils.hasText(request.getCurrentVerificationCode())) {
            return;
        }
        if (hasAvailableSensitiveContactBindFactor(user, currentUser.getUserUuid())) {
            return;
        }
        if (canTrustWechatFirstContactBind(currentUser, user)) {
            return;
        }
        String currentPassword = normalizeNullableText(request.getCurrentPassword());
        if (!StringUtils.hasText(currentPassword)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Please enter your current password before changing this sign-in method");
        }
        String trustedUserUuid = currentUser.getUserUuid();
        String currentPasswordHash = iamUserService.findActiveCredential(user.getId(), trustedUserUuid, "PASSWORD")
                .map(IamUserAccount.CredentialView::getCredentialSecret)
                .orElse(null);
        if (!StringUtils.hasText(currentPasswordHash)) {
            currentPasswordHash = user.getPasswordHash();
        }
        if (!StringUtils.hasText(currentPasswordHash) || !passwordEncoder.matches(currentPassword, currentPasswordHash)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Current password is incorrect");
        }
    }

    private boolean hasAvailableSensitiveContactBindFactor(SysUserEntity user, String trustedUserUuid) {
        List<SystemVO.VerificationProviderVO> providers = systemVerificationAppService.listProviders(user.getId(), trustedUserUuid);
        if (providers != null && providers.stream().anyMatch(provider ->
                "totp".equalsIgnoreCase(provider.getFactorCode())
                        && Boolean.TRUE.equals(provider.getBound())
                        && Boolean.TRUE.equals(provider.getEnabled()))) {
            return true;
        }
        if (systemVerificationAppService.isContactBindAvailable("mobile") && StringUtils.hasText(user.getMobile())) {
            return true;
        }
        return systemVerificationAppService.isContactBindAvailable("email") && StringUtils.hasText(user.getEmail());
    }

    private boolean canTrustWechatFirstContactBind(CurrentUser currentUser, SysUserEntity user) {
        if (currentUser == null
                || user == null
                || !"WECHAT".equalsIgnoreCase(normalizeNullableText(currentUser.getLoginType()))
                || StringUtils.hasText(user.getMobile())
                || StringUtils.hasText(user.getEmail())) {
            return false;
        }
        return currentUserProfileRepository.hasActiveWechatBinding(user.getId(), currentUser.getUserUuid());
    }

    @Transactional
    public CurrentUserVO updateCurrentUserContactBinding(CurrentUser currentUser, ProfileDTO.ContactBindRequest request) {
        SysUserEntity user = requireAuthenticatedUserEntity(currentUser);
        requireRequest(request, "Contact bind request is required");
        String contactType = normalizeContactType(request.getContactType());
        String value = normalizeContactValue(contactType, request.getValue());

        if (!systemVerificationAppService.isContactBindAvailable(contactType)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Contact binding is not enabled on this platform");
        }
        if (!StringUtils.hasText(request.getChallengeId()) || !StringUtils.hasText(request.getVerificationCode())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Verification challenge and code are required");
        }
        systemVerificationAppService.completeContactBind(
                currentUser.getUserId(),
                currentUser.getUserUuid(),
                contactType,
                request.getChallengeId(),
                request.getVerificationCode(),
                value
        );

        if (!"mobile".equals(contactType) && !"email".equals(contactType)) {
            throw new BizException(ErrorCode.NOT_FOUND, "Contact binding record does not exist");
        }
        int updated = currentUserProfileRepository.updateContact(
                user.getId(), currentUser.getUserUuid(), contactType, value, currentUserProfileActor(currentUser), LocalDateTime.now()
        );
        requireSystemWrite(updated, "User profile changed, please retry");

        userDomainService.findById(user.getId()).ifPresent(iamUserService::updateProfile);
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "profile", "bind", "UPDATE", "SUCCESS", "Update contact binding");
        return buildCurrentUser(currentUser);
    }

    @Transactional
    public CurrentUserVO updateCurrentUserLocale(CurrentUser currentUser, ProfileDTO.LocaleUpdateRequest request) {
        SysUserEntity user = requireAuthenticatedUserEntity(currentUser);
        requireRequest(request, "Locale update request is required");
        String locale = normalizeLocale(request.getLocale());
        currentUserProfileRepository.upsertLocale(new SystemCurrentUserProfileRepository.LocaleProfile(
                user.getId(), currentUser.getUserUuid(), user.getNickname(), user.getRealName(), user.getGender(), user.getBirthMonth(),
                user.getRegion(), locale
        ));
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "profile", "update-locale", "UPDATE", "SUCCESS", "Update locale settings");
        return buildCurrentUser(currentUser);
    }

    @Transactional
    public boolean updateCurrentUserPassword(CurrentUser currentUser, ProfileDTO.PasswordUpdateRequest request) {
        if (request == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Password update request is required");
        }
        SysUserEntity user = requireAuthenticatedUserEntity(currentUser);

        String currentPassword = normalizeNullableText(request.getCurrentPassword());
        String newPassword = normalizeNullableText(request.getNewPassword());
        String confirmPassword = normalizeNullableText(request.getConfirmPassword());
        boolean initialPasswordChange = Boolean.TRUE.equals(currentUser.getRequiresPasswordChange());

        if ((!initialPasswordChange && !StringUtils.hasText(currentPassword))
                || !StringUtils.hasText(newPassword)
                || !StringUtils.hasText(confirmPassword)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Please enter complete password information");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "New password and confirmation password do not match");
        }
        String trustedUserUuid = currentUser.getUserUuid();
        String currentPasswordHash = iamUserService.findActiveCredential(user.getId(), trustedUserUuid, "PASSWORD")
                .map(IamUserAccount.CredentialView::getCredentialSecret)
                .orElse(null);
        boolean fallbackToSysUserPassword = !StringUtils.hasText(currentPasswordHash);
        if (fallbackToSysUserPassword) {
            currentPasswordHash = user.getPasswordHash();
        }
        if (!StringUtils.hasText(currentPasswordHash)
                || (!initialPasswordChange && !passwordEncoder.matches(currentPassword, currentPasswordHash))) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Current password is incorrect");
        }
        if (passwordEncoder.matches(newPassword, currentPasswordHash)) {
            throw new BizException(
                    ErrorCode.PASSWORD_POLICY_VIOLATION,
                    "New password must be different from current password",
                    "新密码不能与当前密码相同"
            );
        }
        if (fallbackToSysUserPassword) {
            iamUserService.upsertPasswordCredential(user.getId(), trustedUserUuid, user.getPasswordHash());
        }

        passwordPolicyService.validatePassword(newPassword);
        String encodedPassword = passwordEncoder.encode(newPassword);
        int updated = currentUserProfileRepository.updatePasswordHash(
                user.getId(), currentUser.getUserUuid(), encodedPassword, currentUserProfileActor(currentUser), LocalDateTime.now()
        );
        if (updated <= 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "User password changed, please retry");
        }
        iamUserService.upsertPasswordCredential(user.getId(), trustedUserUuid, encodedPassword);
        authSessionStore.markPasswordChangeResolved(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getSessionId(), true);
        authSessionStore.revokeUserSessionsExcept(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getSessionId(), true);
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "profile", "password", "UPDATE", "SUCCESS", "Update login password");
        return true;
    }

    public List<ProfileFieldSettingVO> getProfileFieldSettings(CurrentUser currentUser) {
        return systemProfileSettingsAppService.getProfileFieldSettings(currentUser);
    }

    public List<ProfileFieldSettingVO> getProfileFieldSettings(CurrentUser currentUser, String pageKey) {
        return systemProfileSettingsAppService.getProfileFieldSettingsForManagement(currentUser, pageKey);
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
        requirePermission(currentUser, "system:role:view");
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
        List<SystemVO.PermissionVO> permissions = new ArrayList<>(menuRepository.findPermissions().stream()
                .filter(permission -> !isAiPermission(permission))
                .toList());
        permissions.sort(Comparator.comparing(SystemVO.PermissionVO::getPermissionGroup, Comparator.nullsLast(String::compareTo))
                .thenComparing(SystemVO.PermissionVO::getPermissionKey, Comparator.nullsLast(String::compareTo)));
        return List.copyOf(permissions);
    }

    public List<SystemVO.PermissionTreeVO> listPermissionTree(CurrentUser currentUser) {
        requirePermission(currentUser, "system:role:view");
        long menuTreeVersion = currentMenuTreeVersion();
        String permissionCatalogVersion = currentPermissionCatalogVersion();
        String cacheKey = permissionTreeCacheKey(menuTreeVersion, permissionCatalogVersion);
        pruneStalePermissionTreeCache(cacheKey);
        List<SystemVO.PermissionTreeVO> cached = permissionTreeCache.getIfPresent(cacheKey);
        if (cached != null) {
            return copyPermissionTrees(cached);
        }

        List<SystemVO.PermissionTreeVO> permissionTree = permissionTreeAssembler.build(
                loadPermissionMenusByVersion(currentUser, menuTreeVersion),
                listPermissionsByVersion(permissionCatalogVersion)
        );
        List<SystemVO.PermissionTreeVO> snapshot = copyPermissionTrees(permissionTree);
        permissionTreeCache.put(cacheKey, snapshot);
        return copyPermissionTrees(snapshot);
    }

    public List<SystemVO.MenuVO> listMenus(CurrentUser currentUser) {
        requirePermission(currentUser, "system:menu:view");
        return loadMenusByVersion(currentUser, currentMenuTreeVersion());
    }

    private List<SystemVO.MenuVO> listPersistedMenus(CurrentUser currentUser) {
        List<SystemVO.MenuVO> menus = menuRepository.findMenus().stream()
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

    private List<SystemVO.MenuVO> loadPermissionMenusByVersion(CurrentUser currentUser, long menuTreeVersion) {
        List<SystemVO.MenuVO> menus = new ArrayList<>(loadMenusByVersion(currentUser, menuTreeVersion));
        List<SystemMenuManagementRepository.PluginMenu> pluginMenus = menuRepository.findActivePluginMenus();
        if (pluginMenus.isEmpty()) {
            return menus;
        }

        Map<String, SystemVO.MenuVO> byMenuCode = new LinkedHashMap<>();
        indexMenusByCode(menus, byMenuCode);
        for (SystemMenuManagementRepository.PluginMenu pluginMenu : pluginMenus) {
            String menuCode = pluginMenu.menuCode();
            if (!StringUtils.hasText(menuCode) || byMenuCode.containsKey(menuCode)) {
                continue;
            }
            SystemVO.MenuVO node = new SystemVO.MenuVO();
            node.setMenuCode(menuCode);
            node.setMenuName(pluginMenu.menuName());
            node.setMenuType("MENU");
            node.setPath(pluginMenu.path());
            node.setComponent("plugin:" + pluginMenu.pluginCode());
            node.setIcon(pluginMenu.icon());
            node.setPermissionKey(pluginMenu.permissionKey());
            node.setSortNo(pluginMenu.sortNo() == null ? 0 : pluginMenu.sortNo());
            node.setStatus("ENABLED");
            node.setChildren(new ArrayList<>());

            String parentMenuCode = pluginMenu.parentMenuCode();
            SystemVO.MenuVO parent = StringUtils.hasText(parentMenuCode) ? byMenuCode.get(parentMenuCode) : null;
            if (parent == null) {
                menus.add(node);
            } else {
                List<SystemVO.MenuVO> children = parent.getChildren();
                if (children == null) {
                    children = new ArrayList<>();
                    parent.setChildren(children);
                }
                children.add(node);
            }
            byMenuCode.put(menuCode, node);
        }
        return menus;
    }

    private void indexMenusByCode(List<SystemVO.MenuVO> menus, Map<String, SystemVO.MenuVO> byMenuCode) {
        if (CollectionUtils.isEmpty(menus)) {
            return;
        }
        for (SystemVO.MenuVO menu : menus) {
            if (menu == null) {
                continue;
            }
            if (StringUtils.hasText(menu.getMenuCode())) {
                byMenuCode.put(menu.getMenuCode(), menu);
            }
            indexMenusByCode(menu.getChildren(), byMenuCode);
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
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
        Long operatorId = requirePermission(currentUser, "system:menu:update");
        String operatorUuid = currentUser.getUserUuid();
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
            SystemVO.MenuVO menu = ensureEditableMenu(item.getId());
            ensureEditableParentMenu(item.getParentId());
            int updated = menuRepository.reorder(new SystemMenuManagementRepository.MenuOrder(
                    menuVersion(menu), item.getParentId(), item.getSortNo(), menuActor(operatorId, operatorUuid), now
            ));
            requireSystemWrite(updated, "Menu changed, please retry");
        }

        operationAuditService.log(
                operatorId,
                currentUser.getUserUuid(),
                currentUser.getUsername(),
                "menu",
                "reorder",
                "UPDATE",
                "SUCCESS",
                "Reorder menus"
        );
        bumpMenuTreeReadModelVersion("system.menu.reorder");
        invalidateMenuCountCache();
        return true;
    }

    public SystemVO.MenuVO getMenu(CurrentUser currentUser, Long menuId) {
        requirePermission(currentUser, "system:menu:view");
        SystemVO.MenuVO menu = loadMenu(menuId);
        ensureEditableMenu(menu);
        return menu;
    }

    @Transactional
    public SystemVO.MenuVO createMenu(CurrentUser currentUser, SystemDTO.MenuUpsertRequest request) {
        requirePermission(currentUser, "system:menu:create");
        requireRequest(request, "Menu request is required");
        ensureEditableMenuRequest(request);
        ensureEditableParentMenu(request.getParentId());
        Long menuId = insertMenu(null, request, currentUser.getUserId(), currentUser.getUserUuid());
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "menu", "create", "CREATE", "SUCCESS", "创建菜单: " + request.getMenuName());
        bumpMenuTreeReadModelVersion("system.menu.create");
        invalidateMenuCountCache();
        return loadMenu(menuId);
    }

    @Transactional
    public SystemVO.MenuVO updateMenu(CurrentUser currentUser, Long menuId, SystemDTO.MenuUpsertRequest request) {
        requirePermission(currentUser, "system:menu:update");
        requirePositiveId(menuId, "Menu id is required");
        requireRequest(request, "Menu request is required");
        SystemVO.MenuVO existingMenu = ensureEditableMenu(menuId);
        ensureEditableParentMenu(request.getParentId());
        insertMenu(menuId, existingMenu, request, currentUser.getUserId(), currentUser.getUserUuid());
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "menu", "update", "UPDATE", "SUCCESS", "更新菜单: " + request.getMenuName());
        bumpMenuTreeReadModelVersion("system.menu.update");
        invalidateMenuCountCache();
        return loadMenu(menuId);
    }

    @Transactional
    public boolean updateMenuStatus(CurrentUser currentUser, Long menuId, String status) {
        requirePermission(currentUser, "system:menu:status");
        requirePositiveId(menuId, "Menu id is required");
        SystemVO.MenuVO menu = ensureEditableMenu(menuId);
        int updated = menuRepository.updateStatus(
                menuVersion(menu), status, menuActor(currentUser.getUserId(), currentUser.getUserUuid()), LocalDateTime.now()
        );
        requireSystemWrite(updated, "Menu changed, please retry");
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "menu", "status", "UPDATE", "SUCCESS", "更新菜单状态: " + menuId + " -> " + status);
        bumpMenuTreeReadModelVersion("system.menu.status");
        invalidateMenuCountCache();
        return true;
    }

    @Transactional
    public boolean deleteMenu(CurrentUser currentUser, Long menuId) {
        requirePermission(currentUser, "system:menu:delete");
        requirePositiveId(menuId, "Menu id is required");
        SystemVO.MenuVO editableMenu = ensureEditableMenu(menuId);
        boolean hasChildMenu = menuRepository.hasActiveChild(menuId);
        if (hasChildMenu) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "The menu has child menus and cannot be deleted");
        }
        SystemVO.MenuVO menu = editableMenu;
        if (StringUtils.hasText(menu.getPermissionKey())) {
            boolean permissionReferenced = menuRepository.hasActivePermissionReference(menu.getPermissionKey());
            if (permissionReferenced) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "The menu permission is referenced by roles and cannot be deleted");
            }
        }
        int updated = menuRepository.softDelete(
                menuVersion(editableMenu), menuActor(currentUser.getUserId(), currentUser.getUserUuid()), LocalDateTime.now()
        );
        requireSystemWrite(updated, "Menu changed, please retry");
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "menu", "delete", "DELETE", "SUCCESS", "删除菜单: " + menu.getMenuName());
        bumpMenuTreeReadModelVersion("system.menu.delete");
        permissionSnapshotService.invalidatePermissions();
        invalidateMenuCountCache();
        return true;
    }

    public PageResponse<SystemVO.DictTypeVO> listDictTypes(CurrentUser currentUser, String dictCode, String dictName, String status, long pageNo, long pageSize) {
        requirePermission(currentUser, "system:dict:view");
        return dictionaryRepository.findTypes(new SystemDictionaryManagementRepository.TypeSearch(dictCode, dictName, status, pageNo, pageSize));
    }

    public SystemVO.DictTypeVO getDictType(CurrentUser currentUser, Long id) {
        requirePermission(currentUser, "system:dict:view");
        return loadDictType(id);
    }

    @Transactional
    public SystemVO.DictTypeVO createDictType(CurrentUser currentUser, SystemDTO.DictTypeUpsertRequest request) {
        requirePermission(currentUser, "system:dict:create");
        requireRequest(request, "Dict type request is required");
        Long id = upsertDictType(null, request, currentUser.getUserId(), currentUser.getUserUuid());
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "dict", "create", "CREATE", "SUCCESS", "创建字典类型: " + request.getDictCode());
        return loadDictType(id);
    }

    @Transactional
    public SystemVO.DictTypeVO updateDictType(CurrentUser currentUser, Long id, SystemDTO.DictTypeUpsertRequest request) {
        requirePermission(currentUser, "system:dict:update");
        requirePositiveId(id, "Dict type id is required");
        requireRequest(request, "Dict type request is required");
        SystemVO.DictTypeVO existingType = loadDictType(id);
        upsertDictType(id, existingType, request, currentUser.getUserId(), currentUser.getUserUuid());
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "dict", "update", "UPDATE", "SUCCESS", "更新字典类型: " + request.getDictCode());
        return loadDictType(id);
    }

    @Transactional
    public boolean deleteDictType(CurrentUser currentUser, Long id) {
        requirePermission(currentUser, "system:dict:delete");
        requirePositiveId(id, "Dict type id is required");
        SystemVO.DictTypeVO type = loadDictType(id);
        if (isSystemDictType(type)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Built-in dictionary types cannot be deleted");
        }
        SystemDictionaryManagementRepository.Actor actor = dictionaryActor(currentUser);
        LocalDateTime now = LocalDateTime.now();
        int typeDeleted = dictionaryRepository.softDeleteType(dictionaryTypeVersion(type), actor, now);
        requireSystemWrite(typeDeleted, "Dict type changed, please retry");
        dictionaryRepository.retireItemsForType(id, actor, now);
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "dict", "delete", "DELETE", "SUCCESS", "删除字典类型: " + type.getDictCode());
        return true;
    }

    public List<SystemVO.DictItemVO> listDictItems(CurrentUser currentUser, Long dictTypeId) {
        requirePermission(currentUser, "system:dict:view");
        loadDictType(dictTypeId);
        return dictionaryRepository.findActiveItems(dictTypeId);
    }

    public List<SystemVO.DictItemVO> listEnabledDictItemsByCode(CurrentUser currentUser, String dictCode) {
        requirePermission(currentUser, "system:dict:view");
        if (!StringUtils.hasText(dictCode)) {
            return List.of();
        }
        String normalizedCode = dictCode.trim();
        return dictionaryRepository.findEnabledItemsByCode(normalizedCode);
    }

    public SystemVO.DictItemVO getDictItem(CurrentUser currentUser, Long dictTypeId, Long itemId) {
        requirePermission(currentUser, "system:dict:view");
        loadDictType(dictTypeId);
        return loadDictItem(dictTypeId, itemId);
    }

    @Transactional
    public SystemVO.DictItemVO createDictItem(CurrentUser currentUser, Long dictTypeId, SystemDTO.DictItemUpsertRequest request) {
        requirePermission(currentUser, "system:dict:create");
        requirePositiveId(dictTypeId, "Dict type id is required");
        requireRequest(request, "Dict item request is required");
        loadDictType(dictTypeId);
        Long id = upsertDictItem(null, dictTypeId, request, currentUser.getUserId(), currentUser.getUserUuid());
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "dict", "item-create", "CREATE", "SUCCESS", "创建字典项: " + request.getItemLabel());
        return loadDictItem(dictTypeId, id);
    }

    @Transactional
    public SystemVO.DictItemVO updateDictItem(CurrentUser currentUser, Long dictTypeId, Long itemId, SystemDTO.DictItemUpsertRequest request) {
        requirePermission(currentUser, "system:dict:update");
        requirePositiveId(dictTypeId, "Dict type id is required");
        requirePositiveId(itemId, "Dict item id is required");
        requireRequest(request, "Dict item request is required");
        loadDictType(dictTypeId);
        SystemVO.DictItemVO existingItem = loadDictItem(dictTypeId, itemId);
        upsertDictItem(itemId, dictTypeId, existingItem, request, currentUser.getUserId(), currentUser.getUserUuid());
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "dict", "item-update", "UPDATE", "SUCCESS", "更新字典项: " + request.getItemLabel());
        return loadDictItem(dictTypeId, itemId);
    }

    @Transactional
    public boolean deleteDictItem(CurrentUser currentUser, Long dictTypeId, Long itemId) {
        requirePermission(currentUser, "system:dict:delete");
        requirePositiveId(dictTypeId, "Dict type id is required");
        requirePositiveId(itemId, "Dict item id is required");
        loadDictType(dictTypeId);
        SystemVO.DictItemVO item = loadDictItem(dictTypeId, itemId);
        int deleted = dictionaryRepository.softDeleteItem(
                dictionaryItemVersion(item), dictionaryActor(currentUser), LocalDateTime.now()
        );
        requireSystemWrite(deleted, "Dict item changed, please retry");
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "dict", "item-delete", "DELETE", "SUCCESS", "删除字典项: " + item.getItemLabel());
        return true;
    }

    public PageResponse<SystemVO.ConfigVO> listConfigs(CurrentUser currentUser, String configKey, String configName, long pageNo, long pageSize) {
        requirePermission(currentUser, "system:config:view");
        PageResponse<SystemVO.ConfigVO> page = configurationRepository.findConfigs(
                new SystemConfigurationManagementRepository.ConfigSearch(configKey, configName, pageNo, pageSize)
        );
        maskSensitiveConfigValues(page.getRecords());
        return page;
    }

    public SystemVO.ConfigVO getConfig(CurrentUser currentUser, Long id) {
        requirePermission(currentUser, "system:config:view");
        SystemVO.ConfigVO config = loadConfig(id);
        maskSensitiveConfigValue(config);
        return config;
    }

    public SystemVO.ConfigVO getConfigForUpdate(CurrentUser currentUser, Long id) {
        requirePermission(currentUser, "system:config:update");
        SystemVO.ConfigVO config = loadConfig(id);
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
        requirePermission(currentUser, "system:config:update");
        requirePositiveId(id, "Config id is required");
        requireRequest(request, "Config request is required");
        SystemVO.ConfigVO currentConfig = loadConfig(id);
        SystemConfigVersioningService governanceService = governanceServiceForWrite();
        if (governanceService != null) {
            governanceService.validateGovernedKey(request.getConfigKey());
            if (!governanceGroup(currentConfig.getConfigKey()).equals(governanceGroup(request.getConfigKey()))) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "Config key cannot move between governance groups");
            }
        }
        SystemConfigVersioningService.GovernanceSession configVersion = governanceService == null ? null : governanceService.begin(
                new SystemConfigVersioningService.ChangeRequest(
                        governanceGroup(request.getConfigKey()),
                        SystemConfigVersioningService.DOMAIN_PLATFORM,
                        request.getExpectedConfigVersion(),
                        request.getChangeReason(),
                        currentUser
                ),
                List.of(currentConfig.getConfigKey(), request.getConfigKey())
        );
        int updated = configurationRepository.updateEditablePlatformConfig(new SystemConfigurationManagementRepository.ConfigWrite(
                configurationVersion(currentConfig),
                request.getConfigKey(),
                request.getConfigName(),
                resolveStoredConfigValue(id, currentConfig.getConfigKey(), request.getConfigKey(), request.getConfigValue()),
                request.getRemark(),
                configurationActor(currentUser),
                LocalDateTime.now()
        ));
        if (updated <= 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Config changed, please retry");
        }
        if (governanceService != null) {
            governanceService.finish(configVersion);
        }
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "config", "update", "UPDATE", "SUCCESS", "更新配置: " + request.getConfigKey());
        SystemVO.ConfigVO config = loadConfig(id);
        maskSensitiveConfigValue(config);
        return config;
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
        requirePermission(currentUser, "system:config:update");
        requireRequest(request, "Config request is required");
        SystemConfigVersioningService governanceService = governanceServiceForWrite();
        if (governanceService != null) {
            governanceService.validateGovernedKey(request.getConfigKey());
        }
        SystemConfigVersioningService.GovernanceSession configVersion = governanceService == null ? null : governanceService.begin(
                new SystemConfigVersioningService.ChangeRequest(
                        governanceGroup(request.getConfigKey()),
                        SystemConfigVersioningService.DOMAIN_PLATFORM,
                        request.getExpectedConfigVersion(),
                        request.getChangeReason(),
                        currentUser
                ),
                List.of(request.getConfigKey())
        );
        SystemConfigurationManagementRepository.ConfigWriteResult created = configurationRepository.createPlatformConfig(
                new SystemConfigurationManagementRepository.ConfigCreate(
                        request.getConfigKey(), request.getConfigName(), encryptConfigValue(request.getConfigKey(), request.getConfigValue()),
                        request.getRemark(), configurationActor(currentUser)
                )
        );
        requireSystemWrite(created.writeCount(), "Config changed, please retry");
        if (governanceService != null) {
            governanceService.finish(configVersion);
        }
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "config", "create", "CREATE", "SUCCESS", "创建配置: " + request.getConfigKey());
        SystemVO.ConfigVO config = configurationRepository.findLatestActiveConfigByKey(request.getConfigKey());
        maskSensitiveConfigValue(config);
        return config;
    }

    private String resolveStoredConfigValue(Long id, String currentConfigKey, String requestedConfigKey, String requestedValue) {
        if (isSensitiveConfigKey(currentConfigKey)
                && currentConfigKey.equals(requestedConfigKey)
                && MASKED_CONFIG_VALUE.equals(requestedValue)) {
            return configurationRepository.findEditablePlatformValue(id, currentConfigKey);
        }
        return encryptConfigValue(requestedConfigKey, requestedValue);
    }

    private String encryptConfigValue(String configKey, String configValue) {
        if (!isSensitiveConfigKey(configKey) || fieldCryptoService == null) {
            return configValue;
        }
        return fieldCryptoService.encrypt(configValue);
    }

    private String governanceGroup(String configKey) {
        String key = configKey == null ? "" : configKey.trim().toLowerCase(Locale.ROOT);
        if (key.startsWith("branding.")) return "BRANDING";
        if (key.startsWith("agreement.")) return "AGREEMENT";
        if (key.startsWith("smtp.")) return "SMTP";
        if (key.startsWith("notification.wechat-official.")) return "WECHAT_OFFICIAL";
        if (key.startsWith("watermark.")) return "WATERMARK";
        if (key.startsWith("floating-window.")) return "FLOATING_WINDOW";
        if (key.startsWith("profile.")) return "PROFILE";
        if (key.startsWith("verification.")) return "VERIFICATION";
        if (key.startsWith("security.")) return "SECURITY";
        if (key.startsWith("certificate.")) return "CERTIFICATE";
        return "SYSTEM_CONFIG";
    }

    private SystemConfigVersioningService governanceServiceForWrite() {
        if (configVersioningService == null && enforceTrustedUserResolution) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Configuration governance is unavailable; configuration was not changed");
        }
        return configVersioningService;
    }

    public SystemVO.SecuritySettingsVO getSecuritySettings() {
        return toSecuritySettingsVO(securitySettingsService.loadSettings());
    }

    public SystemVO.SecuritySettingsVO getSecuritySettings(CurrentUser currentUser) {
        requirePermission(currentUser, "system:config:view");
        return getSecuritySettings();
    }

    @Transactional
    public SystemVO.SecuritySettingsVO updateSecuritySettings(CurrentUser currentUser, SystemDTO.SecuritySettingsRequest request) {
        Long operatorId = requirePermission(currentUser, "system:config:update");
        requireRequest(request, "Security settings request is required");
        SecuritySettingsService.SecuritySettingsSnapshot updated = securitySettingsService.updateSettings(
                toSnapshot(securitySettingsService.loadSettings(), request),
                currentUser,
                request.getExpectedConfigVersion(),
                request.getChangeReason()
        );
        if (!updated.isAllowMultiDeviceLogin()) {
            onlineSessionManagementAppService.retainLatestSessionForEachUser();
        }
        operationAuditService.log(
                operatorId,
                currentUser.getUserUuid(),
                currentUser.getUsername(),
                "security",
                "update",
                "UPDATE",
                "SUCCESS",
                "Update security settings"
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

    public SystemVO.RuntimeAppearanceSettingsVO getPublicRuntimeAppearanceSettings() {
        SystemVO.RuntimeAppearanceSettingsVO settings = new SystemVO.RuntimeAppearanceSettingsVO();
        settings.setBrandingSettings(getPublicBrandingSettings());
        settings.setWatermarkSettings(getPublicWatermarkSettings());
        settings.setFloatingWindowSettings(getPublicFloatingWindowSettings());
        return settings;
    }

    public SystemVO.BrandingSettingsVO getPublicBrandingSettings() {
        return systemPlatformSettingsAppService.getPublicBrandingSettings();
    }

    public SystemVO.SecuritySettingsVO getPublicSecuritySettings() {
        return toPublicSecuritySettingsVO(securitySettingsService.loadSettingsFresh());
    }

    public SystemVO.AgreementSettingsVO getAgreementSettings() {
        return systemPlatformSettingsAppService.getAgreementSettings();
    }

    public SystemVO.AgreementSettingsVO getAgreementSettings(CurrentUser currentUser) {
        requirePermission(currentUser, "system:config:view");
        return getAgreementSettings();
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

    public SystemVO.WatermarkSettingsVO getPublicWatermarkSettings() {
        return systemPlatformSettingsAppService.getPublicWatermarkSettings();
    }

    @Transactional
    public SystemVO.WatermarkSettingsVO updateWatermarkSettings(CurrentUser currentUser, SystemDTO.WatermarkSettingsRequest request) {
        return systemPlatformSettingsAppService.updateWatermarkSettings(currentUser, request);
    }

    public SystemVO.FloatingWindowSettingsVO getFloatingWindowSettings(CurrentUser currentUser) {
        return systemPlatformSettingsAppService.getFloatingWindowSettings(currentUser);
    }

    public SystemVO.FloatingWindowSettingsVO getPublicFloatingWindowSettings() {
        return systemPlatformSettingsAppService.getPublicFloatingWindowSettings();
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
        requireAnyPermission(currentUser, "audit:view", "audit:login:view");
        return auditQueryRepository.findLoginLogs(new SystemAuditQueryRepository.LoginSearch(
                username,
                loginType,
                StringUtils.hasText(startTime) ? parseDateTime(startTime) : null,
                StringUtils.hasText(endTime) ? parseDateTime(endTime) : null,
                pageNo,
                pageSize
        ));
    }

    private List<AuditLogVO> listCurrentUserSuccessfulLoginLogs(CurrentUser currentUser, long pageSize) {
        return auditQueryRepository.findSuccessfulLoginLogs(currentUser.getUserId(), pageSize);
    }

    private List<AuditLogVO> listRecentOperationLogs(CurrentUser currentUser, String username, long pageSize) {
        return auditQueryRepository.findRecentOperationLogs(username, pageSize);
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
        requireAnyPermission(currentUser, "audit:view", "audit:operation:view");
        return auditQueryRepository.findOperationLogs(new SystemAuditQueryRepository.OperationSearch(
                username,
                StringUtils.hasText(startTime) ? parseDateTime(startTime) : null,
                StringUtils.hasText(endTime) ? parseDateTime(endTime) : null,
                pageNo,
                pageSize
        ));
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
        requireAnyPermission(currentUser, "audit:view", "audit:operation:view");
        return auditQueryRepository.findVerificationLogs(new SystemAuditQueryRepository.VerificationSearch(
                StringUtils.hasText(channel) ? channel.trim().toUpperCase(Locale.ROOT) : null,
                StringUtils.hasText(scene) ? scene.trim().toUpperCase(Locale.ROOT) : null,
                StringUtils.hasText(resultStatus) ? resultStatus.trim().toUpperCase(Locale.ROOT) : null,
                StringUtils.hasText(startTime) ? parseDateTime(startTime) : null,
                StringUtils.hasText(endTime) ? parseDateTime(endTime) : null,
                pageNo,
                pageSize
        ));
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
        requireAnyPermission(currentUser, "audit:view", "audit:operation:view");
        return auditQueryRepository.findAiCallLogs(new SystemAuditQueryRepository.AiCallSearch(
                employeeId,
                skillCode,
                resultStatus,
                StringUtils.hasText(startTime) ? parseDateTime(startTime) : null,
                StringUtils.hasText(endTime) ? parseDateTime(endTime) : null,
                pageNo,
                pageSize
        ));
    }

    public Integer countMenus() {
        Integer cached = menuCountCache.getIfPresent(MENU_COUNT_CACHE_KEY);
        if (cached != null) {
            return cached;
        }
        int result = (int) menuRepository.findEnabledMenus().stream()
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

    private String normalizeOptionalProfileUrl(String value, String fieldName) {
        String normalized = normalizeNullableText(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.startsWith("/") && !normalized.startsWith("//") && !normalized.contains("\\")) {
            return normalized;
        }
        try {
            URI uri = new URI(normalized);
            String scheme = uri.getScheme();
            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                return normalized;
            }
        } catch (URISyntaxException exception) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, fieldName + " is invalid");
        }
        throw new BizException(ErrorCode.VALIDATION_ERROR, fieldName + " is invalid");
    }

    private boolean canViewSensitiveUserInfo(CurrentUser currentUser) {
        return AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)
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
            throw new BizException(ErrorCode.UNPROCESSABLE_ENTITY, "时间格式不正确，请使用 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss");
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
        String owner = StringUtils.hasText(companyName) ? companyName : "Lumira";
        return "Copyright (c) " + yearLabel + " " + owner + " All Rights Reserved";
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

    private SystemVO.SecuritySettingsVO toPublicSecuritySettingsVO(SecuritySettingsService.SecuritySettingsSnapshot snapshot) {
        SystemVO.SecuritySettingsVO vo = new SystemVO.SecuritySettingsVO();
        vo.setCaptchaEnabled(snapshot.isCaptchaEnabled());
        vo.setCaptchaType(defaultIfBlank(snapshot.getCaptchaType(), "IMAGE").trim().toUpperCase());
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
                        "当前用户不存在: " + currentUser.getUserId(),
                        ErrorCode.SESSION_EXPIRED.getDefaultUserMessage()
                ));
        String userUuid = currentUser.getUserUuid();
        CompletableFuture<Map<String, String>> extraProfileValuesFuture = CompletableFuture.supplyAsync(() -> loadExtraProfileValues(user.getId(), userUuid), BLOCKING_IO_EXECUTOR);
        CompletableFuture<String> localeFuture = CompletableFuture.supplyAsync(() -> resolveLocale(user.getId(), userUuid), BLOCKING_IO_EXECUTOR);
        CompletableFuture<List<CurrentUserVO.RoleOptionVO>> availableRolesFuture = CompletableFuture.supplyAsync(
                () -> listAvailableRoles(currentUser.getUserId(), userUuid),
                BLOCKING_IO_EXECUTOR
        );
        String avatarUrl = user.getAvatarUrl();
        if (!StringUtils.hasText(avatarUrl)) {
            String generatedAvatarUrl = UserAvatarDefaults.generatedAvatarUrl(user.getUuid());
            if (generatedAvatarUrl != null) {
                int initialized = currentUserProfileRepository.initializeAvatarIfAbsent(
                        user.getId(),
                        user.getUuid(),
                        generatedAvatarUrl,
                        currentUserProfileActor(currentUser),
                        LocalDateTime.now()
                );
                if (initialized > 0) {
                    avatarUrl = generatedAvatarUrl;
                    user.setAvatarUrl(avatarUrl);
                } else {
                    // A concurrent upload may have won the conditional update.
                    avatarUrl = userDomainService.findById(user.getId())
                            .map(SysUserEntity::getAvatarUrl)
                            .orElse(generatedAvatarUrl);
                    user.setAvatarUrl(avatarUrl);
                }
            }
        }
        CurrentUserVO response = new CurrentUserVO();
        response.setUserId(user.getId());
        response.setUserUuid(user.getUuid());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setRealName(user.getRealName());
        response.setAvatarUrl(avatarUrl);
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

    private void updateCurrentUserExtraProfileValues(CurrentUser currentUser, Long userId, String userUuid, Map<String, String> requestedValues) {
        Map<String, String> sanitizedValues = sanitizeExtraProfileValues(currentUser, requestedValues);
        try {
            String extraJson = OBJECT_MAPPER.writeValueAsString(Map.of(EXTRA_PROFILE_VALUES_KEY, sanitizedValues));
            currentUserProfileRepository.mergeExtraProfileJson(userId, userUuid, extraJson);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "Failed to serialize extra profile values");
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

    private Map<String, String> loadExtraProfileValues(Long userId, String userUuid) {
        String extraJson = currentUserProfileRepository.findExtraProfileJson(userId, userUuid);
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

    private Long requireAuthenticatedUser(CurrentUser currentUser) {
        refreshTrustedCurrentUser(currentUser);
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "User login is required");
        }
        return currentUser.getUserId();
    }

    private SysUserEntity requireAuthenticatedUserEntity(CurrentUser currentUser) {
        Long userId = requireAuthenticatedUser(currentUser);
        SysUserEntity user = userDomainService.findById(userId)
                .orElseThrow(() -> new BizException(ErrorCode.ACCOUNT_NOT_FOUND, "User account does not exist"));
        if (!StringUtils.hasText(user.getUuid()) || !user.getUuid().trim().equals(currentUser.getUserUuid().trim())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity mismatch");
        }
        return user;
    }

    private Long requirePermission(CurrentUser currentUser, String permission) {
        Long userId = requireAuthenticatedUser(currentUser);
        if (!hasPermission(currentUser, permission)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Missing permission: " + permission);
        }
        return userId;
    }

    private Long requireAnyPermission(CurrentUser currentUser, String messagePermission, String... permissions) {
        Long userId = requireAuthenticatedUser(currentUser);
        if (hasPermission(currentUser, messagePermission)) {
            return userId;
        }
        for (String permission : permissions) {
            if (hasPermission(currentUser, permission)) {
                return userId;
            }
        }
        throw new BizException(ErrorCode.FORBIDDEN, "Missing permission: " + messagePermission);
    }

    private boolean hasPermission(CurrentUser currentUser, String permission) {
        Set<String> permissions = currentUser == null ? null : currentUser.getPermissions();
        return permissions != null && (permissions.contains("*") || permissions.contains(permission));
    }

    private void requireRequest(Object request, String message) {
        if (request == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, message);
        }
    }

    private Long requirePositiveId(Long id, String message) {
        if (id == null || id <= 0) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, message);
        }
        return id;
    }

    private void requireSystemWrite(int updated, String message) {
        if (updated <= 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, message);
        }
    }

    private SystemCurrentUserProfileRepository.Actor currentUserProfileActor(CurrentUser currentUser) {
        return new SystemCurrentUserProfileRepository.Actor(currentUser.getUserId(), currentUser.getUserUuid());
    }

    private SystemMenuManagementRepository.Actor menuActor(Long userId, String userUuid) {
        return new SystemMenuManagementRepository.Actor(userId, userUuid);
    }

    private SystemMenuManagementRepository.MenuVersion menuVersion(SystemVO.MenuVO menu) {
        return new SystemMenuManagementRepository.MenuVersion(menu.getId(), menu.getMenuCode(), menu.getMenuType());
    }

    private SystemDictionaryManagementRepository.Actor dictionaryActor(CurrentUser currentUser) {
        return dictionaryActor(currentUser.getUserId(), currentUser.getUserUuid());
    }

    private SystemDictionaryManagementRepository.Actor dictionaryActor(Long userId, String userUuid) {
        return new SystemDictionaryManagementRepository.Actor(userId, userUuid);
    }

    private SystemDictionaryManagementRepository.TypeVersion dictionaryTypeVersion(SystemVO.DictTypeVO type) {
        return new SystemDictionaryManagementRepository.TypeVersion(type.getId(), type.getDictCode(), type.getIsSystem());
    }

    private SystemDictionaryManagementRepository.ItemVersion dictionaryItemVersion(SystemVO.DictItemVO item) {
        return new SystemDictionaryManagementRepository.ItemVersion(
                item.getId(), item.getDictTypeId(), item.getItemValue(), item.getStatus()
        );
    }

    private SystemConfigurationManagementRepository.Actor configurationActor(CurrentUser currentUser) {
        return new SystemConfigurationManagementRepository.Actor(currentUser.getUserId(), currentUser.getUserUuid());
    }

    private SystemConfigurationManagementRepository.ConfigVersion configurationVersion(SystemVO.ConfigVO config) {
        return new SystemConfigurationManagementRepository.ConfigVersion(config.getId(), config.getConfigKey());
    }

    private PermissionSnapshotService.PermissionSnapshot resolvePermissionSnapshot(Long userId, Long simulatedRoleId, CurrentUser currentUser) {
        if (userId == null) {
            return PermissionSnapshotService.PermissionSnapshot.empty();
        }
        if (permissionSnapshotService == null) {
            if (enforceTrustedUserResolution && AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user resolver is unavailable");
            }
            return PermissionSnapshotService.PermissionSnapshot.empty();
        }
        if (simulatedRoleId != null) {
            PermissionSnapshotService.PermissionSnapshot roleSnapshot = permissionSnapshotService.loadGrantedRoleSnapshot(
                    userId,
                    currentUser.getUserUuid(),
                    simulatedRoleId
            );
            if (roleSnapshot == null && enforceTrustedUserResolution && AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user permission snapshot is unavailable");
            }
            return roleSnapshot == null ? PermissionSnapshotService.PermissionSnapshot.empty() : roleSnapshot;
        }
        PermissionSnapshotService.PermissionSnapshot snapshotFromCurrentUser = snapshotFromCurrentUser(currentUser);
        if (snapshotFromCurrentUser != null) {
            return snapshotFromCurrentUser;
        }
        if (currentUser == null || !StringUtils.hasText(currentUser.getUserUuid())) {
            return PermissionSnapshotService.PermissionSnapshot.empty();
        }
        PermissionSnapshotService.PermissionSnapshot snapshot = permissionSnapshotService.loadSnapshot(userId, currentUser.getUserUuid());
        if (snapshot == null && enforceTrustedUserResolution && AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user permission snapshot is unavailable");
        }
        return snapshot == null ? PermissionSnapshotService.PermissionSnapshot.empty() : snapshot;
    }

    private void refreshTrustedCurrentUser(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            return;
        }
        if (sessionAuthenticationService != null) {
            CurrentUser refreshedUser = requireTrustedAuthenticatedCurrentUser(
                    sessionAuthenticationService.authenticateSessionTicket(
                            currentUser.getSessionId(),
                            currentUser.getUserId(),
                            currentUser.getUserUuid(),
                            currentUser.getSimulatedRoleId(),
                            currentUser.getSessionVersion(),
                            currentUser.getPermissionsVersion()
                    )
            );
            copyTrustedCurrentUser(currentUser, refreshedUser);
            return;
        }
        if (permissionSnapshotService == null) {
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user resolver is unavailable");
            }
            return;
        }
        Long userId = currentUser.getUserId();
        String normalizedUserUuid = StringUtils.hasText(currentUser.getUserUuid()) ? currentUser.getUserUuid().trim() : null;
        if (userId == null || userId <= 0 || !StringUtils.hasText(normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "User login is required");
        }
        if (systemInternalApi != null) {
            SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
            if (userSnapshot == null || userSnapshot.userId() == null || !userId.equals(userSnapshot.userId())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "User login is required");
            }
            if (!StringUtils.hasText(userSnapshot.userUuid())
                    || !normalizedUserUuid.equals(userSnapshot.userUuid().trim())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "User login is required");
            }
            if (!STATUS_ENABLED.equalsIgnoreCase(userSnapshot.status())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
            }
            String currentUsername = StringUtils.hasText(userSnapshot.username()) ? userSnapshot.username().trim() : null;
            if (!StringUtils.hasText(currentUsername)) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user username is unavailable");
            }
            currentUser.setUserId(userSnapshot.userId());
            currentUser.setUserUuid(userSnapshot.userUuid().trim());
            currentUser.setUsername(currentUsername);
            normalizedUserUuid = userSnapshot.userUuid().trim();
        }
        if (!permissionSnapshotService.isTrustedActiveUser(userId, normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
        }
        Long simulatedRoleId = normalizeSimulatedRoleId(currentUser.getSimulatedRoleId());
        PermissionSnapshotService.PermissionSnapshot snapshot = simulatedRoleId != null
                ? permissionSnapshotService.loadGrantedRoleSnapshot(
                userId,
                normalizedUserUuid,
                simulatedRoleId
        )
                : permissionSnapshotService.loadSnapshot(userId, normalizedUserUuid);
        if (snapshot == null) {
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user permission snapshot is unavailable");
            }
            return;
        }
        currentUser.setSimulatedRoleId(simulatedRoleId);
        currentUser.setUserUuid(normalizedUserUuid);
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
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(refreshedUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "User login is required");
        }
        return refreshedUser;
    }

    private Long normalizeSimulatedRoleId(Long simulatedRoleId) {
        return simulatedRoleId == null || simulatedRoleId <= 0 ? null : simulatedRoleId;
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

    private PermissionSnapshotService.PermissionSnapshot snapshotFromCurrentUser(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)
                || !StringUtils.hasText(currentUser.getPermissionsVersion())) {
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

    private List<CurrentUserVO.RoleOptionVO> listAvailableRoles(Long userId, String userUuid) {
        if (userId == null) {
            return List.of();
        }
        return currentUserProfileRepository.findAvailableRoles(userId, userUuid);
    }

    private String resolveLocale(Long userId, String userUuid) {
        if (userId == null || !StringUtils.hasText(userUuid)) {
            return DEFAULT_LOCALE;
        }
        return normalizeLocale(currentUserProfileRepository.findLocale(userId, userUuid));
    }

    private List<String> listCurrentRoleNames(Long userId, String userUuid) {
        return currentUserProfileRepository.findRoleNames(userId, userUuid);
    }

    private Long insertMenu(Long menuId, SystemDTO.MenuUpsertRequest request, Long operatorId, String operatorUuid) {
        return insertMenu(menuId, null, request, operatorId, operatorUuid);
    }

    private Long insertMenu(Long menuId, SystemVO.MenuVO existingMenu, SystemDTO.MenuUpsertRequest request, Long operatorId, String operatorUuid) {
        SystemMenuManagementRepository.MenuSaveResult saved = menuRepository.save(new SystemMenuManagementRepository.MenuSave(
                existingMenu == null ? null : menuVersion(existingMenu),
                request.getParentId(),
                request.getMenuCode(),
                request.getMenuName(),
                request.getMenuType(),
                request.getPath(),
                request.getComponent(),
                request.getIcon(),
                request.getSortNo(),
                request.getPermissionKey(),
                request.getStatus(),
                menuActor(operatorId, operatorUuid),
                LocalDateTime.now()
        ));
        requireSystemWrite(saved.writeCount(), "Menu changed, please retry");
        if (saved.menuId() == null) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Menu changed, please retry");
        }
        return saved.menuId();
    }

    private SystemVO.MenuVO loadMenu(Long menuId) {
        requirePositiveId(menuId, "Menu id is required");
        SystemVO.MenuVO menu = menuRepository.findActiveMenu(menuId);
        if (menu == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Menu does not exist");
        }
        return menu;
    }

    private SystemVO.MenuVO ensureEditableMenu(Long menuId) {
        if (menuId == null || menuId <= 0) {
            return null;
        }
        SystemVO.MenuVO menu = loadMenu(menuId);
        ensureEditableMenu(menu);
        return menu;
    }

    private void ensureEditableMenu(SystemVO.MenuVO menu) {
        if (menu == null || menu.getId() == null || menu.getId() <= 0 || menu.isBuiltin()) {
            throw new BizException(ErrorCode.FORBIDDEN, "Built-in menu cannot be edited or deleted");
        }
    }

    private void ensureEditableParentMenu(Long parentId) {
        if (parentId == null || parentId == 0) {
            return;
        }
        if (parentId < 0) {
            throw new BizException(ErrorCode.FORBIDDEN, "Built-in menu cannot be edited or deleted");
        }
        ensureEditableMenu(parentId);
    }

    private void ensureEditableMenuRequest(SystemDTO.MenuUpsertRequest request) {
        if (request == null) {
            return;
        }
        if (SystemRouteCatalog.isBuiltInMenuPath(request.getPath()) || SystemRouteCatalog.isBuiltInMenuComponent(request.getComponent())) {
            throw new BizException(ErrorCode.FORBIDDEN, "Built-in menu cannot be edited or deleted");
        }
    }

    private SystemVO.DictTypeVO loadDictType(Long id) {
        requirePositiveId(id, "Dict type id is required");
        SystemVO.DictTypeVO type = dictionaryRepository.findActiveType(id);
        if (type == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Dictionary type does not exist");
        }
        return type;
    }

    private SystemVO.DictItemVO loadDictItem(Long dictTypeId, Long itemId) {
        requirePositiveId(itemId, "Dict item id is required");
        SystemVO.DictItemVO item = dictionaryRepository.findActiveItem(dictTypeId, itemId);
        if (item == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Dictionary item does not exist");
        }
        return item;
    }

    private SystemVO.ConfigVO loadConfig(Long id) {
        requirePositiveId(id, "Config id is required");
        SystemVO.ConfigVO config = configurationRepository.findActiveConfig(id);
        if (config == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Config does not exist");
        }
        return config;
    }

    private Long upsertDictType(Long id, SystemDTO.DictTypeUpsertRequest request, Long operatorId, String operatorUuid) {
        return upsertDictType(id, null, request, operatorId, operatorUuid);
    }

    private Long upsertDictType(Long id, SystemVO.DictTypeVO existingType, SystemDTO.DictTypeUpsertRequest request, Long operatorId, String operatorUuid) {
        SystemDictionaryManagementRepository.DictionaryWriteResult saved = dictionaryRepository.saveType(
                new SystemDictionaryManagementRepository.TypeWrite(
                        existingType == null ? null : dictionaryTypeVersion(existingType),
                        request.getDictCode(),
                        request.getDictName(),
                        request.getStatus(),
                        existingType == null ? 0 : existingType.getIsSystem(),
                        request.getRemark(),
                        dictionaryActor(operatorId, operatorUuid),
                        LocalDateTime.now()
                )
        );
        requireSystemWrite(saved.writeCount(), "Dict type changed, please retry");
        if (saved.id() == null) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Dict type changed, please retry");
        }
        return saved.id();
    }

    private Long upsertDictItem(Long id, Long dictTypeId, SystemDTO.DictItemUpsertRequest request, Long operatorId, String operatorUuid) {
        return upsertDictItem(id, dictTypeId, null, request, operatorId, operatorUuid);
    }

    private Long upsertDictItem(Long id, Long dictTypeId, SystemVO.DictItemVO existingItem, SystemDTO.DictItemUpsertRequest request, Long operatorId, String operatorUuid) {
        SystemDictionaryManagementRepository.DictionaryWriteResult saved = dictionaryRepository.saveItem(
                new SystemDictionaryManagementRepository.ItemWrite(
                        existingItem == null ? null : dictionaryItemVersion(existingItem),
                        dictTypeId,
                        request.getItemLabel(),
                        request.getItemValue(),
                        request.getSortNo(),
                        request.getStatus(),
                        request.getRemark(),
                        dictionaryActor(operatorId, operatorUuid),
                        LocalDateTime.now()
                )
        );
        requireSystemWrite(saved.writeCount(), "Dict item changed, please retry");
        if (saved.id() == null) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Dict item changed, please retry");
        }
        return saved.id();
    }

    private boolean isSystemDictType(SystemVO.DictTypeVO type) {
        return type != null && type.getIsSystem() != null && type.getIsSystem() != 0;
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

    private static SystemVO.ShortcutVO shortcut(String title, String description, String path, String permission) {
        SystemVO.ShortcutVO shortcut = new SystemVO.ShortcutVO();
        shortcut.setTitle(title);
        shortcut.setDescription(description);
        shortcut.setPath(path);
        shortcut.setPermission(permission);
        return shortcut;
    }

    private LocalDateTime parseDateTime(String value) {
        return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private String normalizeContactType(String contactType) {
        if (!StringUtils.hasText(contactType)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Contact type is required");
        }
        String normalized = contactType.trim().toLowerCase(Locale.ROOT);
        if (!"mobile".equals(normalized) && !"email".equals(normalized)) {
            throw new BizException(ErrorCode.NOT_FOUND, "Unsupported contact type");
        }
        return normalized;
    }

    private String normalizeContactValue(String contactType, String value) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Contact value is required");
        }
        String normalized = value.trim();
        if ("mobile".equals(contactType) && !normalized.matches("^1[3-9]\\d{9}$")) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Mobile number format is invalid");
        }
        if ("email".equals(contactType) && !normalized.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Email format is invalid");
        }
        return normalized;
    }
    private record CachedReadModelVersion(long version, long expiresAtEpochMillis) {
    }
}
