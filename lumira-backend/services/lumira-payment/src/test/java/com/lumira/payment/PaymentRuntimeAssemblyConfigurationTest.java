package com.lumira.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.payment.service.WechatPayV3Service;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentRuntimeAssemblyConfigurationTest {

    @Test
    void importsWechatPayV3ServiceForExplicitRuntimeAssembly() {
        Import imports = PaymentRuntimeAssemblyConfiguration.class.getAnnotation(Import.class);

        assertThat(imports).isNotNull();
        assertThat(Arrays.asList(imports.value())).contains(WechatPayV3Service.class);
    }

    @Test
    void constructsExplicitlyImportedWechatPayV3Service() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerSingleton("objectMapper", new ObjectMapper());
            context.register(WechatPayV3ImportConfiguration.class);
            context.refresh();

            assertThat(context.getBean(WechatPayV3Service.class)).isNotNull();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(WechatPayV3Service.class)
    static class WechatPayV3ImportConfiguration {
    }
}
