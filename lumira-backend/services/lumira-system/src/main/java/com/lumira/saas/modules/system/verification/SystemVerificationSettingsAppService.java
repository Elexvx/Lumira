package com.lumira.saas.modules.system.verification;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.FieldCryptoService;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.lumira.saas.infrastructure.readmodel.ReadModelVersionService;
import com.lumira.saas.infrastructure.readmodel.ReadModelEventKey;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.vo.SystemVO;
import com.lumira.saas.modules.system.support.SmtpMailService;
import com.lumira.saas.modules.system.config.app.SystemConfigVersioningService;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

@Service
@ConditionalOnLumiraControlPlaneEnabled
public class SystemVerificationSettingsAppService {

    private static final Duration CONFIG_SNAPSHOT_TTL = Duration.ofSeconds(30);
    private static final int CONFIG_SNAPSHOT_MAX_ENTRIES = 2048;
    private static final long READ_MODEL_VERSION_CACHE_TTL_MS = 2_000L;
    private static final String PERMISSION_VERIFICATION_MANAGE = "system:verification:manage";
    private static final String PERMISSION_CONFIG_UPDATE = "system:config:update";
    private static final String STATUS_ENABLED = "ENABLED";

    private static final String TOTP_CONFIG_ENABLED_KEY = "verification.totp.enabled";
    private static final String PASSWORD_LOGIN_ENABLED_KEY = "verification.password-login.enabled";
    private static final String EMAIL_LOGIN_ENABLED_KEY = "verification.email-login.enabled";
    private static final String LOGIN_MODE_ORDER_KEY = "verification.login-mode.order";
    private static final List<String> DEFAULT_LOGIN_MODE_ORDER = List.of("password", "sms", "email", "wechat", "passkey");
    private static final String SMS_CONFIG_ENABLED_KEY = "verification.sms.enabled";
    private static final String SMS_CONFIG_PROVIDER_KEY = "verification.sms.provider";
    private static final String SMS_CONFIG_SIGN_NAME_KEY = "verification.sms.sign-name";
    private static final String SMS_CONFIG_TEMPLATE_CODE_KEY = "verification.sms.template-code";
    private static final String SMS_CONFIG_ACCESS_KEY_ID_KEY = "verification.sms.access-key-id";
    private static final String SMS_CONFIG_ACCESS_KEY_SECRET_KEY = "verification.sms.access-key-secret";
    private static final String SMS_CONFIG_ENDPOINT_KEY = "verification.sms.endpoint";
    private static final String SMS_CONFIG_REGION_KEY = "verification.sms.region";
    private static final String PASSKEY_ENABLED_KEY = "verification.passkey.enabled";
    private static final String PASSKEY_PASSWORDLESS_ENABLED_KEY = "verification.passkey.passwordless-enabled";
    private static final String PASSKEY_SELF_BINDING_ENABLED_KEY = "verification.passkey.self-binding-enabled";
    private static final String PASSKEY_RP_ID_KEY = "verification.passkey.rp-id";
    private static final String PASSKEY_RP_NAME_KEY = "verification.passkey.rp-name";
    private static final String PASSKEY_ALLOWED_ORIGINS_KEY = "verification.passkey.allowed-origins";
    private static final String PASSKEY_CHALLENGE_TTL_KEY = "verification.passkey.challenge-ttl-seconds";
    private static final String READ_MODEL_CONTEXT_PLATFORM = "platform";
    private static final String READ_MODEL_SCOPE_PUBLIC_BOOTSTRAP = "public-bootstrap";
    private static final List<String> LOGIN_CAPABILITY_CONFIG_KEYS = List.of(
            PASSWORD_LOGIN_ENABLED_KEY,
            EMAIL_LOGIN_ENABLED_KEY,
            LOGIN_MODE_ORDER_KEY,
            SMS_CONFIG_ENABLED_KEY,
            SMS_CONFIG_PROVIDER_KEY,
            SMS_CONFIG_SIGN_NAME_KEY,
            SMS_CONFIG_TEMPLATE_CODE_KEY,
            SMS_CONFIG_ACCESS_KEY_ID_KEY,
            SMS_CONFIG_ACCESS_KEY_SECRET_KEY,
            SMS_CONFIG_ENDPOINT_KEY,
            SMS_CONFIG_REGION_KEY,
            PASSKEY_ENABLED_KEY,
            PASSKEY_PASSWORDLESS_ENABLED_KEY,
            PASSKEY_SELF_BINDING_ENABLED_KEY,
            PASSKEY_RP_ID_KEY,
            PASSKEY_RP_NAME_KEY,
            PASSKEY_ALLOWED_ORIGINS_KEY,
            PASSKEY_CHALLENGE_TTL_KEY
    );

    private final MyBatisQueryOperations jdbcTemplate;
    private final SystemVerificationProperties properties;
    private final SmtpMailService smtpMailService;
    private final WechatLoginSettingsService wechatLoginSettingsService;
    private final FieldCryptoService fieldCryptoService;
    private final ReadModelVersionService readModelVersionService;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;
    private final boolean enforceTrustedUserResolution;
    private final Cache<String, Map<String, String>> configSnapshotCache;
    private final Cache<String, CompletableFuture<Map<String, String>>> configLoadInFlight;
    private volatile CachedReadModelVersion cachedPublicBootstrapVersion;
    private SystemConfigVersioningService configVersioningService;

