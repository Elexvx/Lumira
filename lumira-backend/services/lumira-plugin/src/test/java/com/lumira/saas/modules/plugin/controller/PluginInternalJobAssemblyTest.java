package com.lumira.saas.modules.plugin.controller;

import com.lumira.saas.modules.plugin.event.PluginOutboxRelay;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PluginInternalJobAssemblyTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues("saas.internal.plugin-token=plugin-token-2026");

    @Test
    void controlPlaneEnabledExposesInternalJobController() {
        contextRunner.run(context ->
                assertThat(context).hasSingleBean(InternalJobController.class));
    }

    @Test
    void controlPlaneDisabledDoesNotExposeInternalJobController() {
        contextRunner.withPropertyValues("lumira.runtime.control-plane-enabled=false").run(context ->
                assertThat(context).doesNotHaveBean(InternalJobController.class));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(InternalJobController.class)
    static class TestConfiguration {

        @Bean
        PluginOutboxRelay pluginOutboxRelay() {
            return mock(PluginOutboxRelay.class);
        }
    }
}
