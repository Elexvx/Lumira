package com.legendary.invention.message.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.legendary.invention.api.message.MessageEventDTO;
import com.legendary.invention.common.web.TraceContext;
import com.legendary.invention.message.mapper.PlatformEventOutboxMapper;
import com.legendary.invention.message.service.MessageEventFactory;
import com.legendary.invention.message.service.MessageEventDeliveryService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final PlatformEventOutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;
    private final Counter recordedCounter;
    private final Counter deliveredCounter;
    private final Counter failedCounter;
    private final Counter replayCounter;

    public PlatformEventOutboxService(PlatformEventOutboxMapper outboxMapper, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.outboxMapper = outboxMapper;
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

        outboxMapper.insert(entity);
        recordedCounter.increment();
        return entity;
    }

    public List<PlatformEventOutboxEntity> listDispatchable(int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit, MAX_DISPATCH_LIMIT));
        LocalDateTime now = LocalDateTime.now();
        return outboxMapper.selectList(new QueryWrapper<PlatformEventOutboxEntity>()
                .eq("deleted", 0)
                .and(wrapper -> wrapper
                        .eq("dispatch_status", STATUS_RECORDED)
                        .or(failed -> failed
                                .eq("dispatch_status", STATUS_FAILED)
                                .and(retry -> retry.isNull("next_retry_at").or().le("next_retry_at", now))))
                .orderByAsc("created_at")
                .last("limit " + normalizedLimit));
    }

    public PlatformEventOutboxEntity findById(Long id) {
        if (id == null) {
            return null;
        }
        return outboxMapper.selectOne(new QueryWrapper<PlatformEventOutboxEntity>()
                .eq("id", id)
                .eq("deleted", 0)
                .last("limit 1"));
    }

    public long countDispatchable() {
        LocalDateTime now = LocalDateTime.now();
        Long count = outboxMapper.selectCount(new QueryWrapper<PlatformEventOutboxEntity>()
                .eq("deleted", 0)
                .and(wrapper -> wrapper
                        .eq("dispatch_status", STATUS_RECORDED)
                        .or(failed -> failed
                                .eq("dispatch_status", STATUS_FAILED)
                                .and(retry -> retry.isNull("next_retry_at").or().le("next_retry_at", now)))));
        return count == null ? 0L : count;
    }

    public Long latestVersionForTenant(Long tenantId) {
        if (tenantId == null) {
            return 0L;
        }
        PlatformEventOutboxEntity entity = outboxMapper.selectOne(new QueryWrapper<PlatformEventOutboxEntity>()
                .select("id")
                .eq("tenant_id", tenantId)
                .eq("source_type", MessageEventFactory.SOURCE_MESSAGE)
                .eq("deleted", 0)
                .orderByDesc("id")
                .last("limit 1"));
        return entity == null || entity.getId() == null ? 0L : entity.getId();
    }

    public boolean claimForDispatch(PlatformEventOutboxEntity event) {
        if (event == null || event.getId() == null) {
            return false;
        }

        int updated = outboxMapper.update(null, new UpdateWrapper<PlatformEventOutboxEntity>()
                .set("dispatch_status", STATUS_DISPATCHING)
                .set("updated_at", LocalDateTime.now())
                .set("updated_by", event.getUpdatedBy() == null ? 0L : event.getUpdatedBy())
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
        outboxMapper.update(null, new UpdateWrapper<PlatformEventOutboxEntity>()
                .set("dispatch_status", STATUS_DELIVERED)
                .set("delivered_at", now)
                .set("next_retry_at", null)
                .set("last_error", null)
                .set("updated_at", now)
                .set("updated_by", event.getUpdatedBy() == null ? 0L : event.getUpdatedBy())
                .eq("id", event.getId())
                .eq("deleted", 0));
        deliveredCounter.increment();
    }

    public void markFailed(PlatformEventOutboxEntity event, RuntimeException exception) {
        if (event == null || event.getId() == null) {
            return;
        }

        int retryCount = event.getRetryCount() == null ? 0 : event.getRetryCount();
        int nextRetryCount = retryCount + 1;
        LocalDateTime now = LocalDateTime.now();
        outboxMapper.update(null, new UpdateWrapper<PlatformEventOutboxEntity>()
                .set("dispatch_status", STATUS_FAILED)
                .set("retry_count", nextRetryCount)
                .set("next_retry_at", now.plusSeconds(calculateRetryDelaySeconds(nextRetryCount)))
                .set("last_error", truncateError(exception == null ? "unknown error" : exception.getMessage()))
                .set("updated_at", now)
                .set("updated_by", event.getUpdatedBy() == null ? 0L : event.getUpdatedBy())
                .eq("id", event.getId())
                .eq("deleted", 0));
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

        outboxMapper.update(null, new UpdateWrapper<PlatformEventOutboxEntity>()
                .set("dispatch_status", STATUS_RECORDED)
                .set("retry_count", 0)
                .set("next_retry_at", null)
                .set("delivered_at", null)
                .set("last_error", null)
                .set("updated_at", event.getUpdatedAt())
                .set("updated_by", event.getUpdatedBy())
                .eq("id", event.getId())
                .eq("deleted", 0));
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
