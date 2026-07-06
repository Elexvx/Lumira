package com.lumira.localization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

class LocalizationServiceApplicationYamlTest {

    @Test
    void standaloneLocalizationServiceBridgesSharedDeploymentEnvNamesAndPublicPermitPaths() {
        Properties properties = loadApplicationProperties();

        assertThat(properties.getProperty("spring.application.name")).isEqualTo("localization-service");
        assertThat(properties.getProperty("spring.profiles.active")).isEqualTo("${SPRING_PROFILES_ACTIVE:prod}");
        assertThat(properties.getProperty("spring.datasource.url"))
                .isEqualTo("${DB_URL:jdbc:mysql://localhost:3306/lumira?useUnicode=true&characterEncoding=utf-8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai}");
        assertThat(properties.getProperty("saas.security.jwt-secret")).isEqualTo("${JWT_SECRET:}");
        assertThat(properties.getProperty("saas.security.field-secret")).isEqualTo("${FIELD_SECRET:}");
        assertThat(properties.getProperty("saas.security.permit-paths[0]")).isEqualTo("/api/v2/localization/runtime/**");
        assertThat(properties.getProperty("saas.security.permit-paths[7]")).isEqualTo("/api/v2/localization/readiness");
        assertThat(properties.getProperty("saas.security.permit-paths[8]")).isEqualTo("/api/v2/localization/health");
        assertThat(properties.getProperty("saas.security.permit-paths[9]")).isEqualTo("/api/v2/localization/metrics");
    }

    private Properties loadApplicationProperties() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application.yml"));
        Properties properties = factory.getObject();
        assertThat(properties).isNotNull();
        return properties;
    }
}
