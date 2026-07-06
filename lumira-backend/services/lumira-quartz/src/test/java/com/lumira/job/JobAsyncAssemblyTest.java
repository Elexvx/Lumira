package com.lumira.job;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class JobAsyncAssemblyTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                    "saas.job.backend-base-url=http://127.0.0.1:18080",
                    "saas.job.message-service-base-url=http://127.0.0.1:18081",
                    "saas.job.file-service-base-url=http://127.0.0.1:18082",
                    "saas.job.payment-service-base-url=http://127.0.0.1:18083",
                    "saas.job.plugin-service-base-url=http://127.0.0.1:18084"
            );

    @Test
    void asyncEnabledExposesOwnerJobBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(BackendJobClient.class);
            assertThat(context).hasSingleBean(JobReadinessV2Controller.class);
            assertThat(context).hasSingleBean(VersionController.class);
        });
    }

    @Test
    void asyncDisabledDoesNotExposeOwnerJobBeans() {
        contextRunner.withPropertyValues("lumira.runtime.async-enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(BackendJobClient.class);
            assertThat(context).doesNotHaveBean(JobReadinessV2Controller.class);
            assertThat(context).doesNotHaveBean(VersionController.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JobExecutorProperties.class)
    @Import({
            BackendJobClient.class,
            JobReadinessV2Controller.class,
            VersionController.class
    })
    static class TestConfiguration {
    }
}
