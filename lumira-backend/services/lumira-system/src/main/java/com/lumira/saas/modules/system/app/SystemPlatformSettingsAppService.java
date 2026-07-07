package com.lumira.saas.modules.system.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.FieldCryptoService;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.lumira.saas.infrastructure.readmodel.ReadModelVersionService;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.architecture.application.OwnerRuntimeMetrics;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.vo.SystemVO;
import com.lumira.saas.modules.system.support.SmtpMailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@ConditionalOnLumiraControlPlaneEnabled
public class SystemPlatformSettingsAppService {
    private static final String STATUS_ENABLED = "ENABLED";

    private static final String CONTEXT_PLATFORM = "platform";
    private static final String SCOPE_RUNTIME_APPEARANCE = "runtime-appearance";
    private static final String SCOPE_PUBLIC_BOOTSTRAP = "public-bootstrap";
    private static final String RUNTIME_APPEARANCE_CACHE_KEY = "runtime-appearance";
    private static final Duration CONFIG_SNAPSHOT_TTL = Duration.ofSeconds(30);
    private static final int CONFIG_SNAPSHOT_MAX_ENTRIES = 4096;
    private static final int CONFIG_SINGLE_FLIGHT_MAX_ENTRIES = 2048;
    private static final Duration RUNTIME_APPEARANCE_VERSION_TTL = Duration.ofSeconds(10);
    private static final int RUNTIME_APPEARANCE_VERSION_MAX_ENTRIES = 1024;

    private static final String BRANDING_WEBSITE_NAME_KEY = "branding.website-name";
    private static final String BRANDING_WEBSITE_FAVICON_URL_KEY = "branding.website-favicon-url";
    private static final String BRANDING_WEBSITE_LOGO_URL_KEY = "branding.website-logo-url";
    private static final String BRANDING_LOGIN_BACKGROUND_URL_KEY = "branding.login-background-url";
    private static final String BRANDING_GITHUB_LINK_ENABLED_KEY = "branding.github-link-enabled";
    private static final String BRANDING_GITHUB_LINK_URL_KEY = "branding.github-link-url";
    private static final String BRANDING_HELP_LINK_ENABLED_KEY = "branding.help-link-enabled";
    private static final String BRANDING_HELP_LINK_URL_KEY = "branding.help-link-url";
    private static final String BRANDING_COMPANY_NAME_KEY = "branding.company-name";
    private static final String BRANDING_COPYRIGHT_START_YEAR_KEY = "branding.copyright-start-year";
    private static final String BRANDING_FOOTER_ICP_KEY = "branding.footer-icp";
    private static final String BRANDING_FOOTER_POLICE_BEIAN_KEY = "branding.footer-police-beian";
    private static final String BRANDING_FOOTER_COPYRIGHT_KEY = "branding.footer-copyright";
    private static final List<String> BRANDING_CONFIG_KEYS = List.of(
            BRANDING_WEBSITE_NAME_KEY,
            BRANDING_WEBSITE_FAVICON_URL_KEY,
            BRANDING_WEBSITE_LOGO_URL_KEY,
            BRANDING_LOGIN_BACKGROUND_URL_KEY,
            BRANDING_GITHUB_LINK_ENABLED_KEY,
            BRANDING_GITHUB_LINK_URL_KEY,
            BRANDING_HELP_LINK_ENABLED_KEY,
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

    private static final String WECHAT_OFFICIAL_ENABLED_KEY = "notification.wechat-official.enabled";
    private static final String WECHAT_OFFICIAL_APP_ID_KEY = "notification.wechat-official.app-id";
    private static final String WECHAT_OFFICIAL_APP_SECRET_KEY = "notification.wechat-official.app-secret";
    private static final String WECHAT_OFFICIAL_TEMPLATE_ID_KEY = "notification.wechat-official.template-id";
    private static final String WECHAT_OFFICIAL_DETAIL_URL_KEY = "notification.wechat-official.detail-url";
    private static final List<String> WECHAT_OFFICIAL_CONFIG_KEYS = List.of(
            WECHAT_OFFICIAL_ENABLED_KEY,
            WECHAT_OFFICIAL_APP_ID_KEY,
            WECHAT_OFFICIAL_APP_SECRET_KEY,
            WECHAT_OFFICIAL_TEMPLATE_ID_KEY,
            WECHAT_OFFICIAL_DETAIL_URL_KEY
    );

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
            WATERMARK_ENABLED_KEY,
            WATERMARK_MODE_KEY,
            WATERMARK_TEXT_LINES_KEY,
            WATERMARK_IMAGE_URL_KEY,
            WATERMARK_FONT_COLOR_KEY,
            WATERMARK_FONT_SIZE_KEY,
            WATERMARK_FONT_WEIGHT_KEY,
            WATERMARK_ROTATE_KEY,
            WATERMARK_GAP_X_KEY,
            WATERMARK_GAP_Y_KEY,
            WATERMARK_OFFSET_X_KEY,
            WATERMARK_OFFSET_Y_KEY,
            WATERMARK_Z_INDEX_KEY,
            WATERMARK_OPACITY_KEY
    );

    private static final String FLOATING_API_DOCS_QR_ENABLED_KEY = "floating-window.api-docs-qr-enabled";
    private static final String FLOATING_API_DOCS_QR_TITLE_KEY = "floating-window.api-docs-qr-title";
    private static final String FLOATING_API_DOCS_QR_IMAGE_URL_KEY = "floating-window.api-docs-qr-image-url";
    private static final List<String> FLOATING_WINDOW_CONFIG_KEYS = List.of(
            FLOATING_API_DOCS_QR_ENABLED_KEY,
            FLOATING_API_DOCS_QR_TITLE_KEY,
            FLOATING_API_DOCS_QR_IMAGE_URL_KEY
    );

    private final MyBatisQueryOperations jdbcTemplate;
    private final OperationAuditService operationAuditService;
    private final FieldCryptoService fieldCryptoService;
    private final ReadModelVersionService readModelVersionService;
    private final OwnerRuntimeMetrics ownerRuntimeMetrics;
    private final SmtpMailService smtpMailService;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;
    private final boolean enforceTrustedUserResolution;
    private final Cache<String, Map<String, String>> configSnapshotCache;
    private final Cache<String, CompletableFuture<Map<String, String>>> configLoadInFlight;
    private final Cache<String, Long> runtimeAppearanceVersionCache;
    private final Cache<String, CompletableFuture<Long>> runtimeAppearanceVersionLoadInFlight;

