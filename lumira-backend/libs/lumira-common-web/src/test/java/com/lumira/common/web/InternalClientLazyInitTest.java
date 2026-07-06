package com.lumira.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class InternalClientLazyInitTest {

    @Test
    void sharedInternalClientsRegisterAsLazyBeansWithoutTokens() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(
                    AuthInternalClientConfiguration.class,
                    FileInternalClientConfiguration.class,
                    PaymentInternalClientConfiguration.class,
                    SystemInternalClientConfiguration.class,
                    TeamInternalClientConfiguration.class
            );
            context.refresh();

            assertThat(context.getBeanFactory().getBeanDefinition("authInternalApi").isLazyInit()).isTrue();
            assertThat(context.getBeanFactory().getBeanDefinition("fileInternalApi").isLazyInit()).isTrue();
            assertThat(context.getBeanFactory().getBeanDefinition("paymentInternalApi").isLazyInit()).isTrue();
            assertThat(context.getBeanFactory().getBeanDefinition("systemInternalApi").isLazyInit()).isTrue();
            assertThat(context.getBeanFactory().getBeanDefinition("teamInternalApi").isLazyInit()).isTrue();
        }
    }
}
