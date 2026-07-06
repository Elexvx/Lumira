package com.lumira.file;

import com.lumira.api.client.FileInternalApi;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.file.app.FileManagementAppService;
import com.lumira.file.controller.InternalFileController;
import com.lumira.file.service.FileInternalApiService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FileInternalAssemblyTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void monolithKeepsLocalInternalApiButDoesNotExposeController() {
        contextRunner.withPropertyValues("lumira.monolith=true").run(context -> {
            assertThat(context.getBeansOfType(FileInternalApi.class)).hasSize(1);
            assertThat(context.getBeansOfType(FileInternalApiService.class)).hasSize(1);
            assertThat(context.getBeansOfType(InternalFileController.class)).isEmpty();
        });
    }

    @Test
    void splitRuntimeExposesControllerAndLocalInternalApi() {
        contextRunner.withPropertyValues("lumira.monolith=false").run(context -> {
            assertThat(context.getBeansOfType(FileInternalApi.class)).hasSize(1);
            assertThat(context.getBeansOfType(FileInternalApiService.class)).hasSize(1);
            assertThat(context.getBeansOfType(InternalFileController.class)).hasSize(1);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({FileInternalApiService.class, InternalFileController.class})
    static class TestConfiguration {

        @Bean
        FileManagementAppService fileManagementAppService() {
            return mock(FileManagementAppService.class);
        }

        @Bean
        SecurityContextFacade securityContextFacade() {
            return mock(SecurityContextFacade.class);
        }

        @Bean
        SystemInternalApi systemInternalApi() {
            return mock(SystemInternalApi.class);
        }
    }
}
