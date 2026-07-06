package com.lumira.saas.infrastructure.security.service;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.readmodel.ReadModelVersionService;
import com.lumira.saas.infrastructure.security.SecurityProperties;
import com.lumira.saas.modules.system.config.entity.SysConfigEntity;
import com.lumira.saas.modules.system.config.mapper.SysConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class SecuritySettingsService {

    private static final String PLATFORM_SCOPE = "PLATFORM";
    private static final String IDLE_TIMEOUT_KEY = "security.idle-timeout-seconds";
    private static final String ACCESS_TOKEN_EXPIRE_KEY = "security.access-token-expire-seconds";
    private static final String REFRESH_TOKEN_EXPIRE_KEY = "security.refresh-token-expire-seconds";
    private static final String ALLOW_MULTI_DEVICE_LOGIN_KEY = "security.allow-multi-device-login";
    private static final String CAPTCHA_ENABLED_KEY = "security.captcha-enabled";
    private static final String CAPTCHA_TYPE_KEY = "security.captcha-type";
    private static final String LOGIN_DEFENSE_WINDOW_MINUTES_KEY = "security.login-defense-window-minutes";
    private static final String LOGIN_MAX_VALIDATION_ATTEMPTS_KEY = "security.login-max-validation-attempts";
    private static final String LOGIN_MAX_FAILURE_COUNT_KEY = "security.login-max-failure-count";
    private static final String VERIFICATION_CODE_EXPIRE_SECONDS_KEY = "security.verification-code-expire-seconds";
    private static final String VERIFICATION_CODE_COOLDOWN_SECONDS_KEY = "security.verification-code-cooldown-seconds";
    private static final String PASSWORD_MIN_LENGTH_KEY = "security.password-min-length";
    private static final String PASSWORD_REQUIRE_UPPERCASE_KEY = "security.password-require-uppercase";
    private static final String PASSWORD_REQUIRE_LOWERCASE_KEY = "security.password-require-lowercase";
    private static final String PASSWORD_REQUIRE_SPECIAL_CHARACTER_KEY = "security.password-require-special-character";
    private static final String PASSWORD_ALLOW_CONSECUTIVE_CHARACTERS_KEY = "security.password-allow-consecutive-characters";
    private static final long SETTINGS_CACHE_TTL_MS = 30_000L;
    private static final long READ_MODEL_VERSION_CACHE_TTL_MS = 2_000L;
    private static final String READ_MODEL_CONTEXT_PLATFORM = "platform";
    private static final String READ_MODEL_SCOPE_PUBLIC_BOOTSTRAP = "public-bootstrap";
    private static final List<String> SETTINGS_KEYS = List.of(
            IDLE_TIMEOUT_KEY,
            ACCESS_TOKEN_EXPIRE_KEY,
            REFRESH_TOKEN_EXPIRE_KEY,
            ALLOW_MULTI_DEVICE_LOGIN_KEY,
            CAPTCHA_ENABLED_KEY,
            CAPTCHA_TYPE_KEY,
            LOGIN_DEFENSE_WINDOW_MINUTES_KEY,
            LOGIN_MAX_VALIDATION_ATTEMPTS_KEY,
            LOGIN_MAX_FAILURE_COUNT_KEY,
            VERIFICATION_CODE_EXPIRE_SECONDS_KEY,
            VERIFICATION_CODE_COOLDOWN_SECONDS_KEY,
            PASSWORD_MIN_LENGTH_KEY,
            PASSWORD_REQUIRE_UPPERCASE_KEY,
            PASSWORD_REQUIRE_LOWERCASE_KEY,
            PASSWORD_REQUIRE_SPECIAL_CHARACTER_KEY,
            PASSWORD_ALLOW_CONSECUTIVE_CHARACTERS_KEY
    );

    private final SysConfigMapper sysConfigMapper;
    private final SecurityProperties securityProperties;
    private final ReadModelVersionService readModelVersionService;
    private final SessionAuthenticationService sessionAuthenticationService;
    private volatile CachedSecuritySettings cachedSettings;
    private volatile CachedReadModelVersion cachedReadModelVersion;
    private final ThreadLocal<CurrentUser> currentUpdateOperator = new ThreadLocal<>();

    public SecuritySettingsService(SysConfigMapper sysConfigMapper, SecurityProperties securityProperties) {
        this(sysConfigMapper, securityProperties, null, null);
    }

    @Autowired
    public SecuritySettingsService(
            SysConfigMapper sysConfigMapper,
            SecurityProperties securityProperties,
            ReadModelVersionService readModelVersionService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this.sysConfigMapper = sysConfigMapper;
        this.securityProperties = securityProperties;
        this.readModelVersionService = readModelVersionService;
        this.sessionAuthenticationService = sessionAuthenticationService;
    }

    public SecuritySettingsService(
            SysConfigMapper sysConfigMapper,
            SecurityProperties securityProperties,
            ReadModelVersionService readModelVersionService
    ) {
        this(sysConfigMapper, securityProperties, readModelVersionService, null);
    }

    public long getIdleTimeoutSeconds() {
        return loadSettings().getIdleTimeoutSeconds();
    }

    public long getAccessTokenExpireSeconds() {
        return loadSettings().getAccessTokenExpireSeconds();
    }

    public long getRefreshTokenExpireSeconds() {
        return loadSettings().getRefreshTokenExpireSeconds();
    }

    public boolean isAllowMultiDeviceLogin() {
        return loadSettings().isAllowMultiDeviceLogin();
    }

    public boolean isCaptchaEnabled() {
        return loadSettings().isCaptchaEnabled();
    }

    public String getCaptchaType() {
        return loadSettings().getCaptchaType();
    }

    public long getLoginDefenseWindowMinutes() {
        return loadSettings().getLoginDefenseWindowMinutes();
    }

    public long getLoginMaxValidationAttempts() {
        return loadSettings().getLoginMaxValidationAttempts();
    }

    public long getLoginMaxFailureCount() {
        return loadSettings().getLoginMaxFailureCount();
    }

    public long getVerificationCodeExpireSeconds() {
        return loadSettings().getVerificationCodeExpireSeconds();
    }

    public long getVerificationCodeCooldownSeconds() {
        return loadSettings().getVerificationCodeCooldownSeconds();
    }

    public long getPasswordMinLength() {
        return loadSettings().getPasswordMinLength();
    }

    public boolean isPasswordRequireUppercase() {
        return loadSettings().isPasswordRequireUppercase();
    }

    public boolean isPasswordRequireLowercase() {
        return loadSettings().isPasswordRequireLowercase();
    }

    public boolean isPasswordRequireSpecialCharacter() {
        return loadSettings().isPasswordRequireSpecialCharacter();
    }

    public boolean isPasswordAllowConsecutiveCharacters() {
        return loadSettings().isPasswordAllowConsecutiveCharacters();
    }

    public SecuritySettingsSnapshot loadSettings() {
        Long publicBootstrapVersion = currentPublicBootstrapVersion();
        CachedSecuritySettings cached = cachedSettings;
        if (isSettingsCacheCurrent(cached, publicBootstrapVersion)) {
            return cached.settings();
        }
        synchronized (this) {
            publicBootstrapVersion = currentPublicBootstrapVersion();
            cached = cachedSettings;
            if (isSettingsCacheCurrent(cached, publicBootstrapVersion)) {
                return cached.settings();
            }
            SecuritySettingsSnapshot loaded = loadSettingsFreshFromDatabase();
            cachedSettings = new CachedSecuritySettings(
                    loaded,
                    publicBootstrapVersion,
                    System.currentTimeMillis() + SETTINGS_CACHE_TTL_MS
            );
            return loaded;
        }
    }

    public SecuritySettingsSnapshot loadSettingsFresh() {
        return loadSettingsFreshFromDatabase();
    }

    private SecuritySettingsSnapshot loadSettingsFreshFromDatabase() {
        Map<String, String> values = loadConfigValues(SETTINGS_KEYS);
        return new SecuritySettingsSnapshot(
                resolveSeconds(values, IDLE_TIMEOUT_KEY, securityProperties.getIdleTimeoutSeconds()),
                resolveSeconds(values, ACCESS_TOKEN_EXPIRE_KEY, securityProperties.getAccessTokenExpireSeconds()),
                resolveSeconds(values, REFRESH_TOKEN_EXPIRE_KEY, securityProperties.getRefreshTokenExpireSeconds()),
                resolveBoolean(values, ALLOW_MULTI_DEVICE_LOGIN_KEY, securityProperties.isAllowMultiDeviceLogin()),
                resolveBoolean(values, CAPTCHA_ENABLED_KEY, securityProperties.isCaptchaEnabled()),
                resolveCaptchaType(values, CAPTCHA_TYPE_KEY, securityProperties.getCaptchaType()),
                resolvePositiveLong(values, LOGIN_DEFENSE_WINDOW_MINUTES_KEY, securityProperties.getLoginDefenseWindowMinutes()),
                resolvePositiveLong(values, LOGIN_MAX_VALIDATION_ATTEMPTS_KEY, securityProperties.getLoginMaxValidationAttempts()),
                resolvePositiveLong(values, LOGIN_MAX_FAILURE_COUNT_KEY, securityProperties.getLoginMaxFailureCount()),
                resolvePositiveLong(values, VERIFICATION_CODE_EXPIRE_SECONDS_KEY, securityProperties.getVerificationCodeExpireSeconds()),
                resolvePositiveLong(values, VERIFICATION_CODE_COOLDOWN_SECONDS_KEY, securityProperties.getVerificationCodeCooldownSeconds()),
                resolvePositiveLong(values, PASSWORD_MIN_LENGTH_KEY, securityProperties.getPasswordMinLength()),
                resolveBoolean(values, PASSWORD_REQUIRE_UPPERCASE_KEY, securityProperties.isPasswordRequireUppercase()),
                resolveBoolean(values, PASSWORD_REQUIRE_LOWERCASE_KEY, securityProperties.isPasswordRequireLowercase()),
                resolveBoolean(values, PASSWORD_REQUIRE_SPECIAL_CHARACTER_KEY, securityProperties.isPasswordRequireSpecialCharacter()),
                resolveBoolean(values, PASSWORD_ALLOW_CONSECUTIVE_CHARACTERS_KEY, securityProperties.isPasswordAllowConsecutiveCharacters())
        );
    }

    public SecuritySettingsSnapshot updateSettings(SecuritySettingsSnapshot request, CurrentUser operator) {
        CurrentUser trustedOperator = requireTrustedOperator(operator);
        currentUpdateOperator.set(trustedOperator);
        try {
        validatePositive(request.getIdleTimeoutSeconds(), "空闲超时时间");
        validatePositive(request.getAccessTokenExpireSeconds(), "access token 过期时间");
        validatePositive(request.getRefreshTokenExpireSeconds(), "refresh token 刷新时限");
        validatePositive(request.getLoginDefenseWindowMinutes(), "统计窗口");
        validatePositive(request.getLoginMaxValidationAttempts(), "最大验证次数");
        validatePositive(request.getLoginMaxFailureCount(), "最大错误次数");
        validatePositive(request.getVerificationCodeExpireSeconds(), "验证码有效期");
        validatePositive(request.getVerificationCodeCooldownSeconds(), "验证码重发冷却");
        validatePositive(request.getPasswordMinLength(), "最短长度");
        String captchaType = normalizeCaptchaType(request.getCaptchaType());

        upsertConfig(
                IDLE_TIMEOUT_KEY,
                "空闲超时时间",
                request.getIdleTimeoutSeconds(),
                "会话在无操作状态下允许保持的秒数"
        );
        upsertConfig(
                ACCESS_TOKEN_EXPIRE_KEY,
                "Access Token 过期时间",
                request.getAccessTokenExpireSeconds(),
                "Access Token 的有效秒数"
        );
        upsertConfig(
                REFRESH_TOKEN_EXPIRE_KEY,
                "Refresh Token 刷新时限",
                request.getRefreshTokenExpireSeconds(),
                "Refresh Token 的有效秒数"
        );
        upsertConfig(
                ALLOW_MULTI_DEVICE_LOGIN_KEY,
                "多设备登录",
                request.isAllowMultiDeviceLogin(),
                "是否允许同一账号在多个设备同时在线"
        );
        upsertConfig(
                CAPTCHA_ENABLED_KEY,
                "验证码开关",
                request.isCaptchaEnabled(),
                "是否开启登录时的人机验证码"
        );
        upsertConfig(
                CAPTCHA_TYPE_KEY,
                "验证码类型",
                captchaType,
                "验证码类型：IMAGE=图片验证码"
        );
        upsertConfig(
                LOGIN_DEFENSE_WINDOW_MINUTES_KEY,
                "登录防御统计窗口",
                request.getLoginDefenseWindowMinutes(),
                "统计登录尝试与错误次数的时间窗口（分钟）"
        );
        upsertConfig(
                LOGIN_MAX_VALIDATION_ATTEMPTS_KEY,
                "最大验证次数",
                request.getLoginMaxValidationAttempts(),
                "统计窗口内允许的最大验证码/登录验证尝试次数"
        );
        upsertConfig(
                LOGIN_MAX_FAILURE_COUNT_KEY,
                "最大错误次数",
                request.getLoginMaxFailureCount(),
                "统计窗口内允许的最大登录失败次数"
        );
        upsertConfig(
                VERIFICATION_CODE_EXPIRE_SECONDS_KEY,
                "验证码有效期",
                request.getVerificationCodeExpireSeconds(),
                "短信/邮箱验证码的有效秒数"
        );
        upsertConfig(
                VERIFICATION_CODE_COOLDOWN_SECONDS_KEY,
                "验证码重发冷却",
                request.getVerificationCodeCooldownSeconds(),
                "同一账号同一验证码渠道再次发送前需要等待的秒数"
        );
        upsertConfig(
                PASSWORD_MIN_LENGTH_KEY,
                "密码最短长度",
                request.getPasswordMinLength(),
                "用户密码允许的最少字符数"
        );
        upsertConfig(
                PASSWORD_REQUIRE_UPPERCASE_KEY,
                "密码必须包含大写字母",
                request.isPasswordRequireUppercase(),
                "强制密码包含 A-Z"
        );
        upsertConfig(
                PASSWORD_REQUIRE_LOWERCASE_KEY,
                "密码必须包含小写字母",
                request.isPasswordRequireLowercase(),
                "强制密码包含 a-z"
        );
        upsertConfig(
                PASSWORD_REQUIRE_SPECIAL_CHARACTER_KEY,
                "密码必须包含特殊字符",
                request.isPasswordRequireSpecialCharacter(),
                "强制密码包含特殊字符"
        );
        upsertConfig(
                PASSWORD_ALLOW_CONSECUTIVE_CHARACTERS_KEY,
                "允许连续字符",
                request.isPasswordAllowConsecutiveCharacters(),
                "是否允许密码中出现连续字符"
        );

        clearCache();
        if (readModelVersionService != null) {
            readModelVersionService.bump(
                    READ_MODEL_CONTEXT_PLATFORM,
                    READ_MODEL_SCOPE_PUBLIC_BOOTSTRAP,
                    "security-update"
            );
        }
        return loadSettings();
        } finally {
            currentUpdateOperator.remove();
        }
    }

    private Map<String, String> loadConfigValues(List<String> keys) {
        return sysConfigMapper.listEffectiveValues(PLATFORM_SCOPE, keys).stream()
                .filter(item -> StringUtils.hasText(item.getConfigKey()))
                .collect(Collectors.toMap(
                        SysConfigEntity::getConfigKey,
                        item -> item.getConfigValue() == null ? "" : item.getConfigValue(),
                        (first, ignored) -> first
                ));
    }

    private void clearCache() {
        cachedSettings = null;
        cachedReadModelVersion = null;
    }

    private boolean isSettingsCacheCurrent(CachedSecuritySettings cached, Long publicBootstrapVersion) {
        if (cached == null || cached.settings() == null) {
            return false;
        }
        if (publicBootstrapVersion == null) {
            return !cached.isExpired();
        }
        return Objects.equals(cached.publicBootstrapVersion(), publicBootstrapVersion);
    }

    private Long currentPublicBootstrapVersion() {
        if (readModelVersionService == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        CachedReadModelVersion cached = cachedReadModelVersion;
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
        cachedReadModelVersion = new CachedReadModelVersion(version, now + READ_MODEL_VERSION_CACHE_TTL_MS);
        return version;
    }

    private long resolveSeconds(Map<String, String> values, String configKey, long defaultValue) {
        String configValue = values.get(configKey);
        if (!StringUtils.hasText(configValue)) {
            return defaultValue;
        }

        try {
            long parsed = Long.parseLong(configValue.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private boolean resolveBoolean(Map<String, String> values, String configKey, boolean defaultValue) {
        String configValue = values.get(configKey);
        if (!StringUtils.hasText(configValue)) {
            return defaultValue;
        }

        String normalized = configValue.trim().toLowerCase();
        if ("1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized) || "on".equals(normalized)) {
            return true;
        }
        if ("0".equals(normalized) || "false".equals(normalized) || "no".equals(normalized) || "off".equals(normalized)) {
            return false;
        }
        return defaultValue;
    }

    private String resolveCaptchaType(Map<String, String> values, String configKey, String defaultValue) {
        String configValue = values.get(configKey);
        if (!StringUtils.hasText(configValue)) {
            return normalizeCaptchaType(defaultValue);
        }

        return normalizeCaptchaType(configValue);
    }

    private long resolvePositiveLong(Map<String, String> values, String configKey, long defaultValue) {
        String configValue = values.get(configKey);
        if (!StringUtils.hasText(configValue)) {
            return defaultValue;
        }

        try {
            long parsed = Long.parseLong(configValue.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private void validatePositive(long value, String fieldName) {
        if (value <= 0) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, fieldName + "必须大于0");
        }
    }

    private void upsertConfig(String configKey, String configName, long configValue, String remark) {
        upsertConfig(configKey, configName, String.valueOf(configValue), remark);
    }

    private void upsertConfig(String configKey, String configName, boolean configValue, String remark) {
        upsertConfig(configKey, configName, configValue ? "1" : "0", remark);
    }

    private void upsertConfig(String configKey, String configName, String configValue, String remark) {
        CurrentUser operator = requireTrustedOperator(currentUpdateOperator.get());
        Long operatorId = operator.getUserId();
        String operatorUuid = operator.getUserUuid();
        SysConfigEntity entity = new SysConfigEntity();
        entity.setConfigKey(configKey);
        entity.setConfigName(configName);
        entity.setConfigValue(configValue);
        entity.setIsSystem(1);
        entity.setRemark(remark);
        entity.setCreatedBy(operatorId);
        entity.setCreatedByUuid(operatorUuid);
        entity.setUpdatedBy(operatorId);
        entity.setUpdatedByUuid(operatorUuid);
        sysConfigMapper.upsertPlatformConfig(entity);
    }

    private CurrentUser requireTrustedOperator(CurrentUser operator) {
        if (sessionAuthenticationService != null) {
            CurrentUser refreshedOperator = requireTrustedAuthenticatedCurrentUser(
                    sessionAuthenticationService.authenticateSessionTicket(
                            operator == null ? null : operator.getSessionId(),
                            operator == null ? null : operator.getUserId(),
                            operator == null ? null : operator.getUserUuid(),
                            operator == null ? null : operator.getSimulatedRoleId(),
                            operator == null ? null : operator.getSessionVersion(),
                            operator == null ? null : operator.getPermissionsVersion()
                    )
            );
            if (operator != null) {
                copyTrustedCurrentUser(operator, refreshedOperator);
                return operator;
            }
            return refreshedOperator;
        }
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(operator)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "A trusted operator identity is required");
        }
        return operator;
    }

    private CurrentUser requireTrustedAuthenticatedCurrentUser(SessionAuthenticationService.AuthenticatedAccess authenticatedAccess) {
        CurrentUser refreshedOperator = authenticatedAccess == null ? null : authenticatedAccess.currentUser();
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(refreshedOperator)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "A trusted operator identity is required");
        }
        return refreshedOperator;
    }

    private void copyTrustedCurrentUser(CurrentUser target, CurrentUser source) {
        target.setUserId(source.getUserId());
        target.setUserUuid(source.getUserUuid());
        target.setUsername(source.getUsername());
        target.setSessionId(source.getSessionId());
        target.setSessionVersion(source.getSessionVersion());
        target.setAuthenticated(source.isAuthenticated());
        target.setPermissions(source.getPermissions());
        target.setRoleIds(source.getRoleIds());
        target.setPrimaryDeptId(source.getPrimaryDeptId());
        target.setDeptIds(source.getDeptIds());
        target.setDescendantDeptIds(source.getDescendantDeptIds());
        target.setDataScopes(source.getDataScopes());
        target.setPermissionsVersion(source.getPermissionsVersion());
        target.setRequiresPasswordChange(source.getRequiresPasswordChange());
        target.setDefaultHomePath(source.getDefaultHomePath());
        target.setSimulatedRoleId(source.getSimulatedRoleId());
        target.setLoginType(source.getLoginType());
    }

    private String normalizeCaptchaType(String value) {
        if (!StringUtils.hasText(value)) {
            return "IMAGE";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return "SLIDER".equals(normalized) ? "SLIDER" : "IMAGE";
    }

    public static class SecuritySettingsSnapshot {
        private long idleTimeoutSeconds;
        private long accessTokenExpireSeconds;
        private long refreshTokenExpireSeconds;
        private boolean allowMultiDeviceLogin;
        private boolean captchaEnabled;
        private String captchaType;
        private long loginDefenseWindowMinutes;
        private long loginMaxValidationAttempts;
        private long loginMaxFailureCount;
        private long verificationCodeExpireSeconds;
        private long verificationCodeCooldownSeconds;
        private long passwordMinLength;
        private boolean passwordRequireUppercase;
        private boolean passwordRequireLowercase;
        private boolean passwordRequireSpecialCharacter;
        private boolean passwordAllowConsecutiveCharacters;

        public SecuritySettingsSnapshot() {
        }

        public SecuritySettingsSnapshot(
                long idleTimeoutSeconds,
                long accessTokenExpireSeconds,
                long refreshTokenExpireSeconds,
                boolean allowMultiDeviceLogin,
                boolean captchaEnabled,
                String captchaType,
                long loginDefenseWindowMinutes,
                long loginMaxValidationAttempts,
                long loginMaxFailureCount,
                long verificationCodeExpireSeconds,
                long verificationCodeCooldownSeconds,
                long passwordMinLength,
                boolean passwordRequireUppercase,
                boolean passwordRequireLowercase,
                boolean passwordRequireSpecialCharacter,
                boolean passwordAllowConsecutiveCharacters
        ) {
            this.idleTimeoutSeconds = idleTimeoutSeconds;
            this.accessTokenExpireSeconds = accessTokenExpireSeconds;
            this.refreshTokenExpireSeconds = refreshTokenExpireSeconds;
            this.allowMultiDeviceLogin = allowMultiDeviceLogin;
            this.captchaEnabled = captchaEnabled;
            this.captchaType = captchaType;
            this.loginDefenseWindowMinutes = loginDefenseWindowMinutes;
            this.loginMaxValidationAttempts = loginMaxValidationAttempts;
            this.loginMaxFailureCount = loginMaxFailureCount;
            this.verificationCodeExpireSeconds = verificationCodeExpireSeconds;
            this.verificationCodeCooldownSeconds = verificationCodeCooldownSeconds;
            this.passwordMinLength = passwordMinLength;
            this.passwordRequireUppercase = passwordRequireUppercase;
            this.passwordRequireLowercase = passwordRequireLowercase;
            this.passwordRequireSpecialCharacter = passwordRequireSpecialCharacter;
            this.passwordAllowConsecutiveCharacters = passwordAllowConsecutiveCharacters;
        }

        public long getIdleTimeoutSeconds() {
            return idleTimeoutSeconds;
        }

        public void setIdleTimeoutSeconds(long idleTimeoutSeconds) {
            this.idleTimeoutSeconds = idleTimeoutSeconds;
        }

        public long getAccessTokenExpireSeconds() {
            return accessTokenExpireSeconds;
        }

        public void setAccessTokenExpireSeconds(long accessTokenExpireSeconds) {
            this.accessTokenExpireSeconds = accessTokenExpireSeconds;
        }

        public long getRefreshTokenExpireSeconds() {
            return refreshTokenExpireSeconds;
        }

        public void setRefreshTokenExpireSeconds(long refreshTokenExpireSeconds) {
            this.refreshTokenExpireSeconds = refreshTokenExpireSeconds;
        }

        public boolean isAllowMultiDeviceLogin() {
            return allowMultiDeviceLogin;
        }

        public void setAllowMultiDeviceLogin(boolean allowMultiDeviceLogin) {
            this.allowMultiDeviceLogin = allowMultiDeviceLogin;
        }

        public boolean isCaptchaEnabled() {
            return captchaEnabled;
        }

        public void setCaptchaEnabled(boolean captchaEnabled) {
            this.captchaEnabled = captchaEnabled;
        }

        public String getCaptchaType() {
            return captchaType;
        }

        public void setCaptchaType(String captchaType) {
            this.captchaType = captchaType;
        }

        public long getLoginDefenseWindowMinutes() {
            return loginDefenseWindowMinutes;
        }

        public void setLoginDefenseWindowMinutes(long loginDefenseWindowMinutes) {
            this.loginDefenseWindowMinutes = loginDefenseWindowMinutes;
        }

        public long getLoginMaxValidationAttempts() {
            return loginMaxValidationAttempts;
        }

        public void setLoginMaxValidationAttempts(long loginMaxValidationAttempts) {
            this.loginMaxValidationAttempts = loginMaxValidationAttempts;
        }

        public long getLoginMaxFailureCount() {
            return loginMaxFailureCount;
        }

        public void setLoginMaxFailureCount(long loginMaxFailureCount) {
            this.loginMaxFailureCount = loginMaxFailureCount;
        }

        public long getVerificationCodeExpireSeconds() {
            return verificationCodeExpireSeconds;
        }

        public void setVerificationCodeExpireSeconds(long verificationCodeExpireSeconds) {
            this.verificationCodeExpireSeconds = verificationCodeExpireSeconds;
        }

        public long getVerificationCodeCooldownSeconds() {
            return verificationCodeCooldownSeconds;
        }

        public void setVerificationCodeCooldownSeconds(long verificationCodeCooldownSeconds) {
            this.verificationCodeCooldownSeconds = verificationCodeCooldownSeconds;
        }

        public long getPasswordMinLength() {
            return passwordMinLength;
        }

        public void setPasswordMinLength(long passwordMinLength) {
            this.passwordMinLength = passwordMinLength;
        }

        public boolean isPasswordRequireUppercase() {
            return passwordRequireUppercase;
        }

        public void setPasswordRequireUppercase(boolean passwordRequireUppercase) {
            this.passwordRequireUppercase = passwordRequireUppercase;
        }

        public boolean isPasswordRequireLowercase() {
            return passwordRequireLowercase;
        }

        public void setPasswordRequireLowercase(boolean passwordRequireLowercase) {
            this.passwordRequireLowercase = passwordRequireLowercase;
        }

        public boolean isPasswordRequireSpecialCharacter() {
            return passwordRequireSpecialCharacter;
        }

        public void setPasswordRequireSpecialCharacter(boolean passwordRequireSpecialCharacter) {
            this.passwordRequireSpecialCharacter = passwordRequireSpecialCharacter;
        }

        public boolean isPasswordAllowConsecutiveCharacters() {
            return passwordAllowConsecutiveCharacters;
        }

        public void setPasswordAllowConsecutiveCharacters(boolean passwordAllowConsecutiveCharacters) {
            this.passwordAllowConsecutiveCharacters = passwordAllowConsecutiveCharacters;
        }
    }

    private record CachedSecuritySettings(
            SecuritySettingsSnapshot settings,
            Long publicBootstrapVersion,
            long expiresAtMillis
    ) {
        private boolean isExpired() {
            return System.currentTimeMillis() > expiresAtMillis;
        }
    }

    private record CachedReadModelVersion(Long version, long expiresAtMillis) {
    }
}