    @Autowired
    public SystemPlatformSettingsAppService(
            MyBatisQueryOperations jdbcTemplate,
            OperationAuditService operationAuditService,
            FieldCryptoService fieldCryptoService,
            ReadModelVersionService readModelVersionService,
            OwnerRuntimeMetrics ownerRuntimeMetrics,
            SmtpMailService smtpMailService,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(jdbcTemplate, operationAuditService, fieldCryptoService, readModelVersionService, ownerRuntimeMetrics, smtpMailService, permissionSnapshotService, systemInternalApi, sessionAuthenticationService, true);
    }

    private SystemPlatformSettingsAppService(
            MyBatisQueryOperations jdbcTemplate,
            OperationAuditService operationAuditService,
            FieldCryptoService fieldCryptoService,
            ReadModelVersionService readModelVersionService,
            OwnerRuntimeMetrics ownerRuntimeMetrics,
            SmtpMailService smtpMailService,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService,
            boolean enforceTrustedUserResolution
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.operationAuditService = operationAuditService;
        this.fieldCryptoService = fieldCryptoService;
        this.readModelVersionService = readModelVersionService;
        this.ownerRuntimeMetrics = ownerRuntimeMetrics;
        this.smtpMailService = smtpMailService;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
        this.configSnapshotCache = CacheBuilder.newBuilder()
                .maximumSize(CONFIG_SNAPSHOT_MAX_ENTRIES)
                .expireAfterWrite(CONFIG_SNAPSHOT_TTL.toMillis(), TimeUnit.MILLISECONDS)
                .build();
        this.configLoadInFlight = CacheBuilder.newBuilder()
                .maximumSize(CONFIG_SINGLE_FLIGHT_MAX_ENTRIES)
                .expireAfterWrite(CONFIG_SNAPSHOT_TTL.toMillis(), TimeUnit.MILLISECONDS)
                .build();
        this.runtimeAppearanceVersionCache = CacheBuilder.newBuilder()
                .maximumSize(RUNTIME_APPEARANCE_VERSION_MAX_ENTRIES)
                .expireAfterWrite(RUNTIME_APPEARANCE_VERSION_TTL.toMillis(), TimeUnit.MILLISECONDS)
                .build();
        this.runtimeAppearanceVersionLoadInFlight = CacheBuilder.newBuilder()
                .maximumSize(RUNTIME_APPEARANCE_VERSION_MAX_ENTRIES)
                .expireAfterWrite(RUNTIME_APPEARANCE_VERSION_TTL.toMillis(), TimeUnit.MILLISECONDS)
                .build();
    }

    public SystemPlatformSettingsAppService(
            MyBatisQueryOperations jdbcTemplate,
            OperationAuditService operationAuditService,
            FieldCryptoService fieldCryptoService,
            ReadModelVersionService readModelVersionService,
            OwnerRuntimeMetrics ownerRuntimeMetrics,
            SmtpMailService smtpMailService
    ) {
        this(jdbcTemplate,
                operationAuditService,
                fieldCryptoService,
                readModelVersionService,
                ownerRuntimeMetrics,
                smtpMailService,
                null,
                null,
                null,
                false);
    }

    public SystemPlatformSettingsAppService(
            MyBatisQueryOperations jdbcTemplate,
            OperationAuditService operationAuditService,
            FieldCryptoService fieldCryptoService,
            ReadModelVersionService readModelVersionService,
            OwnerRuntimeMetrics ownerRuntimeMetrics,
            SmtpMailService smtpMailService,
            PermissionSnapshotService permissionSnapshotService
    ) {
        this(jdbcTemplate,
                operationAuditService,
                fieldCryptoService,
                readModelVersionService,
                ownerRuntimeMetrics,
                smtpMailService,
                permissionSnapshotService,
                null,
                null,
                false);
    }

    public SystemVO.BrandingSettingsVO getBrandingSettings(CurrentUser currentUser) {
        requirePermission(currentUser, "system:config:view");
        return loadBrandingSettings();
    }

    public SystemVO.BrandingSettingsVO getPublicBrandingSettings() {
        return loadBrandingSettings();
    }

    public SystemVO.AgreementSettingsVO getAgreementSettings() {
        return loadAgreementSettings();
    }

    public SystemVO.AgreementSettingsVO getPublicAgreementSettings() {
        return loadAgreementSettings();
    }

    @Transactional
    public SystemVO.BrandingSettingsVO updateBrandingSettings(CurrentUser currentUser, SystemDTO.BrandingSettingsRequest request) {
        Long operatorId = requirePermission(currentUser, "system:config:update");
        requireRequest(request, "Branding settings request is required");
        String websiteName = sanitizeBrandingText(request.getWebsiteName(), "Website name");
        String companyName = sanitizeBrandingText(request.getCompanyName(), websiteName);
        Integer copyrightStartYear = request.getCopyrightStartYear() == null ? LocalDate.now().getYear() : request.getCopyrightStartYear();
        upsertBrandingConfig(BRANDING_WEBSITE_NAME_KEY, "Website name", websiteName, "Website name shown in branding and browser title", operatorId);
        upsertBrandingConfig(BRANDING_WEBSITE_FAVICON_URL_KEY, "Website favicon URL", sanitizeBrandingText(request.getWebsiteFaviconUrl(), ""), "Favicon URL used by the website", operatorId);
        upsertBrandingConfig(BRANDING_WEBSITE_LOGO_URL_KEY, "Website logo URL", sanitizeBrandingText(request.getWebsiteLogoUrl(), ""), "Logo URL used by the website", operatorId);
        upsertBrandingConfig(BRANDING_LOGIN_BACKGROUND_URL_KEY, "Login background URL", sanitizeBrandingText(request.getLoginBackgroundUrl(), ""), "Background image shown on the login page", operatorId);
        upsertBrandingConfig(BRANDING_GITHUB_LINK_ENABLED_KEY, "GitHub link enabled", String.valueOf(request.getGithubLinkEnabled() == null || request.getGithubLinkEnabled()), "Whether the GitHub link is shown", operatorId);
        upsertBrandingConfig(BRANDING_GITHUB_LINK_URL_KEY, "GitHub link URL", sanitizeBrandingText(request.getGithubLinkUrl(), ""), "GitHub link target URL", operatorId);
        upsertBrandingConfig(BRANDING_HELP_LINK_ENABLED_KEY, "Help link enabled", String.valueOf(request.getHelpLinkEnabled() == null || request.getHelpLinkEnabled()), "Whether the help link is shown", operatorId);
        upsertBrandingConfig(BRANDING_HELP_LINK_URL_KEY, "Help link URL", sanitizeBrandingText(request.getHelpLinkUrl(), ""), "Help link target URL", operatorId);
        upsertBrandingConfig(BRANDING_COMPANY_NAME_KEY, "Company name", companyName, "Company name shown in branding", operatorId);
        upsertBrandingConfig(BRANDING_COPYRIGHT_START_YEAR_KEY, "Copyright start year", String.valueOf(copyrightStartYear), "Copyright start year", operatorId);
        upsertBrandingConfig(BRANDING_FOOTER_ICP_KEY, "Footer ICP", sanitizeBrandingText(request.getFooterIcp(), ""), "ICP text shown in the footer", operatorId);
        upsertBrandingConfig(BRANDING_FOOTER_POLICE_BEIAN_KEY, "Footer police beian", sanitizeBrandingText(request.getFooterPoliceBeian(), ""), "Police beian text shown in the footer", operatorId);
        String footerCopyright = sanitizeBrandingText(
                request.getFooterCopyright(),
                buildCopyrightText(companyName, copyrightStartYear)
        );
        upsertBrandingConfig(BRANDING_FOOTER_COPYRIGHT_KEY, "Footer copyright", footerCopyright, "Footer copyright text", operatorId);
        operationAuditService.log(
                operatorId,
                currentUser.getUserUuid(),
                currentUser.getUsername(),
                "system",
                "branding-update",
                "UPDATE",
                "SUCCESS",
                "Update branding settings"
        );
        markRuntimeAppearanceChanged("branding-update");
        markPublicBootstrapChanged("branding-update");
        return loadBrandingSettings();
    }

