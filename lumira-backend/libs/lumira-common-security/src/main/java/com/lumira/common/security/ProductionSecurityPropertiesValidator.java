package com.lumira.common.security;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Set;

@Component
public class ProductionSecurityPropertiesValidator implements InitializingBean {

    private static final Set<String> PROD_PROFILES = Set.of("prod", "production");
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
        requireSecret("saas.job.internal-token", "SAAS_JOB_INTERNAL_TOKEN", UNSAFE_JOB_TOKENS, 24);
        requireSecret("saas.plugin.signature-secret", "PLUGIN_SIGNATURE_SECRET", UNSAFE_PLUGIN_SECRETS, 32, false);
        requireSecret("xxl.job.accessToken", "XXL_JOB_ACCESS_TOKEN", UNSAFE_XXL_TOKENS, 16, false);
        requireCorsAllowedOrigins();
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
        if (unsafeValues.contains(value) || value.startsWith("change-me-")) {
            throw new IllegalStateException("生产环境 " + envName + " 不能使用示例值或默认弱配置");
        }
        if (value.length() < minLength) {
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
}
