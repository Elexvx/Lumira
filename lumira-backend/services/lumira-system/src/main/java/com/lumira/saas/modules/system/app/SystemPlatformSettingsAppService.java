package com.lumira.saas.modules.system.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.FieldCryptoService;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.lumira.saas.infrastructure.readmodel.ReadModelVersionService;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class SystemPlatformSettingsAppService {

    private static final Long DEFAULT_PUBLIC_TENANT_ID = com.lumira.common.constant.PlatformConstants.PLATFORM_TENANT_ID;
    private static final String CONTEXT_PLATFORM = "platform";
    private static final String SCOPE_RUNTIME_APPEARANCE = "runtime-appearance";
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
    private final Cache<String, Map<String, String>> configSnapshotCache;
    private final Cache<String, CompletableFuture<Map<String, String>>> configLoadInFlight;
    private final Cache<Long, Long> runtimeAppearanceVersionCache;
    private final Cache<Long, CompletableFuture<Long>> runtimeAppearanceVersionLoadInFlight;

    @Autowired
    public SystemPlatformSettingsAppService(
            MyBatisQueryOperations jdbcTemplate,
            OperationAuditService operationAuditService,
            FieldCryptoService fieldCryptoService,
            ReadModelVersionService readModelVersionService,
            OwnerRuntimeMetrics ownerRuntimeMetrics,
            SmtpMailService smtpMailService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.operationAuditService = operationAuditService;
        this.fieldCryptoService = fieldCryptoService;
        this.readModelVersionService = readModelVersionService;
        this.ownerRuntimeMetrics = ownerRuntimeMetrics;
        this.smtpMailService = smtpMailService;
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

    public SystemVO.BrandingSettingsVO getBrandingSettings(CurrentUser currentUser) {
        return loadBrandingSettings(currentTenantId(currentUser));
    }

    public SystemVO.BrandingSettingsVO getPublicBrandingSettings(Long preferredTenantId) {
        Long tenantId = preferredTenantId == null ? DEFAULT_PUBLIC_TENANT_ID : preferredTenantId;
        return loadBrandingSettings(tenantId);
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
        upsertBrandingConfig(tenantId, BRANDING_WEBSITE_NAME_KEY, "站点名称", websiteName, "控制台顶部与浏览器标题展示名称", operatorId);
        upsertBrandingConfig(tenantId, BRANDING_WEBSITE_FAVICON_URL_KEY, "站点图标地址", sanitizeBrandingText(request.getWebsiteFaviconUrl(), ""), "浏览器标签页 icon 地址", operatorId);
        upsertBrandingConfig(tenantId, BRANDING_WEBSITE_LOGO_URL_KEY, "站点 Logo 地址", sanitizeBrandingText(request.getWebsiteLogoUrl(), ""), "控制台左上角品牌 Logo 地址", operatorId);
        upsertBrandingConfig(tenantId, BRANDING_LOGIN_BACKGROUND_URL_KEY, "登录页背景图地址", sanitizeBrandingText(request.getLoginBackgroundUrl(), ""), "登录页背景图地址", operatorId);
        upsertBrandingConfig(tenantId, BRANDING_GITHUB_LINK_ENABLED_KEY, "GitHub 链接开关", String.valueOf(request.getGithubLinkEnabled() == null || request.getGithubLinkEnabled()), "是否显示顶部 GitHub 图标", operatorId);
        upsertBrandingConfig(tenantId, BRANDING_GITHUB_LINK_URL_KEY, "GitHub 链接", sanitizeBrandingText(request.getGithubLinkUrl(), ""), "顶部 GitHub 图标跳转地址", operatorId);
        upsertBrandingConfig(tenantId, BRANDING_HELP_LINK_ENABLED_KEY, "帮助链接开关", String.valueOf(request.getHelpLinkEnabled() == null || request.getHelpLinkEnabled()), "是否显示顶部帮助图标", operatorId);
        upsertBrandingConfig(tenantId, BRANDING_HELP_LINK_URL_KEY, "帮助链接", sanitizeBrandingText(request.getHelpLinkUrl(), ""), "顶部帮助图标跳转地址", operatorId);
        upsertBrandingConfig(tenantId, BRANDING_COMPANY_NAME_KEY, "公司名称", companyName, "页脚版权主体名称", operatorId);
        upsertBrandingConfig(tenantId, BRANDING_COPYRIGHT_START_YEAR_KEY, "版权起始年份", String.valueOf(copyrightStartYear), "页脚版权起始年份", operatorId);
        upsertBrandingConfig(tenantId, BRANDING_FOOTER_ICP_KEY, "页脚 ICP 备案", sanitizeBrandingText(request.getFooterIcp(), ""), "页脚备案信息", operatorId);
        upsertBrandingConfig(tenantId, BRANDING_FOOTER_POLICE_BEIAN_KEY, "页脚公安备案", sanitizeBrandingText(request.getFooterPoliceBeian(), ""), "页脚公安备案信息", operatorId);
        upsertBrandingConfig(tenantId, BRANDING_FOOTER_COPYRIGHT_KEY, "页脚版权声明", buildCopyrightText(companyName, copyrightStartYear), "页脚版权声明（由公司名称和起始年份生成）", operatorId);
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
        upsertConfigValue(tenantId, AGREEMENT_USER_MARKDOWN_KEY, "用户协议", normalizeMarkdownText(request.getUserAgreementMarkdown()), "用户协议 Markdown", operatorId);
        upsertConfigValue(tenantId, AGREEMENT_PRIVACY_MARKDOWN_KEY, "隐私协议", normalizeMarkdownText(request.getPrivacyAgreementMarkdown()), "隐私协议 Markdown", operatorId);
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

    public SystemVO.FloatingWindowSettingsVO getFloatingWindowSettings(CurrentUser currentUser) {
        return loadFloatingWindowSettings(currentTenantId(currentUser));
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

    @Transactional
    public SystemVO.FloatingWindowSettingsVO updateFloatingWindowSettings(CurrentUser currentUser, SystemDTO.FloatingWindowSettingsRequest request) {
        Long tenantId = currentTenantId(currentUser);
        Long operatorId = currentUser.getUserId();
        upsertBrandingConfig(tenantId, FLOATING_API_DOCS_QR_ENABLED_KEY, "接口文档二维码开关", String.valueOf(request.getApiDocsQrEnabled() == null || request.getApiDocsQrEnabled()), "是否在全局悬浮窗展示接口文档二维码入口", operatorId);
        upsertBrandingConfig(tenantId, FLOATING_API_DOCS_QR_TITLE_KEY, "接口文档二维码标题", defaultIfBlank(request.getApiDocsQrTitle(), "微信扫码联系我们"), "接口文档二维码弹层标题", operatorId);
        upsertBrandingConfig(tenantId, FLOATING_API_DOCS_QR_IMAGE_URL_KEY, "接口文档二维码图片", defaultIfBlank(request.getApiDocsQrImageUrl(), ""), "接口文档悬浮入口展开后展示的二维码图片", operatorId);
        operationAuditService.log(
                tenantId,
                currentUser.getUserId(),
                currentUser.getUsername(),
                "system",
                "floating-window-update",
                "UPDATE",
                "SUCCESS",
                "更新悬浮窗设置"
        );
        return loadFloatingWindowSettings(tenantId);
    }

    public SystemVO.SmtpSettingsVO getSmtpSettings(CurrentUser currentUser) {
        return loadSmtpSettings(currentTenantId(currentUser));
    }

    public SystemVO.WechatOfficialAccountSettingsVO getWechatOfficialAccountSettings(CurrentUser currentUser) {
        return loadWechatOfficialAccountSettings(currentTenantId(currentUser));
    }

    @Transactional
    public SystemVO.SmtpSettingsVO updateSmtpSettings(CurrentUser currentUser, SystemDTO.SmtpSettingsRequest request) {
        Long tenantId = currentTenantId(currentUser);
        Long operatorId = currentUser.getUserId();
        Map<String, String> currentValues = loadConfigValuesByKeys(tenantId, SMTP_CONFIG_KEYS);
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

        upsertPlatformConfig(tenantId, SMTP_ENABLED_KEY, "SMTP 邮箱通知启用", String.valueOf(enabled), "是否启用邮箱通知渠道", operatorId);
        upsertPlatformConfig(tenantId, SMTP_HOST_KEY, "SMTP 主机", host, "邮件服务器地址", operatorId);
        upsertPlatformConfig(tenantId, SMTP_PORT_KEY, "SMTP 端口", String.valueOf(port == null ? 25 : port), "邮件服务器端口", operatorId);
        upsertPlatformConfig(tenantId, SMTP_USERNAME_KEY, "SMTP 用户名", username, "SMTP 登录用户名", operatorId);
        upsertPlatformConfig(tenantId, SMTP_PASSWORD_KEY, "SMTP 密码", password, "SMTP 登录密码", operatorId);
        upsertPlatformConfig(tenantId, SMTP_FROM_KEY, "发件人地址", from, "SMTP 默认发件人", operatorId);
        upsertPlatformConfig(tenantId, SMTP_AUTH_ENABLED_KEY, "SMTP 认证", String.valueOf(authEnabled), "是否启用 SMTP AUTH", operatorId);
        upsertPlatformConfig(tenantId, SMTP_STARTTLS_ENABLED_KEY, "SMTP STARTTLS", String.valueOf(startTlsEnabled), "是否启用 STARTTLS", operatorId);
        upsertPlatformConfig(tenantId, SMTP_SSL_ENABLED_KEY, "SMTP SSL", String.valueOf(sslEnabled), "是否启用 SSL", operatorId);
        smtpMailService.invalidateTenant(tenantId);
        operationAuditService.log(tenantId, operatorId, currentUser.getUsername(), "smtp", "update", "UPDATE", "SUCCESS", "更新 SMTP 配置");
        currentValues.put(SMTP_ENABLED_KEY, String.valueOf(enabled));
        currentValues.put(SMTP_HOST_KEY, host);
        currentValues.put(SMTP_PORT_KEY, String.valueOf(port == null ? 25 : port));
        currentValues.put(SMTP_USERNAME_KEY, username);
        currentValues.put(SMTP_PASSWORD_KEY, password);
        currentValues.put(SMTP_FROM_KEY, from);
        currentValues.put(SMTP_AUTH_ENABLED_KEY, String.valueOf(authEnabled));
        currentValues.put(SMTP_STARTTLS_ENABLED_KEY, String.valueOf(startTlsEnabled));
        currentValues.put(SMTP_SSL_ENABLED_KEY, String.valueOf(sslEnabled));
        return buildSmtpSettings(currentValues);
    }

    @Transactional
    public SystemVO.SmtpSettingsVO resetSmtpSettings(CurrentUser currentUser) {
        Long tenantId = currentTenantId(currentUser);
        Long operatorId = currentUser.getUserId();
        upsertPlatformConfig(tenantId, SMTP_ENABLED_KEY, "SMTP 邮箱通知启用", "false", "是否启用邮箱通知渠道", operatorId);
        upsertPlatformConfig(tenantId, SMTP_HOST_KEY, "SMTP 主机", "", "邮件服务器地址", operatorId);
        upsertPlatformConfig(tenantId, SMTP_PORT_KEY, "SMTP 端口", "25", "邮件服务器端口", operatorId);
        upsertPlatformConfig(tenantId, SMTP_USERNAME_KEY, "SMTP 用户名", "", "SMTP 登录用户名", operatorId);
        upsertPlatformConfig(tenantId, SMTP_PASSWORD_KEY, "SMTP 密码", "", "SMTP 登录密码", operatorId);
        upsertPlatformConfig(tenantId, SMTP_FROM_KEY, "发件人地址", "", "SMTP 默认发件人", operatorId);
        upsertPlatformConfig(tenantId, SMTP_AUTH_ENABLED_KEY, "SMTP 认证", "true", "是否启用 SMTP AUTH", operatorId);
        upsertPlatformConfig(tenantId, SMTP_STARTTLS_ENABLED_KEY, "SMTP STARTTLS", "true", "是否启用 STARTTLS", operatorId);
        upsertPlatformConfig(tenantId, SMTP_SSL_ENABLED_KEY, "SMTP SSL", "false", "是否启用 SSL", operatorId);
        smtpMailService.invalidateTenant(tenantId);
        operationAuditService.log(tenantId, operatorId, currentUser.getUsername(), "smtp", "reset", "DELETE", "SUCCESS", "重置 SMTP 配置");
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
        Long tenantId = currentTenantId(currentUser);
        Long operatorId = currentUser.getUserId();
        Map<String, String> currentValues = loadConfigValuesByKeys(tenantId, WECHAT_OFFICIAL_CONFIG_KEYS);
        SystemVO.WechatOfficialAccountSettingsVO current = buildWechatOfficialAccountSettings(currentValues);
        boolean enabled = request.getEnabled() == null ? Boolean.TRUE.equals(current.getEnabled()) : Boolean.TRUE.equals(request.getEnabled());
        String appId = sanitizeText(request.getAppId(), current.getAppId());
        String existingSecret = defaultIfBlank(currentValues.get(WECHAT_OFFICIAL_APP_SECRET_KEY), "");
        String appSecret = StringUtils.hasText(request.getAppSecret()) ? request.getAppSecret().trim() : existingSecret;
        String templateId = sanitizeText(request.getTemplateId(), current.getTemplateId());
        String detailUrl = sanitizeText(request.getDetailUrl(), current.getDetailUrl());

        upsertPlatformConfig(tenantId, WECHAT_OFFICIAL_ENABLED_KEY, "微信公众号通知启用", String.valueOf(enabled), "是否启用微信公众号/服务号模板消息通知", operatorId);
        upsertPlatformConfig(tenantId, WECHAT_OFFICIAL_APP_ID_KEY, "微信公众号 AppID", appId, "微信公众号或服务号 AppID", operatorId);
        upsertPlatformConfig(tenantId, WECHAT_OFFICIAL_APP_SECRET_KEY, "微信公众号 AppSecret", appSecret, "微信公众号或服务号 AppSecret", operatorId);
        upsertPlatformConfig(tenantId, WECHAT_OFFICIAL_TEMPLATE_ID_KEY, "微信公众号模板 ID", templateId, "用于系统通知的公众号模板消息 ID", operatorId);
        upsertPlatformConfig(tenantId, WECHAT_OFFICIAL_DETAIL_URL_KEY, "微信公众号通知详情链接", detailUrl, "模板消息点击后打开的系统链接，可留空", operatorId);
        operationAuditService.log(tenantId, operatorId, currentUser.getUsername(), "notification", "wechat-official-update", "UPDATE", "SUCCESS", "更新微信公众号通知配置");
        currentValues.put(WECHAT_OFFICIAL_ENABLED_KEY, String.valueOf(enabled));
        currentValues.put(WECHAT_OFFICIAL_APP_ID_KEY, appId);
        currentValues.put(WECHAT_OFFICIAL_APP_SECRET_KEY, appSecret);
        currentValues.put(WECHAT_OFFICIAL_TEMPLATE_ID_KEY, templateId);
        currentValues.put(WECHAT_OFFICIAL_DETAIL_URL_KEY, detailUrl);
        return buildWechatOfficialAccountSettings(currentValues);
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

    private SystemVO.BrandingSettingsVO loadBrandingSettings(Long tenantId) {
        Map<String, String> valueByKey = loadConfigValuesByKeys(tenantId, BRANDING_CONFIG_KEYS);
        SystemVO.BrandingSettingsVO settings = new SystemVO.BrandingSettingsVO();
        settings.setWebsiteName(defaultIfBlank(valueByKey.get(BRANDING_WEBSITE_NAME_KEY), "宏翔商道"));
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

    private SystemVO.AgreementSettingsVO loadAgreementSettings(Long tenantId) {
        Map<String, String> valueByKey = loadConfigValuesByKeys(tenantId, AGREEMENT_CONFIG_KEYS, false);
        SystemVO.AgreementSettingsVO settings = new SystemVO.AgreementSettingsVO();
        settings.setUserAgreementMarkdown(defaultIfBlank(valueByKey.get(AGREEMENT_USER_MARKDOWN_KEY), ""));
        settings.setPrivacyAgreementMarkdown(defaultIfBlank(valueByKey.get(AGREEMENT_PRIVACY_MARKDOWN_KEY), ""));
        return settings;
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

    private SystemVO.FloatingWindowSettingsVO loadFloatingWindowSettings(Long tenantId) {
        Map<String, String> valueByKey = loadConfigValuesByKeys(tenantId, FLOATING_WINDOW_CONFIG_KEYS);
        SystemVO.FloatingWindowSettingsVO settings = new SystemVO.FloatingWindowSettingsVO();
        settings.setApiDocsQrEnabled(Boolean.parseBoolean(defaultIfBlank(valueByKey.get(FLOATING_API_DOCS_QR_ENABLED_KEY), "true")));
        settings.setApiDocsQrTitle(defaultIfBlank(valueByKey.get(FLOATING_API_DOCS_QR_TITLE_KEY), "微信扫码联系我们"));
        settings.setApiDocsQrImageUrl(defaultIfBlank(valueByKey.get(FLOATING_API_DOCS_QR_IMAGE_URL_KEY), ""));
        return settings;
    }

    private SystemVO.SmtpSettingsVO loadSmtpSettings(Long tenantId) {
        Map<String, String> valueByKey = loadConfigValuesByKeys(tenantId, SMTP_CONFIG_KEYS);
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

    private SystemVO.WechatOfficialAccountSettingsVO loadWechatOfficialAccountSettings(Long tenantId) {
        Map<String, String> valueByKey = loadConfigValuesByKeys(tenantId, WECHAT_OFFICIAL_CONFIG_KEYS);
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

    private Map<String, String> loadConfigValuesByKeys(Long tenantId, List<String> keys) {
        return loadConfigValuesByKeys(tenantId, keys, true);
    }

    private Map<String, String> loadConfigValuesByKeys(Long tenantId, List<String> keys, boolean trimValues) {
        Long effectiveTenantId = tenantId == null ? DEFAULT_PUBLIC_TENANT_ID : tenantId;
        long version = loadRuntimeAppearanceVersion(effectiveTenantId);
        String cacheKey = configSnapshotCacheKey(effectiveTenantId, keys, trimValues, version);
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
        return loadConfigValuesByKeysWithSingleFlight(effectiveTenantId, cacheKey, keys, trimValues);
    }

    private Map<String, String> getCachedConfigSnapshot(String cacheKey) {
        Map<String, String> cached = configSnapshotCache.getIfPresent(cacheKey);
        return cached == null ? null : new LinkedHashMap<>(cached);
    }

    private void cacheConfigSnapshot(String cacheKey, Map<String, String> valueByKey) {
        configSnapshotCache.put(cacheKey, new LinkedHashMap<>(valueByKey));
    }

    private Map<String, String> loadConfigValuesByKeysWithSingleFlight(
            Long tenantId,
            String cacheKey,
            List<String> keys,
            boolean trimValues
    ) {
        try {
            CompletableFuture<Map<String, String>> inFlight = configLoadInFlight.get(
                    cacheKey,
                    () -> CompletableFuture.completedFuture(loadConfigValuesByKeysFromDatabase(tenantId, cacheKey, keys, trimValues))
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
            Long tenantId,
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
                select tenant_id as tenantId, config_key as configKey, config_value as configValue
                from sys_config
                where deleted = 0
                  and config_scope = 'PLATFORM'
                  and config_key in (%s)
                  and (tenant_id = ? or tenant_id is null)
                order by case when tenant_id = ? then 0 else 1 end, id desc
                """.formatted(placeholders);
        List<Object> params = new ArrayList<>(keys);
        params.add(tenantId);
        params.add(tenantId);
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

    private String configSnapshotCacheKey(Long tenantId, List<String> keys, boolean trimValues, long version) {
        String joinedKeys = keys.stream().sorted().collect(Collectors.joining(","));
        return tenantId + ":" + version + ":" + trimValues + ":" + joinedKeys;
    }

    private long loadRuntimeAppearanceVersion(Long tenantId) {
        Long cachedVersion = runtimeAppearanceVersionCache.getIfPresent(tenantId);
        if (cachedVersion != null) {
            return cachedVersion;
        }
        if (readModelVersionService == null) {
            return 0L;
        }
        try {
            CompletableFuture<Long> inFlight = runtimeAppearanceVersionLoadInFlight.get(
                    tenantId,
                    () -> CompletableFuture.completedFuture(
                            readModelVersionService.getOrInitialize(tenantId, CONTEXT_PLATFORM, SCOPE_RUNTIME_APPEARANCE)
                    )
            );
            long version = inFlight.join();
            runtimeAppearanceVersionCache.put(tenantId, version);
            return version;
        } catch (CompletionException exception) {
            runtimeAppearanceVersionLoadInFlight.invalidate(tenantId);
            Long fallback = runtimeAppearanceVersionCache.getIfPresent(tenantId);
            if (fallback != null) {
                return fallback;
            }
            return 0L;
        } catch (ExecutionException exception) {
            runtimeAppearanceVersionLoadInFlight.invalidate(tenantId);
            Long fallback = runtimeAppearanceVersionCache.getIfPresent(tenantId);
            if (fallback != null) {
                return fallback;
            }
            return 0L;
        }
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
                    encryptConfigValue(configKey, configValue),
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
                encryptConfigValue(configKey, configValue),
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

    private String normalizeMarkdownText(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeConfigText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String normalizeConfigTextRaw(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Long currentTenantId(CurrentUser currentUser) {
        if (currentUser == null || currentUser.getCurrentTenantId() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "租户上下文缺失");
        }
        return currentUser.getCurrentTenantId();
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

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
