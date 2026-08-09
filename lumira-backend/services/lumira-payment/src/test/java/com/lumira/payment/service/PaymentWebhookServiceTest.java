package com.lumira.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
import com.lumira.domain.event.DomainEvent;
import com.lumira.domain.event.DomainEventPublisher;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

class PaymentWebhookServiceTest {

    @Test
    void paidCompetitionRegistrationWebhookPublishesOwnedEventWithoutWritingCompetitionTables() {
        JdbcTemplate jdbcTemplate = mockJdbcTemplateWithProcessMark();
        PaymentManagementAppService managementAppService = mock(PaymentManagementAppService.class);
        PaymentOutboxService outboxService = mock(PaymentOutboxService.class);
        DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
        PaymentProviderCatalog providerCatalog = new PaymentProviderCatalog();
        when(managementAppService.getRequiredProviderSettings("stripe")).thenReturn(stripeSettings());
        doThrow(new EmptyResultDataAccessException(1)).when(jdbcTemplate).queryForObject(
                contains("from payment_webhook_event"),
                any(BeanPropertyRowMapper.class),
                any(),
                any()
        );
        PaymentOrderRow order = new PaymentOrderRow();
        order.setId(501L);
        order.setOrderNo("REG-1-ABCD");
        order.setProviderCode("stripe");
        order.setAmountMinor(8800L);
        order.setStatus("PENDING");
        order.setCreatedBy(1001L);
        order.setCreatedByUuid("user-uuid-1001");
        order.setRequestJson("""
                {"metadata":{"bizType":"competition_registration","registrationId":1,"competitionId":11,"teamId":21,"projectId":31}}
                """);
        doReturn(order).when(jdbcTemplate).queryForObject(
                contains("from payment_order"),
                any(BeanPropertyRowMapper.class),
                eq("stripe"),
                any()
        );
        doReturn("AIADC2026-0001").when(jdbcTemplate).queryForObject(
                contains("from competition_registration cr"),
                eq(String.class),
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
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String payload = "{\"eventId\":\"evt-paid\",\"eventType\":\"payment.succeeded\",\"orderNo\":\"REG-1-ABCD\",\"providerTxnId\":\"txn-provider-1001\"}";

        PaymentWebhookEventDTO result = service.handleWebhook(
                "stripe",
                payload,
                Map.of(
                        "X-Event-Id", "evt-paid",
                        "X-Event-Type", "payment.succeeded",
                        "X-Timestamp", timestamp,
                        "Stripe-Signature", stripeSignature(timestamp, payload)
                )
        );

        assertThat(result.processed()).isTrue();
        verify(jdbcTemplate, never()).update(
                contains("update competition_registration"),
                eq("AIADC2026-0001"),
                eq("REG-1-ABCD"),
                isNull(),
                isNull(),
                any(),
                eq(1L),
                eq(1001L),
                eq("user-uuid-1001"),
                eq("REG-1-ABCD")
        );
        verify(jdbcTemplate).queryForObject(
                contains("provider_code = ?"),
                any(BeanPropertyRowMapper.class),
                eq("stripe"),
                eq("REG-1-ABCD")
        );
        verify(jdbcTemplate).update(
                contains("provider_code = ?"),
                any(),
                any(),
                isNull(),
                isNull(),
                eq(501L),
                eq("REG-1-ABCD"),
                eq("stripe"),
                eq(8800L),
                eq(1001L),
                eq("user-uuid-1001"),
                eq("PENDING")
        );
        verify(jdbcTemplate).update(
                contains("set provider_order_no = ?"),
                eq("txn-provider-1001"),
                any(),
                eq(501L),
                eq("REG-1-ABCD"),
                eq("stripe")
        );
        verify(jdbcTemplate, never()).update(contains("owner_user_id = ? and owner_user_uuid = ?"), any(Object[].class));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<? extends DomainEvent>> events = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(domainEventPublisher).publishAll(events.capture());
        assertThat(events.getValue()).singleElement().satisfies(event -> {
            assertThat(event.eventType()).isEqualTo("PAYMENT_ORDER_PAID");
            assertThat(event.aggregateId()).isEqualTo("REG-1-ABCD");
            assertThat(event.attributes()).containsEntry("registrationId", 1L);
            assertThat(event.attributes()).containsEntry("bizType", "competition_registration");
            assertThat(event.attributes()).containsEntry("userId", 1001L);
            assertThat(event.attributes()).containsEntry("userUuid", "user-uuid-1001");
        });
        ArgumentCaptor<Object[]> webhookInsertParams = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(contains("insert into payment_webhook_event"), webhookInsertParams.capture());
        assertThat(webhookInsertParams.getValue())
                .hasSize(14);
        assertThat(webhookInsertParams.getValue()[10]).isNull();
        assertThat(webhookInsertParams.getValue()[11]).isNull();
        assertThat(webhookInsertParams.getValue()[12]).isNull();
        assertThat(webhookInsertParams.getValue()[13]).isNull();
        verify(outboxService).record(
                isNull(),
                eq("payment"),
                eq("payment.webhook.received"),
                eq("stripe:evt-paid"),
                any()
        );
    }

    @Test
    void paidCompetitionRegistrationWebhookDoesNotDependOnRegistrationSnapshotWrite() {
        JdbcTemplate jdbcTemplate = mockJdbcTemplateWithProcessMark();
        PaymentManagementAppService managementAppService = mock(PaymentManagementAppService.class);
        PaymentOutboxService outboxService = mock(PaymentOutboxService.class);
        DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
        PaymentProviderCatalog providerCatalog = new PaymentProviderCatalog();
        when(managementAppService.getRequiredProviderSettings("stripe")).thenReturn(stripeSettings());
        doThrow(new EmptyResultDataAccessException(1)).when(jdbcTemplate).queryForObject(
                contains("from payment_webhook_event"),
                any(BeanPropertyRowMapper.class),
                any(),
                any()
        );
        PaymentOrderRow order = new PaymentOrderRow();
        order.setId(501L);
        order.setOrderNo("REG-1-ABCD");
        order.setProviderCode("stripe");
        order.setAmountMinor(8800L);
        order.setStatus("PENDING");
        order.setCreatedBy(1001L);
        order.setCreatedByUuid("user-uuid-1001");
        order.setRequestJson("""
                {"metadata":{"bizType":"competition_registration","registrationId":1,"competitionId":11,"teamId":21,"projectId":31}}
                """);
        doReturn(order).when(jdbcTemplate).queryForObject(
                contains("from payment_order"),
                any(BeanPropertyRowMapper.class),
                eq("stripe"),
                any()
        );
        doReturn("AIADC2026-0001").when(jdbcTemplate).queryForObject(
                contains("from competition_registration cr"),
                eq(String.class),
                any()
        );
        doReturn(0).when(jdbcTemplate).update(contains("update competition_registration"), any(Object[].class));
        PaymentWebhookService service = new PaymentWebhookService(
                jdbcTemplate,
                new ObjectMapper(),
                managementAppService,
                providerCatalog,
                outboxService,
                domainEventPublisher
        );
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String payload = "{\"eventId\":\"evt-paid-registration-conflict\",\"eventType\":\"payment.succeeded\",\"orderNo\":\"REG-1-ABCD\"}";

        PaymentWebhookEventDTO result = service.handleWebhook(
                "stripe",
                payload,
                Map.of(
                        "X-Event-Id", "evt-paid-registration-conflict",
                        "X-Event-Type", "payment.succeeded",
                        "X-Timestamp", timestamp,
                        "Stripe-Signature", stripeSignature(timestamp, payload)
                )
        );

        assertThat(result.processed()).isTrue();
        verify(jdbcTemplate, never()).update(contains("update competition_registration"), any(Object[].class));
        verify(domainEventPublisher).publishAll(any());
    }

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
        when(managementAppService.getRequiredProviderSettings("stripe")).thenReturn(settings);

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

        String payload = "{\"eventId\":\"evt-1\",\"eventType\":\"payment.succeeded\"}";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        PaymentWebhookEventDTO result = service.handleWebhook(
                "stripe",
                payload,
                Map.of(
                        "X-Event-Id", "evt-1",
                        "X-Timestamp", timestamp,
                        "Stripe-Signature", stripeSignature(timestamp, payload)
                )
        );

        assertThat(result.eventId()).isEqualTo("evt-1");
        assertThat(result.processed()).isTrue();
        assertThat(result.processMessage()).isEqualTo("already processed");
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
        verifyNoInteractions(outboxService, domainEventPublisher);
    }

