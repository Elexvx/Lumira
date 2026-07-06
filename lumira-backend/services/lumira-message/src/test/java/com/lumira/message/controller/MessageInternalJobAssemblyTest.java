package com.lumira.message.controller;

import com.lumira.message.app.PlatformEventOutboxService;
import com.lumira.message.config.MessageProperties;
import com.lumira.message.service.MessageEventDeliveryService;
import com.lumira.message.service.MessageWebSocketRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MessageInternalJobAssemblyTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues("saas.internal.message-token=message-token-2026");

    @Test
    void asyncEnabledExposesInternalJobController() {
        contextRunner.run(context ->
                assertThat(context).hasSingleBean(InternalJobController.class));
    }

    @Test
    void asyncDisabledDoesNotExposeInternalJobController() {
        contextRunner.withPropertyValues("lumira.runtime.async-enabled=false").run(context ->
                assertThat(context).doesNotHaveBean(InternalJobController.class));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(InternalJobController.class)
    static class TestConfiguration {

        @Bean
        MessageWebSocketRegistry messageWebSocketRegistry() {
            return mock(MessageWebSocketRegistry.class);
        }

        @Bean
        PlatformEventOutboxService platformEventOutboxService() {
            return mock(PlatformEventOutboxService.class);
        }

        @Bean
        MessageEventDeliveryService messageEventDeliveryService() {
            return mock(MessageEventDeliveryService.class);
        }

        @Bean
        MessageProperties messageProperties() {
            return new MessageProperties();
        }
    }
}
