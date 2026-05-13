package com.legendary.invention.saas.infrastructure.event;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legendary.invention.common.web.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final int MAX_ERROR_LENGTH = 1024;
    private static final int MAX_RETRY_DELAY_SECONDS = 300;

    private final ObjectMapper objectMapper;
    private final PlatformEventOutboxMapper platformEventOutboxMapper;

    public PlatformEventOutboxService(ObjectMapper objectMapper, PlatformEventOutboxMapper platformEventOutboxMapper) {
        this.objectMapper = objectMapper;
        this.platformEventOutboxMapper = platformEventOutboxMapper;
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
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setDeleted(0);
        platformEventOutboxMapper.insert(entity);
        return entity;
    }

    public List<PlatformEventOutboxEntity> listDispatchable(int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit, 200));
        LocalDateTime now = LocalDateTime.now();
        return platformEventOutboxMapper.selectList(new QueryWrapper<PlatformEventOutboxEntity>()
                .eq("deleted", 0)
                .and(query -> query
                        .eq("dispatch_status", STATUS_RECORDED)
                        .or(nested -> nested
                                .eq("dispatch_status", STATUS_FAILED)
                                .and(retry -> retry
                                        .isNull("next_retry_at")
                                        .or()
                                        .le("next_retry_at", now))))
                .orderByAsc("created_at")
                .last("limit " + normalizedLimit));
    }

    public boolean claimForDispatch(PlatformEventOutboxEntity event) {
        if (event == null || event.getId() == null) {
            return false;
        }

        PlatformEventOutboxEntity update = new PlatformEventOutboxEntity();
        update.setDispatchStatus(STATUS_DISPATCHING);
        update.setUpdatedAt(LocalDateTime.now());
        update.setUpdatedBy(event.getUpdatedBy() == null ? 0L : event.getUpdatedBy());

        int updated = platformEventOutboxMapper.update(update, new UpdateWrapper<PlatformEventOutboxEntity>()
                .eq("id", event.getId())
                .eq("deleted", 0)
                .eq("dispatch_status", event.getDispatchStatus()));
        return updated > 0;
    }

    public void markDelivered(PlatformEventOutboxEntity event) {
        if (event == null || event.getId() == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        platformEventOutboxMapper.update(null, new UpdateWrapper<PlatformEventOutboxEntity>()
                .eq("id", event.getId())
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
        LocalDateTime now = LocalDateTime.now();
        platformEventOutboxMapper.update(null, new UpdateWrapper<PlatformEventOutboxEntity>()
                .eq("id", event.getId())
                .set("dispatch_status", STATUS_FAILED)
                .set("retry_count", nextRetryCount)
                .set("next_retry_at", now.plusSeconds(calculateRetryDelaySeconds(nextRetryCount)))
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
}
