package com.lumira.message.event;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;

/**
 * Commits a message-side effect and its idempotency receipt in one database
 * transaction.
 */
@Service
public class MessageEventConsumptionGuard {
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public MessageEventConsumptionGuard(
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    public boolean executeOnce(EventIdentity event, Runnable sideEffect) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(sideEffect, "sideEffect");
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            int claimed = jdbcTemplate.update(
                    """
                            insert ignore into event_consumer_receipt
                              (consumer_name, event_id, event_type, source_module, aggregate_id, result_status)
                            values (?, ?, ?, ?, ?, 'PROCESSING')
                            """,
                    event.consumerName(),
                    event.eventId(),
                    event.eventType(),
                    event.sourceModule(),
                    event.aggregateId()
            );
            if (claimed == 0) {
                return false;
            }
            sideEffect.run();
            int completed = jdbcTemplate.update(
                    """
                            update event_consumer_receipt
                               set result_status = 'SUCCEEDED',
                                   processed_at = current_timestamp(6)
                             where consumer_name = ?
                               and event_id = ?
                               and result_status = 'PROCESSING'
                            """,
                    event.consumerName(),
                    event.eventId()
            );
            if (completed != 1) {
                throw new IllegalStateException("message event receipt changed before completion");
            }
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
            consumerName = requireText(consumerName, "consumerName", 128);
            eventId = requireText(eventId, "eventId", 64);
            eventType = requireText(eventType, "eventType", 128);
            sourceModule = requireText(sourceModule, "sourceModule", 64);
            aggregateId = requireText(aggregateId, "aggregateId", 191);
        }

        private static String requireText(String value, String field, int maxLength) {
            if (value == null || value.isBlank() || value.trim().length() > maxLength) {
                throw new IllegalArgumentException(field + " is invalid");
            }
            return value.trim();
        }
    }
}
