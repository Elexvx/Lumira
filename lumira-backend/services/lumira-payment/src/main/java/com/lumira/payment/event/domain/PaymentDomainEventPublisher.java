package com.lumira.payment.event.domain;

import com.lumira.domain.event.DomainEvent;
import com.lumira.domain.event.DomainEventPublisher;
import com.lumira.payment.service.PaymentOutboxService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service("paymentDomainEventPublisher")
public class PaymentDomainEventPublisher implements DomainEventPublisher {

    private final PaymentOutboxService outboxService;

    public PaymentDomainEventPublisher(PaymentOutboxService outboxService) {
        this.outboxService = outboxService;
    }

    @Override
    public void publish(DomainEvent event) {
        if (event == null) {
            return;
        }
        outboxService.record(
                resolveUserId(event.attributes()),
                "payment",
                event.eventType(),
                event.eventKey(),
                envelope(event)
        );
    }

    private Long resolveUserId(Map<String, Object> attributes) {
        if (!hasUserUuid(attributes)) {
            return null;
        }
        Object userId = attributes == null ? null : attributes.get("userId");
        if (userId instanceof Number number) {
            long value = number.longValue();
            return value > 0 ? value : null;
        }
        return null;
    }

    private boolean hasUserUuid(Map<String, Object> attributes) {
        Object value = attributes == null ? null : attributes.get("userUuid");
        return value instanceof String text && !text.isBlank();
    }

    private Map<String, Object> envelope(DomainEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", event.eventId().toString());
        payload.put("eventType", event.eventType());
        payload.put("aggregateType", event.aggregateType());
        payload.put("aggregateId", event.aggregateId());
        payload.put("schemaVersion", event.schemaVersion());
        payload.put("eventKey", event.eventKey());
        payload.put("occurredAt", event.occurredAt().toString());
        payload.put("attributes", event.attributes() == null ? Map.of() : event.attributes());
        return payload;
    }
}
