package com.lumira.payment;

import com.lumira.api.client.PaymentInternalApi;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.payment.controller.InternalPaymentController;
import com.lumira.payment.service.PaymentInternalApiService;
import com.lumira.payment.service.PaymentTransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PaymentInternalAssemblyTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void monolithKeepsLocalInternalApiButDoesNotExposeController() {
        contextRunner.withPropertyValues("lumira.monolith=true").run(context -> {
            assertThat(context.getBeansOfType(PaymentInternalApi.class)).hasSize(1);
            assertThat(context.getBeansOfType(PaymentInternalApiService.class)).hasSize(1);
            assertThat(context.getBeansOfType(InternalPaymentController.class)).isEmpty();
        });
    }

    @Test
    void splitRuntimeExposesControllerAndLocalInternalApi() {
        contextRunner.withPropertyValues("lumira.monolith=false").run(context -> {
            assertThat(context.getBeansOfType(PaymentInternalApi.class)).hasSize(1);
            assertThat(context.getBeansOfType(PaymentInternalApiService.class)).hasSize(1);
            assertThat(context.getBeansOfType(InternalPaymentController.class)).hasSize(1);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({PaymentInternalApiService.class, InternalPaymentController.class})
    static class TestConfiguration {

        @Bean
        PaymentTransactionService paymentTransactionService() {
            return mock(PaymentTransactionService.class);
        }

        @Bean
        SystemInternalApi systemInternalApi() {
            return mock(SystemInternalApi.class);
        }
    }
}
