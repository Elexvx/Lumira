package com.legendary.invention.auth.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Set;

@Component
public class RuntimeSecurityPropertiesValidator implements ApplicationRunner {

    private static final Set<String> UNSAFE_JWT_SECRETS = Set.of(
            "replace_me",
            "saas_foundation_jwt_secret_for_dev_env_please_change_me_2026"
    );

    private final SecurityProperties securityProperties;
    private final Environment environment;

    public RuntimeSecurityPropertiesValidator(SecurityProperties securityProperties, Environment environment) {
        this.securityProperties = securityProperties;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!isProdProfileActive()) {
            return;
        }
        String jwtSecret = normalize(securityProperties.getJwtSecret());
        if (!StringUtils.hasText(jwtSecret) || UNSAFE_JWT_SECRETS.contains(jwtSecret)) {
            throw new IllegalStateException("生产环境 auth-service 必须配置安全的 JWT_SECRET，不能使用默认 JWT 密钥");
        }
        if (jwtSecret.length() < 32) {
            throw new IllegalStateException("生产环境 auth-service JWT_SECRET 长度不能少于 32 个字符");
        }
    }

    private boolean isProdProfileActive() {
        return Arrays.stream(environment.getActiveProfiles()).anyMatch("prod"::equalsIgnoreCase);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
