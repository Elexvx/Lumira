package com.lumira.saas.infrastructure.security;

import java.util.Arrays;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RuntimeSecurityPropertiesValidator implements ApplicationRunner {

    static final Set<String> UNSAFE_JWT_SECRETS = Set.of(
            "replace_me",
            "saas_foundation_jwt_secret_for_dev_env_please_change_me_2026"
    );
    static final Set<String> UNSAFE_FIELD_SECRETS = Set.of(
            "replace_me",
            "lumira-ai-secret",
            "saas_foundation_field_secret_for_dev_env_please_change_me_2026"
    );
    static final Set<String> UNSAFE_DATABASE_PASSWORDS = Set.of("123456", "root", "password");

    private final SecurityProperties securityProperties;
    private final Environment environment;

    public RuntimeSecurityPropertiesValidator(
            SecurityProperties securityProperties,
            Environment environment
    ) {
        this.securityProperties = securityProperties;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!isProdProfileActive()) {
            return;
        }
        validateJwtSecret();
        validateFieldSecret();
        validateDatabasePassword();
        validateCorsAllowedOrigins();
    }

    private void validateJwtSecret() {
        String jwtSecret = normalize(securityProperties.getJwtSecret());
        if (!StringUtils.hasText(jwtSecret) || UNSAFE_JWT_SECRETS.contains(jwtSecret)) {
            throw new IllegalStateException("生产环境必须配置安全的 JWT_SECRET，不能使用默认 JWT 密钥");
        }
        if (jwtSecret.length() < 32) {
            throw new IllegalStateException("生产环境 JWT_SECRET 长度不能少于 32 个字符");
        }
    }

    private void validateDatabasePassword() {
        String password = normalize(environment.getProperty("spring.datasource.password"));
        if (!StringUtils.hasText(password) || UNSAFE_DATABASE_PASSWORDS.contains(password)) {
            throw new IllegalStateException("生产环境必须配置安全的 DB_PASSWORD，不能使用默认数据库密码");
        }
    }

    private void validateFieldSecret() {
        String fieldSecret = normalize(securityProperties.getFieldSecret());
        if (!StringUtils.hasText(fieldSecret) || UNSAFE_FIELD_SECRETS.contains(fieldSecret) || fieldSecret.startsWith("change-me-")) {
            throw new IllegalStateException("生产环境必须配置安全的 FIELD_SECRET，不能使用默认字段加密密钥");
        }
        if (fieldSecret.length() < 32) {
            throw new IllegalStateException("生产环境 FIELD_SECRET 长度不能少于 32 个字符");
        }
    }

    private void validateCorsAllowedOrigins() {
        String corsAllowedOrigins = normalize(environment.getProperty("saas.web.cors-allowed-origin-patterns"));
        if (!StringUtils.hasText(corsAllowedOrigins)) {
            throw new IllegalStateException("生产环境必须配置 CORS_ALLOWED_ORIGIN_PATTERNS，不能使用空白 CORS 白名单");
        }
        if (corsAllowedOrigins.contains("localhost") || corsAllowedOrigins.contains("127.0.0.1")) {
            throw new IllegalStateException("生产环境 CORS_ALLOWED_ORIGIN_PATTERNS 不能包含本地调试地址");
        }
    }

    private boolean isProdProfileActive() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile)
                        || "production".equalsIgnoreCase(profile)
                        || "cloud".equalsIgnoreCase(profile));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
