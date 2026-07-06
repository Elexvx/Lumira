package com.lumira.common.web;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

class InternalClientLazyInitTest {

    @Test
    void sharedInternalClientsRegisterAsLazyBeansWithoutTokens() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources()
                    .addFirst(new MapPropertySource("test", Map.of("lumira.monolith", "false")));
            context.register(
                    AuthInternalClientConfiguration.class,
                    FileInternalClientConfiguration.class,
                    PaymentInternalClientConfiguration.class,
                    SystemInternalClientConfiguration.class,
                    TeamInternalClientConfiguration.class
            );
            context.refresh();

            assertThat(context.getBeanFactory().getBeanDefinition("remoteAuthInternalApi").isLazyInit()).isTrue();
            assertThat(context.getBeanFactory().getBeanDefinition("remoteFileInternalApi").isLazyInit()).isTrue();
            assertThat(context.getBeanFactory().getBeanDefinition("remotePaymentInternalApi").isLazyInit()).isTrue();
            assertThat(context.getBeanFactory().getBeanDefinition("remoteSystemInternalApi").isLazyInit()).isTrue();
            assertThat(context.getBeanFactory().getBeanDefinition("remoteTeamInternalApi").isLazyInit()).isTrue();
        }
    }

    @Test
    void monolithRuntimeDoesNotRegisterRemoteInternalClients() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources()
                    .addFirst(new MapPropertySource("test", Map.of("lumira.monolith", "true")));
            context.register(
                    AuthInternalClientConfiguration.class,
                    FileInternalClientConfiguration.class,
                    PaymentInternalClientConfiguration.class,
                    SystemInternalClientConfiguration.class,
                    TeamInternalClientConfiguration.class
            );
            context.refresh();

            assertThat(context.containsBeanDefinition("remoteAuthInternalApi")).isFalse();
            assertThat(context.containsBeanDefinition("remoteFileInternalApi")).isFalse();
            assertThat(context.containsBeanDefinition("remotePaymentInternalApi")).isFalse();
            assertThat(context.containsBeanDefinition("remoteSystemInternalApi")).isFalse();
            assertThat(context.containsBeanDefinition("remoteTeamInternalApi")).isFalse();
        }
    }
}
