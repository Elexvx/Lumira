package com.lumira.file.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.web.TraceContext;
import com.lumira.file.mapper.FilePlatformEventOutboxMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service("filePlatformEventOutboxService")
public class PlatformEventOutboxService {

    public static final String STATUS_RECORDED = "RECORDED";
    public static final String STATUS_DISPATCHING = "DISPATCHING";
    public static final String STATUS_DELIVERED = "DELIVERED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_DEAD_LETTER = "DEAD_LETTER";
    private static final Logger logger = LoggerFactory.getLogger(PlatformEventOutboxService.class);
    private static final int MAX_DISPATCH_LIMIT = 200;
    private static final int MAX_RETRY_COUNT = 8;
    private static final int MAX_RETRY_DELAY_SECONDS = 300;
    private static final int MAX_ERROR_LENGTH = 512;
    private static final long SNAPSHOT_CACHE_TTL_MS = 15_000L;
    private static final long CLAIM_LEASE_MINUTES = 15L;

    private final ObjectMapper objectMapper;
    private final FilePlatformEventOutboxMapper platformEventOutboxMapper;
    private final JdbcTemplate jdbcTemplate;
    private volatile OutboxMetricsSnapshot cachedSnapshot;
    private volatile long cachedSnapshotUntilMillis;

    @Autowired
    public PlatformEventOutboxService(ObjectMapper objectMapper, FilePlatformEventOutboxMapper platformEventOutboxMapper, JdbcTemplate jdbcTemplate) {
        this.objectMapper = objectMapper;
        this.platformEventOutboxMapper = platformEventOutboxMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    public PlatformEventOutboxService(ObjectMapper objectMapper, FilePlatformEventOutboxMapper platformEventOutboxMapper) {
        this.objectMapper = objectMapper;
        this.platformEventOutboxMapper = platformEventOutboxMapper;
        this.jdbcTemplate = null;
    }

    public void recordAfterCommit(String sourceType, String eventType, Long userId, String eventKey, Object payload) {
        Runnable recordAction = () -> record(sourceType, eventType, userId, eventKey, payload);
        if (!TransactionSynchronizationManager.isSynchronizationActive() || !TransactionSynchronizationManager.isActualTransactionActive()) {
            recordAction.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    recordAction.run();
                } catch (RuntimeException exception) {
                    logger.warn("文件事件 outbox 记录失败: {}", exception.getMessage(), exception);
                }
            }
        });
    }

    public PlatformEventOutboxEntity record(String sourceType, String eventType, Long userId, String eventKey, Object payload) {
        LocalDateTime now = LocalDateTime.now();
        PlatformEventOutboxEntity entity = new PlatformEventOutboxEntity();
        entity.setUserId(userId);
        entity.setSourceType(sourceType);
        entity.setEventType(eventType);
        entity.setEventKey(eventKey);
        entity.setPayloadJson(serialize(payload));
        entity.setDispatchStatus(STATUS_RECORDED);
        entity.setRetryCount(0);
        entity.setTraceId(TraceContext.getTraceId());
        entity.setRequestId(TraceContext.getRequestId());
        entity.setCreatedBy(userId == null ? 0L : userId);
        entity.setUpdatedBy(userId == null ? 0L : userId);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setDeleted(0);
        platformEventOutboxMapper.insert(entity);
        return entity;
    }

    public int dispatchPending(FileOutboxDispatcher dispatcher, int limit) {
        if (dispatcher == null || jdbcTemplate == null) {
            return 0;
        }
        int delivered = 0;
        for (PlatformEventOutboxEntity row : claimForDispatchBatch(limit)) {
            try {
                dispatcher.dispatch(row);
                markDelivered(row);
                delivered++;
            } catch (RuntimeException exception) {
                logger.warn("文件 outbox 投递失败: id={}, eventType={}, message={}",
                        row.getId(), row.getEventType(), exception.getMessage());
                markFailed(row, exception);
            }
        }
        return delivered;
    }

    public boolean replay(Long id, FileOutboxDispatcher dispatcher) {
        if (id == null || dispatcher == null || jdbcTemplate == null) {
            return false;
        }
        PlatformEventOutboxEntity row = findById(id);
        if (row == null) {
            return false;
        }
        resetForReplay(row);
        return dispatchSingle(row, dispatcher);
    }

    public long dispatchableBacklog() {
        return snapshot().dispatchableBacklog();
    }

    public OutboxMetricsSnapshot snapshot() {
        if (jdbcTemplate == null) {
            return new OutboxMetricsSnapshot(0L, 0L, 0L, 0L);
        }
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
                        select coalesce(sum(case when dispatch_status = 'RECORDED' then 1 else 0 end), 0) as pending_backlog,
                               coalesce(sum(case when dispatch_status = 'FAILED' then 1 else 0 end), 0) as failed_backlog,
                               coalesce(sum(case when dispatch_status = 'DEAD_LETTER' then 1 else 0 end), 0) as dead_letter_count,
                               coalesce(sum(case when dispatch_status = 'RECORDED'
                                                 or (dispatch_status = 'FAILED' and (next_retry_at is null or next_retry_at <= ?))
                                                 then 1 else 0 end), 0) as dispatchable_backlog
                        from platform_event_outbox
                        where deleted = 0
                          and source_type = ?
                        """,
                LocalDateTime.now(),
                FilePlatformEventTypes.SOURCE_FILE
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

    private List<PlatformEventOutboxEntity> claimForDispatchBatch(int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit, MAX_DISPATCH_LIMIT));
        LocalDateTime now = LocalDateTime.now();
        String claimToken = UUID.randomUUID().toString();
        LocalDateTime claimExpiresAt = now.plusMinutes(CLAIM_LEASE_MINUTES);
        int updated = jdbcTemplate.update(
                """
                        update platform_event_outbox t
                        join (
                            select id
                            from platform_event_outbox force index (idx_platform_event_outbox_owner_queue)
                            where deleted = 0
                              and source_type = ?
                              and (
                                    dispatch_status = ?
                                    or (dispatch_status = ? and (next_retry_at is null or next_retry_at <= ?))
                                    or (dispatch_status = ? and claim_expires_at is not null and claim_expires_at <= ?)
                              )
                            order by created_at asc, id asc
                            limit ?
                        ) picked on picked.id = t.id
                        set t.dispatch_status = ?,
                            t.claim_token = ?,
                            t.claim_expires_at = ?,
                            t.updated_at = ?,
                            t.updated_by = ?
                        where t.deleted = 0
                          and t.source_type = ?
                        """,
                FilePlatformEventTypes.SOURCE_FILE,
                STATUS_RECORDED,
                STATUS_FAILED,
                now,
                STATUS_DISPATCHING,
                now,
                normalizedLimit,
                STATUS_DISPATCHING,
                claimToken,
                claimExpiresAt,
                now,
                0L,
                FilePlatformEventTypes.SOURCE_FILE
        );
        if (updated <= 0) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                        select id, user_id as userId, source_type as sourceType,
                               event_type as eventType, event_key as eventKey, payload_json as payloadJson,
                               dispatch_status as dispatchStatus, retry_count as retryCount,
                               next_retry_at as nextRetryAt, delivered_at as deliveredAt, last_error as lastError,
                               trace_id as traceId, request_id as requestId, created_by as createdBy,
                               claim_token as claimToken, claim_expires_at as claimExpiresAt,
                               created_at as createdAt, updated_by as updatedBy, updated_at as updatedAt, deleted
                        from platform_event_outbox
                        where deleted = 0
                          and source_type = ?
                          and claim_token = ?
                        order by created_at asc, id asc
                        """,
                new BeanPropertyRowMapper<>(PlatformEventOutboxEntity.class),
                FilePlatformEventTypes.SOURCE_FILE,
                claimToken
        );
    }

    private PlatformEventOutboxEntity findById(Long id) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select id, user_id as userId, source_type as sourceType,
                                   event_type as eventType, event_key as eventKey, payload_json as payloadJson,
                                   dispatch_status as dispatchStatus, retry_count as retryCount,
                                   next_retry_at as nextRetryAt, delivered_at as deliveredAt, last_error as lastError,
                                   trace_id as traceId, request_id as requestId, created_by as createdBy,
                                   claim_token as claimToken, claim_expires_at as claimExpiresAt,
                                   created_at as createdAt, updated_by as updatedBy, updated_at as updatedAt, deleted
                            from platform_event_outbox
                            where id = ? and deleted = 0 and source_type = ?
                            limit 1
                            """,
                    new BeanPropertyRowMapper<>(PlatformEventOutboxEntity.class),
                    id,
                    FilePlatformEventTypes.SOURCE_FILE
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean claimForDispatch(PlatformEventOutboxEntity row) {
        String claimToken = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime claimExpiresAt = now.plusMinutes(CLAIM_LEASE_MINUTES);
        int updated = jdbcTemplate.update(
                """
                        update platform_event_outbox
                        set dispatch_status = ?, claim_token = ?, claim_expires_at = ?, updated_at = ?, updated_by = ?
                        where id = ? and deleted = 0 and source_type = ?
                          and (
                                dispatch_status = ?
                                or (dispatch_status = ? and claim_expires_at is not null and claim_expires_at <= ?)
                          )
                """,
                STATUS_DISPATCHING,
                claimToken,
                claimExpiresAt,
                now,
                row.getUpdatedBy() == null ? 0L : row.getUpdatedBy(),
                row.getId(),
                FilePlatformEventTypes.SOURCE_FILE,
                row.getDispatchStatus(),
                STATUS_DISPATCHING,
                now
        );
        if (updated > 0) {
            row.setClaimToken(claimToken);
            row.setClaimExpiresAt(claimExpiresAt);
            row.setDispatchStatus(STATUS_DISPATCHING);
        }
        return updated > 0;
    }

    private void markDelivered(PlatformEventOutboxEntity row) {
        int updated = jdbcTemplate.update(
                """
                        update platform_event_outbox
                        set dispatch_status = ?, delivered_at = ?, next_retry_at = null, last_error = null,
                            claim_token = null, claim_expires_at = null, updated_at = ?, updated_by = ?
                        where id = ? and deleted = 0 and source_type = ? and dispatch_status = ? and claim_token = ?
                        """,
                STATUS_DELIVERED,
                LocalDateTime.now(),
                LocalDateTime.now(),
                row.getUpdatedBy() == null ? 0L : row.getUpdatedBy(),
                row.getId(),
                FilePlatformEventTypes.SOURCE_FILE,
                STATUS_DISPATCHING,
                row.getClaimToken()
        );
        logClaimMismatchIfNeeded(updated, row, "markDelivered");
    }

    private void markFailed(PlatformEventOutboxEntity row, RuntimeException exception) {
        int retryCount = row.getRetryCount() == null ? 0 : row.getRetryCount();
        int nextRetryCount = retryCount + 1;
        boolean deadLetter = nextRetryCount >= MAX_RETRY_COUNT;
        LocalDateTime now = LocalDateTime.now();
        int updated = jdbcTemplate.update(
                """
                        update platform_event_outbox
                        set dispatch_status = ?, retry_count = ?, next_retry_at = ?, last_error = ?,
                            claim_token = null, claim_expires_at = null, updated_at = ?, updated_by = ?
                        where id = ? and deleted = 0 and source_type = ? and dispatch_status = ? and claim_token = ?
                        """,
                deadLetter ? STATUS_DEAD_LETTER : STATUS_FAILED,
                nextRetryCount,
                deadLetter ? null : now.plusSeconds(calculateRetryDelaySeconds(nextRetryCount)),
                truncate(exception == null ? "unknown error" : exception.getMessage()),
                now,
                row.getUpdatedBy() == null ? 0L : row.getUpdatedBy(),
                row.getId(),
                FilePlatformEventTypes.SOURCE_FILE,
                STATUS_DISPATCHING,
                row.getClaimToken()
        );
        logClaimMismatchIfNeeded(updated, row, "markFailed");
    }

    private void resetForReplay(PlatformEventOutboxEntity row) {
        jdbcTemplate.update(
                """
                        update platform_event_outbox
                        set dispatch_status = ?, retry_count = 0, next_retry_at = null, last_error = null,
                            claim_token = null, claim_expires_at = null, updated_at = ?, updated_by = ?
                        where id = ? and deleted = 0 and source_type = ?
                        """,
                STATUS_RECORDED,
                LocalDateTime.now(),
                row.getUpdatedBy() == null ? 0L : row.getUpdatedBy(),
                row.getId(),
                FilePlatformEventTypes.SOURCE_FILE
        );
    }

    private boolean dispatchSingle(PlatformEventOutboxEntity row, FileOutboxDispatcher dispatcher) {
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
            logger.warn("文件 outbox 投递失败: id={}, eventType={}, message={}",
                    row.getId(), row.getEventType(), exception.getMessage());
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

    private void logClaimMismatchIfNeeded(int updated, PlatformEventOutboxEntity row, String operation) {
        if (updated > 0) {
            return;
        }
        logger.warn("File outbox claim mismatch operation={} id={} eventType={}", operation, row.getId(), row.getEventType());
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("文件事件 outbox payload 序列化失败", exception);
        }
    }

    public record OutboxMetricsSnapshot(
            long pendingBacklog,
            long failedBacklog,
            long deadLetterCount,
            long dispatchableBacklog
    ) {
    }
}
