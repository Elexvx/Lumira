package com.lumira.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSecurityPropertiesValidatorTest {

    @Test
    void shouldSkipValidationOutsideProductionProfiles() {
        StandardEnvironment environment = environment("dev", Map.of());
        ProductionSecurityPropertiesValidator validator = new ProductionSecurityPropertiesValidator(environment);

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void shouldAllowStrongProductionSecrets() {
        StandardEnvironment environment = environment("prod", strongProperties());
        ProductionSecurityPropertiesValidator validator = new ProductionSecurityPropertiesValidator(environment);

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void shouldAllowProductionWithoutLegacyGlobalJobToken() {
        StandardEnvironment environment = environment("prod", strongProperties());
        ProductionSecurityPropertiesValidator validator = new ProductionSecurityPropertiesValidator(environment);

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectMissingRedisPasswordInProduction() {
        Map<String, Object> properties = strongProperties();
        properties.remove("spring.data.redis.password");
        StandardEnvironment environment = environment("production", properties);
        ProductionSecurityPropertiesValidator validator = new ProductionSecurityPropertiesValidator(environment);

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REDIS_PASSWORD");
    }

    @Test
    void shouldValidateCloudProfileAsProduction() {
        Map<String, Object> properties = strongProperties();
        properties.remove("spring.data.redis.password");
        StandardEnvironment environment = environment("cloud", properties);
        ProductionSecurityPropertiesValidator validator = new ProductionSecurityPropertiesValidator(environment);

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REDIS_PASSWORD");
    }

    @Test
    void shouldRejectWildcardCorsInProduction() {
        Map<String, Object> properties = strongProperties();
        properties.put("saas.web.cors-allowed-origin-patterns", "https://*.example.com");
        StandardEnvironment environment = environment("prod", properties);
        ProductionSecurityPropertiesValidator validator = new ProductionSecurityPropertiesValidator(environment);

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS_ALLOWED_ORIGIN_PATTERNS");
    }

    @Test
    void shouldRejectServiceDevJwtSecretInProduction() {
        Map<String, Object> properties = strongProperties();
        properties.put("saas.security.jwt-secret", "saas_file_jwt_secret_for_dev_env_change_me_2026");
        StandardEnvironment environment = environment("prod", properties);
        ProductionSecurityPropertiesValidator validator = new ProductionSecurityPropertiesValidator(environment);

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void shouldRejectInternalPermitPathInProduction() {
        Map<String, Object> properties = strongProperties();
        properties.put("saas.security.permit-paths", "/api/v1/auth/login,/internal/auth/**");
        StandardEnvironment environment = environment("prod", properties);
        ProductionSecurityPropertiesValidator validator = new ProductionSecurityPropertiesValidator(environment);

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("/internal/auth/**");
    }

    @Test
    void shouldRejectIndexedInternalPermitPathInProduction() {
        Map<String, Object> properties = strongProperties();
        properties.put("saas.security.permit-paths[0]", "/api/v1/auth/login");
        properties.put("saas.security.permit-paths[1]", "/internal/auth/**");
        StandardEnvironment environment = environment("prod", properties);
        ProductionSecurityPropertiesValidator validator = new ProductionSecurityPropertiesValidator(environment);

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("/internal/auth/**");
    }

    @Test
    void shouldRejectPublicMetricsPermitPathInProduction() {
        Map<String, Object> properties = strongProperties();
        properties.put("saas.security.permit-paths", "/actuator/health,/actuator/prometheus");
        StandardEnvironment environment = environment("prod", properties);
        ProductionSecurityPropertiesValidator validator = new ProductionSecurityPropertiesValidator(environment);

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("/actuator/prometheus");
    }

    @Test
    void shouldRejectPublicMetricsAliasPermitPathInProduction() {
        Map<String, Object> properties = strongProperties();
        properties.put("saas.security.permit-paths", "/api/v2/system/metrics");
        StandardEnvironment environment = environment("prod", properties);
        ProductionSecurityPropertiesValidator validator = new ProductionSecurityPropertiesValidator(environment);

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("/api/v2/system/metrics");
    }

    @Test
    void shouldRejectPlaintextLoginPasswordTransportInProduction() {
        Map<String, Object> properties = strongProperties();
        properties.put("saas.security.allow-plaintext-login-password", "true");
        StandardEnvironment environment = environment("prod", properties);
        ProductionSecurityPropertiesValidator validator = new ProductionSecurityPropertiesValidator(environment);

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("plaintext login password");
    }

    @Test
    void shouldRejectMissingScopedInternalTokenInProduction() {
        Map<String, Object> properties = strongProperties();
        properties.remove("saas.internal.file-token");
        StandardEnvironment environment = environment("prod", properties);
        ProductionSecurityPropertiesValidator validator = new ProductionSecurityPropertiesValidator(environment);

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SAAS_INTERNAL_FILE_TOKEN");
    }

    @Test
    void shouldRejectMissingTeamScopedInternalTokenInProduction() {
        Map<String, Object> properties = strongProperties();
        properties.remove("saas.internal.team-token");
        StandardEnvironment environment = environment("prod", properties);
        ProductionSecurityPropertiesValidator validator = new ProductionSecurityPropertiesValidator(environment);

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SAAS_INTERNAL_TEAM_TOKEN");
    }

    @Test
    void shouldRejectMissingAuthSystemTokenInProduction() {
        Map<String, Object> properties = strongProperties();
        properties.remove("saas.internal.auth-system-token");
        StandardEnvironment environment = environment("prod", properties);
        ProductionSecurityPropertiesValidator validator = new ProductionSecurityPropertiesValidator(environment);

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SAAS_INTERNAL_AUTH_SYSTEM_TOKEN");
    }

    @Test
    void shouldAllowJobExecutorWithoutAuthScopedTokensInProduction() {
        Map<String, Object> properties = strongProperties();
        properties.put("spring.application.name", "lumira-job-executor");
        properties.remove("saas.internal.system-token");
        properties.remove("saas.internal.auth-token");
        properties.remove("saas.internal.auth-system-token");
        properties.remove("saas.internal.team-token");
        StandardEnvironment environment = environment("prod", properties);
        ProductionSecurityPropertiesValidator validator = new ProductionSecurityPropertiesValidator(environment);

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void shouldAllowJobExecutorWithoutControlPlaneSecretsInProduction() {
        Map<String, Object> properties = strongProperties();
        properties.put("spring.application.name", "lumira-job-executor");
        removeControlPlaneSecrets(properties);
        properties.remove("spring.data.redis.password");
        properties.remove("saas.internal.system-token");
        properties.remove("saas.internal.auth-token");
        properties.remove("saas.internal.auth-system-token");
        properties.remove("saas.internal.team-token");
        StandardEnvironment environment = environment("prod", properties);
        ProductionSecurityPropertiesValidator validator = new ProductionSecurityPropertiesValidator(environment);

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void shouldAllowAsyncWithoutAuthSystemTokenInProduction() {
        Map<String, Object> properties = strongProperties();
        properties.put("spring.application.name", "lumira-async");
        properties.remove("saas.internal.auth-system-token");
        StandardEnvironment environment = environment("prod", properties);
        ProductionSecurityPropertiesValidator validator = new ProductionSecurityPropertiesValidator(environment);

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void shouldAllowAsyncWithoutAuthTokenInProduction() {
        Map<String, Object> properties = strongProperties();
        properties.put("spring.application.name", "lumira-async");
        properties.remove("saas.internal.auth-token");
        StandardEnvironment environment = environment("prod", properties);
        ProductionSecurityPropertiesValidator validator = new ProductionSecurityPropertiesValidator(environment);

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void shouldAllowAsyncWithoutSystemAndTeamScopedTokensInProduction() {
        Map<String, Object> properties = strongProperties();
        properties.put("spring.application.name", "lumira-async");
        properties.remove("saas.internal.system-token");
        properties.remove("saas.internal.team-token");
        StandardEnvironment environment = environment("prod", properties);
        ProductionSecurityPropertiesValidator validator = new ProductionSecurityPropertiesValidator(environment);

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void shouldAllowAsyncWithoutControlPlaneSecretsInProduction() {
        Map<String, Object> properties = strongProperties();
        properties.put("spring.application.name", "lumira-async");
        removeControlPlaneSecrets(properties);
        properties.remove("saas.internal.system-token");
        properties.remove("saas.internal.auth-token");
        properties.remove("saas.internal.auth-system-token");
        properties.remove("saas.internal.team-token");
        StandardEnvironment environment = environment("prod", properties);
        ProductionSecurityPropertiesValidator validator = new ProductionSecurityPropertiesValidator(environment);

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void shouldStillRejectMissingRedisPasswordForAsyncInProduction() {
        Map<String, Object> properties = strongProperties();
        properties.put("spring.application.name", "lumira-async");
        removeControlPlaneSecrets(properties);
        properties.remove("spring.data.redis.password");
        StandardEnvironment environment = environment("prod", properties);
        ProductionSecurityPropertiesValidator validator = new ProductionSecurityPropertiesValidator(environment);

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REDIS_PASSWORD");
    }

    @Test
    void shouldAllowMessageServiceWithoutAuthSystemTokenInProduction() {
        Map<String, Object> properties = strongProperties();
        properties.put("spring.application.name", "message-service");
        properties.remove("saas.internal.auth-system-token");
        StandardEnvironment environment = environment("prod", properties);
        ProductionSecurityPropertiesValidator validator = new ProductionSecurityPropertiesValidator(environment);

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void shouldAllowAiServiceWithoutAuthSystemTokenInProduction() {
        Map<String, Object> properties = strongProperties();
        properties.put("spring.application.name", "ai-service");
        properties.remove("saas.internal.auth-system-token");
        StandardEnvironment environment = environment("prod", properties);
        ProductionSecurityPropertiesValidator validator = new ProductionSecurityPropertiesValidator(environment);

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectReusedScopedInternalTokenInProduction() {
        Map<String, Object> properties = strongProperties();
        properties.put("saas.internal.file-token", properties.get("saas.internal.message-token"));
        StandardEnvironment environment = environment("prod", properties);
        ProductionSecurityPropertiesValidator validator = new ProductionSecurityPropertiesValidator(environment);

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be scoped and unique");
    }

    @Test
    void shouldAcceptJobScopedInternalTokenAliasesInProduction() {
        Map<String, Object> properties = strongProperties();
        properties.put("spring.application.name", "lumira-job-executor");
        properties.remove("saas.internal.auth-token");
        properties.remove("saas.internal.auth-system-token");
        properties.remove("saas.internal.system-token");
        move(properties, "saas.internal.file-token", "saas.job.internal.file-token");
        move(properties, "saas.internal.message-token", "saas.job.internal.message-token");
        move(properties, "saas.internal.payment-token", "saas.job.internal.payment-token");
        move(properties, "saas.internal.plugin-token", "saas.job.internal.plugin-token");
        properties.remove("saas.internal.team-token");
        move(properties, "saas.internal.job-token", "saas.job.internal.job-token");
        StandardEnvironment environment = environment("prod", properties);
        ProductionSecurityPropertiesValidator validator = new ProductionSecurityPropertiesValidator(environment);

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    private static StandardEnvironment environment(String profile, Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles(profile);
        environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
        return environment;
    }

    private static Map<String, Object> strongProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.datasource.password", "strong-db-password-2026");
        properties.put("spring.data.redis.password", "strong-redis-password-2026");
        properties.put("saas.security.jwt-secret", "strong-jwt-secret-at-least-32-chars-2026");
        properties.put("saas.security.field-secret", "strong-field-secret-at-least-32-chars-2026");
        properties.put("saas.internal.system-token", "strong-system-internal-token-2026");
        properties.put("saas.internal.auth-token", "strong-auth-internal-token-2026");
        properties.put("saas.internal.auth-system-token", "strong-auth-system-internal-token-2026");
        properties.put("saas.internal.file-token", "strong-file-internal-token-2026");
        properties.put("saas.internal.message-token", "strong-message-internal-token-2026");
        properties.put("saas.internal.payment-token", "strong-payment-internal-token-2026");
        properties.put("saas.internal.plugin-token", "strong-plugin-internal-token-2026");
        properties.put("saas.internal.team-token", "strong-team-internal-token-2026");
        properties.put("saas.internal.job-token", "strong-job-scheduler-token-2026");
        properties.put("saas.plugin.signature-secret", "strong-plugin-secret-at-least-32-chars-2026");
        properties.put("xxl.job.accessToken", "strong-xxl-token-2026");
        properties.put("saas.web.cors-allowed-origin-patterns", "https://app.example.com");
        return properties;
    }

    private static void move(Map<String, Object> properties, String source, String target) {
        properties.put(target, properties.remove(source));
    }

    private static void removeControlPlaneSecrets(Map<String, Object> properties) {
        properties.remove("spring.datasource.password");
        properties.remove("saas.security.jwt-secret");
        properties.remove("saas.security.field-secret");
        properties.remove("saas.plugin.signature-secret");
        properties.remove("saas.web.cors-allowed-origin-patterns");
    }
}