    public SystemVerificationSettingsAppService(
            MyBatisQueryOperations jdbcTemplate,
            SystemVerificationProperties properties,
            SmtpMailService smtpMailService,
            WechatLoginSettingsService wechatLoginSettingsService,
            FieldCryptoService fieldCryptoService,
            ReadModelVersionService readModelVersionService,
            PermissionSnapshotService permissionSnapshotService
    ) {
        this(
                jdbcTemplate,
                properties,
                smtpMailService,
                wechatLoginSettingsService,
                fieldCryptoService,
                readModelVersionService,
                permissionSnapshotService,
                null,
                null,
                false
        );
    }

    @Autowired
    public SystemVerificationSettingsAppService(
            MyBatisQueryOperations jdbcTemplate,
            SystemVerificationProperties properties,
            SmtpMailService smtpMailService,
            WechatLoginSettingsService wechatLoginSettingsService,
            FieldCryptoService fieldCryptoService,
            ReadModelVersionService readModelVersionService,
            PermissionSnapshotService permissionSnapshotService,
            @Lazy
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(
                jdbcTemplate,
                properties,
                smtpMailService,
                wechatLoginSettingsService,
                fieldCryptoService,
                readModelVersionService,
                permissionSnapshotService,
                systemInternalApi,
                sessionAuthenticationService,
                true
        );
    }

    private SystemVerificationSettingsAppService(
            MyBatisQueryOperations jdbcTemplate,
            SystemVerificationProperties properties,
            SmtpMailService smtpMailService,
            WechatLoginSettingsService wechatLoginSettingsService,
            FieldCryptoService fieldCryptoService,
            ReadModelVersionService readModelVersionService,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService,
            boolean enforceTrustedUserResolution
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.smtpMailService = smtpMailService;
        this.wechatLoginSettingsService = wechatLoginSettingsService;
        this.fieldCryptoService = fieldCryptoService;
        this.readModelVersionService = readModelVersionService;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
        this.configSnapshotCache = CacheBuilder.newBuilder()
                .maximumSize(CONFIG_SNAPSHOT_MAX_ENTRIES)
                .expireAfterWrite(CONFIG_SNAPSHOT_TTL.toMillis(), TimeUnit.MILLISECONDS)
                .build();
        this.configLoadInFlight = CacheBuilder.newBuilder()
                .maximumSize(CONFIG_SNAPSHOT_MAX_ENTRIES)
                .expireAfterWrite(CONFIG_SNAPSHOT_TTL.toMillis(), TimeUnit.MILLISECONDS)
                .build();
    }

    @Autowired
    public void setConfigVersioningService(SystemConfigVersioningService configVersioningService) {
        this.configVersioningService = configVersioningService;
    }

    public SystemVerificationSettingsAppService(
            MyBatisQueryOperations jdbcTemplate,
            SystemVerificationProperties properties,
            SmtpMailService smtpMailService,
            WechatLoginSettingsService wechatLoginSettingsService,
            FieldCryptoService fieldCryptoService,
            ReadModelVersionService readModelVersionService,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(
                jdbcTemplate,
                properties,
                smtpMailService,
                wechatLoginSettingsService,
                fieldCryptoService,
                readModelVersionService,
                permissionSnapshotService,
                null,
                sessionAuthenticationService,
                false
        );
    }

    public SystemVerificationSettingsAppService(
            MyBatisQueryOperations jdbcTemplate,
            SystemVerificationProperties properties,
            SmtpMailService smtpMailService,
            WechatLoginSettingsService wechatLoginSettingsService,
            FieldCryptoService fieldCryptoService,
            ReadModelVersionService readModelVersionService
    ) {
        this(jdbcTemplate,
                properties,
                smtpMailService,
                wechatLoginSettingsService,
                fieldCryptoService,
                readModelVersionService,
                null);
    }

    public SystemVO.SmsVerificationSettingsVO getSmsSettings() {
        SmsVerificationSettingsRecord record = loadSmsSettingsRecord();
        SystemVO.SmsVerificationSettingsVO settings = new SystemVO.SmsVerificationSettingsVO();
        settings.setEnabled(record.enabled());
        settings.setProvider(record.provider());
        settings.setSignName(record.signName());
        settings.setTemplateCode(record.templateCode());
        settings.setAccessKeyId(record.accessKeyId());
        settings.setAccessKeySecret("");
        settings.setAccessKeySecretConfigured(StringUtils.hasText(record.accessKeySecret()));
        settings.setEndpoint(record.endpoint());
        settings.setRegion(record.region());
        settings.setConfigured(record.configured());
        return settings;
    }

    public SystemVO.VerificationSettingsVO getVerificationSettings() {
        Map<String, String> values = loadConfigValuesByKeys(List.of(
                TOTP_CONFIG_ENABLED_KEY,
                EMAIL_LOGIN_ENABLED_KEY,
                PASSWORD_LOGIN_ENABLED_KEY,
                LOGIN_MODE_ORDER_KEY
        ));
        SystemVO.VerificationSettingsVO settings = new SystemVO.VerificationSettingsVO();
        settings.setEnabled(isTotpEnabled(values));
        settings.setEmailLoginEnabled(isEmailLoginEnabled(values));
        settings.setPasswordLoginEnabled(isPasswordLoginEnabled(values));
        settings.setLoginModeOrder(loginModeOrder(values));
        return settings;
    }

