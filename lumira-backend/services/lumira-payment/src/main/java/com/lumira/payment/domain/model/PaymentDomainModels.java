package com.lumira.payment.domain.model;

import com.lumira.domain.event.StandardDomainEvent;
import com.lumira.domain.model.AggregateRoot;
import com.lumira.domain.model.EntityId;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PaymentDomainModels {

    private PaymentDomainModels() {
    }

    public static final class PaymentOrderAggregate extends AggregateRoot<String> {
        private final BigDecimal amount;
        private String status;

        public PaymentOrderAggregate(String orderNo, BigDecimal amount, String status) {
            super(EntityId.of(orderNo));
            if (amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("amount must be positive");
            }
            this.amount = amount;
            this.status = status == null ? "CREATED" : status;
        }

        public void recordCreated(String providerCode, String currency) {
            recordCreated(providerCode, currency, null, null);
        }

        public void recordCreated(String providerCode, String currency, Long userId, String userUuid) {
            if (userId != null && (userId <= 0 || userUuid == null || userUuid.isBlank())) {
                throw new IllegalArgumentException("trusted payment actor identity is required");
            }
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("amount", amount);
            attributes.put("providerCode", providerCode == null ? "" : providerCode);
            attributes.put("currency", currency == null ? "" : currency);
            if (userId != null) {
                attributes.put("userId", userId);
            }
            if (userUuid != null && !userUuid.isBlank()) {
                attributes.put("userUuid", userUuid.trim());
            }
            registerEvent(StandardDomainEvent.of(
                    "PAYMENT_ORDER_CREATED",
                    "payment.order",
                    id().value(),
                    attributes
            ));
        }

        public void markPaid(String providerTxnId) {
            markPaid(providerTxnId, null, null, null);
        }

        public void markPaid(String providerTxnId, Long userId, String userUuid, Long registrationId) {
            if ("PAID".equals(status)) {
                return;
            }
            if (userId != null && (userId <= 0 || userUuid == null || userUuid.isBlank())) {
                throw new IllegalArgumentException("trusted payment owner identity is required");
            }
            status = "PAID";
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("amount", amount);
            attributes.put("providerTxnId", providerTxnId == null ? "" : providerTxnId);
            if (userId != null) {
                attributes.put("userId", userId);
                attributes.put("userUuid", userUuid.trim());
            }
            if (registrationId != null && registrationId > 0) {
                attributes.put("registrationId", registrationId);
                attributes.put("bizType", "competition_registration");
            }
            registerEvent(StandardDomainEvent.of(
                    "PAYMENT_ORDER_PAID",
                    "payment.order",
                    id().value(),
                    attributes
            ));
        }
    }

    public record WebhookEvent(String providerCode, String eventId, String signature, String payload) {

        public String idempotencyKey() {
            return providerCode + ":" + eventId;
        }
    }
}
