package com.lumira.saas.modules.system.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.FieldCryptoService;
import com.lumira.saas.infrastructure.readmodel.ReadModelVersionService;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.architecture.application.OwnerRuntimeMetrics;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.config.app.SystemConfigVersioningService;
import com.lumira.saas.modules.system.vo.SystemVO;
import com.lumira.saas.modules.system.support.SmtpMailService;
import org.springframework.beans.factory.annotation.Autowired;
import com.lumira.saas.modules.system.settings.repository.SystemPlatformSettingsRepository;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

@Service
@ConditionalOnLumiraControlPlaneEnabled
public class SystemPlatformSettingsAppService {
    private static final String STATUS_ENABLED = "ENABLED";

    private static final String CONTEXT_PLATFORM = "platform";
    private static final String SCOPE_RUNTIME_APPEARANCE = "runtime-appearance";
    private static final String SCOPE_PUBLIC_BOOTSTRAP = "public-bootstrap";

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
    private static final String BRANDING_MAINTENANCE_MODE_ENABLED_KEY = "branding.maintenance-mode-enabled";
    private static final String BRANDING_MAINTENANCE_TITLE_KEY = "branding.maintenance-title";
    private static final String BRANDING_MAINTENANCE_MESSAGE_KEY = "branding.maintenance-message";
    private static final String BRANDING_MAINTENANCE_END_AT_KEY = "branding.maintenance-end-at";
    private static final String GROUP_BRANDING = "BRANDING";

    private static final String AGREEMENT_USER_MARKDOWN_KEY = "agreement.user-agreement-markdown";
    private static final String AGREEMENT_PRIVACY_MARKDOWN_KEY = "agreement.privacy-agreement-markdown";
    private static final String GROUP_AGREEMENT = "AGREEMENT";

    private static final String SMTP_HOST_KEY = "smtp.host";
    private static final String SMTP_ENABLED_KEY = "smtp.enabled";
    private static final String SMTP_PORT_KEY = "smtp.port";
    private static final String SMTP_USERNAME_KEY = "smtp.username";
    private static final String SMTP_PASSWORD_KEY = "smtp.password";
    private static final String SMTP_FROM_KEY = "smtp.from";
    private static final String SMTP_AUTH_ENABLED_KEY = "smtp.auth-enabled";
    private static final String SMTP_STARTTLS_ENABLED_KEY = "smtp.starttls-enabled";
    private static final String SMTP_SSL_ENABLED_KEY = "smtp.ssl-enabled";
    private static final String SMTP_TEST_SUBJECT_KEY = "smtp.test-subject";
    private static final String SMTP_TEST_CONTENT_KEY = "smtp.test-content";
    private static final String SMTP_CONNECTION_TIMEOUT_KEY = "smtp.connection-timeout-ms";
    private static final String SMTP_READ_TIMEOUT_KEY = "smtp.read-timeout-ms";
    private static final String SMTP_WRITE_TIMEOUT_KEY = "smtp.write-timeout-ms";
    private static final String GROUP_SMTP = "SMTP";

    private static final String WECHAT_OFFICIAL_ENABLED_KEY = "notification.wechat-official.enabled";
    private static final String WECHAT_OFFICIAL_APP_ID_KEY = "notification.wechat-official.app-id";
    private static final String WECHAT_OFFICIAL_APP_SECRET_KEY = "notification.wechat-official.app-secret";
    private static final String WECHAT_OFFICIAL_TEMPLATE_ID_KEY = "notification.wechat-official.template-id";
    private static final String WECHAT_OFFICIAL_DETAIL_URL_KEY = "notification.wechat-official.detail-url";
    private static final String GROUP_WECHAT_OFFICIAL = "WECHAT_OFFICIAL";

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
    private static final String GROUP_WATERMARK = "WATERMARK";

    private static final String FLOATING_API_DOCS_QR_ENABLED_KEY = "floating-window.api-docs-qr-enabled";
    private static final String FLOATING_API_DOCS_QR_TITLE_KEY = "floating-window.api-docs-qr-title";
    private static final String FLOATING_API_DOCS_QR_IMAGE_URL_KEY = "floating-window.api-docs-qr-image-url";
    private static final String GROUP_FLOATING_WINDOW = "FLOATING_WINDOW";

    private final SystemPlatformSettingsRepository repository;
    private final OperationAuditService operationAuditService;
    private final FieldCryptoService fieldCryptoService;
    private final ReadModelVersionService readModelVersionService;
    private final OwnerRuntimeMetrics ownerRuntimeMetrics;
    private final SmtpMailService smtpMailService;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;
    private final boolean enforceTrustedUserResolution;
    private SystemConfigVersioningService configVersioningService;

    @Autowired
    public SystemPlatformSettingsAppService(
            SystemPlatformSettingsRepository repository,
            OperationAuditService operationAuditService,
            FieldCryptoService fieldCryptoService,
            ReadModelVersionService readModelVersionService,
            OwnerRuntimeMetrics ownerRuntimeMetrics,
            SmtpMailService smtpMailService,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(repository, operationAuditService, fieldCryptoService, readModelVersionService, ownerRuntimeMetrics, smtpMailService, permissionSnapshotService, systemInternalApi, sessionAuthenticationService, true);
    }