    public SystemVO.LoginCapabilitiesVO loadLoginCapabilities() {
        return buildLoginCapabilities(
                loadConfigValuesByKeys(LOGIN_CAPABILITY_CONFIG_KEYS),
                wechatLoginSettingsService.loadSettings()
        );
    }

    public SystemVO.LoginCapabilitiesVO loadLoginCapabilitiesFresh() {
        return buildLoginCapabilities(
                loadConfigValuesByKeysFresh(LOGIN_CAPABILITY_CONFIG_KEYS),
                wechatLoginSettingsService.loadSettingsFresh()
        );
    }

    private SystemVO.LoginCapabilitiesVO buildLoginCapabilities(
            Map<String, String> values,
            WechatLoginSettingsService.WechatLoginSettingsRecord wechatSettings
    ) {
        SystemVO.LoginCapabilitiesVO capabilities = new SystemVO.LoginCapabilitiesVO();
        capabilities.setPasswordLoginAvailable(isPasswordLoginEnabled(values));
        capabilities.setSmsLoginAvailable(isSmsLoginAvailable(values));
        capabilities.setEmailLoginAvailable(isEmailLoginAvailable(values));
        capabilities.setWechatLoginAvailable(wechatSettings.available());
        SystemVO.PasskeySettingsVO passkey = toPasskeySettings(values);
        capabilities.setPasskeyLoginAvailable(Boolean.TRUE.equals(passkey.getEnabled()));
        capabilities.setPasskeyPasswordlessAvailable(Boolean.TRUE.equals(passkey.getEnabled()) && Boolean.TRUE.equals(passkey.getPasswordlessEnabled()));
        capabilities.setLoginModeOrder(loginModeOrder(values));
        return capabilities;
    }

    public SystemVO.WechatLoginSettingsVO getWechatSettings() {
        return wechatLoginSettingsService.getSettings();
    }

    public SystemVO.PasskeySettingsVO getPasskeySettings() {
        return toPasskeySettings(loadConfigValuesByKeys(passkeyConfigKeys()));
    }

    private SystemVO.PasskeySettingsVO toPasskeySettings(Map<String, String> values) {
        SystemVO.PasskeySettingsVO settings = new SystemVO.PasskeySettingsVO();
        settings.setEnabled(Boolean.parseBoolean(defaultIfBlank(values.get(PASSKEY_ENABLED_KEY), "false")));
        settings.setPasswordlessEnabled(Boolean.parseBoolean(defaultIfBlank(values.get(PASSKEY_PASSWORDLESS_ENABLED_KEY), "false")));
        settings.setSelfBindingEnabled(Boolean.parseBoolean(defaultIfBlank(values.get(PASSKEY_SELF_BINDING_ENABLED_KEY), "false")));
        settings.setRpId(defaultIfBlank(values.get(PASSKEY_RP_ID_KEY), ""));
        settings.setRpName(defaultIfBlank(values.get(PASSKEY_RP_NAME_KEY), ""));
        settings.setAllowedOrigins(splitLines(defaultIfBlank(values.get(PASSKEY_ALLOWED_ORIGINS_KEY), "")));
        settings.setChallengeTtlSeconds(parseInt(defaultIfBlank(values.get(PASSKEY_CHALLENGE_TTL_KEY), "120"), 120));
        return settings;
    }

    @Transactional
    public SystemVO.VerificationSettingsVO updateVerificationSettings(CurrentUser currentUser, SystemDTO.VerificationSettingsRequest request) {
        Long operatorId = requireConfigManagePermission(currentUser);
        String operatorUuid = currentUser.getUserUuid();
        requireRequest(request, "Verification settings request");
        boolean enabled = request.getEnabled() == null ? isTotpEnabled() : request.getEnabled();
        boolean emailLoginEnabled = request.getEmailLoginEnabled() == null ? isEmailLoginEnabled() : request.getEmailLoginEnabled();
        boolean passwordLoginEnabled = request.getPasswordLoginEnabled() == null ? isPasswordLoginEnabled() : request.getPasswordLoginEnabled();
        SystemConfigVersioningService.GovernanceSession governance = beginGovernance(
                request.getExpectedConfigVersion(),
                request.getChangeReason(),
                currentUser,
                List.of(TOTP_CONFIG_ENABLED_KEY, EMAIL_LOGIN_ENABLED_KEY, PASSWORD_LOGIN_ENABLED_KEY, LOGIN_MODE_ORDER_KEY)
        );
        upsertPlatformConfigValue(TOTP_CONFIG_ENABLED_KEY, "2FA enabled", String.valueOf(enabled), "Whether 2FA login is enabled", operatorId, operatorUuid);
        upsertPlatformConfigValue(EMAIL_LOGIN_ENABLED_KEY, "Email code login", String.valueOf(emailLoginEnabled), "Whether email code login is enabled", operatorId, operatorUuid);
        upsertPlatformConfigValue(PASSWORD_LOGIN_ENABLED_KEY, "Password login", String.valueOf(passwordLoginEnabled), "Whether password login is enabled", operatorId, operatorUuid);
        if (request.getLoginModeOrder() != null) {
            upsertPlatformConfigValue(LOGIN_MODE_ORDER_KEY, "Login mode order", String.join(",", normalizeLoginModeOrder(request.getLoginModeOrder())), "Display order of login modes", operatorId, operatorUuid);
        }
        markPublicBootstrapChanged("verification-settings-update");
        finishGovernance(governance);
        return getVerificationSettings();
    }

