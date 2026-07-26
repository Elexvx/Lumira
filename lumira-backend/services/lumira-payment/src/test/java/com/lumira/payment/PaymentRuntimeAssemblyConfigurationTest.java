package com.lumira.payment;

import com.lumira.payment.service.WechatPayV3Service;
import org.junit.jupiter.api.Test;
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
}
