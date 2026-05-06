package com.legendary.invention.message.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legendary.invention.api.message.MessageEventDTO;
import com.legendary.invention.common.web.TraceContext;
import com.legendary.invention.message.service.MessageEventFactory;
import com.legendary.invention.message.service.MessageEventDeliveryService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PlatformEventOutboxService {

    private static final Logger logger = LoggerFactory.getLogger(PlatformEventOutboxService.class);
    public static final String STATUS_RECORDED = "RECORDED";
    public static final String STATUS_DISPATCHING = "DISPATCHING";
    public static final String STATUS_DELIVERED = "DELIVERED";
    public static final String STATUS_FAILED = "FAILED";
    private static final int MAX_ERROR_LENGTH = 1024;
    private static final int MAX_RETRY_DELAY_SECONDS = 300;
    private static final int MAX_DISPATCH_LIMIT = 200;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Counter recordedCounter;
    private final Counter deliveredCounter;
    private final Counter failedCounter;
    private final Counter replayCounter;

    public PlatformEventOutboxService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.recordedCounter = Counter.builder("message.outbox.record.total").register(meterRegistry);
        this.deliveredCounter = Counter.builder("message.outbox.delivered.total").register(meterRegistry);
        this.failedCounter = Counter.builder("message.outbox.failed.total").register(meterRegistry);
        this.replayCounter = Counter.builder("message.outbox.replay.total").register(meterRegistry);
    }

    public void recordAfterCommit(MessageEventDTO event) {
        Runnable recordAction = () -> record(event);
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

    public PlatformEventOutboxEntity record(MessageEventDTO event) {
        if (event == null) {
            throw new IllegalArgumentException("event不能为空");
        }
        PlatformEventOutboxEntity entity = new PlatformEventOutboxEntity();
        entity.setTenantId(event.getTenantId());
        entity.setUserId(event.getUserId());
        entity.setSourceType(resolveSourceType(event));
        entity.setEventType(resolveEventType(event));
        entity.setEventKey(resolveEventKey(event));
        entity.setPayloadJson(serialize(event));
        entity.setDispatchStatus(STATUS_RECORDED);
        entity.setRetryCount(0);
        entity.setTraceId(resolveTraceId(event));
        entity.setRequestId(resolveRequestId(event));
        entity.setCreatedBy(event.getUserId() == null ? 0L : event.getUserId());
        entity.setUpdatedBy(event.getUserId() == null ? 0L : event.getUserId());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setDeleted(0);

        jdbcTemplate.update(
                """
                        insert into platform_event_outbox (
                            tenant_id, user_id, source_type, event_type, event_key, payload_json, dispatch_status,
                            retry_count, next_retry_at, delivered_at, last_error, trace_id, request_id, created_by,
                            created_at, updated_by, updated_at, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, null, null, null, ?, ?, ?, ?, ?, ?, 0)
                        """,
                entity.getTenantId(),
                entity.getUserId(),
                entity.getSourceType(),
                entity.getEventType(),
                entity.getEventKey(),
                entity.getPayloadJson(),
                entity.getDispatchStatus(),
                entity.getRetryCount(),
                entity.getTraceId(),
                entity.getRequestId(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedBy(),
                entity.getUpdatedAt()
        );
        recordedCounter.increment();
        return entity;
    }

    public List<PlatformEventOutboxEntity> listDispatchable(int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit, MAX_DISPATCH_LIMIT));
        LocalDateTime now = LocalDateTime.now();
        return jdbcTemplate.query(
                """
                        select *
                        from platform_event_outbox
                        where deleted = 0
                          and (
                              dispatch_status = ?
                              or (
                                  dispatch_status = ?
                                  and (next_retry_at is null or next_retry_at <= ?)
                              )
                          )
                        order by created_at asc
                        limit ?
                        """,
                this::mapEntity,
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
        List<PlatformEventOutboxEntity> results = jdbcTemplate.query(
                """
                        select *
                        from platform_event_outbox
                        where id = ?
                          and deleted = 0
                        limit 1
                        """,
                this::mapEntity,
                id
        );
        return results.isEmpty() ? null : results.get(0);
    }

    public long countDispatchable() {
        Long count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from platform_event_outbox
                        where deleted = 0
                          and (
                              dispatch_status = ?
                              or (
                                  dispatch_status = ?
                                  and (next_retry_at is null or next_retry_at <= ?)
                              )
                          )
                        """,
                Long.class,
                STATUS_RECORDED,
                STATUS_FAILED,
                LocalDateTime.now()
        );
        return count == null ? 0L : count;
    }

    public Long latestVersionForTenant(Long tenantId) {
        if (tenantId == null) {
            return 0L;
        }
        Long version = jdbcTemplate.queryForObject(
                """
                        select coalesce(max(id), 0)
                        from platform_event_outbox
                        where tenant_id = ?
                          and source_type = ?
                          and deleted = 0
                        """,
                Long.class,
                tenantId,
                MessageEventFactory.SOURCE_MESSAGE
        );
        return version == null ? 0L : version;
    }

    public boolean claimForDispatch(PlatformEventOutboxEntity event) {
        if (event == null || event.getId() == null) {
            return false;
        }

        PlatformEventOutboxEntity update = new PlatformEventOutboxEntity();
        update.setDispatchStatus(STATUS_DISPATCHING);
        update.setUpdatedAt(LocalDateTime.now());
        update.setUpdatedBy(event.getUpdatedBy() == null ? 0L : event.getUpdatedBy());

        int updated = jdbcTemplate.update(
                """
                        update platform_event_outbox
                        set dispatch_status = ?, updated_at = ?, updated_by = ?
                        where id = ?
                          and deleted = 0
                          and dispatch_status = ?
                        """,
                update.getDispatchStatus(),
                update.getUpdatedAt(),
                update.getUpdatedBy(),
                event.getId(),
                event.getDispatchStatus()
        );
        return updated > 0;
    }

    public void markDelivered(PlatformEventOutboxEntity event) {
        if (event == null || event.getId() == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                        update platform_event_outbox
                        set dispatch_status = ?,
                            delivered_at = ?,
                            next_retry_at = null,
                            last_error = null,
                            updated_at = ?,
                            updated_by = ?
                        where id = ?
                          and deleted = 0
                        """,
                STATUS_DELIVERED,
                now,
                now,
                event.getUpdatedBy() == null ? 0L : event.getUpdatedBy(),
                event.getId()
        );
        deliveredCounter.increment();
    }

    public void markFailed(PlatformEventOutboxEntity event, RuntimeException exception) {
        if (event == null || event.getId() == null) {
            return;
        }

        int retryCount = event.getRetryCount() == null ? 0 : event.getRetryCount();
        int nextRetryCount = retryCount + 1;
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                        update platform_event_outbox
                        set dispatch_status = ?,
                            retry_count = ?,
                            next_retry_at = ?,
                            last_error = ?,
                            updated_at = ?,
                            updated_by = ?
                        where id = ?
                          and deleted = 0
                        """,
                STATUS_FAILED,
                nextRetryCount,
                now.plusSeconds(calculateRetryDelaySeconds(nextRetryCount)),
                truncateError(exception == null ? "unknown error" : exception.getMessage()),
                now,
                event.getUpdatedBy() == null ? 0L : event.getUpdatedBy(),
                event.getId()
        );
        failedCounter.increment();
    }

    public int dispatchPending(MessageEventDeliveryService deliveryService, int limit) {
        if (deliveryService == null) {
            return 0;
        }

        int delivered = 0;
        for (PlatformEventOutboxEntity event : listDispatchable(limit)) {
            if (!dispatchSingle(event, deliveryService)) {
                continue;
            }
            delivered += 1;
        }
        return delivered;
    }

    public boolean replayById(Long eventId, MessageEventDeliveryService deliveryService) {
        PlatformEventOutboxEntity event = findById(eventId);
        if (event == null || deliveryService == null) {
            return false;
        }

        resetForReplay(event);
        replayCounter.increment();
        return dispatchSingle(event, deliveryService);
    }

    private boolean dispatchSingle(PlatformEventOutboxEntity event, MessageEventDeliveryService deliveryService) {
        if (!claimForDispatch(event)) {
            return false;
        }

        try {
            deliveryService.deliver(deserialize(event.getPayloadJson()));
            markDelivered(event);
            return true;
        } catch (RuntimeException exception) {
            logger.warn("消息 outbox 投递失败: id={}, eventType={}, message={}",
                    event.getId(), event.getEventType(), exception.getMessage());
            markFailed(event, exception);
            return false;
        }
    }

    private void resetForReplay(PlatformEventOutboxEntity event) {
        event.setDispatchStatus(STATUS_RECORDED);
        event.setRetryCount(0);
        event.setNextRetryAt(null);
        event.setDeliveredAt(null);
        event.setLastError(null);
        event.setUpdatedAt(LocalDateTime.now());
        event.setUpdatedBy(event.getUpdatedBy() == null ? 0L : event.getUpdatedBy());

        jdbcTemplate.update(
                """
                        update platform_event_outbox
                        set dispatch_status = ?,
                            retry_count = 0,
                            next_retry_at = null,
                            delivered_at = null,
                            last_error = null,
                            updated_at = ?,
                            updated_by = ?
                        where id = ?
                          and deleted = 0
                        """,
                STATUS_RECORDED,
                event.getUpdatedAt(),
                event.getUpdatedBy(),
                event.getId()
        );
    }

    private MessageEventDTO deserialize(String payloadJson) {
        if (!StringUtils.hasText(payloadJson)) {
            throw new IllegalStateException("平台事件 payload 为空");
        }
        try {
            return objectMapper.readValue(payloadJson, MessageEventDTO.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("平台事件 payload 反序列化失败", exception);
        }
    }

    private String serialize(MessageEventDTO event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("平台事件 outbox payload 序列化失败", exception);
        }
    }

    private PlatformEventOutboxEntity mapEntity(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        PlatformEventOutboxEntity entity = new PlatformEventOutboxEntity();
        entity.setId(rs.getLong("id"));
        entity.setTenantId(rs.getLong("tenant_id"));
        long userId = rs.getLong("user_id");
        entity.setUserId(rs.wasNull() ? null : userId);
        entity.setSourceType(rs.getString("source_type"));
        entity.setEventType(rs.getString("event_type"));
        entity.setEventKey(rs.getString("event_key"));
        entity.setPayloadJson(rs.getString("payload_json"));
        entity.setDispatchStatus(rs.getString("dispatch_status"));
        int retryCount = rs.getInt("retry_count");
        entity.setRetryCount(rs.wasNull() ? null : retryCount);
        entity.setNextRetryAt(toLocalDateTime(rs.getTimestamp("next_retry_at")));
        entity.setDeliveredAt(toLocalDateTime(rs.getTimestamp("delivered_at")));
        entity.setLastError(rs.getString("last_error"));
        entity.setTraceId(rs.getString("trace_id"));
        entity.setRequestId(rs.getString("request_id"));
        long createdBy = rs.getLong("created_by");
        entity.setCreatedBy(rs.wasNull() ? null : createdBy);
        entity.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        long updatedBy = rs.getLong("updated_by");
        entity.setUpdatedBy(rs.wasNull() ? null : updatedBy);
        entity.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        int deleted = rs.getInt("deleted");
        entity.setDeleted(rs.wasNull() ? null : deleted);
        return entity;
    }

    private LocalDateTime toLocalDateTime(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String resolveSourceType(MessageEventDTO event) {
        return StringUtils.hasText(event.getSourceType()) ? event.getSourceType() : MessageEventFactory.SOURCE_MESSAGE;
    }

    private String resolveEventType(MessageEventDTO event) {
        return StringUtils.hasText(event.getEventType()) ? event.getEventType() : "UNKNOWN";
    }

    private String resolveEventKey(MessageEventDTO event) {
        if (StringUtils.hasText(event.getEventKey())) {
            return event.getEventKey();
        }
        return MessageEventFactory.SOURCE_MESSAGE + ":" + resolveEventType(event) + ":" + (event.getTenantId() == null ? "unknown" : event.getTenantId());
    }

    private String resolveTraceId(MessageEventDTO event) {
        return StringUtils.hasText(event.getTraceId()) ? event.getTraceId() : TraceContext.getTraceId();
    }

    private String resolveRequestId(MessageEventDTO event) {
        return StringUtils.hasText(event.getRequestId()) ? event.getRequestId() : TraceContext.getRequestId();
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
}