    @Transactional
    public SystemVO.SmsVerificationSettingsVO updateSmsSettings(CurrentUser currentUser, SystemDTO.SmsVerificationSettingsRequest request) {
        Long operatorId = requireConfigManagePermission(currentUser);
        String operatorUuid = currentUser.getUserUuid();
        requireRequest(request, "SMS verification settings request");
        SmsVerificationSettingsRecord current = loadSmsSettingsRecord();
        Boolean enabled = request.getEnabled() == null ? current.enabled() : request.getEnabled();
        String provider = sanitizeText(request.getProvider(), current.provider());
        String signName = sanitizeText(request.getSignName(), current.signName());
        String templateCode = sanitizeText(request.getTemplateCode(), current.templateCode());
        String accessKeyId = sanitizeText(request.getAccessKeyId(), current.accessKeyId());
        String existingSecret = defaultIfBlank(current.accessKeySecret(), "");
        String accessKeySecret = StringUtils.hasText(request.getAccessKeySecret()) ? request.getAccessKeySecret() : existingSecret;
        String endpoint = sanitizeText(request.getEndpoint(), current.endpoint());
        String region = sanitizeText(request.getRegion(), current.region());
        SystemConfigVersioningService.GovernanceSession governance = beginGovernance(
                request.getExpectedConfigVersion(),
                request.getChangeReason(),
                currentUser,
                smsConfigKeys()
        );

        upsertSmsConfigValue(SMS_CONFIG_ENABLED_KEY, "SMS verification enabled", String.valueOf(Boolean.TRUE.equals(enabled)), "Whether SMS verification service is enabled", operatorId, operatorUuid);
        upsertSmsConfigValue(SMS_CONFIG_PROVIDER_KEY, "SMS provider", provider, "SMS verification provider", operatorId, operatorUuid);
        upsertSmsConfigValue(SMS_CONFIG_SIGN_NAME_KEY, "SMS sign name", signName, "SMS verification sign name", operatorId, operatorUuid);
        upsertSmsConfigValue(SMS_CONFIG_TEMPLATE_CODE_KEY, "SMS template code", templateCode, "SMS verification template code", operatorId, operatorUuid);
        upsertSmsConfigValue(SMS_CONFIG_ACCESS_KEY_ID_KEY, "SMS Access Key ID", accessKeyId, "SMS verification access key id", operatorId, operatorUuid);
        upsertSmsConfigValue(SMS_CONFIG_ACCESS_KEY_SECRET_KEY, "SMS Access Key Secret", accessKeySecret, "SMS verification access key secret", operatorId, operatorUuid);
        upsertSmsConfigValue(SMS_CONFIG_ENDPOINT_KEY, "SMS endpoint", endpoint, "SMS verification endpoint", operatorId, operatorUuid);
        upsertSmsConfigValue(SMS_CONFIG_REGION_KEY, "SMS region", region, "SMS verification region", operatorId, operatorUuid);

        markPublicBootstrapChanged("sms-settings-update");
        finishGovernance(governance);
        return getSmsSettings();
    }

    @Transactional
    public SystemVO.SmsVerificationSettingsVO resetSmsSettings(CurrentUser currentUser) {
        Long operatorId = requireConfigManagePermission(currentUser);
        String operatorUuid = currentUser.getUserUuid();
        SystemConfigVersioningService.GovernanceSession governance = beginGovernance(
                null,
                "reset SMS verification settings",
                currentUser,
                smsConfigKeys()
        );
        upsertSmsConfigValue(SMS_CONFIG_ENABLED_KEY, "SMS verification enabled", "false", "Whether SMS verification service is enabled", operatorId, operatorUuid);
        upsertSmsConfigValue(SMS_CONFIG_PROVIDER_KEY, "SMS provider", "aliyun", "SMS verification provider", operatorId, operatorUuid);
        upsertSmsConfigValue(SMS_CONFIG_SIGN_NAME_KEY, "SMS sign name", "", "SMS verification sign name", operatorId, operatorUuid);
        upsertSmsConfigValue(SMS_CONFIG_TEMPLATE_CODE_KEY, "SMS template code", "", "SMS verification template code", operatorId, operatorUuid);
        upsertSmsConfigValue(SMS_CONFIG_ACCESS_KEY_ID_KEY, "SMS Access Key ID", "", "SMS verification access key id", operatorId, operatorUuid);
        upsertSmsConfigValue(SMS_CONFIG_ACCESS_KEY_SECRET_KEY, "SMS Access Key Secret", "", "SMS verification access key secret", operatorId, operatorUuid);
        upsertSmsConfigValue(SMS_CONFIG_ENDPOINT_KEY, "SMS endpoint", "", "SMS verification endpoint", operatorId, operatorUuid);
        upsertSmsConfigValue(SMS_CONFIG_REGION_KEY, "SMS region", "", "SMS verification region", operatorId, operatorUuid);
        markPublicBootstrapChanged("sms-settings-reset");
        finishGovernance(governance);
        return getSmsSettings();
    }

