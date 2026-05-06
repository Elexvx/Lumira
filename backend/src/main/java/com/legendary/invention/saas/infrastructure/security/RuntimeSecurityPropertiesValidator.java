package com.legendary.invention.saas.infrastructure.security;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Set;

@Component
public class RuntimeSecurityPropertiesValidator implements ApplicationRunner {

    static final Set<String> UNSAFE_JWT_SECRETS = Set.of(
            "replace_me",
            "saas_foundation_jwt_secret_for_dev_env_please_change_me_2026"
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
        validateDatabasePassword();
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

    private boolean isProdProfileActive() {
        return Arrays.stream(environment.getActiveProfiles()).anyMatch("prod"::equalsIgnoreCase);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
