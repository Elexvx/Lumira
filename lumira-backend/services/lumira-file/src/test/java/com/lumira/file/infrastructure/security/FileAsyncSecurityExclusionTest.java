package com.lumira.file.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class FileAsyncSecurityExclusionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(FileSecurityComponentsScan.class)
            .withPropertyValues(
                    "lumira.monolith=false",
                    "lumira.runtime.control-plane-enabled=false"
            );

    @Test
    void asyncRuntimeDoesNotCreateFileJwtUserAuthBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(JwtTokenService.class);
            assertThat(context).doesNotHaveBean(FileJwtAuthFilter.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
            JwtTokenService.class,
            FileJwtAuthFilter.class
    })
    static class FileSecurityComponentsScan {
    }
}
