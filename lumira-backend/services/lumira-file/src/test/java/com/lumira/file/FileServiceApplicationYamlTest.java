package com.lumira.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

class FileServiceApplicationYamlTest {

    @Test
    void standaloneFileServiceBridgesSharedDeploymentEnvNames() {
        Properties properties = loadApplicationProperties();

        assertThat(properties.getProperty("spring.application.name")).isEqualTo("file-service");
        assertThat(properties.getProperty("spring.profiles.active")).isEqualTo("${SPRING_PROFILES_ACTIVE:prod}");
        assertThat(properties.getProperty("spring.datasource.url"))
                .isEqualTo("${DB_URL:jdbc:mysql://localhost:3306/lumira?useUnicode=true&characterEncoding=utf-8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai}");
        assertThat(properties.getProperty("saas.security.jwt-secret")).isEqualTo("${JWT_SECRET:}");
        assertThat(properties.getProperty("saas.security.field-secret")).isEqualTo("${FIELD_SECRET:}");
        assertThat(properties.getProperty("lumira.monolith")).isEqualTo("${LUMIRA_MONOLITH:false}");
        assertThat(properties.getProperty("saas.security.permit-paths[0]")).isEqualTo("/api/uploads/**");
        assertThat(properties.getProperty("saas.security.permit-paths[7]")).isEqualTo("/api/v2/files/readiness");
        assertThat(properties.getProperty("saas.security.permit-paths[8]")).isEqualTo("/api/v2/files/health");
        assertThat(properties.getProperty("saas.security.permit-paths[9]")).isEqualTo("/api/v2/files/metrics");
        assertThat(properties.getProperty("saas.web.cors-allowed-origin-patterns")).isEqualTo("${CORS_ALLOWED_ORIGIN_PATTERNS:}");
    }

    private Properties loadApplicationProperties() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application.yml"));
        Properties properties = factory.getObject();
        assertThat(properties).isNotNull();
        return properties;
    }
}
