package com.lumira.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

class JobExecutorApplicationYamlTest {

    @Test
    void productionRuntimeUrlsTakePrecedenceOverLegacyPerServiceAliases() {
        Properties properties = loadApplicationProperties();

        assertThat(properties.getProperty("saas.job.backend-base-url"))
                .isEqualTo("${SAAS_JOB_ASYNC_RUNTIME_BASE_URL:${SAAS_JOB_BACKEND_BASE_URL:http://localhost:${server.port}}}");
        assertThat(properties.getProperty("saas.job.system-service-base-url"))
                .isEqualTo("${SAAS_JOB_CONTROL_PLANE_BASE_URL:${SAAS_JOB_SYSTEM_SERVICE_BASE_URL:${SAAS_JOB_ASYNC_RUNTIME_BASE_URL:${SAAS_JOB_BACKEND_BASE_URL:http://localhost:${server.port}}}}}");
        for (String property : new String[]{
                "saas.job.message-service-base-url",
                "saas.job.file-service-base-url",
                "saas.job.payment-service-base-url",
                "saas.job.plugin-service-base-url"
        }) {
            assertThat(properties.getProperty(property))
                    .startsWith("${SAAS_JOB_ASYNC_RUNTIME_BASE_URL:")
                    .contains("${SAAS_JOB_")
                    .endsWith("}");
        }
    }

    @Test
    void continuousAdaptiveRelayIsAbsentAndInternalHttpIsBounded() {
        Properties properties = loadApplicationProperties();

        assertThat(properties.stringPropertyNames())
                .noneMatch(name -> name.startsWith("saas.job.adaptive-relay"));
        assertThat(properties.getProperty("saas.job.internal-http.connect-timeout")).isNotBlank();
        assertThat(properties.getProperty("saas.job.internal-http.response-timeout")).isNotBlank();
        assertThat(properties.getProperty("saas.job.internal-http.max-response-bytes")).isNotBlank();
        assertThat(properties.getProperty("saas.job.internal-http.max-attempts")).isNotBlank();
    }

    private Properties loadApplicationProperties() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application.yml"));
        Properties properties = factory.getObject();
        assertThat(properties).isNotNull();
        return properties;
    }
}
