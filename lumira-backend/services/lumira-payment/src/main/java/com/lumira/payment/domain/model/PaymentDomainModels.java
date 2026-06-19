package com.lumira.payment.domain.model;

import com.lumira.domain.event.StandardDomainEvent;
import com.lumira.domain.model.AggregateRoot;
import com.lumira.domain.model.EntityId;
import java.math.BigDecimal;
import java.util.Map;

public final class PaymentDomainModels {

    private PaymentDomainModels() {
    }

    public static final class PaymentOrderAggregate extends AggregateRoot<String> {
        private final Long tenantId;
        private final BigDecimal amount;
        private String status;

        public PaymentOrderAggregate(String orderNo, Long tenantId, BigDecimal amount, String status) {
            super(EntityId.of(orderNo));
            if (amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("amount must be positive");
            }
            this.tenantId = tenantId;
            this.amount = amount;
            this.status = status == null ? "CREATED" : status;
        }

        public void recordCreated(String providerCode, String currency, Long userId) {
            registerEvent(StandardDomainEvent.of(
                    "PAYMENT_ORDER_CREATED",
                    "payment.order",
                    id().value(),
                    tenantId,
                    Map.of(
                            "amount", amount,
                            "providerCode", providerCode == null ? "" : providerCode,
                            "currency", currency == null ? "" : currency,
                            "userId", userId == null ? 0L : userId
                    )
            ));
        }

        public void markPaid(String providerTxnId) {
            if ("PAID".equals(status)) {
                return;
            }
            status = "PAID";
            registerEvent(StandardDomainEvent.of(
                    "PAYMENT_ORDER_PAID",
                    "payment.order",
                    id().value(),
                    tenantId,
                    Map.of("amount", amount, "providerTxnId", providerTxnId == null ? "" : providerTxnId)
            ));
        }
    }

    public record WebhookEvent(String providerCode, String eventId, String signature, String payload) {

        public String idempotencyKey(Long tenantId) {
            return tenantId + ":" + providerCode + ":" + eventId;
        }
    }
}
