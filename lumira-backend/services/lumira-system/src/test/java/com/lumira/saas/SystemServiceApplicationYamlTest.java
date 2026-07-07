package com.lumira.saas;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;

class SystemServiceApplicationYamlTest {

    @Test
    void standaloneSystemServiceBridgesSharedDeploymentEnvNames() {
        Properties properties = loadApplicationProperties();

        assertThat(properties.getProperty("spring.application.name")).isEqualTo("system-service");
        assertThat(properties.getProperty("spring.profiles.active")).isEqualTo("${SPRING_PROFILES_ACTIVE:prod}");
        assertThat(properties.getProperty("spring.datasource.url"))
                .isEqualTo("${DB_URL:jdbc:mysql://localhost:3306/lumira?useUnicode=true&characterEncoding=utf-8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai}");
        assertThat(properties.getProperty("spring.data.redis.host")).isEqualTo("${REDIS_HOST:localhost}");
        assertThat(properties.getProperty("saas.security.jwt-secret")).isEqualTo("${JWT_SECRET:}");
        assertThat(properties.getProperty("saas.security.field-secret")).isEqualTo("${FIELD_SECRET:}");
        assertThat(properties.getProperty("lumira.monolith")).isEqualTo("${LUMIRA_MONOLITH:false}");
        assertThat(properties)
                .containsValue("/api/v2/account-activation/**")
                .containsValue("/api/v2/*/readiness")
                .containsValue("/api/v2/*/health")
                .containsValue("/api/v2/*/metrics")
                .containsValue("/api/v2/runtime/version");
        assertThat(properties.getProperty("saas.web.cors-allowed-origin-patterns")).isEqualTo("${CORS_ALLOWED_ORIGIN_PATTERNS:}");
    }

    private Properties loadApplicationProperties() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new FileSystemResource(Path.of("src", "main", "resources", "application.yml")));
        Properties properties = factory.getObject();
        assertThat(properties).isNotNull();
        return properties;
    }
}