    @Transactional
    public SystemVO.AgreementSettingsVO updateAgreementSettings(CurrentUser currentUser, SystemDTO.AgreementSettingsRequest request) {
        Long operatorId = requirePermission(currentUser, "system:config:update");
        requireRequest(request, "Agreement settings request is required");
        upsertConfigValue(AGREEMENT_USER_MARKDOWN_KEY, "User agreement", normalizeMarkdownText(request.getUserAgreementMarkdown()), "User agreement Markdown", operatorId);
        upsertConfigValue(AGREEMENT_PRIVACY_MARKDOWN_KEY, "Privacy agreement", normalizeMarkdownText(request.getPrivacyAgreementMarkdown()), "Privacy agreement Markdown", operatorId);
        operationAuditService.log(
                operatorId,
                currentUser.getUserUuid(),
                currentUser.getUsername(),
                "system",
                "agreement-update",
                "UPDATE",
                "SUCCESS",
                "Update agreement settings"
        );
        markRuntimeAppearanceChanged("agreement-update");
        markPublicBootstrapChanged("agreement-update");
        return loadAgreementSettings();
    }

    public SystemVO.WatermarkSettingsVO getWatermarkSettings(CurrentUser currentUser) {
        requirePermission(currentUser, "system:config:view");
        return loadWatermarkSettings();
    }

    public SystemVO.WatermarkSettingsVO getPublicWatermarkSettings() {
        return loadWatermarkSettings();
    }

    public SystemVO.FloatingWindowSettingsVO getFloatingWindowSettings(CurrentUser currentUser) {
        requirePermission(currentUser, "system:config:view");
        return loadFloatingWindowSettings();
    }

    public SystemVO.FloatingWindowSettingsVO getPublicFloatingWindowSettings() {
        return loadFloatingWindowSettings();
    }

    @Transactional
    public SystemVO.WatermarkSettingsVO updateWatermarkSettings(CurrentUser currentUser, SystemDTO.WatermarkSettingsRequest request) {
        Long operatorId = requirePermission(currentUser, "system:config:update");
        requireRequest(request, "Watermark settings request is required");
        upsertBrandingConfig(WATERMARK_ENABLED_KEY, "Watermark enabled", String.valueOf(Boolean.TRUE.equals(request.getEnabled())), "Whether watermark display is enabled", operatorId);
        upsertBrandingConfig(WATERMARK_MODE_KEY, "Watermark mode", defaultIfBlank(request.getMode(), "TEXT"), "TEXT/IMAGE", operatorId);
        upsertBrandingConfig(WATERMARK_TEXT_LINES_KEY, "Watermark text lines", String.join("\\n", request.getTextLines() == null ? List.of() : request.getTextLines()), "Watermark text lines", operatorId);
        upsertBrandingConfig(WATERMARK_IMAGE_URL_KEY, "Watermark image URL", defaultIfBlank(request.getImageUrl(), ""), "Watermark image URL", operatorId);
        upsertBrandingConfig(WATERMARK_FONT_COLOR_KEY, "Watermark font color", defaultIfBlank(request.getFontColor(), "rgba(0,0,0,0.15)"), "Watermark font color", operatorId);
        upsertBrandingConfig(WATERMARK_FONT_SIZE_KEY, "Watermark font size", String.valueOf(request.getFontSize() == null ? 14 : request.getFontSize()), "Watermark font size", operatorId);
        upsertBrandingConfig(WATERMARK_FONT_WEIGHT_KEY, "Watermark font weight", defaultIfBlank(request.getFontWeight(), "normal"), "Watermark font weight", operatorId);
        upsertBrandingConfig(WATERMARK_ROTATE_KEY, "Watermark rotate", String.valueOf(request.getRotate() == null ? -22 : request.getRotate()), "Watermark rotate angle", operatorId);
        upsertBrandingConfig(WATERMARK_GAP_X_KEY, "Watermark gap X", String.valueOf(request.getGapX() == null ? 100 : request.getGapX()), "Watermark horizontal gap", operatorId);
        upsertBrandingConfig(WATERMARK_GAP_Y_KEY, "Watermark gap Y", String.valueOf(request.getGapY() == null ? 100 : request.getGapY()), "Watermark vertical gap", operatorId);
        upsertBrandingConfig(WATERMARK_OFFSET_X_KEY, "Watermark offset X", String.valueOf(request.getOffsetX() == null ? 0 : request.getOffsetX()), "Watermark horizontal offset", operatorId);
        upsertBrandingConfig(WATERMARK_OFFSET_Y_KEY, "Watermark offset Y", String.valueOf(request.getOffsetY() == null ? 0 : request.getOffsetY()), "Watermark vertical offset", operatorId);
        upsertBrandingConfig(WATERMARK_Z_INDEX_KEY, "Watermark z-index", String.valueOf(request.getZIndex() == null ? 9 : request.getZIndex()), "Watermark z-index", operatorId);
        upsertBrandingConfig(WATERMARK_OPACITY_KEY, "Watermark opacity", String.valueOf(request.getOpacity() == null ? 0.15D : request.getOpacity()), "Watermark opacity", operatorId);
        markRuntimeAppearanceChanged("watermark-update");
        return loadWatermarkSettings();
    }

