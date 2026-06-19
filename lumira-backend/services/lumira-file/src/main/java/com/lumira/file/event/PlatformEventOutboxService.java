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

    private final ObjectMapper objectMapper;
    private final FilePlatformEventOutboxMapper platformEventOutboxMapper;
    private final JdbcTemplate jdbcTemplate;

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

    public void recordAfterCommit(String sourceType, String eventType, Long tenantId, Long userId, String eventKey, Object payload) {
        Runnable recordAction = () -> record(sourceType, eventType, tenantId, userId, eventKey, payload);
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

    public PlatformEventOutboxEntity record(String sourceType, String eventType, Long tenantId, Long userId, String eventKey, Object payload) {
        LocalDateTime now = LocalDateTime.now();
        PlatformEventOutboxEntity entity = new PlatformEventOutboxEntity();
        entity.setTenantId(tenantId);
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
        for (PlatformEventOutboxEntity row : listDispatchable(limit)) {
            if (!claimForDispatch(row)) {
                continue;
            }
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

    private List<PlatformEventOutboxEntity> listDispatchable(int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit, MAX_DISPATCH_LIMIT));
        LocalDateTime now = LocalDateTime.now();
        return jdbcTemplate.query(
                """
                        select id, tenant_id as tenantId, user_id as userId, source_type as sourceType,
                               event_type as eventType, event_key as eventKey, payload_json as payloadJson,
                               dispatch_status as dispatchStatus, retry_count as retryCount,
                               next_retry_at as nextRetryAt, delivered_at as deliveredAt, last_error as lastError,
                               trace_id as traceId, request_id as requestId, created_by as createdBy,
                               created_at as createdAt, updated_by as updatedBy, updated_at as updatedAt, deleted
                        from platform_event_outbox force index (idx_platform_event_outbox_owner_queue)
                        where deleted = 0
                          and source_type = ?
                          and (
                                dispatch_status = ?
                                or (dispatch_status = ? and (next_retry_at is null or next_retry_at <= ?))
                          )
                        order by created_at asc, id asc
                        limit ?
                        """,
                new BeanPropertyRowMapper<>(PlatformEventOutboxEntity.class),
                FilePlatformEventTypes.SOURCE_FILE,
                STATUS_RECORDED,
                STATUS_FAILED,
                now,
                normalizedLimit
        );
    }

    private PlatformEventOutboxEntity findById(Long id) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select id, tenant_id as tenantId, user_id as userId, source_type as sourceType,
                                   event_type as eventType, event_key as eventKey, payload_json as payloadJson,
                                   dispatch_status as dispatchStatus, retry_count as retryCount,
                                   next_retry_at as nextRetryAt, delivered_at as deliveredAt, last_error as lastError,
                                   trace_id as traceId, request_id as requestId, created_by as createdBy,
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
        int updated = jdbcTemplate.update(
                """
                        update platform_event_outbox
                        set dispatch_status = ?, updated_at = ?, updated_by = ?
                        where id = ? and deleted = 0 and source_type = ? and dispatch_status = ?
                        """,
                STATUS_DISPATCHING,
                LocalDateTime.now(),
                row.getUpdatedBy() == null ? 0L : row.getUpdatedBy(),
                row.getId(),
                FilePlatformEventTypes.SOURCE_FILE,
                row.getDispatchStatus()
        );
        return updated > 0;
    }

    private void markDelivered(PlatformEventOutboxEntity row) {
        jdbcTemplate.update(
                """
                        update platform_event_outbox
                        set dispatch_status = ?, delivered_at = ?, next_retry_at = null, last_error = null,
                            updated_at = ?, updated_by = ?
                        where id = ? and deleted = 0 and source_type = ?
                        """,
                STATUS_DELIVERED,
                LocalDateTime.now(),
                LocalDateTime.now(),
                row.getUpdatedBy() == null ? 0L : row.getUpdatedBy(),
                row.getId(),
                FilePlatformEventTypes.SOURCE_FILE
        );
    }

    private void markFailed(PlatformEventOutboxEntity row, RuntimeException exception) {
        int retryCount = row.getRetryCount() == null ? 0 : row.getRetryCount();
        int nextRetryCount = retryCount + 1;
        boolean deadLetter = nextRetryCount >= MAX_RETRY_COUNT;
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                        update platform_event_outbox
                        set dispatch_status = ?, retry_count = ?, next_retry_at = ?, last_error = ?,
                            updated_at = ?, updated_by = ?
                        where id = ? and deleted = 0 and source_type = ?
                        """,
                deadLetter ? STATUS_DEAD_LETTER : STATUS_FAILED,
                nextRetryCount,
                deadLetter ? null : now.plusSeconds(calculateRetryDelaySeconds(nextRetryCount)),
                truncate(exception == null ? "unknown error" : exception.getMessage()),
                now,
                row.getUpdatedBy() == null ? 0L : row.getUpdatedBy(),
                row.getId(),
                FilePlatformEventTypes.SOURCE_FILE
        );
    }

    private void resetForReplay(PlatformEventOutboxEntity row) {
        jdbcTemplate.update(
                """
                        update platform_event_outbox
                        set dispatch_status = ?, retry_count = 0, next_retry_at = null, last_error = null,
                            updated_at = ?, updated_by = ?
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

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("文件事件 outbox payload 序列化失败", exception);
        }
    }
}
