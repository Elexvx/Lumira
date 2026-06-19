package com.lumira.payment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lumira.payment.service.PaymentOutboxService;
import org.junit.jupiter.api.Test;

class PaymentReadinessV2ControllerTest {

    @Test
    void readiness_shouldExposePaymentSplitGateContract() {
        PaymentOutboxService outboxService = mock(PaymentOutboxService.class);
        when(outboxService.snapshot()).thenReturn(new PaymentOutboxService.OutboxMetricsSnapshot(3L, 2L, 1L, 4L));
        PaymentReadinessV2Controller controller = new PaymentReadinessV2Controller(outboxService);

        var response = controller.readiness();

        assertThat(response.getHttpStatus()).isEqualTo(200);
        var readiness = response.getData();
        assertThat(readiness.context()).isEqualTo("Payment");
        assertThat(readiness.ownerModule()).isEqualTo("payment-service");
        assertThat(readiness.status()).isEqualTo("READY_WITH_BLOCKERS");
        assertThat(readiness.ownerTablePatterns())
                .contains("payment_order", "payment_refund", "payment_webhook_event", "payment_event_outbox");
        assertThat(readiness.apiContracts())
                .contains(
                        "/api/v2/payment/readiness",
                        "/api/v2/payment/health",
                        "/api/v2/payment/metrics",
                        "/api/v2/payment/webhooks/{providerCode}",
                        "/payment/internal/jobs/outbox/relay",
                        "/payment/internal/jobs/outbox/{id}/replay"
                );
        assertThat(readiness.eventContracts())
                .contains("PaymentOrderCreated", "PaymentWebhookProcessed");
        assertThat(readiness.metrics())
                .contains("payment.outbox.dead_letter_count", "payment.outbox.dispatchable_backlog", "payment.webhook.p95");
        assertThat(readiness.blockers())
                .anySatisfy(blocker -> assertThat(blocker).contains("provider sandbox"));

        var health = controller.health().getData();
        assertThat(health.status()).isEqualTo("UP");
        assertThat(health.healthChecks())
                .extracting(check -> check.name())
                .contains("payment.db.owner-tables", "payment.webhook.signature-verifier", "payment.outbox.dispatchable-backlog");

        var metrics = controller.metrics().getData();
        assertThat(metrics.status()).isEqualTo("METRICS_DECLARED");
        assertThat(metrics.metrics())
                .extracting(metric -> metric.name())
                .contains("payment.outbox.pending_backlog", "payment.outbox.failed_backlog", "payment.outbox.dead_letter_count", "payment.outbox.dispatchable_backlog");
        assertThat(metrics.metrics())
                .anySatisfy(metric -> {
                    assertThat(metric.name()).isEqualTo("payment.outbox.dead_letter_count");
                    assertThat(metric.value()).isEqualTo(1.0);
                });
        assertThat(metrics.metrics())
                .anySatisfy(metric -> {
                    assertThat(metric.name()).isEqualTo("payment.outbox.dispatchable_backlog");
                    assertThat(metric.value()).isEqualTo(4.0);
                });
    }
}