    @Transactional
    public SystemVO.FloatingWindowSettingsVO updateFloatingWindowSettings(CurrentUser currentUser, SystemDTO.FloatingWindowSettingsRequest request) {
        Long operatorId = requirePermission(currentUser, "system:config:update");
        requireRequest(request, "Floating window settings request is required");
        upsertBrandingConfig(FLOATING_API_DOCS_QR_ENABLED_KEY, "API docs QR enabled", String.valueOf(Boolean.TRUE.equals(request.getApiDocsQrEnabled())), "Whether API docs QR is shown", operatorId);
        upsertBrandingConfig(FLOATING_API_DOCS_QR_TITLE_KEY, "API docs QR title", defaultIfBlank(request.getApiDocsQrTitle(), ""), "API docs QR title", operatorId);
        upsertBrandingConfig(FLOATING_API_DOCS_QR_IMAGE_URL_KEY, "API docs QR image URL", defaultIfBlank(request.getApiDocsQrImageUrl(), ""), "API docs QR image URL", operatorId);
        operationAuditService.log(
                operatorId,
                currentUser.getUserUuid(),
                currentUser.getUsername(),
                "system",
                "floating-window-update",
                "UPDATE",
                "SUCCESS",
                "Update floating window settings"
        );
        markRuntimeAppearanceChanged("floating-window-update");
        return loadFloatingWindowSettings();
    }

    public SystemVO.SmtpSettingsVO getSmtpSettings(CurrentUser currentUser) {
        requirePermission(currentUser, "system:config:view");
        return loadSmtpSettings();
    }

    public SystemVO.WechatOfficialAccountSettingsVO getWechatOfficialAccountSettings(CurrentUser currentUser) {
        requirePermission(currentUser, "system:config:view");
        return loadWechatOfficialAccountSettings();
    }

    @Transactional
    public SystemVO.SmtpSettingsVO updateSmtpSettings(CurrentUser currentUser, SystemDTO.SmtpSettingsRequest request) {
        Long operatorId = requirePermission(currentUser, "system:config:update");
        requireRequest(request, "SMTP settings request is required");
        Map<String, String> currentValues = loadConfigValuesByKeys(SMTP_CONFIG_KEYS);
        SystemVO.SmtpSettingsVO current = buildSmtpSettings(currentValues);
        boolean enabled = request.getEnabled() == null ? !Boolean.FALSE.equals(current.getEnabled()) : Boolean.TRUE.equals(request.getEnabled());
        String host = sanitizeText(request.getHost(), current.getHost());
        Integer port = request.getPort() == null ? current.getPort() : request.getPort();
        String username = sanitizeText(request.getUsername(), current.getUsername());
        String existingPassword = defaultIfBlank(currentValues.get(SMTP_PASSWORD_KEY), "");
        String password = StringUtils.hasText(request.getPassword()) ? request.getPassword() : existingPassword;
        String from = sanitizeText(request.getFrom(), current.getFrom());
        boolean authEnabled = request.getAuthEnabled() == null ? Boolean.TRUE.equals(current.getAuthEnabled()) : request.getAuthEnabled();
        boolean startTlsEnabled = request.getStartTlsEnabled() == null ? Boolean.TRUE.equals(current.getStartTlsEnabled()) : request.getStartTlsEnabled();
        boolean sslEnabled = request.getSslEnabled() == null ? Boolean.TRUE.equals(current.getSslEnabled()) : request.getSslEnabled();

        upsertPlatformConfig(SMTP_ENABLED_KEY, "SMTP enabled", String.valueOf(enabled), "Whether SMTP is enabled", operatorId);
        upsertPlatformConfig(SMTP_HOST_KEY, "SMTP host", host, "SMTP host address", operatorId);
        upsertPlatformConfig(SMTP_PORT_KEY, "SMTP port", String.valueOf(port == null ? 25 : port), "SMTP port", operatorId);
        upsertPlatformConfig(SMTP_USERNAME_KEY, "SMTP username", username, "SMTP username", operatorId);
        upsertPlatformConfig(SMTP_PASSWORD_KEY, "SMTP password", password, "SMTP password", operatorId);
        upsertPlatformConfig(SMTP_FROM_KEY, "SMTP from", from, "SMTP from address", operatorId);
        upsertPlatformConfig(SMTP_AUTH_ENABLED_KEY, "SMTP auth", String.valueOf(authEnabled), "Whether SMTP AUTH is enabled", operatorId);
        upsertPlatformConfig(SMTP_STARTTLS_ENABLED_KEY, "SMTP STARTTLS", String.valueOf(startTlsEnabled), "Whether SMTP STARTTLS is enabled", operatorId);
        upsertPlatformConfig(SMTP_SSL_ENABLED_KEY, "SMTP SSL", String.valueOf(sslEnabled), "Whether SMTP SSL is enabled", operatorId);
        smtpMailService.invalidate();
        operationAuditService.log(operatorId, currentUser.getUserUuid(), currentUser.getUsername(), "smtp", "update", "UPDATE", "SUCCESS", "Update SMTP settings");
        currentValues.put(SMTP_ENABLED_KEY, String.valueOf(enabled));
        currentValues.put(SMTP_HOST_KEY, host);
        currentValues.put(SMTP_PORT_KEY, String.valueOf(port == null ? 25 : port));
        currentValues.put(SMTP_USERNAME_KEY, username);
        currentValues.put(SMTP_PASSWORD_KEY, password);
        currentValues.put(SMTP_FROM_KEY, from);
        currentValues.put(SMTP_AUTH_ENABLED_KEY, String.valueOf(authEnabled));
        currentValues.put(SMTP_STARTTLS_ENABLED_KEY, String.valueOf(startTlsEnabled));
        currentValues.put(SMTP_SSL_ENABLED_KEY, String.valueOf(sslEnabled));
        markRuntimeAppearanceChanged("smtp-update");
        return buildSmtpSettings(currentValues);
    }

