package com.lumira.ai.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.lumira.api.client.AuthInternalApi;
import com.lumira.common.web.AuthInternalClientConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class AiSecurityAssemblyTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                    "lumira.monolith=false",
                    "saas.security.jwt-secret=ai-security-jwt-secret-2026-0123456789abcdef",
                    "saas.internal.auth-token=auth-token-2026"
            );

    @Test
    void splitRuntimeCreatesAiJwtSecurityBeansWithSharedAuthClient() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(AuthInternalApi.class)).hasSize(1);
            assertThat(context.getBeansOfType(AiJwtAuthFilter.class)).hasSize(1);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SecurityProperties.class)
    @Import({
            AuthInternalClientConfiguration.class,
            JwtTokenService.class,
            AiJwtAuthFilter.class
    })
    static class TestConfiguration {
    }
}