    public SystemVO.WechatLoginSettingsVO updateWechatSettings(CurrentUser currentUser, SystemDTO.WechatLoginSettingsRequest request) {
        requireConfigManagePermission(currentUser);
        requireRequest(request, "Wechat login settings request");
        return wechatLoginSettingsService.updateSettings(currentUser, request);
    }

    public SystemVO.WechatLoginSettingsVO resetWechatSettings(CurrentUser currentUser) {
        requireConfigManagePermission(currentUser);
        return wechatLoginSettingsService.resetSettings(currentUser);
    }

    @Transactional
    public SystemVO.PasskeySettingsVO updatePasskeySettings(CurrentUser currentUser, SystemDTO.PasskeySettingsRequest request) {
        Long operatorId = requireConfigManagePermission(currentUser);
        String operatorUuid = currentUser.getUserUuid();
        requireRequest(request, "Passkey settings request");
        SystemVO.PasskeySettingsVO current = getPasskeySettings();
        boolean enabled = request.getEnabled() == null ? Boolean.TRUE.equals(current.getEnabled()) : request.getEnabled();
        boolean passwordless = request.getPasswordlessEnabled() == null ? Boolean.TRUE.equals(current.getPasswordlessEnabled()) : request.getPasswordlessEnabled();
        boolean selfBinding = request.getSelfBindingEnabled() == null ? Boolean.TRUE.equals(current.getSelfBindingEnabled()) : request.getSelfBindingEnabled();
        String rpId = sanitizeText(request.getRpId(), current.getRpId());
        String rpName = sanitizeText(request.getRpName(), current.getRpName());
        List<String> origins = request.getAllowedOrigins() == null ? current.getAllowedOrigins() : request.getAllowedOrigins().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        int ttl = request.getChallengeTtlSeconds() == null ? current.getChallengeTtlSeconds() : request.getChallengeTtlSeconds();
        SystemConfigVersioningService.GovernanceSession governance = beginGovernance(
                request.getExpectedConfigVersion(),
                request.getChangeReason(),
                currentUser,
                passkeyConfigKeys()
        );

        upsertPlatformConfigValue(PASSKEY_ENABLED_KEY, "Passkey enabled", String.valueOf(enabled), "Whether passkey login is enabled", operatorId, operatorUuid);
        upsertPlatformConfigValue(PASSKEY_PASSWORDLESS_ENABLED_KEY, "Passkey passwordless login", String.valueOf(passwordless), "Whether discoverable credentials may log in without account input", operatorId, operatorUuid);
        upsertPlatformConfigValue(PASSKEY_SELF_BINDING_ENABLED_KEY, "Passkey self binding", String.valueOf(selfBinding), "Whether users may self-bind passkeys in personal settings", operatorId, operatorUuid);
        upsertPlatformConfigValue(PASSKEY_RP_ID_KEY, "Passkey RP ID", rpId, "WebAuthn RP ID", operatorId, operatorUuid);
        upsertPlatformConfigValue(PASSKEY_RP_NAME_KEY, "Passkey RP name", rpName, "WebAuthn RP display name", operatorId, operatorUuid);
        upsertPlatformConfigValue(PASSKEY_ALLOWED_ORIGINS_KEY, "Passkey allowed origins", String.join("\\n", origins), "Allowed frontend origins for WebAuthn", operatorId, operatorUuid);
        upsertPlatformConfigValue(PASSKEY_CHALLENGE_TTL_KEY, "Passkey challenge TTL", String.valueOf(ttl), "WebAuthn challenge lifetime in seconds", operatorId, operatorUuid);
        markPublicBootstrapChanged("passkey-settings-update");
        finishGovernance(governance);
        return getPasskeySettings();
    }

    @Transactional
    public SystemVO.PasskeySettingsVO resetPasskeySettings(CurrentUser currentUser) {
        Long operatorId = requireConfigManagePermission(currentUser);
        String operatorUuid = currentUser.getUserUuid();
        SystemConfigVersioningService.GovernanceSession governance = beginGovernance(
                null,
                "reset passkey settings",
                currentUser,
                passkeyConfigKeys()
        );
        upsertPlatformConfigValue(PASSKEY_ENABLED_KEY, "Passkey enabled", "false", "Whether passkey login is enabled", operatorId, operatorUuid);
        upsertPlatformConfigValue(PASSKEY_PASSWORDLESS_ENABLED_KEY, "Passkey passwordless login", "false", "Whether discoverable credentials may log in without account input", operatorId, operatorUuid);
        upsertPlatformConfigValue(PASSKEY_SELF_BINDING_ENABLED_KEY, "Passkey self binding", "true", "Whether users may self-bind passkeys in personal settings", operatorId, operatorUuid);
        upsertPlatformConfigValue(PASSKEY_RP_ID_KEY, "Passkey RP ID", "", "WebAuthn RP ID", operatorId, operatorUuid);
        upsertPlatformConfigValue(PASSKEY_RP_NAME_KEY, "Passkey RP name", "", "WebAuthn RP display name", operatorId, operatorUuid);
        upsertPlatformConfigValue(PASSKEY_ALLOWED_ORIGINS_KEY, "Passkey allowed origins", "", "Allowed frontend origins for WebAuthn", operatorId, operatorUuid);
        upsertPlatformConfigValue(PASSKEY_CHALLENGE_TTL_KEY, "Passkey challenge TTL", "120", "WebAuthn challenge lifetime in seconds", operatorId, operatorUuid);
        markPublicBootstrapChanged("passkey-settings-reset");
        finishGovernance(governance);
        return getPasskeySettings();
    }

