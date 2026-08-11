package com.lumira.payment.service;

import com.lumira.api.payment.PaymentProviderSettingsDTO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentProviderCatalogBuiltinMockTest {

    @Test
    void shouldExposeTheManagedMockProviderWithAlipayCompatibleScenes() {
        PaymentProviderCatalog catalog = new PaymentProviderCatalog();
        PaymentProviderCatalog.PaymentProviderDefinition definition = catalog.requireDefinition("builtin_mock");
        PaymentProviderSettingsDTO blank = catalog.createBlankSettings("builtin_mock");

        assertThat(definition.providerName()).isEqualTo("内置模拟支付");
        assertThat(definition.defaultEnvironment()).isEqualTo("SANDBOX");
        assertThat(definition.defaultCurrency()).isEqualTo("CNY");
        assertThat(definition.signatureAlgorithm()).isEqualTo("RSA2");
        assertThat(definition.requiredFields()).containsExactly("appId", "privateKey", "publicKey");
        assertThat(blank.getSortOrder()).isEqualTo(900);
        assertThat(blank.getSupportedScenes()).containsExactly("PC_WEB", "WAP", "QR_CODE");
    }
}