    private SystemPlatformSettingsAppService(
            SystemPlatformSettingsRepository repository,
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
        this.repository = repository;
        this.operationAuditService = operationAuditService;
        this.fieldCryptoService = fieldCryptoService;
        this.readModelVersionService = readModelVersionService;
        this.ownerRuntimeMetrics = ownerRuntimeMetrics;
        this.smtpMailService = smtpMailService;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    public SystemPlatformSettingsAppService(
            SystemPlatformSettingsRepository repository,
            OperationAuditService operationAuditService,
            FieldCryptoService fieldCryptoService,
            ReadModelVersionService readModelVersionService,
            OwnerRuntimeMetrics ownerRuntimeMetrics,
            SmtpMailService smtpMailService
    ) {
        this(repository,
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

    @Autowired
    public void setConfigVersioningService(SystemConfigVersioningService configVersioningService) {
        this.configVersioningService = configVersioningService;
    }

    private SystemConfigVersioningService.GovernanceSession beginGovernance(
            String group,
            Long expectedVersion,
            String reason,
            CurrentUser operator,
            List<String> keys
    ) {
        SystemConfigVersioningService governanceService = governanceServiceForWrite();
        if (governanceService == null) {
            return null;
        }
        return governanceService.begin(
                new SystemConfigVersioningService.ChangeRequest(
                        group,
                        SystemConfigVersioningService.DOMAIN_PLATFORM,
                        expectedVersion,
                        reason,
                        operator
                ),
                keys
        );
    }

    private void finishGovernance(SystemConfigVersioningService.GovernanceSession session) {
        if (session != null) {
            governanceServiceForWrite().finish(session);
        }
    }

    /**
     * Production writes must never silently bypass the immutable configuration
     * history boundary. Legacy constructors intentionally keep a null service
     * for isolated unit tests, hence the strict production flag.
     */
    private SystemConfigVersioningService governanceServiceForWrite() {
        if (configVersioningService == null && enforceTrustedUserResolution) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Configuration governance is unavailable; configuration was not changed");
        }
        return configVersioningService;
    }

    public SystemPlatformSettingsAppService(
            SystemPlatformSettingsRepository repository,
            OperationAuditService operationAuditService,
            FieldCryptoService fieldCryptoService,
            ReadModelVersionService readModelVersionService,
            OwnerRuntimeMetrics ownerRuntimeMetrics,
            SmtpMailService smtpMailService,
            PermissionSnapshotService permissionSnapshotService
    ) {
        this(repository,
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
        SystemConfigVersioningService.GovernanceSession configVersion = beginGovernance(GROUP_BRANDING, request.getExpectedConfigVersion(), request.getChangeReason(), currentUser, List.of(
                BRANDING_WEBSITE_NAME_KEY, BRANDING_WEBSITE_FAVICON_URL_KEY, BRANDING_WEBSITE_LOGO_URL_KEY,
                BRANDING_LOGIN_BACKGROUND_URL_KEY, BRANDING_GITHUB_LINK_ENABLED_KEY, BRANDING_GITHUB_LINK_URL_KEY,
                BRANDING_HELP_LINK_ENABLED_KEY, BRANDING_HELP_LINK_URL_KEY, BRANDING_COMPANY_NAME_KEY,
                BRANDING_COPYRIGHT_START_YEAR_KEY, BRANDING_FOOTER_ICP_KEY, BRANDING_FOOTER_POLICE_BEIAN_KEY,
                BRANDING_FOOTER_COPYRIGHT_KEY, BRANDING_MAINTENANCE_MODE_ENABLED_KEY, BRANDING_MAINTENANCE_TITLE_KEY,
                BRANDING_MAINTENANCE_MESSAGE_KEY, BRANDING_MAINTENANCE_END_AT_KEY
        ));
        upsertConfigValue(BRANDING_WEBSITE_NAME_KEY, websiteName, operatorId);
        upsertConfigValue(BRANDING_WEBSITE_FAVICON_URL_KEY, sanitizeBrandingText(request.getWebsiteFaviconUrl(), ""), operatorId);
        upsertConfigValue(BRANDING_WEBSITE_LOGO_URL_KEY, sanitizeBrandingText(request.getWebsiteLogoUrl(), ""), operatorId);
        upsertConfigValue(BRANDING_LOGIN_BACKGROUND_URL_KEY, sanitizeBrandingText(request.getLoginBackgroundUrl(), ""), operatorId);
        upsertConfigValue(BRANDING_GITHUB_LINK_ENABLED_KEY, String.valueOf(request.getGithubLinkEnabled() == null || request.getGithubLinkEnabled()), operatorId);
        upsertConfigValue(BRANDING_GITHUB_LINK_URL_KEY, sanitizeBrandingText(request.getGithubLinkUrl(), ""), operatorId);
        upsertConfigValue(BRANDING_HELP_LINK_ENABLED_KEY, String.valueOf(request.getHelpLinkEnabled() == null || request.getHelpLinkEnabled()), operatorId);
        upsertConfigValue(BRANDING_HELP_LINK_URL_KEY, sanitizeBrandingText(request.getHelpLinkUrl(), ""), operatorId);
        upsertConfigValue(BRANDING_COMPANY_NAME_KEY, companyName, operatorId);
        upsertConfigValue(BRANDING_COPYRIGHT_START_YEAR_KEY, String.valueOf(copyrightStartYear), operatorId);
        upsertConfigValue(BRANDING_FOOTER_ICP_KEY, sanitizeBrandingText(request.getFooterIcp(), ""), operatorId);
        upsertConfigValue(BRANDING_FOOTER_POLICE_BEIAN_KEY, sanitizeBrandingText(request.getFooterPoliceBeian(), ""), operatorId);
        String footerCopyright = sanitizeBrandingText(
                request.getFooterCopyright(),
                buildCopyrightText(companyName, copyrightStartYear)
        );
        upsertConfigValue(BRANDING_FOOTER_COPYRIGHT_KEY, footerCopyright, operatorId);
        upsertConfigValue(BRANDING_MAINTENANCE_MODE_ENABLED_KEY, String.valueOf(Boolean.TRUE.equals(request.getMaintenanceModeEnabled())), operatorId);
        upsertConfigValue(BRANDING_MAINTENANCE_TITLE_KEY, sanitizeBrandingText(request.getMaintenanceTitle(), "马上回来，精彩不掉线"), operatorId);
        upsertConfigValue(BRANDING_MAINTENANCE_MESSAGE_KEY, sanitizeBrandingText(request.getMaintenanceMessage(), "我们正在给系统做个小升级，报名入口很快就回来。请稍等片刻，精彩不会缺席。"), operatorId);
        upsertConfigValue(BRANDING_MAINTENANCE_END_AT_KEY, normalizeMaintenanceEndAt(request.getMaintenanceEndAt()), operatorId);
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
        String settingsEventKey = settingsEventKey("branding-update");
        markRuntimeAppearanceChanged(settingsEventKey);
        markPublicBootstrapChanged(settingsEventKey);
        finishGovernance(configVersion);
        return loadBrandingSettings();
    }

    @Transactional
    public SystemVO.AgreementSettingsVO updateAgreementSettings(CurrentUser currentUser, SystemDTO.AgreementSettingsRequest request) {
        Long operatorId = requirePermission(currentUser, "system:config:update");
        requireRequest(request, "Agreement settings request is required");
        SystemConfigVersioningService.GovernanceSession configVersion = beginGovernance(GROUP_AGREEMENT, request.getExpectedConfigVersion(), request.getChangeReason(), currentUser, List.of(
                AGREEMENT_USER_MARKDOWN_KEY, AGREEMENT_PRIVACY_MARKDOWN_KEY
        ));
        upsertConfigValue(AGREEMENT_USER_MARKDOWN_KEY, normalizeMarkdownText(request.getUserAgreementMarkdown()), operatorId);
        upsertConfigValue(AGREEMENT_PRIVACY_MARKDOWN_KEY, normalizeMarkdownText(request.getPrivacyAgreementMarkdown()), operatorId);
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
        String settingsEventKey = settingsEventKey("agreement-update");
        markRuntimeAppearanceChanged(settingsEventKey);
        markPublicBootstrapChanged(settingsEventKey);
        finishGovernance(configVersion);
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
        SystemConfigVersioningService.GovernanceSession configVersion = beginGovernance(GROUP_WATERMARK, request.getExpectedConfigVersion(), request.getChangeReason(), currentUser, List.of(
                WATERMARK_ENABLED_KEY, WATERMARK_MODE_KEY, WATERMARK_TEXT_LINES_KEY, WATERMARK_IMAGE_URL_KEY,
                WATERMARK_FONT_COLOR_KEY, WATERMARK_FONT_SIZE_KEY, WATERMARK_FONT_WEIGHT_KEY, WATERMARK_ROTATE_KEY,
                WATERMARK_GAP_X_KEY, WATERMARK_GAP_Y_KEY, WATERMARK_OFFSET_X_KEY, WATERMARK_OFFSET_Y_KEY,
                WATERMARK_Z_INDEX_KEY, WATERMARK_OPACITY_KEY
        ));
        Map<String, String> current = loadConfigValuesByGroup(GROUP_WATERMARK, true);
        upsertConfigValue(WATERMARK_ENABLED_KEY, request.getEnabled() == null ? settingValue(current, WATERMARK_ENABLED_KEY) : String.valueOf(request.getEnabled()), operatorId);
        upsertConfigValue(WATERMARK_MODE_KEY, defaultIfBlank(request.getMode(), settingValue(current, WATERMARK_MODE_KEY)), operatorId);
        upsertConfigValue(WATERMARK_TEXT_LINES_KEY, request.getTextLines() == null ? settingValue(current, WATERMARK_TEXT_LINES_KEY) : String.join("\\n", request.getTextLines()), operatorId);
        upsertConfigValue(WATERMARK_IMAGE_URL_KEY, request.getImageUrl() == null ? settingValue(current, WATERMARK_IMAGE_URL_KEY) : request.getImageUrl(), operatorId);
        upsertConfigValue(WATERMARK_FONT_COLOR_KEY, defaultIfBlank(request.getFontColor(), settingValue(current, WATERMARK_FONT_COLOR_KEY)), operatorId);
        upsertConfigValue(WATERMARK_FONT_SIZE_KEY, request.getFontSize() == null ? settingValue(current, WATERMARK_FONT_SIZE_KEY) : String.valueOf(request.getFontSize()), operatorId);
        upsertConfigValue(WATERMARK_FONT_WEIGHT_KEY, defaultIfBlank(request.getFontWeight(), settingValue(current, WATERMARK_FONT_WEIGHT_KEY)), operatorId);
        upsertConfigValue(WATERMARK_ROTATE_KEY, request.getRotate() == null ? settingValue(current, WATERMARK_ROTATE_KEY) : String.valueOf(request.getRotate()), operatorId);
        upsertConfigValue(WATERMARK_GAP_X_KEY, request.getGapX() == null ? settingValue(current, WATERMARK_GAP_X_KEY) : String.valueOf(request.getGapX()), operatorId);
        upsertConfigValue(WATERMARK_GAP_Y_KEY, request.getGapY() == null ? settingValue(current, WATERMARK_GAP_Y_KEY) : String.valueOf(request.getGapY()), operatorId);
        upsertConfigValue(WATERMARK_OFFSET_X_KEY, request.getOffsetX() == null ? settingValue(current, WATERMARK_OFFSET_X_KEY) : String.valueOf(request.getOffsetX()), operatorId);
        upsertConfigValue(WATERMARK_OFFSET_Y_KEY, request.getOffsetY() == null ? settingValue(current, WATERMARK_OFFSET_Y_KEY) : String.valueOf(request.getOffsetY()), operatorId);
        upsertConfigValue(WATERMARK_Z_INDEX_KEY, request.getZIndex() == null ? settingValue(current, WATERMARK_Z_INDEX_KEY) : String.valueOf(request.getZIndex()), operatorId);
        upsertConfigValue(WATERMARK_OPACITY_KEY, request.getOpacity() == null ? settingValue(current, WATERMARK_OPACITY_KEY) : String.valueOf(request.getOpacity()), operatorId);
        markRuntimeAppearanceChanged("watermark-update");
        finishGovernance(configVersion);
        return loadWatermarkSettings();
    }

    @Transactional
    public SystemVO.FloatingWindowSettingsVO updateFloatingWindowSettings(CurrentUser currentUser, SystemDTO.FloatingWindowSettingsRequest request) {
        Long operatorId = requirePermission(currentUser, "system:config:update");
        requireRequest(request, "Floating window settings request is required");
        SystemConfigVersioningService.GovernanceSession configVersion = beginGovernance(GROUP_FLOATING_WINDOW, request.getExpectedConfigVersion(), request.getChangeReason(), currentUser, List.of(
                FLOATING_API_DOCS_QR_ENABLED_KEY, FLOATING_API_DOCS_QR_TITLE_KEY, FLOATING_API_DOCS_QR_IMAGE_URL_KEY
        ));
        Map<String, String> current = loadConfigValuesByGroup(GROUP_FLOATING_WINDOW, true);
        upsertConfigValue(FLOATING_API_DOCS_QR_ENABLED_KEY, request.getApiDocsQrEnabled() == null ? settingValue(current, FLOATING_API_DOCS_QR_ENABLED_KEY) : String.valueOf(request.getApiDocsQrEnabled()), operatorId);
        upsertConfigValue(FLOATING_API_DOCS_QR_TITLE_KEY, request.getApiDocsQrTitle() == null ? settingValue(current, FLOATING_API_DOCS_QR_TITLE_KEY) : request.getApiDocsQrTitle(), operatorId);
        upsertConfigValue(FLOATING_API_DOCS_QR_IMAGE_URL_KEY, request.getApiDocsQrImageUrl() == null ? settingValue(current, FLOATING_API_DOCS_QR_IMAGE_URL_KEY) : request.getApiDocsQrImageUrl(), operatorId);
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
        finishGovernance(configVersion);
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
        SystemConfigVersioningService.GovernanceSession configVersion = beginGovernance(GROUP_SMTP, request.getExpectedConfigVersion(), request.getChangeReason(), currentUser, List.of(
                SMTP_ENABLED_KEY, SMTP_HOST_KEY, SMTP_PORT_KEY, SMTP_USERNAME_KEY, SMTP_PASSWORD_KEY, SMTP_FROM_KEY,
                SMTP_AUTH_ENABLED_KEY, SMTP_STARTTLS_ENABLED_KEY, SMTP_SSL_ENABLED_KEY, SMTP_TEST_SUBJECT_KEY,
                SMTP_TEST_CONTENT_KEY, SMTP_CONNECTION_TIMEOUT_KEY, SMTP_READ_TIMEOUT_KEY, SMTP_WRITE_TIMEOUT_KEY
        ));
        Map<String, String> currentValues = loadConfigValuesByGroup(GROUP_SMTP, true);
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

        upsertConfigValue(SMTP_ENABLED_KEY, String.valueOf(enabled), operatorId);
        upsertConfigValue(SMTP_HOST_KEY, host, operatorId);
        upsertConfigValue(SMTP_PORT_KEY, String.valueOf(port), operatorId);
        upsertConfigValue(SMTP_USERNAME_KEY, username, operatorId);
        upsertConfigValue(SMTP_PASSWORD_KEY, password, operatorId);
        upsertConfigValue(SMTP_FROM_KEY, from, operatorId);
        upsertConfigValue(SMTP_AUTH_ENABLED_KEY, String.valueOf(authEnabled), operatorId);
        upsertConfigValue(SMTP_STARTTLS_ENABLED_KEY, String.valueOf(startTlsEnabled), operatorId);
        upsertConfigValue(SMTP_SSL_ENABLED_KEY, String.valueOf(sslEnabled), operatorId);
        smtpMailService.invalidate();
        operationAuditService.log(operatorId, currentUser.getUserUuid(), currentUser.getUsername(), "smtp", "update", "UPDATE", "SUCCESS", "Update SMTP settings");
        currentValues.put(SMTP_ENABLED_KEY, String.valueOf(enabled));
        currentValues.put(SMTP_HOST_KEY, host);
        currentValues.put(SMTP_PORT_KEY, String.valueOf(port));
        currentValues.put(SMTP_USERNAME_KEY, username);
        currentValues.put(SMTP_PASSWORD_KEY, password);
        currentValues.put(SMTP_FROM_KEY, from);
        currentValues.put(SMTP_AUTH_ENABLED_KEY, String.valueOf(authEnabled));
        currentValues.put(SMTP_STARTTLS_ENABLED_KEY, String.valueOf(startTlsEnabled));
        currentValues.put(SMTP_SSL_ENABLED_KEY, String.valueOf(sslEnabled));
        markRuntimeAppearanceChanged("smtp-update");
        finishGovernance(configVersion);
        return buildSmtpSettings(currentValues);
    }

    @Transactional
    public SystemVO.SmtpSettingsVO resetSmtpSettings(CurrentUser currentUser) {
        Long operatorId = requirePermission(currentUser, "system:config:update");
        SystemConfigVersioningService.GovernanceSession configVersion = beginGovernance(GROUP_SMTP, null, null, currentUser, List.of(
                SMTP_ENABLED_KEY, SMTP_HOST_KEY, SMTP_PORT_KEY, SMTP_USERNAME_KEY, SMTP_PASSWORD_KEY, SMTP_FROM_KEY,
                SMTP_AUTH_ENABLED_KEY, SMTP_STARTTLS_ENABLED_KEY, SMTP_SSL_ENABLED_KEY, SMTP_TEST_SUBJECT_KEY,
                SMTP_TEST_CONTENT_KEY, SMTP_CONNECTION_TIMEOUT_KEY, SMTP_READ_TIMEOUT_KEY, SMTP_WRITE_TIMEOUT_KEY
        ));
        Map<String, String> resetValues = repository.findSettingResetValues(GROUP_SMTP);
        upsertConfigValue(SMTP_ENABLED_KEY, settingValue(resetValues, SMTP_ENABLED_KEY), operatorId);
        upsertConfigValue(SMTP_HOST_KEY, settingValue(resetValues, SMTP_HOST_KEY), operatorId);
        upsertConfigValue(SMTP_PORT_KEY, settingValue(resetValues, SMTP_PORT_KEY), operatorId);
        upsertConfigValue(SMTP_USERNAME_KEY, settingValue(resetValues, SMTP_USERNAME_KEY), operatorId);
        upsertConfigValue(SMTP_PASSWORD_KEY, settingValue(resetValues, SMTP_PASSWORD_KEY), operatorId);
        upsertConfigValue(SMTP_FROM_KEY, settingValue(resetValues, SMTP_FROM_KEY), operatorId);
        upsertConfigValue(SMTP_AUTH_ENABLED_KEY, settingValue(resetValues, SMTP_AUTH_ENABLED_KEY), operatorId);
        upsertConfigValue(SMTP_STARTTLS_ENABLED_KEY, settingValue(resetValues, SMTP_STARTTLS_ENABLED_KEY), operatorId);
        upsertConfigValue(SMTP_SSL_ENABLED_KEY, settingValue(resetValues, SMTP_SSL_ENABLED_KEY), operatorId);
        smtpMailService.invalidate();
        operationAuditService.log(operatorId, currentUser.getUserUuid(), currentUser.getUsername(), "smtp", "reset", "DELETE", "SUCCESS", "Reset SMTP settings");
        markRuntimeAppearanceChanged("smtp-reset");
        finishGovernance(configVersion);
        return buildSmtpSettings(resetValues);
    }

    @Transactional
    public SystemVO.WechatOfficialAccountSettingsVO updateWechatOfficialAccountSettings(CurrentUser currentUser, SystemDTO.WechatOfficialAccountSettingsRequest request) {
        Long operatorId = requirePermission(currentUser, "system:config:update");
        requireRequest(request, "Wechat official account settings request is required");
        SystemConfigVersioningService.GovernanceSession configVersion = beginGovernance(GROUP_WECHAT_OFFICIAL, request.getExpectedConfigVersion(), request.getChangeReason(), currentUser, List.of(
                WECHAT_OFFICIAL_ENABLED_KEY, WECHAT_OFFICIAL_APP_ID_KEY, WECHAT_OFFICIAL_APP_SECRET_KEY,
                WECHAT_OFFICIAL_TEMPLATE_ID_KEY, WECHAT_OFFICIAL_DETAIL_URL_KEY
        ));
        Map<String, String> currentValues = loadConfigValuesByGroup(GROUP_WECHAT_OFFICIAL, true);
        SystemVO.WechatOfficialAccountSettingsVO current = buildWechatOfficialAccountSettings(currentValues);
        boolean enabled = request.getEnabled() == null ? Boolean.TRUE.equals(current.getEnabled()) : Boolean.TRUE.equals(request.getEnabled());
        String appId = sanitizeText(request.getAppId(), current.getAppId());
        String existingSecret = defaultIfBlank(currentValues.get(WECHAT_OFFICIAL_APP_SECRET_KEY), "");
        String appSecret = StringUtils.hasText(request.getAppSecret()) ? request.getAppSecret().trim() : existingSecret;
        String templateId = sanitizeText(request.getTemplateId(), current.getTemplateId());
        String detailUrl = sanitizeText(request.getDetailUrl(), current.getDetailUrl());

        upsertConfigValue(WECHAT_OFFICIAL_ENABLED_KEY, String.valueOf(enabled), operatorId);
        upsertConfigValue(WECHAT_OFFICIAL_APP_ID_KEY, appId, operatorId);
        upsertConfigValue(WECHAT_OFFICIAL_APP_SECRET_KEY, appSecret, operatorId);
        upsertConfigValue(WECHAT_OFFICIAL_TEMPLATE_ID_KEY, templateId, operatorId);
        upsertConfigValue(WECHAT_OFFICIAL_DETAIL_URL_KEY, detailUrl, operatorId);
        operationAuditService.log(operatorId, currentUser.getUserUuid(), currentUser.getUsername(), "notification", "wechat-official-update", "UPDATE", "SUCCESS", "Update WeChat official account notification settings");
        currentValues.put(WECHAT_OFFICIAL_ENABLED_KEY, String.valueOf(enabled));
        currentValues.put(WECHAT_OFFICIAL_APP_ID_KEY, appId);
        currentValues.put(WECHAT_OFFICIAL_APP_SECRET_KEY, appSecret);
        currentValues.put(WECHAT_OFFICIAL_TEMPLATE_ID_KEY, templateId);
        currentValues.put(WECHAT_OFFICIAL_DETAIL_URL_KEY, detailUrl);
        markRuntimeAppearanceChanged("wechat-official-update");
        finishGovernance(configVersion);
        return buildWechatOfficialAccountSettings(currentValues);
    }

    @Transactional
    public SystemVO.SmtpTestVO testSmtpSettings(CurrentUser currentUser, SystemDTO.SmtpTestRequest request) {
        requirePermission(currentUser, "system:config:update");
        requireRequest(request, "SMTP test request is required");
        Map<String, String> values = loadConfigValuesByGroup(GROUP_SMTP, true);
        JavaMailSenderImpl mailSender = buildSmtpSender(values);
        String from = defaultIfBlank(values.get(SMTP_FROM_KEY), values.get(SMTP_USERNAME_KEY));
        if (!StringUtils.hasText(from)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "SMTP sender address is required");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(request.getToEmail());
        message.setFrom(from);
        message.setSubject(defaultIfBlank(request.getSubject(), settingValue(values, SMTP_TEST_SUBJECT_KEY)));
        message.setText(defaultIfBlank(request.getContent(), settingValue(values, SMTP_TEST_CONTENT_KEY)));
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
        Map<String, String> valueByKey = loadConfigValuesByGroup(GROUP_BRANDING, true);
        SystemVO.BrandingSettingsVO settings = new SystemVO.BrandingSettingsVO();
        settings.setWebsiteName(settingValue(valueByKey, BRANDING_WEBSITE_NAME_KEY));
        settings.setWebsiteFaviconUrl(settingValue(valueByKey, BRANDING_WEBSITE_FAVICON_URL_KEY));
        settings.setWebsiteLogoUrl(settingValue(valueByKey, BRANDING_WEBSITE_LOGO_URL_KEY));
        settings.setLoginBackgroundUrl(settingValue(valueByKey, BRANDING_LOGIN_BACKGROUND_URL_KEY));
        settings.setGithubLinkEnabled(Boolean.parseBoolean(settingValue(valueByKey, BRANDING_GITHUB_LINK_ENABLED_KEY)));
        settings.setGithubLinkUrl(settingValue(valueByKey, BRANDING_GITHUB_LINK_URL_KEY));
        settings.setHelpLinkEnabled(Boolean.parseBoolean(settingValue(valueByKey, BRANDING_HELP_LINK_ENABLED_KEY)));
        settings.setHelpLinkUrl(settingValue(valueByKey, BRANDING_HELP_LINK_URL_KEY));
        settings.setCompanyName(defaultIfBlank(valueByKey.get(BRANDING_COMPANY_NAME_KEY), settings.getWebsiteName()));
        settings.setCopyrightStartYear(parseInteger(valueByKey.get(BRANDING_COPYRIGHT_START_YEAR_KEY), LocalDate.now().getYear()));
        settings.setFooterIcp(settingValue(valueByKey, BRANDING_FOOTER_ICP_KEY));
        settings.setFooterPoliceBeian(settingValue(valueByKey, BRANDING_FOOTER_POLICE_BEIAN_KEY));
        settings.setFooterCopyright(defaultIfBlank(
                valueByKey.get(BRANDING_FOOTER_COPYRIGHT_KEY),
                buildCopyrightText(settings.getCompanyName(), settings.getCopyrightStartYear())
        ));
        settings.setMaintenanceModeEnabled(Boolean.parseBoolean(settingValue(valueByKey, BRANDING_MAINTENANCE_MODE_ENABLED_KEY)));
        settings.setMaintenanceTitle(defaultIfBlank(valueByKey.get(BRANDING_MAINTENANCE_TITLE_KEY), "马上回来，精彩不掉线"));
        settings.setMaintenanceMessage(defaultIfBlank(valueByKey.get(BRANDING_MAINTENANCE_MESSAGE_KEY), "我们正在给系统做个小升级，报名入口很快就回来。请稍等片刻，精彩不会缺席。"));
        settings.setMaintenanceEndAt(normalizeMaintenanceEndAt(valueByKey.get(BRANDING_MAINTENANCE_END_AT_KEY)));
        return settings;
    }

    private SystemVO.AgreementSettingsVO loadAgreementSettings() {
        Map<String, String> valueByKey = loadConfigValuesByGroup(GROUP_AGREEMENT, false);
        SystemVO.AgreementSettingsVO settings = new SystemVO.AgreementSettingsVO();
        settings.setUserAgreementMarkdown(settingValue(valueByKey, AGREEMENT_USER_MARKDOWN_KEY));
        settings.setPrivacyAgreementMarkdown(settingValue(valueByKey, AGREEMENT_PRIVACY_MARKDOWN_KEY));
        return settings;
    }

    private SystemVO.WatermarkSettingsVO loadWatermarkSettings() {
        Map<String, String> valueByKey = loadConfigValuesByGroup(GROUP_WATERMARK, true);
        SystemVO.WatermarkSettingsVO settings = new SystemVO.WatermarkSettingsVO();
        settings.setEnabled(Boolean.parseBoolean(settingValue(valueByKey, WATERMARK_ENABLED_KEY)));
        settings.setMode(settingValue(valueByKey, WATERMARK_MODE_KEY));
        settings.setTextLines(parseWatermarkTextLines(valueByKey.get(WATERMARK_TEXT_LINES_KEY)));
        settings.setImageUrl(settingValue(valueByKey, WATERMARK_IMAGE_URL_KEY));
        settings.setFontColor(settingValue(valueByKey, WATERMARK_FONT_COLOR_KEY));
        settings.setFontSize(Integer.parseInt(settingValue(valueByKey, WATERMARK_FONT_SIZE_KEY)));
        settings.setFontWeight(settingValue(valueByKey, WATERMARK_FONT_WEIGHT_KEY));
        settings.setRotate(Integer.parseInt(settingValue(valueByKey, WATERMARK_ROTATE_KEY)));
        settings.setGapX(Integer.parseInt(settingValue(valueByKey, WATERMARK_GAP_X_KEY)));
        settings.setGapY(Integer.parseInt(settingValue(valueByKey, WATERMARK_GAP_Y_KEY)));
        settings.setOffsetX(Integer.parseInt(settingValue(valueByKey, WATERMARK_OFFSET_X_KEY)));
        settings.setOffsetY(Integer.parseInt(settingValue(valueByKey, WATERMARK_OFFSET_Y_KEY)));
        settings.setZIndex(Integer.parseInt(settingValue(valueByKey, WATERMARK_Z_INDEX_KEY)));
        settings.setOpacity(Double.parseDouble(settingValue(valueByKey, WATERMARK_OPACITY_KEY)));
        return settings;
    }

    private SystemVO.FloatingWindowSettingsVO loadFloatingWindowSettings() {
        Map<String, String> valueByKey = loadConfigValuesByGroup(GROUP_FLOATING_WINDOW, true);
        SystemVO.FloatingWindowSettingsVO settings = new SystemVO.FloatingWindowSettingsVO();
        settings.setApiDocsQrEnabled(Boolean.parseBoolean(settingValue(valueByKey, FLOATING_API_DOCS_QR_ENABLED_KEY)));
        settings.setApiDocsQrTitle(settingValue(valueByKey, FLOATING_API_DOCS_QR_TITLE_KEY));
        settings.setApiDocsQrImageUrl(settingValue(valueByKey, FLOATING_API_DOCS_QR_IMAGE_URL_KEY));
        return settings;
    }

    private SystemVO.SmtpSettingsVO loadSmtpSettings() {
        Map<String, String> valueByKey = loadConfigValuesByGroup(GROUP_SMTP, true);
        return buildSmtpSettings(valueByKey);
    }

    private SystemVO.SmtpSettingsVO buildSmtpSettings(Map<String, String> valueByKey) {
        SystemVO.SmtpSettingsVO settings = new SystemVO.SmtpSettingsVO();
        settings.setEnabled(Boolean.parseBoolean(settingValue(valueByKey, SMTP_ENABLED_KEY)));
        settings.setHost(settingValue(valueByKey, SMTP_HOST_KEY));
        settings.setPort(Integer.parseInt(settingValue(valueByKey, SMTP_PORT_KEY)));
        settings.setUsername(settingValue(valueByKey, SMTP_USERNAME_KEY));
        settings.setPassword("");
        settings.setPasswordConfigured(StringUtils.hasText(valueByKey.get(SMTP_PASSWORD_KEY)));
        settings.setFrom(settingValue(valueByKey, SMTP_FROM_KEY));
        settings.setAuthEnabled(Boolean.parseBoolean(settingValue(valueByKey, SMTP_AUTH_ENABLED_KEY)));
        settings.setStartTlsEnabled(Boolean.parseBoolean(settingValue(valueByKey, SMTP_STARTTLS_ENABLED_KEY)));
        settings.setSslEnabled(Boolean.parseBoolean(settingValue(valueByKey, SMTP_SSL_ENABLED_KEY)));
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
        Map<String, String> valueByKey = loadConfigValuesByGroup(GROUP_WECHAT_OFFICIAL, true);
        return buildWechatOfficialAccountSettings(valueByKey);
    }

    private SystemVO.WechatOfficialAccountSettingsVO buildWechatOfficialAccountSettings(Map<String, String> valueByKey) {
        SystemVO.WechatOfficialAccountSettingsVO settings = new SystemVO.WechatOfficialAccountSettingsVO();
        settings.setEnabled(Boolean.parseBoolean(settingValue(valueByKey, WECHAT_OFFICIAL_ENABLED_KEY)));
        settings.setAppId(settingValue(valueByKey, WECHAT_OFFICIAL_APP_ID_KEY));
        settings.setAppSecret("");
        settings.setAppSecretConfigured(StringUtils.hasText(valueByKey.get(WECHAT_OFFICIAL_APP_SECRET_KEY)));
        settings.setTemplateId(settingValue(valueByKey, WECHAT_OFFICIAL_TEMPLATE_ID_KEY));
        settings.setDetailUrl(settingValue(valueByKey, WECHAT_OFFICIAL_DETAIL_URL_KEY));
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
        return loadConfigValuesByKeysFromDatabase(keys, trimValues);
    }

    private Map<String, String> loadConfigValuesByGroup(String groupCode, boolean trimValues) {
        Map<String, String> valueByKey = new LinkedHashMap<>();
        repository.findEffectiveSettingValues(groupCode).forEach((configKey, value) -> {
            String decryptedValue = decryptConfigValue(configKey, value);
            valueByKey.put(configKey, trimValues ? normalizeConfigText(decryptedValue) : decryptedValue);
        });
        if (valueByKey.isEmpty()) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "Platform setting group is not configured: " + groupCode);
        }
        return valueByKey;
    }

    private void markRuntimeAppearanceChanged(String eventKey) {
        if (readModelVersionService != null) {
            readModelVersionService.bump(CONTEXT_PLATFORM, SCOPE_RUNTIME_APPEARANCE, eventKey);
        }
    }

    private void markPublicBootstrapChanged(String eventKey) {
        if (readModelVersionService != null) {
            readModelVersionService.bump(CONTEXT_PLATFORM, SCOPE_PUBLIC_BOOTSTRAP, eventKey);
        }
    }

    private String settingsEventKey(String action) {
        return action + ":" + UUID.randomUUID();
    }

    private Map<String, String> loadConfigValuesByKeysFromDatabase(
            List<String> keys,
            boolean trimValues
    ) {
        Map<String, String> rows = repository.findPlatformConfigValues(keys);
        Map<String, String> valueByKey = new LinkedHashMap<>();
        rows.forEach((configKey, value) -> {
            String decryptedValue = decryptConfigValue(configKey, value);
            valueByKey.put(configKey, trimValues ? normalizeConfigText(decryptedValue) : decryptedValue);
        });
        return valueByKey;
    }

    private void upsertConfigValue(
            String configKey,
            String configValue,
            Long operatorId
    ) {
        String operatorUuid = resolveOperatorUuid(operatorId);
        int updated = repository.upsertPlatformConfig(configKey,
                encryptConfigValue(configKey, configValue), operatorId, operatorUuid);
        if (updated <= 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Platform config changed, please retry");
        }
    }

    private String resolveOperatorUuid(Long operatorId) {
        String operatorUuid = repository.findEnabledUserUuid(operatorId);
        if (!StringUtils.hasText(operatorUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted operator identity is required");
        }
        return operatorUuid.trim();
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

    private String normalizeMaintenanceEndAt(String value) {
        String normalized = normalizeConfigText(value);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        try {
            return Instant.parse(normalized).toString();
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(normalized).toInstant().toString();
            } catch (DateTimeParseException invalid) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "Maintenance end time must be a valid ISO-8601 timestamp");
            }
        }
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
        String host = settingValue(values, SMTP_HOST_KEY);
        Integer port = Integer.parseInt(settingValue(values, SMTP_PORT_KEY));
        String username = settingValue(values, SMTP_USERNAME_KEY);
        String password = settingValue(values, SMTP_PASSWORD_KEY);
        if (!StringUtils.hasText(host)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "SMTP host must be configured");
        }
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(username);
        sender.setPassword(password);
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", settingValue(values, SMTP_AUTH_ENABLED_KEY));
        properties.put("mail.smtp.starttls.enable", settingValue(values, SMTP_STARTTLS_ENABLED_KEY));
        properties.put("mail.smtp.ssl.enable", settingValue(values, SMTP_SSL_ENABLED_KEY));
        properties.put("mail.smtp.connectiontimeout", settingValue(values, SMTP_CONNECTION_TIMEOUT_KEY));
        properties.put("mail.smtp.timeout", settingValue(values, SMTP_READ_TIMEOUT_KEY));
        properties.put("mail.smtp.writetimeout", settingValue(values, SMTP_WRITE_TIMEOUT_KEY));
        return sender;
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String settingValue(Map<String, String> values, String configKey) {
        if (values == null || !values.containsKey(configKey)) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "Platform setting definition is missing: " + configKey);
        }
        return values.get(configKey) == null ? "" : values.get(configKey);
    }
}
