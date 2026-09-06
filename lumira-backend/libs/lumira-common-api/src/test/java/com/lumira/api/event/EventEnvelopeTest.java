package com.lumira.api.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EventEnvelopeTest {

    @Test
    void createsImmutableEnvelopeWithDefaultTimestampAndPayload() {
        Map<String, Object> sourcePayload = new HashMap<>();
        sourcePayload.put("orderNo", "PAY-1");

        EventEnvelope envelope = EventEnvelope.of(
                "evt-1",
                "payment.order.paid",
                "payment",
                "order-1",
                1,
                null,
                "trace-1",
                sourcePayload
        );
        sourcePayload.put("orderNo", "mutated");

        assertThat(envelope.occurredAt()).isNotNull();
        assertThat(envelope.payload()).containsOnly(Map.entry("orderNo", "PAY-1"));
        assertThat(envelope.payload()).isUnmodifiable();
    }

    @Test
    void rejectsInvalidIdentityAndSchema() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EventEnvelope(" ", "type", "source", "aggregate", 1,
                        Instant.EPOCH, null, Map.of()))
                .withMessage("eventId is required");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EventEnvelope("id", "type", "source", "aggregate", 0,
                        Instant.EPOCH, null, Map.of()))
                .withMessage("schemaVersion must be positive");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EventEnvelope("id", "type", "source", "aggregate", 1,
                        Instant.EPOCH, " ", Map.of()))
                .withMessage("traceId must not be blank");
    }
}