    @Transactional
    public SystemVO.SmtpSettingsVO resetSmtpSettings(CurrentUser currentUser) {
        Long operatorId = requirePermission(currentUser, "system:config:update");
        upsertPlatformConfig(SMTP_ENABLED_KEY, "SMTP enabled", "false", "Whether SMTP is enabled", operatorId);
        upsertPlatformConfig(SMTP_HOST_KEY, "SMTP host", "", "SMTP host address", operatorId);
        upsertPlatformConfig(SMTP_PORT_KEY, "SMTP port", "25", "SMTP port", operatorId);
        upsertPlatformConfig(SMTP_USERNAME_KEY, "SMTP username", "", "SMTP username", operatorId);
        upsertPlatformConfig(SMTP_PASSWORD_KEY, "SMTP password", "", "SMTP password", operatorId);
        upsertPlatformConfig(SMTP_FROM_KEY, "SMTP from", "", "SMTP from address", operatorId);
        upsertPlatformConfig(SMTP_AUTH_ENABLED_KEY, "SMTP auth", "true", "Whether SMTP AUTH is enabled", operatorId);
        upsertPlatformConfig(SMTP_STARTTLS_ENABLED_KEY, "SMTP STARTTLS", "true", "Whether SMTP STARTTLS is enabled", operatorId);
        upsertPlatformConfig(SMTP_SSL_ENABLED_KEY, "SMTP SSL", "false", "Whether SMTP SSL is enabled", operatorId);
        smtpMailService.invalidate();
        operationAuditService.log(operatorId, currentUser.getUserUuid(), currentUser.getUsername(), "smtp", "reset", "DELETE", "SUCCESS", "Reset SMTP settings");
        markRuntimeAppearanceChanged("smtp-reset");
        return buildSmtpSettings(Map.of(
                SMTP_ENABLED_KEY, "false",
                SMTP_HOST_KEY, "",
                SMTP_PORT_KEY, "25",
                SMTP_USERNAME_KEY, "",
                SMTP_PASSWORD_KEY, "",
                SMTP_FROM_KEY, "",
                SMTP_AUTH_ENABLED_KEY, "true",
                SMTP_STARTTLS_ENABLED_KEY, "true",
                SMTP_SSL_ENABLED_KEY, "false"
        ));
    }

    @Transactional
    public SystemVO.WechatOfficialAccountSettingsVO updateWechatOfficialAccountSettings(CurrentUser currentUser, SystemDTO.WechatOfficialAccountSettingsRequest request) {
        Long operatorId = requirePermission(currentUser, "system:config:update");
        requireRequest(request, "Wechat official account settings request is required");
        Map<String, String> currentValues = loadConfigValuesByKeys(WECHAT_OFFICIAL_CONFIG_KEYS);
        SystemVO.WechatOfficialAccountSettingsVO current = buildWechatOfficialAccountSettings(currentValues);
        boolean enabled = request.getEnabled() == null ? Boolean.TRUE.equals(current.getEnabled()) : Boolean.TRUE.equals(request.getEnabled());
        String appId = sanitizeText(request.getAppId(), current.getAppId());
        String existingSecret = defaultIfBlank(currentValues.get(WECHAT_OFFICIAL_APP_SECRET_KEY), "");
        String appSecret = StringUtils.hasText(request.getAppSecret()) ? request.getAppSecret().trim() : existingSecret;
        String templateId = sanitizeText(request.getTemplateId(), current.getTemplateId());
        String detailUrl = sanitizeText(request.getDetailUrl(), current.getDetailUrl());

        upsertPlatformConfig(WECHAT_OFFICIAL_ENABLED_KEY, "WeChat official account notifications", String.valueOf(enabled), "Whether official account template notifications are enabled", operatorId);
        upsertPlatformConfig(WECHAT_OFFICIAL_APP_ID_KEY, "WeChat official account AppID", appId, "WeChat official account AppID", operatorId);
        upsertPlatformConfig(WECHAT_OFFICIAL_APP_SECRET_KEY, "WeChat official account AppSecret", appSecret, "WeChat official account AppSecret", operatorId);
        upsertPlatformConfig(WECHAT_OFFICIAL_TEMPLATE_ID_KEY, "WeChat template ID", templateId, "Template message ID for notifications", operatorId);
        upsertPlatformConfig(WECHAT_OFFICIAL_DETAIL_URL_KEY, "WeChat notification detail URL", detailUrl, "Optional URL opened from template messages", operatorId);
        operationAuditService.log(operatorId, currentUser.getUserUuid(), currentUser.getUsername(), "notification", "wechat-official-update", "UPDATE", "SUCCESS", "Update WeChat official account notification settings");
        currentValues.put(WECHAT_OFFICIAL_ENABLED_KEY, String.valueOf(enabled));
        currentValues.put(WECHAT_OFFICIAL_APP_ID_KEY, appId);
        currentValues.put(WECHAT_OFFICIAL_APP_SECRET_KEY, appSecret);
        currentValues.put(WECHAT_OFFICIAL_TEMPLATE_ID_KEY, templateId);
        currentValues.put(WECHAT_OFFICIAL_DETAIL_URL_KEY, detailUrl);
        markRuntimeAppearanceChanged("wechat-official-update");
        return buildWechatOfficialAccountSettings(currentValues);
    }

