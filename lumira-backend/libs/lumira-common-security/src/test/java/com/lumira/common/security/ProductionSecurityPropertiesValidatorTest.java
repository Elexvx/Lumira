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
    void shouldRejectWildcardCorsInProduction() {
        Map<String, Object> properties = strongProperties();
        properties.put("saas.web.cors-allowed-origin-patterns", "https://*.example.com");
        StandardEnvironment environment = environment("prod", properties);
        ProductionSecurityPropertiesValidator validator = new ProductionSecurityPropertiesValidator(environment);

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS_ALLOWED_ORIGIN_PATTERNS");
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
        properties.put("saas.job.internal-token", "strong-job-token-at-least-24-chars");
        properties.put("saas.plugin.signature-secret", "strong-plugin-secret-at-least-32-chars-2026");
        properties.put("xxl.job.accessToken", "strong-xxl-token-2026");
        properties.put("saas.web.cors-allowed-origin-patterns", "https://app.example.com");
        return properties;
    }
}
