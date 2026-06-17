package com.lumira.payment.controller;

import com.lumira.api.architecture.OwnerObservabilityDTO;
import com.lumira.api.architecture.OwnerReadinessDTO;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.web.TraceContext;
import com.lumira.payment.service.PaymentOutboxService;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/payment")
public class PaymentReadinessV2Controller {

    private final PaymentOutboxService paymentOutboxService;

    public PaymentReadinessV2Controller(PaymentOutboxService paymentOutboxService) {
        this.paymentOutboxService = paymentOutboxService;
    }

    @GetMapping("/readiness")
    public ApiResponse<OwnerReadinessDTO> readiness() {
        return ApiResponse.success(new OwnerReadinessDTO(
                "Payment",
                "payment-service",
                "READY_WITH_BLOCKERS",
                "contract-and-observability",
                List.of(
                        "payment_provider_config",
                        "payment_order",
                        "payment_refund",
                        "payment_webhook_event",
                        "payment_event_outbox"
                ),
                List.of(
                        "/api/v2/payment/readiness",
                        "/api/v2/payment/health",
                        "/api/v2/payment/metrics",
                        "/api/v2/payment/providers",
                        "/api/v2/payment/providers/{providerCode}",
                        "/api/v2/payment/providers/{providerCode}/test",
                        "/api/v2/payment/orders",
                        "/api/v2/payment/orders/{orderNo}",
                        "/api/v2/payment/orders/{orderNo}/refunds",
                        "/api/v2/payment/refunds/{refundNo}",
                        "/api/v2/payment/webhooks/{providerCode}",
                        "/payment/internal/jobs/outbox/relay",
                        "/payment/internal/jobs/outbox/{id}/replay"
                ),
                List.of(
                        "PaymentOrderCreated",
                        "PaymentSucceeded",
                        "RefundCreated",
                        "RefundSucceeded",
                        "PaymentWebhookReceived",
                        "PaymentWebhookProcessed"
                ),
                List.of(
                        "payment.db.owner-tables",
                        "payment.provider-config.readable",
                        "payment.webhook.signature-verifier",
                        "payment.webhook.idempotency-store",
                        "payment.outbox.dispatchable-backlog"
                ),
                List.of(
                        "payment.webhook.p95",
                        "payment.webhook.signature_failure.total",
                        "payment.webhook.duplicate.total",
                        "payment.order.success.total",
                        "payment.refund.success.total",
                        "payment.outbox.pending_backlog",
                        "payment.outbox.failed_backlog",
                        "payment.outbox.dead_letter_count",
                        "payment.outbox.dispatchable_backlog"
                ),
                List.of(
                        "provider credentials",
                        "webhook signature secret",
                        "webhook nonce/idempotency storage",
                        "payment_event_outbox",
                        "job-executor internal relay"
                ),
                List.of(
                        "route webhook gateway back to payment-service monolith endpoint",
                        "keep provider callback URL dual-routed during rollback window",
                        "replay payment events by outbox id/eventKey after rollback",
                        "preserve payment_order/payment_refund owner writes in payment-service"
                ),
                List.of(
                        "Payment v2 adapter and owner observability contract are available; provider sandbox or simulator drill is still required before physical split",
                        "provider-specific signature verification and webhook replay runbook must be exercised in a production-like environment"
                )
        ), TraceContext.getRequestId());
    }

    @GetMapping("/health")
    public ApiResponse<OwnerObservabilityDTO> health() {
        return ApiResponse.success(new OwnerObservabilityDTO(
                "Payment",
                "payment-service",
                "UP",
                OffsetDateTime.now(),
                List.of(
                        healthCheck("payment.db.owner-tables", "CONFIGURED", "Payment owner table patterns are declared and guarded by architecture tests."),
                        healthCheck("payment.provider-config.readable", "CONFIGURED", "Provider configuration must be readable before webhook/order traffic is accepted."),
                        healthCheck("payment.webhook.signature-verifier", "CONFIGURED", "Webhook signature verification is enforced by the Payment owner."),
                        healthCheck("payment.webhook.idempotency-store", "CONFIGURED", "Webhook provider event id and nonce idempotency are owned by Payment."),
                        healthCheck("payment.outbox.dispatchable-backlog", "CONFIGURED", "Payment outbox dispatchable backlog is exposed as an owner gauge.")
                ),
                paymentMetrics()
        ), TraceContext.getRequestId());
    }

    @GetMapping("/metrics")
    public ApiResponse<OwnerObservabilityDTO> metrics() {
        return ApiResponse.success(new OwnerObservabilityDTO(
                "Payment",
                "payment-service",
                "METRICS_DECLARED",
                OffsetDateTime.now(),
                List.of(),
                paymentMetrics()
        ), TraceContext.getRequestId());
    }

    private List<OwnerObservabilityDTO.MetricDTO> paymentMetrics() {
        PaymentOutboxService.OutboxMetricsSnapshot snapshot = paymentOutboxService.snapshot();
        return List.of(
                metric("payment.webhook.p95", "timer", "milliseconds", "Payment webhook processing p95 latency tagged by provider/result."),
                metric("payment.webhook.signature_failure.total", "counter", "webhooks", "Webhook signature validation failures tagged by provider."),
                metric("payment.webhook.duplicate.total", "counter", "webhooks", "Duplicate webhook events rejected by provider event id or nonce."),
                metric("payment.order.success.total", "counter", "orders", "Payment order state transitions to success tagged by provider."),
                metric("payment.refund.success.total", "counter", "refunds", "Payment refund state transitions to success tagged by provider."),
                metric("payment.outbox.pending_backlog", "gauge", "events", "Payment owner pending outbox backlog.", snapshot.pendingBacklog()),
                metric("payment.outbox.failed_backlog", "gauge", "events", "Payment owner failed outbox backlog awaiting retry.", snapshot.failedBacklog()),
                metric("payment.outbox.dead_letter_count", "gauge", "events", "Payment owner outbox dead-letter count.", snapshot.deadLetterCount()),
                metric("payment.outbox.dispatchable_backlog", "gauge", "events", "Payment owner outbox events ready for dispatch or retry.", snapshot.dispatchableBacklog())
        );
    }

    private OwnerObservabilityDTO.HealthCheckDTO healthCheck(String name, String status, String description) {
        return new OwnerObservabilityDTO.HealthCheckDTO(name, status, description);
    }

    private OwnerObservabilityDTO.MetricDTO metric(String name, String type, String unit, String description) {
        return new OwnerObservabilityDTO.MetricDTO(name, type, unit, description);
    }

    private OwnerObservabilityDTO.MetricDTO metric(String name, String type, String unit, String description, long value) {
        return new OwnerObservabilityDTO.MetricDTO(name, type, unit, description, (double) value);
    }
}
