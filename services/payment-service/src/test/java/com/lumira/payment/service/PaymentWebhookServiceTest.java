package com.lumira.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.payment.PaymentProviderSettingsDTO;
import com.lumira.api.payment.PaymentWebhookEventDTO;
import com.lumira.domain.event.DomainEventPublisher;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

class PaymentWebhookServiceTest {

    @Test
    void duplicateWebhookReturnsExistingEventWithoutReprocessing() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentManagementAppService managementAppService = mock(PaymentManagementAppService.class);
        PaymentOutboxService outboxService = mock(PaymentOutboxService.class);
        DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
        PaymentProviderCatalog providerCatalog = new PaymentProviderCatalog();
        PaymentProviderSettingsDTO settings = new PaymentProviderSettingsDTO();
        settings.setEnabled(true);
        settings.setWebhookSecret("secret");
        when(managementAppService.getRequiredProviderSettings(1L, "stripe")).thenReturn(settings);

        PaymentWebhookEventRow existing = new PaymentWebhookEventRow();
        existing.setProviderCode("stripe");
        existing.setEventId("evt-1");
        existing.setEventType("payment.succeeded");
        existing.setSignatureValid(1);
        existing.setProcessed(1);
        existing.setProcessMessage("already processed");
        existing.setReceivedAt(LocalDateTime.now().minusMinutes(1));
        existing.setProcessedAt(LocalDateTime.now());
        doReturn(existing).when(jdbcTemplate).queryForObject(
                anyString(),
                any(BeanPropertyRowMapper.class),
                any(),
                any(),
                any()
        );
        PaymentWebhookService service = new PaymentWebhookService(
                jdbcTemplate,
                new ObjectMapper(),
                managementAppService,
                providerCatalog,
                outboxService,
                domainEventPublisher
        );

        PaymentWebhookEventDTO result = service.handleWebhook(
                1L,
                "stripe",
                "{\"eventId\":\"evt-1\",\"eventType\":\"payment.succeeded\"}",
                Map.of("X-Event-Id", "evt-1")
        );

        assertThat(result.eventId()).isEqualTo("evt-1");
        assertThat(result.processed()).isTrue();
        assertThat(result.processMessage()).isEqualTo("already processed");
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
        verifyNoInteractions(outboxService, domainEventPublisher);
    }

    @Test
    void invalidSignatureRecordsRejectedWebhookWithoutApplyingPaymentEvent() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentManagementAppService managementAppService = mock(PaymentManagementAppService.class);
        PaymentOutboxService outboxService = mock(PaymentOutboxService.class);
        DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
        PaymentProviderCatalog providerCatalog = new PaymentProviderCatalog();
        PaymentProviderSettingsDTO settings = stripeSettings();
        when(managementAppService.getRequiredProviderSettings(1L, "stripe")).thenReturn(settings);
        doReturn(null).when(jdbcTemplate).queryForObject(
                anyString(),
                any(BeanPropertyRowMapper.class),
                any(),
                any(),
                any()
        );
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        PaymentWebhookService service = new PaymentWebhookService(
                jdbcTemplate,
                new ObjectMapper(),
                managementAppService,
                providerCatalog,
                outboxService,
                domainEventPublisher
        );

        PaymentWebhookEventDTO result = service.handleWebhook(
                1L,
                "stripe",
                "{\"eventId\":\"evt-bad\",\"eventType\":\"payment.succeeded\",\"orderNo\":\"ord-1\"}",
                Map.of(
                        "X-Event-Id", "evt-bad",
                        "X-Event-Type", "payment.succeeded",
                        "X-Timestamp", String.valueOf(System.currentTimeMillis() / 1000L),
                        "Stripe-Signature", "bad-signature"
                )
        );

        assertThat(result.eventId()).isEqualTo("evt-bad");
        assertThat(result.signatureValid()).isFalse();
        assertThat(result.processed()).isFalse();
        assertThat(result.processMessage()).isEqualTo("签名校验失败");
        verify(outboxService).recordAfterCommit(
                eq(1L),
                eq(0L),
                eq("payment"),
                eq("payment.webhook.received"),
                eq("stripe:evt-bad"),
                any()
        );
        verifyNoInteractions(domainEventPublisher);
        verify(jdbcTemplate, never()).update(contains("update payment_order"), any(Object[].class));
    }

    @Test
    void replayedNonceIsRejectedBeforeSignatureProcessing() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentManagementAppService managementAppService = mock(PaymentManagementAppService.class);
        PaymentOutboxService outboxService = mock(PaymentOutboxService.class);
        DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
        PaymentProviderCatalog providerCatalog = new PaymentProviderCatalog();
        when(managementAppService.getRequiredProviderSettings(1L, "stripe")).thenReturn(stripeSettings());
        doThrow(new EmptyResultDataAccessException(1)).when(jdbcTemplate).queryForObject(
                anyString(),
                any(BeanPropertyRowMapper.class),
                any(),
                any(),
                any()
        );
        doReturn(List.of(1L)).when(jdbcTemplate).queryForList(
                contains("nonce = ?"),
                eq(Long.class),
                any(),
                any(),
                any(),
                any()
        );
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        PaymentWebhookService service = new PaymentWebhookService(
                jdbcTemplate,
                new ObjectMapper(),
                managementAppService,
                providerCatalog,
                outboxService,
                domainEventPublisher
        );

        PaymentWebhookEventDTO result = service.handleWebhook(
                1L,
                "stripe",
                "{\"eventId\":\"evt-replay\",\"eventType\":\"payment.succeeded\"}",
                Map.of(
                        "X-Event-Id", "evt-replay",
                        "X-Event-Type", "payment.succeeded",
                        "X-Timestamp", String.valueOf(System.currentTimeMillis() / 1000L),
                        "X-Nonce", "nonce-1",
                        "Stripe-Signature", "bad-signature"
                )
        );

        assertThat(result.eventId()).isEqualTo("evt-replay");
        assertThat(result.signatureValid()).isFalse();
        assertThat(result.processed()).isFalse();
        assertThat(result.processMessage()).isEqualTo("请求已被重放");
        verifyNoInteractions(outboxService, domainEventPublisher);
    }

    private PaymentProviderSettingsDTO stripeSettings() {
        PaymentProviderSettingsDTO settings = new PaymentProviderSettingsDTO();
        settings.setProviderCode("stripe");
        settings.setEnabled(true);
        settings.setWebhookSecret("secret");
        return settings;
    }
}