    @Test
    void duplicateWebhookStillRequiresValidSignature() {
        JdbcTemplate jdbcTemplate = mockJdbcTemplateWithProcessMark();
        PaymentManagementAppService managementAppService = mock(PaymentManagementAppService.class);
        PaymentOutboxService outboxService = mock(PaymentOutboxService.class);
        DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
        PaymentProviderCatalog providerCatalog = new PaymentProviderCatalog();
        when(managementAppService.getRequiredProviderSettings("stripe")).thenReturn(stripeSettings());
        PaymentWebhookService service = new PaymentWebhookService(
                jdbcTemplate,
                new ObjectMapper(),
                managementAppService,
                providerCatalog,
                outboxService,
                domainEventPublisher
        );

        assertThatThrownBy(() -> service.handleWebhook(
                "stripe",
                "{\"eventId\":\"evt-1\",\"eventType\":\"payment.succeeded\"}",
                Map.of(
                        "X-Event-Id", "evt-1",
                        "X-Timestamp", String.valueOf(Instant.now().getEpochSecond()),
                        "Stripe-Signature", "bad-signature"
                )
        )).isInstanceOf(com.lumira.common.exception.BizException.class)
                .hasMessageContaining("Webhook signature invalid");

        verify(jdbcTemplate, never()).queryForObject(
                contains("from payment_webhook_event"),
                any(BeanPropertyRowMapper.class),
                any(),
                any()
        );
        verifyNoInteractions(outboxService, domainEventPublisher);
    }

