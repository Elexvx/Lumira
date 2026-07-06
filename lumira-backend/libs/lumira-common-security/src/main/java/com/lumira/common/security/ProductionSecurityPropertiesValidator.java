package com.lumira.common.security;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class ProductionSecurityPropertiesValidator implements InitializingBean {

    private static final Set<String> PROD_PROFILES = Set.of("prod", "production", "cloud");
    private static final Set<String> UNSAFE_DB_PASSWORDS = Set.of("123456", "root", "password");
    private static final Set<String> UNSAFE_REDIS_PASSWORDS = Set.of("123456", "redis", "password");
    private static final Set<String> UNSAFE_JWT_SECRETS = Set.of(
            "replace_me",
            "saas_foundation_jwt_secret_for_dev_env_please_change_me_2026"
    );
    private static final Set<String> UNSAFE_PLUGIN_SECRETS = Set.of("change-me-plugin-signature-secret");
    private static final Set<String> UNSAFE_FIELD_SECRETS = Set.of(
            "lumira-ai-secret",
            "saas_foundation_field_secret_for_dev_env_please_change_me_2026"
    );
    private static final Set<String> UNSAFE_JOB_TOKENS = Set.of("lumira-job-token", "change-me-internal-job-token");
    private static final Set<String> UNSAFE_XXL_TOKENS = Set.of("lumira-xxl-job-token", "change-me-xxl-job-token");
    private static final Set<String> SENSITIVE_PUBLIC_PATHS = Set.of(
            "/actuator/prometheus",
            "/actuator/metrics"
    );
    private static final Set<String> AUTH_SYSTEM_REQUIRED_APPS = Set.of(
            "lumira-server",
            "system-service",
            "lumira-system",
            "auth-service",
            "lumira-auth"
    );

    private final Environment environment;

    public ProductionSecurityPropertiesValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        if (!isProductionProfile()) {
            return;
        }

        requireSecret("spring.datasource.password", "DB_PASSWORD", UNSAFE_DB_PASSWORDS, 8);
        requireSecret("spring.data.redis.password", "REDIS_PASSWORD", UNSAFE_REDIS_PASSWORDS, 8);
        requireSecret("saas.security.jwt-secret", "JWT_SECRET", UNSAFE_JWT_SECRETS, 32);
        requireSecret("saas.security.field-secret", "FIELD_SECRET", UNSAFE_FIELD_SECRETS, 32);
        requireSecret("saas.plugin.signature-secret", "PLUGIN_SIGNATURE_SECRET", UNSAFE_PLUGIN_SECRETS, 32, false);
        requireSecret("xxl.job.accessToken", "XXL_JOB_ACCESS_TOKEN", UNSAFE_XXL_TOKENS, 16, false);
        requireScopedInternalTokens();
        requireCorsAllowedOrigins();
        requireNoInternalPermitPaths();
        requireEncryptedLoginPassword();
    }

    private boolean isProductionProfile() {
        return Arrays.stream(environment.getActiveProfiles()).anyMatch(profile -> PROD_PROFILES.contains(profile.toLowerCase()));
    }

    private void requireSecret(String propertyName, String envName, Set<String> unsafeValues, int minLength) {
        requireSecret(propertyName, envName, unsafeValues, minLength, true);
    }

    private void requireSecret(String propertyName, String envName, Set<String> unsafeValues, int minLength, boolean required) {
        String value = environment.getProperty(propertyName);
        if (!StringUtils.hasText(value)) {
            if (required) {
                throw new IllegalStateException("生产环境必须配置 " + envName + "，不能使用空值或默认弱配置");
            }
            return;
        }
        String normalized = value.trim();
        String lower = normalized.toLowerCase();
        if (unsafeValues.contains(normalized)
                || lower.startsWith("change-me-")
                || lower.contains("change_me")
                || lower.contains("dev_env")) {
            throw new IllegalStateException("生产环境 " + envName + " 不能使用示例值或默认弱配置");
        }
        if (normalized.length() < minLength) {
            throw new IllegalStateException("生产环境 " + envName + " 长度不能少于 " + minLength + " 个字符");
        }
    }

    private void requireCorsAllowedOrigins() {
        String value = environment.getProperty("saas.web.cors-allowed-origin-patterns");
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("生产环境必须配置 CORS_ALLOWED_ORIGIN_PATTERNS，不能使用空白 CORS 白名单");
        }
        if (value.contains("localhost") || value.contains("127.0.0.1")) {
            throw new IllegalStateException("生产环境 CORS_ALLOWED_ORIGIN_PATTERNS 不能包含本地调试地址");
        }
        if (value.contains("*")) {
            throw new IllegalStateException("生产环境 CORS_ALLOWED_ORIGIN_PATTERNS 不能包含通配符域名，请配置精确前端 Origin");
        }
    }

    private void requireNoInternalPermitPaths() {
        for (String permitPath : configuredPermitPaths()) {
            String normalized = permitPath == null ? "" : permitPath.trim();
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            if (normalized.equals("/**")
                    || normalized.equals("/api/**")
                    || SENSITIVE_PUBLIC_PATHS.contains(normalized)
                    || normalized.endsWith("/metrics")
                    || normalized.startsWith("/internal/")
                    || normalized.contains("/internal/")) {
                throw new IllegalStateException("Production permit paths must not expose protected or internal APIs: " + normalized);
            }
        }
    }

    private void requireEncryptedLoginPassword() {
        String value = Optional.ofNullable(environment.getProperty("saas.security.allow-plaintext-login-password"))
                .or(() -> Optional.ofNullable(environment.getProperty("ALLOW_PLAINTEXT_LOGIN_PASSWORD")))
                .orElse("false");
        if ("true".equalsIgnoreCase(value.trim())) {
            throw new IllegalStateException("Production must not allow plaintext login password transport");
        }
    }

    private void requireScopedInternalTokens() {
        Map<String, String> tokenByName = new LinkedHashMap<>();
        if (requiresSystemInternalToken()) {
            tokenByName.put("SAAS_INTERNAL_SYSTEM_TOKEN", normalizedRequiredSecret(
                    List.of("saas.internal.system-token", "saas.job.internal.system-token"),
                    "SAAS_INTERNAL_SYSTEM_TOKEN",
                    UNSAFE_JOB_TOKENS,
                    24
            ));
        }
        if (requiresAuthInternalTokens()) {
            tokenByName.put("SAAS_INTERNAL_AUTH_TOKEN", normalizedRequiredSecret(
                    List.of("saas.internal.auth-token", "saas.job.internal.auth-token"),
                    "SAAS_INTERNAL_AUTH_TOKEN",
                    UNSAFE_JOB_TOKENS,
                    24
            ));
        }
        if (requiresAuthSystemInternalToken()) {
            tokenByName.put("SAAS_INTERNAL_AUTH_SYSTEM_TOKEN", normalizedRequiredSecret(
                    List.of("saas.internal.auth-system-token", "saas.job.internal.auth-system-token"),
                    "SAAS_INTERNAL_AUTH_SYSTEM_TOKEN",
                    UNSAFE_JOB_TOKENS,
                    24
            ));
        }
        tokenByName.put("SAAS_INTERNAL_FILE_TOKEN", normalizedRequiredSecret(
                List.of("saas.internal.file-token", "saas.job.internal.file-token"),
                "SAAS_INTERNAL_FILE_TOKEN",
                UNSAFE_JOB_TOKENS,
                24
        ));
        tokenByName.put("SAAS_INTERNAL_MESSAGE_TOKEN", normalizedRequiredSecret(
                List.of("saas.internal.message-token", "saas.job.internal.message-token"),
                "SAAS_INTERNAL_MESSAGE_TOKEN",
                UNSAFE_JOB_TOKENS,
                24
        ));
        tokenByName.put("SAAS_INTERNAL_PAYMENT_TOKEN", normalizedRequiredSecret(
                List.of("saas.internal.payment-token", "saas.job.internal.payment-token"),
                "SAAS_INTERNAL_PAYMENT_TOKEN",
                UNSAFE_JOB_TOKENS,
                24
        ));
        tokenByName.put("SAAS_INTERNAL_PLUGIN_TOKEN", normalizedRequiredSecret(
                List.of("saas.internal.plugin-token", "saas.job.internal.plugin-token"),
                "SAAS_INTERNAL_PLUGIN_TOKEN",
                UNSAFE_JOB_TOKENS,
                24
        ));
        if (requiresTeamInternalToken()) {
            tokenByName.put("SAAS_INTERNAL_TEAM_TOKEN", normalizedRequiredSecret(
                    List.of("saas.internal.team-token", "saas.job.internal.team-token"),
                    "SAAS_INTERNAL_TEAM_TOKEN",
                    UNSAFE_JOB_TOKENS,
                    24
            ));
        }
        tokenByName.put("SAAS_INTERNAL_JOB_TOKEN", normalizedRequiredSecret(
                List.of("saas.internal.job-token", "saas.job.internal.job-token"),
                "SAAS_INTERNAL_JOB_TOKEN",
                UNSAFE_JOB_TOKENS,
                24
        ));

        Map<String, String> seenByToken = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : tokenByName.entrySet()) {
            String firstName = seenByToken.putIfAbsent(entry.getValue(), entry.getKey());
            if (firstName != null) {
                throw new IllegalStateException("Production internal tokens must be scoped and unique: "
                        + firstName + " and " + entry.getKey());
            }
        }
    }

    private boolean requiresAuthInternalTokens() {
        String applicationName = environment.getProperty("spring.application.name", "").trim();
        return !"lumira-job-executor".equalsIgnoreCase(applicationName)
                && !"job-executor".equalsIgnoreCase(applicationName)
                && !"lumira-async".equalsIgnoreCase(applicationName);
    }

    private boolean requiresSystemInternalToken() {
        return !isJobExecutorApplication() && !isAsyncApplication();
    }

    private boolean requiresTeamInternalToken() {
        return !isJobExecutorApplication() && !isAsyncApplication();
    }

    private boolean requiresAuthSystemInternalToken() {
        String applicationName = environment.getProperty("spring.application.name", "").trim().toLowerCase();
        if (!StringUtils.hasText(applicationName)) {
            return true;
        }
        return AUTH_SYSTEM_REQUIRED_APPS.contains(applicationName);
    }

    private boolean isJobExecutorApplication() {
        String applicationName = environment.getProperty("spring.application.name", "").trim();
        return "lumira-job-executor".equalsIgnoreCase(applicationName)
                || "job-executor".equalsIgnoreCase(applicationName);
    }

    private boolean isAsyncApplication() {
        String applicationName = environment.getProperty("spring.application.name", "").trim();
        return "lumira-async".equalsIgnoreCase(applicationName);
    }

    private String normalizedRequiredSecret(List<String> propertyNames, String envName, Set<String> unsafeValues, int minLength) {
        String value = propertyNames.stream()
                .map(environment::getProperty)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
        validateSecretValue(value, envName, unsafeValues, minLength, true);
        return value.trim();
    }

    private void validateSecretValue(String value, String envName, Set<String> unsafeValues, int minLength, boolean required) {
        if (!StringUtils.hasText(value)) {
            if (required) {
                throw new IllegalStateException("Production requires " + envName + " and cannot use an empty internal token");
            }
            return;
        }
        String normalized = value.trim();
        String lower = normalized.toLowerCase();
        if (unsafeValues.contains(normalized)
                || lower.startsWith("change-me-")
                || lower.contains("change_me")
                || lower.contains("dev_env")) {
            throw new IllegalStateException("Production " + envName + " must not use a sample or default token");
        }
        if (normalized.length() < minLength) {
            throw new IllegalStateException("Production " + envName + " length must be at least " + minLength + " characters");
        }
    }

    private String[] configuredPermitPaths() {
        List<String> configured = bindPermitPaths("saas.security.permit-paths");
        if (configured.isEmpty()) {
            configured = bindPermitPaths("saas.security.permitPaths");
        }
        return configured.toArray(String[]::new);
    }

    private List<String> bindPermitPaths(String propertyName) {
        List<String> indexed = indexedPermitPaths(propertyName);
        if (!indexed.isEmpty()) {
            return indexed;
        }
        return Optional.ofNullable(environment.getProperty(propertyName))
                .map(value -> Arrays.stream(value.split(","))
                        .map(String::trim)
                        .filter(StringUtils::hasText)
                        .toList())
                .orElseGet(List::of);
    }

    private List<String> indexedPermitPaths(String propertyName) {
        if (!(environment instanceof ConfigurableEnvironment configurableEnvironment)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (int index = 0; ; index++) {
            String indexedPropertyName = propertyName + "[" + index + "]";
            String value = resolveIndexedProperty(configurableEnvironment, indexedPropertyName);
            if (value == null) {
                break;
            }
            String normalized = value.trim();
            if (StringUtils.hasText(normalized)) {
                values.add(normalized);
            }
        }
        return values;
    }

    private String resolveIndexedProperty(ConfigurableEnvironment configurableEnvironment, String propertyName) {
        for (org.springframework.core.env.PropertySource<?> propertySource : configurableEnvironment.getPropertySources()) {
            if (!(propertySource instanceof EnumerablePropertySource<?> enumerablePropertySource)) {
                continue;
            }
            for (String candidate : enumerablePropertySource.getPropertyNames()) {
                if (!propertyName.equals(candidate)) {
                    continue;
                }
                Object value = enumerablePropertySource.getProperty(candidate);
                return value == null ? null : String.valueOf(value);
            }
        }
        return null;
    }
}
