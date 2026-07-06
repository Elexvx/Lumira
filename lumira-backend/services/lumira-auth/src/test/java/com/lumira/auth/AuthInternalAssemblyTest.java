package com.lumira.auth;

import com.lumira.api.client.AuthInternalApi;
import com.lumira.auth.controller.AuthInternalController;
import com.lumira.auth.service.AuthAppService;
import com.lumira.auth.service.AuthInternalApiService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AuthInternalAssemblyTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void monolithKeepsLocalInternalApiButDoesNotExposeController() {
        contextRunner.withPropertyValues("lumira.monolith=true").run(context -> {
            assertThat(context.getBeansOfType(AuthInternalApi.class)).hasSize(1);
            assertThat(context.getBeansOfType(AuthInternalApiService.class)).hasSize(1);
            assertThat(context.getBeansOfType(AuthInternalController.class)).isEmpty();
        });
    }

    @Test
    void splitRuntimeExposesControllerAndLocalInternalApi() {
        contextRunner.withPropertyValues("lumira.monolith=false").run(context -> {
            assertThat(context.getBeansOfType(AuthInternalApi.class)).hasSize(1);
            assertThat(context.getBeansOfType(AuthInternalApiService.class)).hasSize(1);
            assertThat(context.getBeansOfType(AuthInternalController.class)).hasSize(1);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({AuthInternalApiService.class, AuthInternalController.class})
    static class TestConfiguration {

        @Bean
        AuthAppService authAppService() {
            return mock(AuthAppService.class);
        }
    }
}
