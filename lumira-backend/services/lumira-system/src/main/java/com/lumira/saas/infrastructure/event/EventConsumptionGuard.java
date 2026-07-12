package com.lumira.saas.infrastructure.event;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;

/** Executes a consumer side effect and its durable idempotency receipt atomically. */
@Service
public class EventConsumptionGuard {
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public EventConsumptionGuard(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    public boolean executeOnce(EventIdentity event, Runnable sideEffect) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(sideEffect, "sideEffect");
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            int claimed = jdbcTemplate.update("""
                    insert ignore into event_consumer_receipt
                      (consumer_name, event_id, event_type, source_module, aggregate_id, result_status)
                    values (?, ?, ?, ?, ?, 'PROCESSING')
                    """, event.consumerName(), event.eventId(), event.eventType(),
                    event.sourceModule(), event.aggregateId());
            if (claimed == 0) {
                return false;
            }
            sideEffect.run();
            jdbcTemplate.update("""
                    update event_consumer_receipt set result_status = 'SUCCEEDED', processed_at = current_timestamp(6)
                    where consumer_name = ? and event_id = ?
                    """, event.consumerName(), event.eventId());
            return true;
        }));
    }

    public record EventIdentity(
            String consumerName,
            String eventId,
            String eventType,
            String sourceModule,
            String aggregateId
    ) {
        public EventIdentity {
            consumerName = requireText(consumerName, "consumerName");
            eventId = requireText(eventId, "eventId");
            eventType = requireText(eventType, "eventType");
            sourceModule = requireText(sourceModule, "sourceModule");
            aggregateId = requireText(aggregateId, "aggregateId");
        }

        private static String requireText(String value, String name) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
            return value.trim();
        }
    }
}