    @Transactional
    public SystemVO.SmtpTestVO testSmtpSettings(CurrentUser currentUser, SystemDTO.SmtpTestRequest request) {
        requirePermission(currentUser, "system:config:update");
        requireRequest(request, "SMTP test request is required");
        Map<String, String> values = loadConfigValuesByKeys(SMTP_CONFIG_KEYS);
        JavaMailSenderImpl mailSender = buildSmtpSender(values);
        String from = defaultIfBlank(values.get(SMTP_FROM_KEY), values.get(SMTP_USERNAME_KEY));
        if (!StringUtils.hasText(from)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "SMTP sender address is required");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(request.getToEmail());
        message.setFrom(from);
        message.setSubject(defaultIfBlank(request.getSubject(), "SMTP test email"));
        message.setText(defaultIfBlank(request.getContent(), "This is a test email sent from the system SMTP settings."));
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "SMTP test send failed: " + exception.getMessage());
        }
        SystemVO.SmtpTestVO result = new SystemVO.SmtpTestVO();
        result.setSuccess(Boolean.TRUE);
        result.setMessage("SMTP test email sent");
        result.setToEmail(request.getToEmail());
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "smtp", "test", "CREATE", "SUCCESS", "SMTP 测试发送成功: " + request.getToEmail());
        return result;
    }

    private SystemVO.BrandingSettingsVO loadBrandingSettings() {
        Map<String, String> valueByKey = loadConfigValuesByKeys(BRANDING_CONFIG_KEYS);
        SystemVO.BrandingSettingsVO settings = new SystemVO.BrandingSettingsVO();
        settings.setWebsiteName(defaultIfBlank(valueByKey.get(BRANDING_WEBSITE_NAME_KEY), "Lumira"));
        settings.setWebsiteFaviconUrl(defaultIfBlank(valueByKey.get(BRANDING_WEBSITE_FAVICON_URL_KEY), ""));
        settings.setWebsiteLogoUrl(defaultIfBlank(valueByKey.get(BRANDING_WEBSITE_LOGO_URL_KEY), ""));
        settings.setLoginBackgroundUrl(defaultIfBlank(valueByKey.get(BRANDING_LOGIN_BACKGROUND_URL_KEY), ""));
        settings.setGithubLinkEnabled(Boolean.parseBoolean(defaultIfBlank(valueByKey.get(BRANDING_GITHUB_LINK_ENABLED_KEY), "true")));
        settings.setGithubLinkUrl(defaultIfBlank(valueByKey.get(BRANDING_GITHUB_LINK_URL_KEY), ""));
        settings.setHelpLinkEnabled(Boolean.parseBoolean(defaultIfBlank(valueByKey.get(BRANDING_HELP_LINK_ENABLED_KEY), "true")));
        settings.setHelpLinkUrl(defaultIfBlank(valueByKey.get(BRANDING_HELP_LINK_URL_KEY), ""));
        settings.setCompanyName(defaultIfBlank(valueByKey.get(BRANDING_COMPANY_NAME_KEY), settings.getWebsiteName()));
        settings.setCopyrightStartYear(parseInteger(valueByKey.get(BRANDING_COPYRIGHT_START_YEAR_KEY), LocalDate.now().getYear()));
        settings.setFooterIcp(defaultIfBlank(valueByKey.get(BRANDING_FOOTER_ICP_KEY), ""));
        settings.setFooterPoliceBeian(defaultIfBlank(valueByKey.get(BRANDING_FOOTER_POLICE_BEIAN_KEY), ""));
        settings.setFooterCopyright(defaultIfBlank(
                valueByKey.get(BRANDING_FOOTER_COPYRIGHT_KEY),
                buildCopyrightText(settings.getCompanyName(), settings.getCopyrightStartYear())
        ));
        return settings;
    }

    private SystemVO.AgreementSettingsVO loadAgreementSettings() {
        Map<String, String> valueByKey = loadConfigValuesByKeys(AGREEMENT_CONFIG_KEYS, false);
        SystemVO.AgreementSettingsVO settings = new SystemVO.AgreementSettingsVO();
        settings.setUserAgreementMarkdown(defaultIfBlank(valueByKey.get(AGREEMENT_USER_MARKDOWN_KEY), ""));
        settings.setPrivacyAgreementMarkdown(defaultIfBlank(valueByKey.get(AGREEMENT_PRIVACY_MARKDOWN_KEY), ""));
        return settings;
    }

    private SystemVO.WatermarkSettingsVO loadWatermarkSettings() {
        Map<String, String> valueByKey = loadConfigValuesByKeys(WATERMARK_CONFIG_KEYS);
        SystemVO.WatermarkSettingsVO settings = new SystemVO.WatermarkSettingsVO();
        settings.setEnabled(Boolean.parseBoolean(defaultIfBlank(valueByKey.get(WATERMARK_ENABLED_KEY), "false")));
        settings.setMode(defaultIfBlank(valueByKey.get(WATERMARK_MODE_KEY), "TEXT"));
        settings.setTextLines(parseWatermarkTextLines(valueByKey.get(WATERMARK_TEXT_LINES_KEY)));
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

    private SystemVO.FloatingWindowSettingsVO loadFloatingWindowSettings() {
        Map<String, String> valueByKey = loadConfigValuesByKeys(FLOATING_WINDOW_CONFIG_KEYS);
        SystemVO.FloatingWindowSettingsVO settings = new SystemVO.FloatingWindowSettingsVO();
        settings.setApiDocsQrEnabled(Boolean.parseBoolean(defaultIfBlank(valueByKey.get(FLOATING_API_DOCS_QR_ENABLED_KEY), "false")));
        settings.setApiDocsQrTitle(defaultIfBlank(valueByKey.get(FLOATING_API_DOCS_QR_TITLE_KEY), ""));
        settings.setApiDocsQrImageUrl(defaultIfBlank(valueByKey.get(FLOATING_API_DOCS_QR_IMAGE_URL_KEY), ""));
        return settings;
    }

    private SystemVO.SmtpSettingsVO loadSmtpSettings() {
        Map<String, String> valueByKey = loadConfigValuesByKeys(SMTP_CONFIG_KEYS);
        return buildSmtpSettings(valueByKey);
    }

    private SystemVO.SmtpSettingsVO buildSmtpSettings(Map<String, String> valueByKey) {
        SystemVO.SmtpSettingsVO settings = new SystemVO.SmtpSettingsVO();
        settings.setEnabled(Boolean.parseBoolean(defaultIfBlank(valueByKey.get(SMTP_ENABLED_KEY), "true")));
        settings.setHost(defaultIfBlank(valueByKey.get(SMTP_HOST_KEY), ""));
        settings.setPort(parseInteger(valueByKey.get(SMTP_PORT_KEY), 25));
        settings.setUsername(defaultIfBlank(valueByKey.get(SMTP_USERNAME_KEY), ""));
        settings.setPassword("");
        settings.setPasswordConfigured(StringUtils.hasText(valueByKey.get(SMTP_PASSWORD_KEY)));
        settings.setFrom(defaultIfBlank(valueByKey.get(SMTP_FROM_KEY), ""));
        settings.setAuthEnabled(Boolean.parseBoolean(defaultIfBlank(valueByKey.get(SMTP_AUTH_ENABLED_KEY), "true")));
        settings.setStartTlsEnabled(Boolean.parseBoolean(defaultIfBlank(valueByKey.get(SMTP_STARTTLS_ENABLED_KEY), "true")));
        settings.setSslEnabled(Boolean.parseBoolean(defaultIfBlank(valueByKey.get(SMTP_SSL_ENABLED_KEY), "false")));
        settings.setConfigured(
                Boolean.TRUE.equals(settings.getEnabled())
                        && StringUtils.hasText(settings.getHost())
                        && settings.getPort() != null
                        && settings.getPort() > 0
                        && StringUtils.hasText(settings.getFrom())
        );
        return settings;
    }

    private SystemVO.WechatOfficialAccountSettingsVO loadWechatOfficialAccountSettings() {
        Map<String, String> valueByKey = loadConfigValuesByKeys(WECHAT_OFFICIAL_CONFIG_KEYS);
        return buildWechatOfficialAccountSettings(valueByKey);
    }

    private SystemVO.WechatOfficialAccountSettingsVO buildWechatOfficialAccountSettings(Map<String, String> valueByKey) {
        SystemVO.WechatOfficialAccountSettingsVO settings = new SystemVO.WechatOfficialAccountSettingsVO();
        settings.setEnabled(Boolean.parseBoolean(defaultIfBlank(valueByKey.get(WECHAT_OFFICIAL_ENABLED_KEY), "false")));
        settings.setAppId(defaultIfBlank(valueByKey.get(WECHAT_OFFICIAL_APP_ID_KEY), ""));
        settings.setAppSecret("");
        settings.setAppSecretConfigured(StringUtils.hasText(valueByKey.get(WECHAT_OFFICIAL_APP_SECRET_KEY)));
        settings.setTemplateId(defaultIfBlank(valueByKey.get(WECHAT_OFFICIAL_TEMPLATE_ID_KEY), ""));
        settings.setDetailUrl(defaultIfBlank(valueByKey.get(WECHAT_OFFICIAL_DETAIL_URL_KEY), ""));
        settings.setConfigured(
                Boolean.TRUE.equals(settings.getEnabled())
                        && StringUtils.hasText(settings.getAppId())
                        && Boolean.TRUE.equals(settings.getAppSecretConfigured())
                        && StringUtils.hasText(settings.getTemplateId())
        );
        return settings;
    }

    private Map<String, String> loadConfigValuesByKeys(List<String> keys) {
        return loadConfigValuesByKeys(keys, true);
    }

    private Map<String, String> loadConfigValuesByKeys(List<String> keys, boolean trimValues) {
        long version = loadRuntimeAppearanceVersion();
        String cacheKey = configSnapshotCacheKey(keys, trimValues, version);
        Map<String, String> cached = getCachedConfigSnapshot(cacheKey);
        if (cached != null) {
            if (ownerRuntimeMetrics != null) {
                ownerRuntimeMetrics.recordPlatformConfigCacheHit();
            }
            return new LinkedHashMap<>(cached);
        }
        if (ownerRuntimeMetrics != null) {
            ownerRuntimeMetrics.recordPlatformConfigCacheMiss();
        }
        return loadConfigValuesByKeysWithSingleFlight(cacheKey, keys, trimValues);
    }

    private Map<String, String> getCachedConfigSnapshot(String cacheKey) {
        Map<String, String> cached = configSnapshotCache.getIfPresent(cacheKey);
        return cached == null ? null : new LinkedHashMap<>(cached);
    }

    private void cacheConfigSnapshot(String cacheKey, Map<String, String> valueByKey) {
        configSnapshotCache.put(cacheKey, new LinkedHashMap<>(valueByKey));
    }

    private void markRuntimeAppearanceChanged(String eventKey) {
        configSnapshotCache.invalidateAll();
        configLoadInFlight.invalidateAll();
        runtimeAppearanceVersionCache.invalidate(RUNTIME_APPEARANCE_CACHE_KEY);
        runtimeAppearanceVersionLoadInFlight.invalidate(RUNTIME_APPEARANCE_CACHE_KEY);
        if (readModelVersionService != null) {
            readModelVersionService.bump(CONTEXT_PLATFORM, SCOPE_RUNTIME_APPEARANCE, eventKey);
        }
    }

    private void markPublicBootstrapChanged(String eventKey) {
        if (readModelVersionService != null) {
            readModelVersionService.bump(CONTEXT_PLATFORM, SCOPE_PUBLIC_BOOTSTRAP, eventKey);
        }
    }

    private Map<String, String> loadConfigValuesByKeysWithSingleFlight(
            String cacheKey,
            List<String> keys,
            boolean trimValues
    ) {
        try {
            CompletableFuture<Map<String, String>> inFlight = configLoadInFlight.get(
                    cacheKey,
                    () -> CompletableFuture.completedFuture(loadConfigValuesByKeysFromDatabase(cacheKey, keys, trimValues))
            );
            return inFlight.join();
        } catch (CompletionException exception) {
            configLoadInFlight.invalidate(cacheKey);
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Failed to load platform config snapshot", cause);
        } catch (ExecutionException exception) {
            configLoadInFlight.invalidate(cacheKey);
            throw new IllegalStateException("Failed to load platform config snapshot", exception);
        }
    }

    private Map<String, String> loadConfigValuesByKeysFromDatabase(
            String cacheKey,
            List<String> keys,
            boolean trimValues
    ) {
        Map<String, String> cached = getCachedConfigSnapshot(cacheKey);
        if (cached != null) {
            return new LinkedHashMap<>(cached);
        }

        String placeholders = keys.stream().map(item -> "?").collect(Collectors.joining(", "));
        String sql = """
                select config_key as configKey, config_value as configValue
                from sys_config
                where deleted = 0
                  and config_scope = 'PLATFORM'
                  and config_key in (%s)
                order by id desc
                """.formatted(placeholders);
        List<Object> params = new ArrayList<>(keys);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
        Map<String, String> valueByKey = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String configKey = String.valueOf(row.get("configKey"));
            if (!valueByKey.containsKey(configKey)) {
                String rawValue = normalizeConfigTextRaw(row.get("configValue"));
                String decryptedValue = decryptConfigValue(configKey, rawValue);
                valueByKey.put(configKey, trimValues ? normalizeConfigText(decryptedValue) : decryptedValue);
            }
        }
        cacheConfigSnapshot(cacheKey, valueByKey);
        return valueByKey;
    }

    private String configSnapshotCacheKey(List<String> keys, boolean trimValues, long version) {
        String joinedKeys = keys.stream().sorted().collect(Collectors.joining(","));
        return "global:" + version + ":" + trimValues + ":" + joinedKeys;
    }

    private long loadRuntimeAppearanceVersion() {
        Long cachedVersion = runtimeAppearanceVersionCache.getIfPresent(RUNTIME_APPEARANCE_CACHE_KEY);
        if (cachedVersion != null) {
            return cachedVersion;
        }
        if (readModelVersionService == null) {
            return 0L;
        }
        try {
            CompletableFuture<Long> inFlight = runtimeAppearanceVersionLoadInFlight.get(
                    RUNTIME_APPEARANCE_CACHE_KEY,
                    () -> CompletableFuture.completedFuture(
                            currentRuntimeAppearanceVersion()
                    )
            );
            long version = inFlight.join();
            runtimeAppearanceVersionCache.put(RUNTIME_APPEARANCE_CACHE_KEY, version);
            return version;
        } catch (CompletionException exception) {
            runtimeAppearanceVersionLoadInFlight.invalidate(RUNTIME_APPEARANCE_CACHE_KEY);
            Long fallback = runtimeAppearanceVersionCache.getIfPresent(RUNTIME_APPEARANCE_CACHE_KEY);
            if (fallback != null) {
                return fallback;
            }
            return 0L;
        } catch (ExecutionException exception) {
            runtimeAppearanceVersionLoadInFlight.invalidate(RUNTIME_APPEARANCE_CACHE_KEY);
            Long fallback = runtimeAppearanceVersionCache.getIfPresent(RUNTIME_APPEARANCE_CACHE_KEY);
            if (fallback != null) {
                return fallback;
            }
            return 0L;
        }
    }

    private long currentRuntimeAppearanceVersion() {
        Long version = readModelVersionService.currentVersion(CONTEXT_PLATFORM, SCOPE_RUNTIME_APPEARANCE);
        return version == null ? 0L : version;
    }

    private void upsertBrandingConfig(
            String configKey,
            String configName,
            String configValue,
            String remark,
            Long operatorId
    ) {
        Long existingId = queryConfigId(configKey);
        upsertConfigRecord(existingId, configKey, configName, configValue, remark, operatorId);
    }

    private void upsertPlatformConfig(
            String configKey,
            String configName,
            String configValue,
            String remark,
            Long operatorId
    ) {
        upsertBrandingConfig(configKey, configName, configValue, remark, operatorId);
    }

    private void upsertConfigValue(
            String configKey,
            String configName,
            String configValue,
            String remark,
            Long operatorId
    ) {
        Long existingId = queryConfigId(configKey);
        upsertConfigRecord(existingId, configKey, configName, configValue, remark, operatorId);
    }

    private void upsertConfigRecord(
            Long existingId,
            String configKey,
            String configName,
            String configValue,
            String remark,
            Long operatorId
    ) {
        String operatorUuid = resolveOperatorUuid(operatorId);
        if (existingId == null) {
            int inserted = jdbcTemplate.update(
                    """
                            insert into sys_config (
                                config_key, config_name, config_value, config_scope, is_system, remark,
                                created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                            ) values (?, ?, ?, 'PLATFORM', 0, ?, ?, ?, ?, 0)
                            """,
                    configKey,
                    configName,
                    encryptConfigValue(configKey, configValue),
                    remark,
                    operatorId,
                    operatorUuid,
                    operatorId,
                    operatorUuid
            );
            if (inserted != 1) {
                throw new BizException(ErrorCode.BIZ_ERROR, "Platform config changed, please retry");
            }
            return;
        }
        int updated = jdbcTemplate.update(
                """
                        update sys_config
                        set config_name = ?, config_value = ?, config_scope = 'PLATFORM', remark = ?,
                            updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ?
                          and config_key = ?
                          and config_scope = 'PLATFORM'
                          and is_system = 0
                          and deleted = 0
                        """,
                configName,
                encryptConfigValue(configKey, configValue),
                remark,
                operatorId,
                operatorUuid,
                LocalDateTime.now(),
                existingId,
                configKey
        );
        if (updated <= 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Platform config changed, please retry");
        }
    }

    private String resolveOperatorUuid(Long operatorId) {
        String operatorUuid;
        try {
            operatorUuid = jdbcTemplate.queryForObject(
                    "select uuid from sys_user where id = ? and status = 'ENABLED' and deleted = 0 limit 1",
                    String.class,
                    operatorId
            );
        } catch (EmptyResultDataAccessException exception) {
            operatorUuid = null;
        }
        if (!StringUtils.hasText(operatorUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted operator identity is required");
        }
        return operatorUuid.trim();
    }

    private Long queryConfigId(String configKey) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select id
                            from sys_config
                            where config_key = ?
                              and config_scope = 'PLATFORM'
                              and is_system = 0
                              and deleted = 0
                            order by id desc
                            limit 1
                            """,
                    Long.class,
                    configKey
            );
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private String encryptConfigValue(String configKey, String configValue) {
        return isSensitiveConfigKey(configKey) ? fieldCryptoService.encrypt(configValue) : configValue;
    }

    private String decryptConfigValue(String configKey, String configValue) {
        return isSensitiveConfigKey(configKey) ? fieldCryptoService.decrypt(configValue) : configValue;
    }

    private boolean isSensitiveConfigKey(String configKey) {
        if (!StringUtils.hasText(configKey)) {
            return false;
        }
        String normalized = configKey.trim().toLowerCase();
        return normalized.endsWith(".password")
                || normalized.endsWith(".secret")
                || normalized.endsWith(".app-secret")
                || normalized.endsWith(".access-key-secret")
                || normalized.endsWith(".private-key")
                || normalized.endsWith(".token")
                || normalized.endsWith(".credential");
    }

    private String sanitizeBrandingText(String value, String fallback) {
        String normalized = normalizeConfigText(value);
        return StringUtils.hasText(normalized) ? normalized : fallback;
    }

    private String sanitizeText(String value, String fallback) {
        String normalized = normalizeConfigText(value);
        return StringUtils.hasText(normalized) ? normalized : fallback;
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

    private String normalizeMarkdownText(String value) {
        return value == null ? "" : value.trim();
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

    private String normalizeConfigText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String normalizeConfigTextRaw(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Long requireAuthenticated(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "User context is required");
        }
        return currentUser.getUserId();
    }

    private Long requirePermission(CurrentUser currentUser, String permission) {
        CurrentUser runtimeUser = refreshTrustedCurrentUser(currentUser);
        Long userId = requireAuthenticated(runtimeUser);
        if (runtimeUser.getPermissions() == null
                || (!runtimeUser.getPermissions().contains("*") && !runtimeUser.getPermissions().contains(permission))) {
            throw new BizException(ErrorCode.FORBIDDEN, "缺少权限: " + permission);
        }
        return userId;
    }

    private CurrentUser refreshTrustedCurrentUser(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            return currentUser;
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
            return currentUser;
        }
        if (permissionSnapshotService == null) {
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user resolver is unavailable");
            }
            return currentUser;
        }
        Long userId = currentUser.getUserId();
        String normalizedUserUuid = StringUtils.hasText(currentUser.getUserUuid()) ? currentUser.getUserUuid().trim() : null;
        if (userId == null || userId <= 0 || !StringUtils.hasText(normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
        }
        if (systemInternalApi != null) {
            SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
            if (userSnapshot == null || userSnapshot.userId() == null || !userId.equals(userSnapshot.userId())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
            }
            if (!StringUtils.hasText(userSnapshot.userUuid())
                    || !normalizedUserUuid.equals(userSnapshot.userUuid().trim())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
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
            return currentUser;
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
        return currentUser;
    }

    private CurrentUser requireTrustedAuthenticatedCurrentUser(SessionAuthenticationService.AuthenticatedAccess authenticatedAccess) {
        CurrentUser refreshedUser = authenticatedAccess == null ? null : authenticatedAccess.currentUser();
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(refreshedUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
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
    private void requireRequest(Object request, String message) {
        if (request == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, message);
        }
    }

    private JavaMailSenderImpl buildSmtpSender(Map<String, String> values) {
        String host = defaultIfBlank(values.get(SMTP_HOST_KEY), "");
        Integer port = parseInteger(values.get(SMTP_PORT_KEY), 25);
        String username = defaultIfBlank(values.get(SMTP_USERNAME_KEY), "");
        String password = defaultIfBlank(values.get(SMTP_PASSWORD_KEY), "");
        if (!StringUtils.hasText(host)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "SMTP host must be configured");
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

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