    private SystemConfigVersioningService.GovernanceSession beginGovernance(
            Long expectedConfigVersion,
            String changeReason,
            CurrentUser currentUser,
            List<String> keys
    ) {
        if (configVersioningService == null) {
            return null;
        }
        return configVersioningService.begin(
                new SystemConfigVersioningService.ChangeRequest(
                        SystemConfigVersioningService.GROUP_VERIFICATION,
                        SystemConfigVersioningService.DOMAIN_PLATFORM,
                        expectedConfigVersion,
                        changeReason,
                        currentUser
                ),
                keys
        );
    }

    private void finishGovernance(SystemConfigVersioningService.GovernanceSession governance) {
        if (governance != null) {
            configVersioningService.finish(governance);
        }
    }

    private SmsVerificationSettingsRecord loadSmsSettingsRecord() {
        return toSmsSettingsRecord(loadConfigValuesByKeys(smsConfigKeys()));
    }

    private SmsVerificationSettingsRecord toSmsSettingsRecord(Map<String, String> values) {
        boolean enabled = Boolean.parseBoolean(defaultIfBlank(values.get(SMS_CONFIG_ENABLED_KEY), "false"));
        String provider = defaultIfBlank(values.get(SMS_CONFIG_PROVIDER_KEY), "aliyun");
        String signName = defaultIfBlank(values.get(SMS_CONFIG_SIGN_NAME_KEY), "");
        String templateCode = defaultIfBlank(values.get(SMS_CONFIG_TEMPLATE_CODE_KEY), "");
        String accessKeyId = defaultIfBlank(values.get(SMS_CONFIG_ACCESS_KEY_ID_KEY), "");
        String accessKeySecret = defaultIfBlank(values.get(SMS_CONFIG_ACCESS_KEY_SECRET_KEY), "");
        String endpoint = defaultIfBlank(values.get(SMS_CONFIG_ENDPOINT_KEY), "");
        String region = defaultIfBlank(values.get(SMS_CONFIG_REGION_KEY), "");
        boolean configured = StringUtils.hasText(provider)
                && StringUtils.hasText(signName)
                && StringUtils.hasText(templateCode)
                && StringUtils.hasText(accessKeyId)
                && StringUtils.hasText(accessKeySecret);
        return new SmsVerificationSettingsRecord(enabled, provider, signName, templateCode, accessKeyId, accessKeySecret, endpoint, region, configured);
    }

    private void upsertSmsConfigValue(String configKey, String configName, String configValue, String remark, Long operatorId, String operatorUuid) {
        upsertConfigValue(configKey, configName, configValue, remark, operatorId, operatorUuid);
    }

    private void upsertPlatformConfigValue(String configKey, String configName, String configValue, String remark, Long operatorId, String operatorUuid) {
        upsertConfigValue(configKey, configName, configValue, remark, operatorId, operatorUuid);
    }

