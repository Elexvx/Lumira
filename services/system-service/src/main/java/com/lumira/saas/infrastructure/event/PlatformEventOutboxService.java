package com.lumira.saas.infrastructure.event;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PlatformEventOutboxService {

    private static final Logger logger = LoggerFactory.getLogger(PlatformEventOutboxService.class);
    public static final String STATUS_RECORDED = "RECORDED";
    public static final String STATUS_DISPATCHING = "DISPATCHING";
    public static final String STATUS_DELIVERED = "DELIVERED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_DEAD_LETTER = "DEAD_LETTER";
    private static final int MAX_ERROR_LENGTH = 1024;
    private static final int MAX_RETRY_DELAY_SECONDS = 300;
    private static final int MAX_RETRY_COUNT = 8;
    private static final String SYSTEM_SOURCE_TYPE = PlatformEventTypes.SOURCE_SYSTEM;

    private static final String SQL_LIST_DISPATCHABLE = """
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
            """;

    private static final String SQL_FIND_BY_ID = """
            select id, tenant_id as tenantId, user_id as userId, source_type as sourceType,
                   event_type as eventType, event_key as eventKey, payload_json as payloadJson,
                   dispatch_status as dispatchStatus, retry_count as retryCount,
                   next_retry_at as nextRetryAt, delivered_at as deliveredAt, last_error as lastError,
                   trace_id as traceId, request_id as requestId, created_by as createdBy,
                   created_at as createdAt, updated_by as updatedBy, updated_at as updatedAt, deleted
            from platform_event_outbox
            where id = ? and deleted = 0 and source_type = ?
            limit 1
            """;

    private static final String SQL_CLAIM_FOR_DISPATCH = """
            update platform_event_outbox
            set dispatch_status = ?, updated_at = ?, updated_by = ?
            where deleted = 0 and source_type = ? and id = ? and dispatch_status = ?
            """;

    private static final String SQL_MARK_DELIVERED = """
            update platform_event_outbox
            set dispatch_status = ?, delivered_at = ?, next_retry_at = null, last_error = null,
                updated_at = ?, updated_by = ?
            where deleted = 0 and source_type = ? and id = ?
            """;

    private static final String SQL_MARK_FAILED = """
            update platform_event_outbox
            set dispatch_status = ?, retry_count = ?, next_retry_at = ?, last_error = ?,
                updated_at = ?, updated_by = ?
            where deleted = 0 and source_type = ? and id = ?
            """;

    private static final String SQL_RESET_FOR_REPLAY = """
            update platform_event_outbox
            set dispatch_status = ?, retry_count = 0, next_retry_at = null, last_error = null,
                delivered_at = null, updated_at = ?, updated_by = ?
            where deleted = 0 and source_type = ? and id = ?
            """;

    private final ObjectMapper objectMapper;
    private final PlatformEventOutboxMapper platformEventOutboxMapper;
    private final MyBatisQueryOperations queryOperations;
    private final BeanPropertyRowMapper<PlatformEventOutboxEntity> rowMapper = new BeanPropertyRowMapper<>(PlatformEventOutboxEntity.class);

    @Autowired
    public PlatformEventOutboxService(ObjectMapper objectMapper,
                                      PlatformEventOutboxMapper platformEventOutboxMapper,
                                      MyBatisQueryOperations queryOperations) {
        this.objectMapper = objectMapper;
        this.platformEventOutboxMapper = platformEventOutboxMapper;
        this.queryOperations = queryOperations;
    }

    public PlatformEventOutboxService(ObjectMapper objectMapper, PlatformEventOutboxMapper platformEventOutboxMapper) {
        this(objectMapper, platformEventOutboxMapper, null);
    }

    public void recordAfterCommit(
            String sourceType,
            String eventType,
            Long tenantId,
            Long userId,
            String eventKey,
            Object payload
    ) {
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
                    logger.warn("平台事件 outbox 记录失败: {}", exception.getMessage(), exception);
                }
            }
        });
    }

    public PlatformEventOutboxEntity record(
            String sourceType,
            String eventType,
            Long tenantId,
            Long userId,
            String eventKey,
            Object payload
    ) {
        ensureSystemSource(sourceType);
        PlatformEventOutboxEntity entity = new PlatformEventOutboxEntity();
        entity.setTenantId(tenantId);
        entity.setUserId(userId);
        entity.setSourceType(SYSTEM_SOURCE_TYPE);
        entity.setEventType(eventType);
        entity.setEventKey(eventKey);
        entity.setPayloadJson(serialize(payload));
        entity.setDispatchStatus(STATUS_RECORDED);
        entity.setRetryCount(0);
        entity.setTraceId(TraceContext.getTraceId());
        entity.setRequestId(TraceContext.getRequestId());
        entity.setCreatedBy(userId == null ? 0L : userId);
        entity.setUpdatedBy(userId == null ? 0L : userId);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setDeleted(0);
        platformEventOutboxMapper.insert(entity);
        return entity;
    }

    public List<PlatformEventOutboxEntity> listDispatchable(int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit, 200));
        LocalDateTime now = LocalDateTime.now();

        if (queryOperations == null) {
            return platformEventOutboxMapper.selectList(new QueryWrapper<PlatformEventOutboxEntity>()
                    .eq("deleted", 0)
                    .eq("source_type", SYSTEM_SOURCE_TYPE)
                    .and(query -> query
                            .eq("dispatch_status", STATUS_RECORDED)
                            .or(nested -> nested
                                    .eq("dispatch_status", STATUS_FAILED)
                                    .and(retry -> retry
                                            .isNull("next_retry_at")
                                            .or()
                                            .le("next_retry_at", now))))
                    .orderByAsc("created_at")
                    .orderByAsc("id")
                    .last("limit " + normalizedLimit));
        }

        return queryOperations.query(
                SQL_LIST_DISPATCHABLE,
                rowMapper,
                SYSTEM_SOURCE_TYPE,
                STATUS_RECORDED,
                STATUS_FAILED,
                now,
                normalizedLimit
        );
    }

    public PlatformEventOutboxEntity findById(Long id) {
        if (id == null) {
            return null;
        }

        if (queryOperations == null) {
            return platformEventOutboxMapper.selectOne(new QueryWrapper<PlatformEventOutboxEntity>()
                    .eq("id", id)
                    .eq("deleted", 0)
                    .eq("source_type", SYSTEM_SOURCE_TYPE)
                    .last("limit 1"));
        }

        return queryOperations.queryForObject(SQL_FIND_BY_ID, rowMapper, id, SYSTEM_SOURCE_TYPE);
    }

    public boolean claimForDispatch(PlatformEventOutboxEntity event) {
        if (event == null || event.getId() == null) {
            return false;
        }

        if (queryOperations != null) {
            int updated = queryOperations.update(
                    SQL_CLAIM_FOR_DISPATCH,
                    STATUS_DISPATCHING,
                    LocalDateTime.now(),
                    event.getUpdatedBy() == null ? 0L : event.getUpdatedBy(),
                    SYSTEM_SOURCE_TYPE,
                    event.getId(),
                    event.getDispatchStatus()
            );
            return updated > 0;
        }

        PlatformEventOutboxEntity update = new PlatformEventOutboxEntity();
        update.setDispatchStatus(STATUS_DISPATCHING);
        update.setUpdatedAt(LocalDateTime.now());
        update.setUpdatedBy(event.getUpdatedBy() == null ? 0L : event.getUpdatedBy());

        int updated = platformEventOutboxMapper.update(update, new UpdateWrapper<PlatformEventOutboxEntity>()
                .eq("id", event.getId())
                .eq("deleted", 0)
                .eq("source_type", SYSTEM_SOURCE_TYPE)
                .eq("dispatch_status", event.getDispatchStatus()));
        return updated > 0;
    }

    public void markDelivered(PlatformEventOutboxEntity event) {
        if (event == null || event.getId() == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        if (queryOperations != null) {
            queryOperations.update(
                    SQL_MARK_DELIVERED,
                    STATUS_DELIVERED,
                    now,
                    now,
                    event.getUpdatedBy() == null ? 0L : event.getUpdatedBy(),
                    SYSTEM_SOURCE_TYPE,
                    event.getId()
            );
            return;
        }

        platformEventOutboxMapper.update(null, new UpdateWrapper<PlatformEventOutboxEntity>()
                .eq("id", event.getId())
                .eq("deleted", 0)
                .eq("source_type", SYSTEM_SOURCE_TYPE)
                .set("dispatch_status", STATUS_DELIVERED)
                .set("delivered_at", now)
                .set("next_retry_at", null)
                .set("last_error", null)
                .set("updated_at", now)
                .set("updated_by", event.getUpdatedBy() == null ? 0L : event.getUpdatedBy()));
    }

    public void markFailed(PlatformEventOutboxEntity event, RuntimeException exception) {
        if (event == null || event.getId() == null) {
            return;
        }

        int retryCount = event.getRetryCount() == null ? 0 : event.getRetryCount();
        int nextRetryCount = retryCount + 1;
        String nextStatus = nextRetryCount >= MAX_RETRY_COUNT ? STATUS_DEAD_LETTER : STATUS_FAILED;
        LocalDateTime now = LocalDateTime.now();

        if (queryOperations != null) {
            queryOperations.update(
                    SQL_MARK_FAILED,
                    nextStatus,
                    nextRetryCount,
                    STATUS_DEAD_LETTER.equals(nextStatus) ? null : now.plusSeconds(calculateRetryDelaySeconds(nextRetryCount)),
                    truncateError(exception == null ? "unknown error" : exception.getMessage()),
                    now,
                    event.getUpdatedBy() == null ? 0L : event.getUpdatedBy(),
                    SYSTEM_SOURCE_TYPE,
                    event.getId()
            );
            return;
        }

        platformEventOutboxMapper.update(null, new UpdateWrapper<PlatformEventOutboxEntity>()
                .eq("id", event.getId())
                .eq("deleted", 0)
                .eq("source_type", SYSTEM_SOURCE_TYPE)
                .set("dispatch_status", nextStatus)
                .set("retry_count", nextRetryCount)
                .set("next_retry_at", STATUS_DEAD_LETTER.equals(nextStatus) ? null : now.plusSeconds(calculateRetryDelaySeconds(nextRetryCount)))
                .set("last_error", truncateError(exception == null ? "unknown error" : exception.getMessage()))
                .set("updated_at", now)
                .set("updated_by", event.getUpdatedBy() == null ? 0L : event.getUpdatedBy()));
    }

    public int dispatchPending(PlatformEventDispatcher dispatcher, int limit) {
        if (dispatcher == null) {
            return 0;
        }

        int delivered = 0;
        for (PlatformEventOutboxEntity event : listDispatchable(limit)) {
            if (!claimForDispatch(event)) {
                continue;
            }

            try {
                dispatcher.dispatch(event);
                markDelivered(event);
                delivered += 1;
            } catch (RuntimeException exception) {
                logger.warn("平台事件 outbox 投递失败: id={}, eventType={}, message={}",
                        event.getId(), event.getEventType(), exception.getMessage());
                markFailed(event, exception);
            }
        }
        return delivered;
    }

    public boolean replayById(Long eventId, PlatformEventDispatcher dispatcher) {
        PlatformEventOutboxEntity event = findById(eventId);
        if (event == null || dispatcher == null) {
            return false;
        }

        resetForReplay(event);
        return dispatchSingle(event, dispatcher);
    }

    private boolean dispatchSingle(PlatformEventOutboxEntity event, PlatformEventDispatcher dispatcher) {
        if (!claimForDispatch(event)) {
            return false;
        }

        try {
            dispatcher.dispatch(event);
            markDelivered(event);
            return true;
        } catch (RuntimeException exception) {
            logger.warn("平台事件 outbox 投递失败: id={}, eventType={}, message={}",
                    event.getId(), event.getEventType(), exception.getMessage());
            markFailed(event, exception);
            return false;
        }
    }

    private void resetForReplay(PlatformEventOutboxEntity event) {
        LocalDateTime now = LocalDateTime.now();
        event.setDispatchStatus(STATUS_RECORDED);
        event.setRetryCount(0);
        event.setNextRetryAt(null);
        event.setDeliveredAt(null);
        event.setLastError(null);
        event.setUpdatedAt(now);
        event.setUpdatedBy(event.getUpdatedBy() == null ? 0L : event.getUpdatedBy());

        if (queryOperations != null) {
            queryOperations.update(
                    SQL_RESET_FOR_REPLAY,
                    STATUS_RECORDED,
                    now,
                    event.getUpdatedBy() == null ? 0L : event.getUpdatedBy(),
                    SYSTEM_SOURCE_TYPE,
                    event.getId()
            );
            return;
        }

        platformEventOutboxMapper.update(null, new UpdateWrapper<PlatformEventOutboxEntity>()
                .eq("id", event.getId())
                .eq("deleted", 0)
                .eq("source_type", SYSTEM_SOURCE_TYPE)
                .set("dispatch_status", STATUS_RECORDED)
                .set("retry_count", 0)
                .set("next_retry_at", null)
                .set("delivered_at", null)
                .set("last_error", null)
                .set("updated_at", now)
                .set("updated_by", event.getUpdatedBy()));
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("平台事件 outbox payload 序列化失败", exception);
        }
    }

    private long calculateRetryDelaySeconds(int retryCount) {
        int exponent = Math.min(Math.max(retryCount, 1), 8);
        return Math.min(MAX_RETRY_DELAY_SECONDS, (long) Math.pow(2, exponent));
    }

    private String truncateError(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }

    private void ensureSystemSource(String sourceType) {
        if (!SYSTEM_SOURCE_TYPE.equals(sourceType)) {
            throw new IllegalArgumentException("平台事件 outbox sourceType 必须为 SYSTEM");
        }
    }
}