    @Test
    void signedPayloadIdentityShouldOverrideUnsignedEventHeaders() {
        JdbcTemplate jdbcTemplate = mockJdbcTemplateWithProcessMark();
        PaymentManagementAppService managementAppService = mock(PaymentManagementAppService.class);
        PaymentOutboxService outboxService = mock(PaymentOutboxService.class);
        DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
        PaymentProviderCatalog providerCatalog = new PaymentProviderCatalog();
        when(managementAppService.getRequiredProviderSettings("stripe")).thenReturn(stripeSettings());
        stubWebhookEventCanBeMarkedProcessed(jdbcTemplate);
        doThrow(new EmptyResultDataAccessException(1)).when(jdbcTemplate).queryForObject(
                contains("from payment_webhook_event"),
                any(BeanPropertyRowMapper.class),
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
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String payload = "{\"eventId\":\"evt-signed\",\"eventType\":\"payment.succeeded\"}";

        PaymentWebhookEventDTO result = service.handleWebhook(
                "stripe",
                payload,
                Map.of(
                        "X-Event-Id", "evt-header-tampered",
                        "X-Event-Type", "refund.succeeded",
                        "X-Timestamp", timestamp,
                        "Stripe-Signature", stripeSignature(timestamp, payload)
                )
        );

        assertThat(result.eventId()).isEqualTo("evt-signed");
        assertThat(result.eventType()).isEqualTo("payment.succeeded");
        verify(jdbcTemplate).queryForObject(
                contains("from payment_webhook_event"),
                any(BeanPropertyRowMapper.class),
                eq("stripe"),
                eq("evt-signed")
        );
        verify(jdbcTemplate).update(
                contains("insert into payment_webhook_event"),
                eq("stripe"),
                eq("evt-signed"),
                eq("payment.succeeded"),
                any(),
                any(),
                eq(payload),
                any(),
                eq(1),
                eq("PENDING"),
                any(),
                isNull(),
                isNull(),
                isNull(),
                isNull()
        );
        verify(outboxService).record(
                isNull(),
                eq("payment"),
                eq("payment.webhook.received"),
                eq("stripe:evt-signed"),
                any()
        );
    }

    @Test
    void refundWebhookShouldBindTrustedRefundSnapshotOnFinalWrite() {
        JdbcTemplate jdbcTemplate = mockJdbcTemplateWithProcessMark();
        PaymentManagementAppService managementAppService = mock(PaymentManagementAppService.class);
        PaymentOutboxService outboxService = mock(PaymentOutboxService.class);
        DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
        PaymentProviderCatalog providerCatalog = new PaymentProviderCatalog();
        when(managementAppService.getRequiredProviderSettings("stripe")).thenReturn(stripeSettings());
        stubWebhookEventCanBeMarkedProcessed(jdbcTemplate);
        doThrow(new EmptyResultDataAccessException(1)).when(jdbcTemplate).queryForObject(
                contains("from payment_webhook_event"),
                any(BeanPropertyRowMapper.class),
                any(),
                any()
        );
        PaymentRefundRow refund = new PaymentRefundRow();
        refund.setId(701L);
        refund.setRefundNo("REF-1");
        refund.setOrderNo("ORD-1");
        refund.setProviderCode("stripe");
        refund.setAmountMinor(8800L);
        refund.setStatus("REFUNDING");
        refund.setCreatedBy(1001L);
        refund.setCreatedByUuid("user-uuid-1001");
        doReturn(refund).when(jdbcTemplate).queryForObject(
                contains("from payment_refund"),
                any(BeanPropertyRowMapper.class),
                eq("stripe"),
                eq("REF-1")
        );
        doReturn(1).when(jdbcTemplate).update(contains("update payment_refund"), any(Object[].class));
        PaymentWebhookService service = new PaymentWebhookService(
                jdbcTemplate,
                new ObjectMapper(),
                managementAppService,
                providerCatalog,
                outboxService,
                domainEventPublisher
        );
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String payload = "{\"eventId\":\"evt-refund\",\"eventType\":\"refund.succeeded\",\"refundNo\":\"REF-1\"}";

        PaymentWebhookEventDTO result = service.handleWebhook(
                "stripe",
                payload,
                Map.of(
                        "X-Event-Id", "evt-refund",
                        "X-Event-Type", "refund.succeeded",
                        "X-Timestamp", timestamp,
                        "Stripe-Signature", stripeSignature(timestamp, payload)
                )
        );

        assertThat(result.processed()).isTrue();
        verify(jdbcTemplate).update(
                contains("update payment_refund"),
                any(),
                any(),
                isNull(),
                isNull(),
                eq(701L),
                eq("REF-1"),
                eq("ORD-1"),
                eq("stripe"),
                eq(8800L),
                eq(1001L),
                eq("user-uuid-1001"),
                eq("REFUNDING")
        );
    }

    @Test
    void refundWebhookShouldRejectWhenRefundSnapshotChanged() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentManagementAppService managementAppService = mock(PaymentManagementAppService.class);
        PaymentOutboxService outboxService = mock(PaymentOutboxService.class);
        DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
        PaymentProviderCatalog providerCatalog = new PaymentProviderCatalog();
        when(managementAppService.getRequiredProviderSettings("stripe")).thenReturn(stripeSettings());
        stubWebhookEventCanBeInserted(jdbcTemplate);
        doThrow(new EmptyResultDataAccessException(1)).when(jdbcTemplate).queryForObject(
                contains("from payment_webhook_event"),
                any(BeanPropertyRowMapper.class),
                any(),
                any()
        );
        PaymentRefundRow refund = new PaymentRefundRow();
        refund.setId(701L);
        refund.setRefundNo("REF-1");
        refund.setOrderNo("ORD-1");
        refund.setProviderCode("stripe");
        refund.setAmountMinor(8800L);
        refund.setStatus("REFUNDING");
        refund.setCreatedBy(1001L);
        refund.setCreatedByUuid("user-uuid-1001");
        doReturn(refund).when(jdbcTemplate).queryForObject(
                contains("from payment_refund"),
                any(BeanPropertyRowMapper.class),
                eq("stripe"),
                eq("REF-1")
        );
        doReturn(0).when(jdbcTemplate).update(contains("update payment_refund"), any(Object[].class));
        PaymentWebhookService service = new PaymentWebhookService(
                jdbcTemplate,
                new ObjectMapper(),
                managementAppService,
                providerCatalog,
                outboxService,
                domainEventPublisher
        );
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String payload = "{\"eventId\":\"evt-refund-conflict\",\"eventType\":\"refund.succeeded\",\"refundNo\":\"REF-1\"}";

        assertThatThrownBy(() -> service.handleWebhook(
                "stripe",
                payload,
                Map.of(
                        "X-Event-Id", "evt-refund-conflict",
                        "X-Event-Type", "refund.succeeded",
                        "X-Timestamp", timestamp,
                        "Stripe-Signature", stripeSignature(timestamp, payload)
                )
        )).isInstanceOf(com.lumira.common.exception.BizException.class)
                .hasMessageContaining("Payment refund state changed during webhook processing");

        verifyNoInteractions(outboxService, domainEventPublisher);
    }


