package com.legendary.invention.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class PaymentOutboxService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentOutboxService.class);
    private static final int MAX_RETRY_DELAY_SECONDS = 300;
    private static final int MAX_ERROR_LENGTH = 512;
    private static final int MAX_DISPATCH_LIMIT = 200;
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_DISPATCHING = "DISPATCHING";
    private static final String STATUS_DELIVERED = "DELIVERED";
    private static final String STATUS_FAILED = "FAILED";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PaymentOutboxService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void recordAfterCommit(Long tenantId, Long userId, String sourceType, String eventType, String eventKey, Object payload) {
        Runnable action = () -> record(tenantId, userId, sourceType, eventType, eventKey, payload);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }

    public void record(Long tenantId, Long userId, String sourceType, String eventType, String eventKey, Object payload) {
        jdbcTemplate.update(
                """
                        insert into payment_event_outbox (
                            tenant_id, user_id, source_type, event_type, event_key, payload_json,
                            status, retry_count, next_retry_at, created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, 'PENDING', 0, ?, ?, ?, 0)
                        """,
                tenantId,
                userId,
                sourceType,
                eventType,
                eventKey,
                serialize(payload),
                LocalDateTime.now(),
                userId == null ? 0L : userId,
                userId == null ? 0L : userId
        );
    }

    public int dispatchPending(PaymentOutboxDispatcher dispatcher, int limit) {
        if (dispatcher == null) {
            return 0;
        }

        int delivered = 0;
        for (PaymentOutboxRow row : listDispatchable(limit)) {
            if (!claimForDispatch(row)) {
                continue;
            }

            try {
                dispatcher.dispatch(row);
                markDelivered(row);
                delivered++;
            } catch (RuntimeException exception) {
                logger.warn("支付 outbox 投递失败: id={}, eventType={}, message={}", row.getId(), row.getEventType(), exception.getMessage());
                markFailed(row, exception);
            }
        }
        return delivered;
    }

    public void replay(Long id, PaymentOutboxDispatcher dispatcher) {
        PaymentOutboxRow row = findById(id);
        if (row == null || dispatcher == null) {
            return;
        }
        resetForReplay(row);
        dispatchPending(dispatcher, 1);
    }

    private List<PaymentOutboxRow> listDispatchable(int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit, MAX_DISPATCH_LIMIT));
        LocalDateTime now = LocalDateTime.now();
        return jdbcTemplate.query(
                """
                        select id, tenant_id as tenantId, user_id as userId, source_type as sourceType, event_type as eventType,
                               event_key as eventKey, payload_json as payloadJson, status, retry_count as retryCount,
                               next_retry_at as nextRetryAt, last_error_message as lastErrorMessage, created_by as createdBy,
                               created_at as createdAt, updated_by as updatedBy, updated_at as updatedAt, deleted
                        from payment_event_outbox
                        where deleted = 0
                          and (
                                status = ?
                                or (status = ? and (next_retry_at is null or next_retry_at <= ?))
                          )
                        order by created_at asc, id asc
                        limit ?
                        """,
                new BeanPropertyRowMapper<>(PaymentOutboxRow.class),
                STATUS_PENDING,
                STATUS_FAILED,
                now,
                normalizedLimit
        );
    }

    private PaymentOutboxRow findById(Long id) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select id, tenant_id as tenantId, user_id as userId, source_type as sourceType, event_type as eventType,
                                   event_key as eventKey, payload_json as payloadJson, status, retry_count as retryCount,
                                   next_retry_at as nextRetryAt, last_error_message as lastErrorMessage, created_by as createdBy,
                                   created_at as createdAt, updated_by as updatedBy, updated_at as updatedAt, deleted
                            from payment_event_outbox
                            where id = ? and deleted = 0
                            limit 1
                            """,
                    new BeanPropertyRowMapper<>(PaymentOutboxRow.class),
                    id
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean claimForDispatch(PaymentOutboxRow row) {
        int updated = jdbcTemplate.update(
                """
                        update payment_event_outbox
                        set status = ?, updated_at = ?, updated_by = ?
                        where id = ? and deleted = 0 and status = ?
                        """,
                STATUS_DISPATCHING,
                LocalDateTime.now(),
                row.getUpdatedBy() == null ? 0L : row.getUpdatedBy(),
                row.getId(),
                row.getStatus()
        );
        return updated > 0;
    }

    private void markDelivered(PaymentOutboxRow row) {
        jdbcTemplate.update(
                """
                        update payment_event_outbox
                        set status = ?, next_retry_at = null, last_error_message = null, updated_at = ?, updated_by = ?
                        where id = ? and deleted = 0
                        """,
                STATUS_DELIVERED,
                LocalDateTime.now(),
                row.getUpdatedBy() == null ? 0L : row.getUpdatedBy(),
                row.getId()
        );
    }

    private void markFailed(PaymentOutboxRow row, RuntimeException exception) {
        int retryCount = row.getRetryCount() == null ? 0 : row.getRetryCount();
        int nextRetryCount = retryCount + 1;
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                        update payment_event_outbox
                        set status = ?, retry_count = ?, next_retry_at = ?, last_error_message = ?, updated_at = ?, updated_by = ?
                        where id = ? and deleted = 0
                        """,
                STATUS_FAILED,
                nextRetryCount,
                now.plusSeconds(calculateRetryDelaySeconds(nextRetryCount)),
                truncate(exception == null ? "unknown error" : exception.getMessage()),
                now,
                row.getUpdatedBy() == null ? 0L : row.getUpdatedBy(),
                row.getId()
        );
    }

    private void resetForReplay(PaymentOutboxRow row) {
        jdbcTemplate.update(
                """
                        update payment_event_outbox
                        set status = ?, retry_count = 0, next_retry_at = null, last_error_message = null, updated_at = ?, updated_by = ?
                        where id = ? and deleted = 0
                        """,
                STATUS_PENDING,
                LocalDateTime.now(),
                row.getUpdatedBy() == null ? 0L : row.getUpdatedBy(),
                row.getId()
        );
    }

    private long calculateRetryDelaySeconds(int retryCount) {
        int exponent = Math.min(Math.max(retryCount, 1), 8);
        return Math.min(MAX_RETRY_DELAY_SECONDS, (long) Math.pow(2, exponent));
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (Exception exception) {
            throw new IllegalStateException("支付 outbox payload 序列化失败", exception);
        }
    }
}
