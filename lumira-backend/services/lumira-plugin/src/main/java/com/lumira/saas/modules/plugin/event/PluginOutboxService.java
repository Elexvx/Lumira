package com.lumira.saas.modules.plugin.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class PluginOutboxService {

    private static final Logger logger = LoggerFactory.getLogger(PluginOutboxService.class);
    private static final int MAX_DISPATCH_LIMIT = 200;
    private static final int MAX_RETRY_DELAY_SECONDS = 300;
    private static final int MAX_RETRY_COUNT = 8;
    private static final int MAX_ERROR_LENGTH = 512;
    private static final long SNAPSHOT_CACHE_TTL_MS = 15_000L;
    private static final long CLAIM_LEASE_MINUTES = 5L;
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_DISPATCHING = "DISPATCHING";
    private static final String STATUS_DELIVERED = "DELIVERED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_DEAD_LETTER = "DEAD_LETTER";
    private static final String WORKER_ID = "plugin-outbox@" + ManagementFactory.getRuntimeMXBean().getName();

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private volatile OutboxMetricsSnapshot cachedSnapshot;
    private volatile long cachedSnapshotUntilMillis;

    public PluginOutboxService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void recordAfterCommit(Long userId, String eventType, String eventKey, Object payload) {
        Runnable action = () -> record(userId, eventType, eventKey, payload);
        if (!TransactionSynchronizationManager.isSynchronizationActive() || !TransactionSynchronizationManager.isActualTransactionActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    public void record(Long userId, String eventType, String eventKey, Object payload) {
        jdbcTemplate.update(
                """
                        insert into plugin_event_outbox (
                            user_id, event_type, event_key, payload_json,
                            status, retry_count, next_retry_at, created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, 'PENDING', 0, ?, ?, ?, 0)
                        on duplicate key update payload_json = values(payload_json), status = 'PENDING',
                            next_retry_at = values(next_retry_at), updated_by = values(updated_by), updated_at = current_timestamp
                        """,
                userId,
                eventType,
                eventKey,
                serialize(payload),
                LocalDateTime.now(),
                userId == null ? 0L : userId,
                userId == null ? 0L : userId
        );
    }

    public int dispatchPending(PluginOutboxDispatcher dispatcher, int limit) {
        if (dispatcher == null) {
            return 0;
        }
        int delivered = 0;
        for (PluginOutboxRow row : claimForDispatchBatch(limit)) {
            try {
                dispatcher.dispatch(row);
                markDelivered(row);
                delivered++;
            } catch (RuntimeException exception) {
                logger.warn("Plugin outbox dispatch failed, id={}, eventType={}, message={}", row.getId(), row.getEventType(), exception.getMessage());
                markFailed(row, exception);
            }
        }
        return delivered;
    }

    public boolean replay(Long id, PluginOutboxDispatcher dispatcher) {
        PluginOutboxRow row = findById(id);
        if (row == null || dispatcher == null) {
            return false;
        }
        resetForReplay(row);
        return dispatchSingle(row, dispatcher);
    }

    public long pendingBacklog() {
        return snapshot().pendingBacklog();
    }

    public long failedBacklog() {
        return snapshot().failedBacklog();
    }

    public long deadLetterCount() {
        return snapshot().deadLetterCount();
    }

    public long dispatchableBacklog() {
        return snapshot().dispatchableBacklog();
    }

    public OutboxMetricsSnapshot snapshot() {
        long now = System.currentTimeMillis();
        OutboxMetricsSnapshot cached = cachedSnapshot;
        if (cached != null && now < cachedSnapshotUntilMillis) {
            return cached;
        }
        synchronized (this) {
            now = System.currentTimeMillis();
            cached = cachedSnapshot;
            if (cached != null && now < cachedSnapshotUntilMillis) {
                return cached;
            }
            OutboxMetricsSnapshot snapshot = loadSnapshot();
            cachedSnapshot = snapshot;
            cachedSnapshotUntilMillis = now + SNAPSHOT_CACHE_TTL_MS;
            return snapshot;
        }
    }

    private OutboxMetricsSnapshot loadSnapshot() {
        Map<String, Object> row = firstRow(
                """
                        select coalesce(sum(case when status = 'PENDING' then 1 else 0 end), 0) as pending_backlog,
                               coalesce(sum(case when status = 'FAILED' then 1 else 0 end), 0) as failed_backlog,
                               coalesce(sum(case when status = 'DEAD_LETTER' then 1 else 0 end), 0) as dead_letter_count,
                               coalesce(sum(case when status = 'PENDING'
                                                 or (status = 'FAILED' and (next_retry_at is null or next_retry_at <= ?))
                                                 then 1 else 0 end), 0) as dispatchable_backlog
                        from plugin_event_outbox
                        where deleted = 0
                        """,
                LocalDateTime.now()
        );
        return new OutboxMetricsSnapshot(
                longValue(row.get("pending_backlog")),
                longValue(row.get("failed_backlog")),
                longValue(row.get("dead_letter_count")),
                longValue(row.get("dispatchable_backlog"))
        );
    }

    private Map<String, Object> firstRow(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private long longValue(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private List<PluginOutboxRow> claimForDispatchBatch(int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit, MAX_DISPATCH_LIMIT));
        LocalDateTime now = LocalDateTime.now();
        String claimToken = UUID.randomUUID().toString();
        LocalDateTime claimExpiresAt = now.plusMinutes(CLAIM_LEASE_MINUTES);
        int updated = jdbcTemplate.update(
                """
                        update plugin_event_outbox t
                        join (
                            select id
                            from plugin_event_outbox force index (idx_plugin_event_outbox_deleted_status_retry_created)
                            where deleted = 0
                              and (
                                    status = ?
                                    or (status = ? and (next_retry_at is null or next_retry_at <= ?))
                                    or (status = ? and claim_expires_at is not null and claim_expires_at <= ?)
                              )
                            order by created_at asc, id asc
                            limit ?
                        ) picked on picked.id = t.id
                        set t.status = ?,
                            t.claimed_by = ?,
                            t.claim_token = ?,
                            t.claim_expires_at = ?,
                            t.updated_at = ?
                        where t.deleted = 0
                        """,
                STATUS_PENDING,
                STATUS_FAILED,
                now,
                STATUS_DISPATCHING,
                now,
                normalizedLimit,
                STATUS_DISPATCHING,
                workerId(),
                claimToken,
                claimExpiresAt,
                now
        );
        if (updated <= 0) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                        select id, user_id as userId, event_type as eventType,
                               event_key as eventKey, payload_json as payloadJson, status, retry_count as retryCount,
                               next_retry_at as nextRetryAt, last_error_message as lastErrorMessage, created_by as createdBy,
                               created_at as createdAt, updated_by as updatedBy, updated_at as updatedAt, deleted,
                               claimed_by as claimedBy, claim_token as claimToken, claim_expires_at as claimExpiresAt
                        from plugin_event_outbox
                        where deleted = 0 and claim_token = ?
                        order by created_at asc, id asc
                        """,
                new BeanPropertyRowMapper<>(PluginOutboxRow.class),
                claimToken
        );
    }

    private PluginOutboxRow findById(Long id) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select id, user_id as userId, event_type as eventType,
                                   event_key as eventKey, payload_json as payloadJson, status, retry_count as retryCount,
                                   next_retry_at as nextRetryAt, last_error_message as lastErrorMessage, created_by as createdBy,
                                   created_at as createdAt, updated_by as updatedBy, updated_at as updatedAt, deleted,
                                   claimed_by as claimedBy, claim_token as claimToken, claim_expires_at as claimExpiresAt
                            from plugin_event_outbox
                            where id = ? and deleted = 0
                            limit 1
                            """,
                    new BeanPropertyRowMapper<>(PluginOutboxRow.class),
                    id
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean claimForDispatch(PluginOutboxRow row) {
        if (row == null || row.getId() == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        String claimToken = UUID.randomUUID().toString();
        LocalDateTime claimExpiresAt = now.plusMinutes(CLAIM_LEASE_MINUTES);
        int updated = jdbcTemplate.update(
                """
                        update plugin_event_outbox
                        set status = ?, claimed_by = ?, claim_token = ?, claim_expires_at = ?, updated_at = ?
                        where id = ? and deleted = 0
                          and (
                                status = ?
                                or (status = ? and claim_expires_at is not null and claim_expires_at <= ?)
                          )
                        """,
                STATUS_DISPATCHING,
                workerId(),
                claimToken,
                claimExpiresAt,
                now,
                row.getId(),
                row.getStatus(),
                STATUS_DISPATCHING,
                now
        );
        if (updated > 0) {
            row.setStatus(STATUS_DISPATCHING);
            row.setClaimedBy(workerId());
            row.setClaimToken(claimToken);
            row.setClaimExpiresAt(claimExpiresAt);
        }
        return updated > 0;
    }

    private void markDelivered(PluginOutboxRow row) {
        int updated = jdbcTemplate.update(
                """
                        update plugin_event_outbox
                        set status = ?, claimed_by = null, claim_token = null, claim_expires_at = null,
                            next_retry_at = null, last_error_message = null, updated_at = ?, updated_by = ?
                        where id = ? and deleted = 0 and status = ? and claim_token = ?
                        """,
                STATUS_DELIVERED,
                LocalDateTime.now(),
                row.getUpdatedBy() == null ? 0L : row.getUpdatedBy(),
                row.getId(),
                STATUS_DISPATCHING,
                row.getClaimToken()
        );
        logClaimMismatchIfNeeded(updated, row, "markDelivered");
    }

    private void markFailed(PluginOutboxRow row, RuntimeException exception) {
        int retryCount = row.getRetryCount() == null ? 0 : row.getRetryCount();
        int nextRetryCount = retryCount + 1;
        boolean deadLetter = nextRetryCount >= MAX_RETRY_COUNT;
        LocalDateTime now = LocalDateTime.now();
        int updated = jdbcTemplate.update(
                """
                        update plugin_event_outbox
                        set status = ?, retry_count = ?, next_retry_at = ?, last_error_message = ?,
                            claimed_by = null, claim_token = null, claim_expires_at = null,
                            updated_at = ?, updated_by = ?
                        where id = ? and deleted = 0 and status = ? and claim_token = ?
                        """,
                deadLetter ? STATUS_DEAD_LETTER : STATUS_FAILED,
                nextRetryCount,
                deadLetter ? null : now.plusSeconds(calculateRetryDelaySeconds(nextRetryCount)),
                truncate(exception == null ? "unknown error" : exception.getMessage()),
                now,
                row.getUpdatedBy() == null ? 0L : row.getUpdatedBy(),
                row.getId(),
                STATUS_DISPATCHING,
                row.getClaimToken()
        );
        logClaimMismatchIfNeeded(updated, row, "markFailed");
    }

    private void resetForReplay(PluginOutboxRow row) {
        jdbcTemplate.update(
                """
                        update plugin_event_outbox
                        set status = ?, retry_count = 0, next_retry_at = null, last_error_message = null,
                            claimed_by = null, claim_token = null, claim_expires_at = null,
                            updated_at = ?, updated_by = ?
                        where id = ? and deleted = 0
                        """,
                STATUS_PENDING,
                LocalDateTime.now(),
                row.getUpdatedBy() == null ? 0L : row.getUpdatedBy(),
                row.getId()
        );
        row.setStatus(STATUS_PENDING);
        row.setRetryCount(0);
        row.setNextRetryAt(null);
        row.setLastErrorMessage(null);
        row.setClaimedBy(null);
        row.setClaimToken(null);
        row.setClaimExpiresAt(null);
    }

    private boolean dispatchSingle(PluginOutboxRow row, PluginOutboxDispatcher dispatcher) {
        if (row == null || dispatcher == null) {
            return false;
        }
        if (!claimForDispatch(row)) {
            return false;
        }

        try {
            dispatcher.dispatch(row);
            markDelivered(row);
            return true;
        } catch (RuntimeException exception) {
            logger.warn("Plugin outbox dispatch failed, id={}, eventType={}, message={}", row.getId(), row.getEventType(), exception.getMessage());
            markFailed(row, exception);
            return false;
        }
    }

    private long calculateRetryDelaySeconds(int retryCount) {
        int exponent = Math.min(Math.max(retryCount, 1), MAX_RETRY_COUNT);
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
            throw new IllegalStateException("Plugin outbox payload serialization failed", exception);
        }
    }

    private void logClaimMismatchIfNeeded(int updated, PluginOutboxRow row, String operation) {
        if (updated > 0) {
            return;
        }
        logger.warn("Plugin outbox claim mismatch operation={} id={} eventType={}", operation, row.getId(), row.getEventType());
    }

    private String workerId() {
        return WORKER_ID;
    }

    public record OutboxMetricsSnapshot(
            long pendingBacklog,
            long failedBacklog,
            long deadLetterCount,
            long dispatchableBacklog
    ) {
    }
}