    @Test
    void invalidSignatureRecordsRejectedWebhookWithoutApplyingPaymentEvent() {
        JdbcTemplate jdbcTemplate = mockJdbcTemplateWithProcessMark();
        PaymentManagementAppService managementAppService = mock(PaymentManagementAppService.class);
        PaymentOutboxService outboxService = mock(PaymentOutboxService.class);
        DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
        PaymentProviderCatalog providerCatalog = new PaymentProviderCatalog();
        PaymentProviderSettingsDTO settings = stripeSettings();
        when(managementAppService.getRequiredProviderSettings("stripe")).thenReturn(settings);
        doReturn(null).when(jdbcTemplate).queryForObject(
                anyString(),
                any(BeanPropertyRowMapper.class),
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

        assertThatThrownBy(() -> service.handleWebhook(
                "stripe",
                "{\"eventId\":\"evt-bad\",\"eventType\":\"payment.succeeded\",\"orderNo\":\"ord-1\"}",
                Map.of(
                        "X-Event-Id", "evt-bad",
                        "X-Event-Type", "payment.succeeded",
                        "X-Timestamp", String.valueOf(System.currentTimeMillis() / 1000L),
                        "Stripe-Signature", "bad-signature"
                )
        )).isInstanceOf(com.lumira.common.exception.BizException.class)
                .hasMessageContaining("Webhook signature invalid");

        verifyNoInteractions(outboxService, domainEventPublisher);
        verify(jdbcTemplate, never()).update(contains("insert into payment_webhook_event"), any(Object[].class));
        verify(jdbcTemplate, never()).update(contains("update payment_order"), any(Object[].class));
    }

    @Test
    void oversizedPayloadShouldRejectBeforeParsingOrProviderLookup() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentManagementAppService managementAppService = mock(PaymentManagementAppService.class);
        PaymentOutboxService outboxService = mock(PaymentOutboxService.class);
        DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
        PaymentWebhookService service = new PaymentWebhookService(
                jdbcTemplate,
                new ObjectMapper(),
                managementAppService,
                new PaymentProviderCatalog(),
                outboxService,
                domainEventPublisher
        );
        String payload = "x".repeat(256 * 1024 + 1);

        assertThatThrownBy(() -> service.handleWebhook("stripe", payload, Map.of()))
                .isInstanceOf(com.lumira.common.exception.BizException.class)
                .hasMessageContaining("Webhook payload too large");

        verifyNoInteractions(managementAppService, outboxService, domainEventPublisher);
        verify(jdbcTemplate, never()).queryForObject(anyString(), any(BeanPropertyRowMapper.class), any(), any());
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void signedWebhookShouldRejectMissingEventIdBeforeIdempotencyLookup() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentManagementAppService managementAppService = mock(PaymentManagementAppService.class);
        PaymentOutboxService outboxService = mock(PaymentOutboxService.class);
        DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
        when(managementAppService.getRequiredProviderSettings("stripe")).thenReturn(stripeSettings());
        PaymentWebhookService service = new PaymentWebhookService(
                jdbcTemplate,
                new ObjectMapper(),
                managementAppService,
                new PaymentProviderCatalog(),
                outboxService,
                domainEventPublisher
        );
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String payload = "{\"eventType\":\"payment.succeeded\",\"orderNo\":\"ORD-1\"}";

        assertThatThrownBy(() -> service.handleWebhook(
                "stripe",
                payload,
                Map.of(
                        "X-Event-Type", "payment.succeeded",
                        "X-Timestamp", timestamp,
                        "Stripe-Signature", stripeSignature(timestamp, payload)
                )
        )).isInstanceOf(com.lumira.common.exception.BizException.class)
                .hasMessageContaining("Webhook event id is required");

        verify(jdbcTemplate, never()).queryForObject(
                contains("from payment_webhook_event"),
                any(BeanPropertyRowMapper.class),
                any(),
                any()
        );
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
        verifyNoInteractions(outboxService, domainEventPublisher);
    }

