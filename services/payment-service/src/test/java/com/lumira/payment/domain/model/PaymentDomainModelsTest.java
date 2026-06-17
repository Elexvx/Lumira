package com.lumira.payment.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lumira.payment.domain.model.PaymentDomainModels.PaymentOrderAggregate;
import com.lumira.payment.domain.model.PaymentDomainModels.WebhookEvent;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PaymentDomainModelsTest {

    @Test
    void paymentOrderAggregateEmitsPaidEventOnce() {
        PaymentOrderAggregate order = new PaymentOrderAggregate("pay-100", 1L, BigDecimal.TEN, "PENDING");

        order.markPaid("txn-1");
        order.markPaid("txn-1");

        assertThat(order.domainEvents()).hasSize(1);
        assertThat(order.domainEvents().getFirst().eventType()).isEqualTo("PAYMENT_ORDER_PAID");
        assertThat(order.domainEvents().getFirst().aggregateId()).isEqualTo("pay-100");
    }

    @Test
    void paymentOrderRejectsNonPositiveAmount() {
        assertThatThrownBy(() -> new PaymentOrderAggregate("pay-100", 1L, BigDecimal.ZERO, "PENDING"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @Test
    void webhookEventBuildsTenantScopedIdempotencyKey() {
        WebhookEvent event = new WebhookEvent("mockpay", "evt-1", "sig", "{}");

        assertThat(event.idempotencyKey(1L)).isEqualTo("1:mockpay:evt-1");
    }
}