    private void upsertConfigValue(String configKey, String configName, String configValue, String remark, Long operatorId, String operatorUuid) {
        Long existingId = queryConfigId(configKey);
        if (existingId == null) {
            int inserted = jdbcTemplate.update(
                    """
                            insert into sys_config (
                                config_key, config_name, config_value, config_scope, is_system, remark,
                                created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                            ) values (?, ?, ?, 'PLATFORM', 0, ?, ?, ?, ?, ?, 0)
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
                throw new BizException(ErrorCode.BIZ_ERROR, "Verification config changed, please retry");
            }
            invalidateConfigCaches();
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
            throw new BizException(ErrorCode.BIZ_ERROR, "Verification config changed, please retry");
        }
        invalidateConfigCaches();
    }

    private Long queryConfigId(String configKey) {
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
    }

    private List<String> smsConfigKeys() {
        return List.of(
                SMS_CONFIG_ENABLED_KEY,
                SMS_CONFIG_PROVIDER_KEY,
                SMS_CONFIG_SIGN_NAME_KEY,
                SMS_CONFIG_TEMPLATE_CODE_KEY,
                SMS_CONFIG_ACCESS_KEY_ID_KEY,
                SMS_CONFIG_ACCESS_KEY_SECRET_KEY,
                SMS_CONFIG_ENDPOINT_KEY,
                SMS_CONFIG_REGION_KEY
        );
    }

    private List<String> passkeyConfigKeys() {
        return List.of(
                PASSKEY_ENABLED_KEY,
                PASSKEY_PASSWORDLESS_ENABLED_KEY,
                PASSKEY_SELF_BINDING_ENABLED_KEY,
                PASSKEY_RP_ID_KEY,
                PASSKEY_RP_NAME_KEY,
                PASSKEY_ALLOWED_ORIGINS_KEY,
                PASSKEY_CHALLENGE_TTL_KEY
        );
    }

    private Map<String, String> loadConfigValuesByKeys(List<String> keys) {
        String cacheKey = configSnapshotCacheKey(keys, currentPublicBootstrapVersion());
        Map<String, String> cached = configSnapshotCache.getIfPresent(cacheKey);
        if (cached != null) {
            return new LinkedHashMap<>(cached);
        }
        try {
            CompletableFuture<Map<String, String>> inFlight = configLoadInFlight.get(
                    cacheKey,
                    () -> CompletableFuture.completedFuture(loadConfigValuesByKeysFromDatabase(cacheKey, keys))
            );
            return new LinkedHashMap<>(inFlight.join());
        } catch (CompletionException exception) {
            configLoadInFlight.invalidate(cacheKey);
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Failed to load verification config snapshot", cause);
        } catch (ExecutionException exception) {
            configLoadInFlight.invalidate(cacheKey);
            throw new IllegalStateException("Failed to load verification config snapshot", exception);
        }
    }

    private Map<String, String> loadConfigValuesByKeysFresh(List<String> keys) {
        return queryConfigValuesByKeys(keys);
    }

    private Map<String, String> loadConfigValuesByKeysFromDatabase(String cacheKey, List<String> keys) {
        Map<String, String> cached = configSnapshotCache.getIfPresent(cacheKey);
        if (cached != null) {
            return new LinkedHashMap<>(cached);
        }
        Map<String, String> valueByKey = queryConfigValuesByKeys(keys);
        configSnapshotCache.put(cacheKey, new LinkedHashMap<>(valueByKey));
        return valueByKey;
    }

    private Map<String, String> queryConfigValuesByKeys(List<String> keys) {
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
                valueByKey.put(configKey, normalizeConfigText(decryptConfigValue(configKey, normalizeConfigTextRaw(row.get("configValue")))));
            }
        }
        return valueByKey;
    }

    private String configSnapshotCacheKey(List<String> keys, Long publicBootstrapVersion) {
        String keySignature = keys.stream().sorted().collect(Collectors.joining(","));
        if (publicBootstrapVersion == null) {
            return "global:" + keySignature;
        }
        return "global:v" + publicBootstrapVersion + ":" + keySignature;
    }

    private void invalidateConfigCaches() {
        configSnapshotCache.invalidateAll();
        configLoadInFlight.invalidateAll();
        cachedPublicBootstrapVersion = null;
    }

    private void markPublicBootstrapChanged(String eventKey) {
        if (readModelVersionService != null) {
            readModelVersionService.bump(
                    READ_MODEL_CONTEXT_PLATFORM,
                    READ_MODEL_SCOPE_PUBLIC_BOOTSTRAP,
                    ReadModelEventKey.unique(eventKey)
            );
        }
    }

    private String encryptConfigValue(String configKey, String configValue) {
        return isSensitiveConfigKey(configKey) ? fieldCryptoService.encrypt(configValue) : configValue;
    }

    private String decryptConfigValue(String configKey, String configValue) {
        return isSensitiveConfigKey(configKey) ? fieldCryptoService.decrypt(configValue) : configValue;
    }

    private boolean isSensitiveConfigKey(String configKey) {
        return SMS_CONFIG_ACCESS_KEY_SECRET_KEY.equals(configKey);
    }

    private String sanitizeText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private List<String> splitLines(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return value.lines().map(String::trim).filter(StringUtils::hasText).distinct().toList();
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }


    private boolean isTotpEnabled() {
        Map<String, String> values = loadConfigValuesByKeys(List.of(TOTP_CONFIG_ENABLED_KEY));
        return isTotpEnabled(values);
    }

    private boolean isTotpEnabled(Map<String, String> values) {
        return Boolean.parseBoolean(defaultIfBlank(values.get(TOTP_CONFIG_ENABLED_KEY), "true"));
    }

    private boolean isEmailLoginEnabled() {
        Map<String, String> values = loadConfigValuesByKeys(List.of(EMAIL_LOGIN_ENABLED_KEY));
        return isEmailLoginEnabled(values);
    }

    private boolean isEmailLoginEnabled(Map<String, String> values) {
        return Boolean.parseBoolean(defaultIfBlank(values.get(EMAIL_LOGIN_ENABLED_KEY), String.valueOf(properties.isEmailLoginEnabled())));
    }

    private boolean isPasswordLoginEnabled() {
        Map<String, String> values = loadConfigValuesByKeys(List.of(PASSWORD_LOGIN_ENABLED_KEY));
        return isPasswordLoginEnabled(values);
    }

    private boolean isPasswordLoginEnabled(Map<String, String> values) {
        return Boolean.parseBoolean(defaultIfBlank(values.get(PASSWORD_LOGIN_ENABLED_KEY), "true"));
    }

    private List<String> loginModeOrder() {
        Map<String, String> values = loadConfigValuesByKeys(List.of(LOGIN_MODE_ORDER_KEY));
        return loginModeOrder(values);
    }

    private List<String> loginModeOrder(Map<String, String> values) {
        String configured = values.get(LOGIN_MODE_ORDER_KEY);
        if (!StringUtils.hasText(configured)) {
            return DEFAULT_LOGIN_MODE_ORDER;
        }
        List<String> normalized = normalizeLoginModeOrder(List.of(configured.split(",")));
        return normalized.isEmpty() ? DEFAULT_LOGIN_MODE_ORDER : normalized;
    }

    private List<String> normalizeLoginModeOrder(List<String> values) {
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            String mode = value.trim();
            if (("passkey".equals(mode) || "sms".equals(mode) || "email".equals(mode) || "wechat".equals(mode) || "password".equals(mode)) && !normalized.contains(mode)) {
                normalized.add(mode);
            }
        }
        return normalized;
    }

    private boolean isEmailLoginAvailable() {
        return isEmailLoginEnabled() && smtpMailService.isConfigured();
    }

    private boolean isEmailLoginAvailable(Map<String, String> values) {
        return isEmailLoginEnabled(values) && smtpMailService.isConfigured();
    }

    private boolean isSmsLoginAvailable() {
        SmsVerificationSettingsRecord smsSettings = loadSmsSettingsRecord();
        return smsSettings.enabled() && smsSettings.configured();
    }

    private boolean isSmsLoginAvailable(Map<String, String> values) {
        SmsVerificationSettingsRecord smsSettings = toSmsSettingsRecord(values);
        return smsSettings.enabled() && smsSettings.configured();
    }

    private Long currentPublicBootstrapVersion() {
        if (readModelVersionService == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        CachedReadModelVersion cached = cachedPublicBootstrapVersion;
        if (cached != null && cached.expiresAtMillis() > now) {
            return cached.version();
        }
        Long version = null;
        try {
            version = readModelVersionService.currentVersion(
                    READ_MODEL_CONTEXT_PLATFORM,
                    READ_MODEL_SCOPE_PUBLIC_BOOTSTRAP
            );
        } catch (Throwable ignored) {
            version = null;
        }
        cachedPublicBootstrapVersion = new CachedReadModelVersion(version, now + READ_MODEL_VERSION_CACHE_TTL_MS);
        return version;
    }

    private String normalizeConfigText(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String normalizeConfigTextRaw(Object value) {
        return value == null ? "" : value.toString();
    }

    private Long requireConfigManagePermission(CurrentUser currentUser) {
        CurrentUser runtimeUser = refreshTrustedCurrentUser(currentUser);
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(runtimeUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        Set<String> permissions = runtimeUser.getPermissions();
        if (permissions == null || permissions.isEmpty()) {
            throw new BizException(ErrorCode.FORBIDDEN, "Missing permission: " + PERMISSION_VERIFICATION_MANAGE);
        }
        if (permissions.contains("*")
                || permissions.contains(PERMISSION_VERIFICATION_MANAGE)
                || permissions.contains(PERMISSION_CONFIG_UPDATE)) {
            return runtimeUser.getUserId();
        }
        throw new BizException(ErrorCode.FORBIDDEN, "Missing permission: " + PERMISSION_VERIFICATION_MANAGE);
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
            userId = userSnapshot.userId();
            normalizedUserUuid = userSnapshot.userUuid().trim();
            currentUser.setUserId(userId);
            currentUser.setUserUuid(normalizedUserUuid);
            currentUser.setUsername(currentUsername);
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
        CurrentUser refreshed = new CurrentUser(
                userId,
                currentUser.getUsername(),
                currentUser.getSessionId(),
                currentUser.getSessionVersion(),
                true,
                snapshot.getPermissions() == null ? Set.of() : Set.copyOf(snapshot.getPermissions()),
                snapshot.getRoleIds() == null ? Set.of() : Set.copyOf(snapshot.getRoleIds()),
                snapshot.getPrimaryDeptId(),
                snapshot.getDeptIds() == null ? Set.of() : Set.copyOf(snapshot.getDeptIds()),
                snapshot.getDescendantDeptIds() == null ? Set.of() : Set.copyOf(snapshot.getDescendantDeptIds()),
                snapshot.getDataScopes() == null ? List.of() : List.copyOf(snapshot.getDataScopes())
        );
        refreshed.setUserUuid(normalizedUserUuid);
        refreshed.setPermissionsVersion(snapshot.getVersion());
        refreshed.setDefaultHomePath(snapshot.getDefaultHomePath());
        refreshed.setRequiresPasswordChange(currentUser.getRequiresPasswordChange());
        refreshed.setSimulatedRoleId(simulatedRoleId);
        refreshed.setLoginType(currentUser.getLoginType());
        copyTrustedCurrentUser(currentUser, refreshed);
        return currentUser;
    }

    private CurrentUser requireTrustedAuthenticatedCurrentUser(SessionAuthenticationService.AuthenticatedAccess authenticatedAccess) {
        if (authenticatedAccess == null || !AuthenticationTrustSupport.isTrustedCurrentUser(authenticatedAccess.currentUser())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return authenticatedAccess.currentUser();
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

    private void requireRequest(Object request, String name) {
        if (request == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, name + " is required");
        }
    }

    private record SmsVerificationSettingsRecord(
            boolean enabled,
            String provider,
            String signName,
            String templateCode,
            String accessKeyId,
            String accessKeySecret,
            String endpoint,
            String region,
            boolean configured
    ) {
    }

    private record CachedReadModelVersion(Long version, long expiresAtMillis) {
    }
}