    @Test
    void signedWebhookShouldNotTrustHeaderOnlyEventIdentity() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentManagementAppService managementAppService = mock(PaymentManagementAppService.class);
        PaymentOutboxService outboxService = mock(PaymentOutboxService.class);
        DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
        when(managementAppService.getRequiredProviderSettings("stripe")).thenReturn(stripeSettings());
        PaymentWebhookService service = new PaymentWebhookService(
                jdbcTemplate,
                new ObjectMapper(),
                managementAppService,
                new PaymentProviderCatalog(),
                outboxService,
                domainEventPublisher
        );
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String payload = "{\"orderNo\":\"ORD-1\"}";

        assertThatThrownBy(() -> service.handleWebhook(
                "stripe",
                payload,
                Map.of(
                        "X-Event-Id", "evt-header-only",
                        "X-Event-Type", "payment.succeeded",
                        "X-Timestamp", timestamp,
                        "Stripe-Signature", stripeSignature(timestamp, payload)
                )
        )).isInstanceOf(com.lumira.common.exception.BizException.class)
                .hasMessageContaining("Webhook event id is required");

        verify(jdbcTemplate, never()).queryForObject(
                contains("from payment_webhook_event"),
                any(BeanPropertyRowMapper.class),
                any(),
                any()
        );
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
        verifyNoInteractions(outboxService, domainEventPublisher);
    }

    @Test
    void signedWebhookShouldRejectMissingEventTypeBeforeInsert() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentManagementAppService managementAppService = mock(PaymentManagementAppService.class);
        PaymentOutboxService outboxService = mock(PaymentOutboxService.class);
        DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
        when(managementAppService.getRequiredProviderSettings("stripe")).thenReturn(stripeSettings());
        PaymentWebhookService service = new PaymentWebhookService(
                jdbcTemplate,
                new ObjectMapper(),
                managementAppService,
                new PaymentProviderCatalog(),
                outboxService,
                domainEventPublisher
        );
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String payload = "{\"eventId\":\"evt-missing-type\",\"orderNo\":\"ORD-1\"}";

        assertThatThrownBy(() -> service.handleWebhook(
                "stripe",
                payload,
                Map.of(
                        "X-Event-Id", "evt-missing-type",
                        "X-Timestamp", timestamp,
                        "Stripe-Signature", stripeSignature(timestamp, payload)
                )
        )).isInstanceOf(com.lumira.common.exception.BizException.class)
                .hasMessageContaining("Webhook event type is required");

        verify(jdbcTemplate, never()).update(contains("insert into payment_webhook_event"), any(Object[].class));
        verify(jdbcTemplate, never()).update(contains("update payment_order"), any(Object[].class));
        verifyNoInteractions(outboxService, domainEventPublisher);
    }

    @Test
    void providerSignedWebhookCannotPayOrderFromAnotherProvider() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentManagementAppService managementAppService = mock(PaymentManagementAppService.class);
        PaymentOutboxService outboxService = mock(PaymentOutboxService.class);
        DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
        PaymentProviderCatalog providerCatalog = new PaymentProviderCatalog();
        when(managementAppService.getRequiredProviderSettings("stripe")).thenReturn(stripeSettings());
        stubWebhookEventCanBeInserted(jdbcTemplate);
        stubWebhookEventCanBeMarkedProcessed(jdbcTemplate);
        doThrow(new EmptyResultDataAccessException(1)).when(jdbcTemplate).queryForObject(
                contains("from payment_webhook_event"),
                any(BeanPropertyRowMapper.class),
                any(),
                any()
        );
        doThrow(new EmptyResultDataAccessException(1)).when(jdbcTemplate).queryForObject(
                contains("provider_code = ?"),
                any(BeanPropertyRowMapper.class),
                eq("stripe"),
                eq("ORD-OTHER")
        );
        PaymentWebhookService service = new PaymentWebhookService(
                jdbcTemplate,
                new ObjectMapper(),
                managementAppService,
                providerCatalog,
                outboxService,
                domainEventPublisher
        );
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String payload = "{\"eventId\":\"evt-cross-provider\",\"eventType\":\"payment.succeeded\",\"orderNo\":\"ORD-OTHER\"}";

        assertThatThrownBy(() -> service.handleWebhook(
                "stripe",
                payload,
                Map.of(
                        "X-Event-Id", "evt-cross-provider",
                        "X-Event-Type", "payment.succeeded",
                        "X-Timestamp", timestamp,
                        "Stripe-Signature", stripeSignature(timestamp, payload)
                )
        )).isInstanceOf(com.lumira.common.exception.BizException.class)
                .hasMessageContaining("Payment order does not exist");

        verify(jdbcTemplate, never()).update(contains("update payment_order"), any(Object[].class));
        verifyNoInteractions(outboxService, domainEventPublisher);
    }

    @Test
    void webhookFinalWritesShouldBindTrustedOrderAndEventSnapshots() throws Exception {
        String source = Files.readString(java.nio.file.Path.of("src/main/java/com/lumira/payment/service/PaymentWebhookService.java"));

        assertThat(source)
                .contains("and amount_minor = ?")
                .contains("and created_by = ?")
                .contains("and created_by_uuid = ?")
                .contains("and status = ?")
                .contains("and event_type = ?")
                .contains("and processed = 0")
                .contains("and signature_valid = 1");
    }

    @Test
    void markProcessedConflictShouldStopWebhookOutbox() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentManagementAppService managementAppService = mock(PaymentManagementAppService.class);
        PaymentOutboxService outboxService = mock(PaymentOutboxService.class);
        DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
        PaymentProviderCatalog providerCatalog = new PaymentProviderCatalog();
        when(managementAppService.getRequiredProviderSettings("stripe")).thenReturn(stripeSettings());
        stubWebhookEventCanBeInserted(jdbcTemplate);
        doThrow(new EmptyResultDataAccessException(1)).when(jdbcTemplate).queryForObject(
                contains("from payment_webhook_event"),
                any(BeanPropertyRowMapper.class),
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
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String payload = "{\"eventId\":\"evt-conflict\",\"eventType\":\"payment.succeeded\"}";

        assertThatThrownBy(() -> service.handleWebhook(
                "stripe",
                payload,
                Map.of(
                        "X-Event-Id", "evt-conflict",
                        "X-Event-Type", "payment.succeeded",
                        "X-Timestamp", timestamp,
                        "Stripe-Signature", stripeSignature(timestamp, payload)
                )
        )).isInstanceOf(com.lumira.common.exception.BizException.class)
                .hasMessageContaining("Payment webhook event was changed during processing");

        verifyNoInteractions(outboxService, domainEventPublisher);
    }

    @Test
    void webhookInsertConflictShouldStopBeforeBusinessWrites() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentManagementAppService managementAppService = mock(PaymentManagementAppService.class);
        PaymentOutboxService outboxService = mock(PaymentOutboxService.class);
        DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
        PaymentProviderCatalog providerCatalog = new PaymentProviderCatalog();
        when(managementAppService.getRequiredProviderSettings("stripe")).thenReturn(stripeSettings());
        doThrow(new EmptyResultDataAccessException(1)).when(jdbcTemplate).queryForObject(
                contains("from payment_webhook_event"),
                any(BeanPropertyRowMapper.class),
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
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String payload = "{\"eventId\":\"evt-insert-conflict\",\"eventType\":\"payment.succeeded\",\"orderNo\":\"REG-1-ABCD\"}";

        assertThatThrownBy(() -> service.handleWebhook(
                "stripe",
                payload,
                Map.of(
                        "X-Event-Id", "evt-insert-conflict",
                        "X-Event-Type", "payment.succeeded",
                        "X-Timestamp", timestamp,
                        "Stripe-Signature", stripeSignature(timestamp, payload)
                )
        )).isInstanceOf(com.lumira.common.exception.BizException.class)
                .hasMessageContaining("Payment webhook event changed during insert");

        verify(jdbcTemplate, never()).update(contains("update payment_order"), any(Object[].class));
        verifyNoInteractions(outboxService, domainEventPublisher);
    }

    @Test
    void replayedNonceIsRejectedAfterSignatureValidation() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentManagementAppService managementAppService = mock(PaymentManagementAppService.class);
        PaymentOutboxService outboxService = mock(PaymentOutboxService.class);
        DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
        PaymentProviderCatalog providerCatalog = new PaymentProviderCatalog();
        when(managementAppService.getRequiredProviderSettings("stripe")).thenReturn(stripeSettings());
        doThrow(new EmptyResultDataAccessException(1)).when(jdbcTemplate).queryForObject(
                anyString(),
                any(BeanPropertyRowMapper.class),
                any(),
                any()
        );
        doReturn(List.of(1L)).when(jdbcTemplate).queryForList(
                contains("nonce = ?"),
                eq(Long.class),
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

        String payload = "{\"eventId\":\"evt-replay\",\"eventType\":\"payment.succeeded\"}";
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);

        assertThatThrownBy(() -> service.handleWebhook(
                "stripe",
                payload,
                Map.of(
                        "X-Event-Id", "evt-replay",
                        "X-Event-Type", "payment.succeeded",
                        "X-Timestamp", timestamp,
                        "X-Nonce", "nonce-1",
                        "Stripe-Signature", stripeSignature(timestamp, payload)
                )
        )).isInstanceOf(com.lumira.common.exception.BizException.class)
                .hasMessageContaining("Webhook replay rejected");

        verifyNoInteractions(outboxService, domainEventPublisher);
        verify(jdbcTemplate, never()).update(contains("insert into payment_webhook_event"), any(Object[].class));
    }

    private void stubWebhookEventCanBeMarkedProcessed(JdbcTemplate jdbcTemplate) {
        doReturn(1).when(jdbcTemplate).update(contains("update payment_webhook_event"), any(Object[].class));
    }

    private void stubWebhookEventCanBeInserted(JdbcTemplate jdbcTemplate) {
        doReturn(1).when(jdbcTemplate).update(contains("insert into payment_webhook_event"), any(Object[].class));
    }

    private JdbcTemplate mockJdbcTemplateWithProcessMark() {
        return mock(JdbcTemplate.class, invocation -> {
            if ("update".equals(invocation.getMethod().getName())
                    && invocation.getArgument(0) instanceof String sql
                    && (sql.contains("insert into payment_webhook_event")
                    || sql.contains("update payment_webhook_event")
                    || sql.contains("update payment_order")
                    || sql.contains("update competition_registration"))) {
                return 1;
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });
    }

    private PaymentProviderSettingsDTO stripeSettings() {
        PaymentProviderSettingsDTO settings = new PaymentProviderSettingsDTO();
        settings.setProviderCode("stripe");
        settings.setEnabled(true);
        settings.setWebhookSecret("secret");
        return settings;
    }

    private String stripeSignature(String timestamp, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec("secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}


