package com.lumira.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.payment.PaymentProviderSettingsDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.BeanPropertyRowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class PaymentManagementAppServiceTest {

    @Test
    void updateProviderSettingsShouldUseUniqueOutboxEventKeyPerUpdate() {
        PaymentOutboxService outboxService = mock(PaymentOutboxService.class);
        PaymentManagementAppService service = new PaymentManagementAppService(
                mock(JdbcTemplate.class),
                new ObjectMapper(),
                mock(PaymentConfigCryptoService.class),
                new PaymentProviderCatalog(),
                outboxService
        );

        service.updatePaymentProviderSettings(1001L, 1001L, "stripe", stripeSettings("first-secret"));
        service.updatePaymentProviderSettings(1001L, 1001L, "stripe", stripeSettings("second-secret"));

        ArgumentCaptor<String> eventKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService, times(2)).recordAfterCommit(
                eq(1001L),
                eq(1001L),
                eq("payment"),
                eq("payment.provider.updated"),
                eventKeyCaptor.capture(),
                any()
        );
        List<String> eventKeys = eventKeyCaptor.getAllValues();
        assertThat(eventKeys).hasSize(2);
        assertThat(eventKeys).allSatisfy(eventKey -> assertThat(eventKey).startsWith("stripe:"));
        assertThat(eventKeys.get(0)).isNotEqualTo(eventKeys.get(1));
    }

    @Test
    void requiredProviderSettingsShouldLoadUnmaskedSecretsForWebhookVerification() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentConfigCryptoService cryptoService = mock(PaymentConfigCryptoService.class);
        PaymentProviderConfigRow row = new PaymentProviderConfigRow();
        row.setTenantId(1001L);
        row.setProviderCode("stripe");
        row.setProviderName("Stripe");
        row.setEnabled(1);
        row.setConfigured(1);
        row.setEnvironment("SANDBOX");
        row.setEncryptedConfigJson("encrypted");
        PaymentProviderSettingsDTO stored = stripeSettings("real-webhook-secret");
        doReturn(row).when(jdbcTemplate).queryForObject(
                anyString(),
                anyPaymentProviderRowMapper(),
                eq(1001L),
                eq("stripe")
        );
        doReturn(stored).when(cryptoService).decryptJson("encrypted", PaymentProviderSettingsDTO.class);
        PaymentManagementAppService service = new PaymentManagementAppService(
                jdbcTemplate,
                new ObjectMapper(),
                cryptoService,
                new PaymentProviderCatalog(),
                mock(PaymentOutboxService.class)
        );

        PaymentProviderSettingsDTO publicSettings = service.paymentProviderSettings(1001L, "stripe");
        PaymentProviderSettingsDTO requiredSettings = service.getRequiredProviderSettings(1001L, "stripe");

        assertThat(publicSettings.getWebhookSecret()).isEmpty();
        assertThat(requiredSettings.getWebhookSecret()).isEqualTo("real-webhook-secret");
        assertThat(requiredSettings.getSecretKey()).isEqualTo("secret");
    }

    private PaymentProviderSettingsDTO stripeSettings(String webhookSecret) {
        PaymentProviderSettingsDTO settings = new PaymentProviderSettingsDTO();
        settings.setProviderCode("stripe");
        settings.setEnabled(true);
        settings.setEnvironment("SANDBOX");
        settings.setCurrency("USD");
        settings.setClientId("client");
        settings.setSecretKey("secret");
        settings.setWebhookSecret(webhookSecret);
        settings.setSandboxEnabled(true);
        return settings;
    }

    private BeanPropertyRowMapper<PaymentProviderConfigRow> anyPaymentProviderRowMapper() {
        return any();
    }
}
