package com.lumira.message.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.lumira.api.message.MessageEventDTO;
import com.lumira.common.web.TraceContext;
import com.lumira.message.mapper.MessagePlatformEventOutboxMapper;
import com.lumira.message.service.MessageEventFactory;
import com.lumira.message.service.MessageEventDeliveryService;
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
import java.util.UUID;
import java.util.regex.Pattern;

@Service("messagePlatformEventOutboxService")
public class PlatformEventOutboxService {

    private static final Logger logger = LoggerFactory.getLogger(PlatformEventOutboxService.class);
    public static final String STATUS_RECORDED = "RECORDED";
    public static final String STATUS_DISPATCHING = "DISPATCHING";
    public static final String STATUS_DELIVERED = "DELIVERED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_DEAD_LETTER = "DEAD_LETTER";
    private static final int MAX_ERROR_LENGTH = 1024;
    private static final int MAX_RETRY_DELAY_SECONDS = 300;
    public static final int MAX_DISPATCH_LIMIT = 200;
    private static final int MAX_RETRY_COUNT = 8;
    private static final int MAX_EVENT_TYPE_LENGTH = 128;
    private static final int MAX_EVENT_KEY_LENGTH = 256;
    private static final int MAX_PAYLOAD_JSON_LENGTH = 256 * 1024;
    private static final long DISPATCHABLE_COUNT_CACHE_TTL_MS = 15_000L;
    private static final long CLAIM_LEASE_MINUTES = 15L;
    private static final Pattern EVENT_TYPE_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_.-]{1,127}$");
    private static final Pattern EVENT_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9._:@/-]{1,256}$");

    private final MessagePlatformEventOutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;
    private final Counter recordedCounter;
    private final Counter deliveredCounter;
    private final Counter failedCounter;
    private final Counter replayCounter;
    private volatile Long cachedDispatchableCount;
    private volatile long cachedDispatchableCountUntilMillis;

    public PlatformEventOutboxService(MessagePlatformEventOutboxMapper outboxMapper, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.outboxMapper = outboxMapper;
        this.objectMapper = objectMapper;
        this.recordedCounter = Counter.builder("message.outbox.record.total").register(meterRegistry);
        this.deliveredCounter = Counter.builder("message.outbox.delivered.total").register(meterRegistry);
        this.failedCounter = Counter.builder("message.outbox.failed.total").register(meterRegistry);
        this.replayCounter = Counter.builder("message.outbox.replay.total").register(meterRegistry);
    }

    public void recordAfterCommit(MessageEventDTO event) {
        record(event);
    }

    public PlatformEventOutboxEntity record(MessageEventDTO event) {
        if (event == null) {
            throw new IllegalArgumentException("event不能为空");
        }
        ensureMessageSource(event.getSourceType());
        Long normalizedUserId = normalizeUserId(event.getUserId());
        String normalizedUserUuid = normalizeUserUuid(normalizedUserId, event);
        PlatformEventOutboxEntity entity = new PlatformEventOutboxEntity();
        entity.setUserId(normalizedUserId);
        entity.setUserUuid(normalizedUserUuid);
        entity.setSourceType(MessageEventFactory.SOURCE_MESSAGE);
        entity.setEventType(resolveEventType(event));
        entity.setEventKey(resolveEventKey(event));
        String payloadJson = serialize(event);
        if (payloadJson.length() > MAX_PAYLOAD_JSON_LENGTH) {
            throw new IllegalArgumentException("Message outbox payload_json is too large");
        }
        entity.setPayloadJson(payloadJson);
        entity.setDispatchStatus(STATUS_RECORDED);
        entity.setRetryCount(0);
        entity.setTraceId(resolveTraceId(event));
        entity.setRequestId(resolveRequestId(event));
        entity.setCreatedBy(normalizedUserId);
        entity.setCreatedByUuid(normalizedUserUuid);
        entity.setUpdatedBy(normalizedUserId);
        entity.setUpdatedByUuid(normalizedUserUuid);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setDeleted(0);

        int inserted = outboxMapper.insert(entity);
        if (inserted != 1) {
            throw new IllegalStateException("Message outbox changed, please retry");
        }
        recordedCounter.increment();
        invalidateDispatchableCountCache();
        return entity;
    }

    private Long normalizeUserId(Long userId) {
        if (userId == null) {
            return null;
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive when present");
        }
        return userId;
    }

    private String normalizeUserUuid(Long userId, MessageEventDTO event) {
        if (userId == null) {
            return null;
        }
        if (event == null || !StringUtils.hasText(event.getUserUuid())) {
            throw new IllegalArgumentException("userUuid must be present when userId is present");
        }
        String resolvedUserUuid = outboxMapper.resolveActiveUserUuid(userId);
        if (!StringUtils.hasText(resolvedUserUuid)) {
            throw new IllegalArgumentException("Message outbox userUuid cannot be verified");
        }
        if (!resolvedUserUuid.trim().equals(event.getUserUuid().trim())) {
            throw new IllegalArgumentException("Message outbox userUuid does not match userId");
        }
        return resolvedUserUuid.trim();
    }

    private Long trustedUserIdOrNull(Long userId) {
        return userId == null || userId <= 0 ? null : userId;
    }

    private String trustedUserUuidOrNull(PlatformEventOutboxEntity event) {
        if (event == null || trustedUserIdOrNull(event.getUpdatedBy()) == null) {
            return null;
        }
        if (StringUtils.hasText(event.getUpdatedByUuid())) {
            return event.getUpdatedByUuid().trim();
        }
        return StringUtils.hasText(event.getUserUuid()) ? event.getUserUuid().trim() : null;
    }

    public List<PlatformEventOutboxEntity> listDispatchable(int limit) {
        int normalizedLimit = requireDispatchLimit(limit);
        LocalDateTime now = LocalDateTime.now();
        return outboxMapper.listDispatchable(
                MessageEventFactory.SOURCE_MESSAGE,
                STATUS_RECORDED,
                STATUS_FAILED,
                now,
                normalizedLimit
        );
    }

    private int requireDispatchLimit(int limit) {
        if (limit < 1 || limit > MAX_DISPATCH_LIMIT) {
            throw new IllegalArgumentException("dispatch limit must be between 1 and " + MAX_DISPATCH_LIMIT);
        }
        return limit;
    }

    public PlatformEventOutboxEntity findById(Long id) {
        if (id == null || id <= 0) {
            return null;
        }
        return outboxMapper.selectOne(new QueryWrapper<PlatformEventOutboxEntity>()
                .eq("id", id)
                .eq("deleted", 0)
                .eq("source_type", MessageEventFactory.SOURCE_MESSAGE)
                .last("limit 1"));
    }

    public long countDispatchable() {
        long nowMillis = System.currentTimeMillis();
        Long cached = cachedDispatchableCount;
        if (cached != null && nowMillis < cachedDispatchableCountUntilMillis) {
            return cached;
        }
        synchronized (this) {
            nowMillis = System.currentTimeMillis();
            cached = cachedDispatchableCount;
            if (cached != null && nowMillis < cachedDispatchableCountUntilMillis) {
                return cached;
            }
            long count = loadDispatchableCount();
            cachedDispatchableCount = count;
            cachedDispatchableCountUntilMillis = nowMillis + DISPATCHABLE_COUNT_CACHE_TTL_MS;
            return count;
        }
    }

    private long loadDispatchableCount() {
        LocalDateTime now = LocalDateTime.now();
        Long count = outboxMapper.selectCount(new QueryWrapper<PlatformEventOutboxEntity>()
                .eq("deleted", 0)
                .eq("source_type", MessageEventFactory.SOURCE_MESSAGE)
                .and(wrapper -> wrapper
                        .eq("dispatch_status", STATUS_RECORDED)
                        .or(failed -> failed
                                .eq("dispatch_status", STATUS_FAILED)
                                .and(retry -> retry.isNull("next_retry_at").or().le("next_retry_at", now)))
                        .or(dispatching -> dispatching
                                .eq("dispatch_status", STATUS_DISPATCHING)
                                .isNotNull("claim_expires_at")
                                .le("claim_expires_at", now))));
        return count == null ? 0L : count;
    }

    public Long latestVersion() {
        PlatformEventOutboxEntity entity = outboxMapper.selectOne(new QueryWrapper<PlatformEventOutboxEntity>()
                .select("id")
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

        LocalDateTime now = LocalDateTime.now();
        String claimToken = UUID.randomUUID().toString();
        LocalDateTime claimExpiresAt = now.plusMinutes(CLAIM_LEASE_MINUTES);
        UpdateWrapper<PlatformEventOutboxEntity> updateWrapper = new UpdateWrapper<PlatformEventOutboxEntity>()
                .set("dispatch_status", STATUS_DISPATCHING)
                .set("claim_token", claimToken)
                .set("claim_expires_at", claimExpiresAt)
                .set("updated_at", now)
                .set("updated_by", trustedUserIdOrNull(event.getUpdatedBy()))
                .set("updated_by_uuid", trustedUserUuidOrNull(event))
                .eq("id", event.getId())
                .eq("source_type", MessageEventFactory.SOURCE_MESSAGE)
                .eq("deleted", 0)
                .eq("event_type", event.getEventType())
                .eq("event_key", event.getEventKey())
                .and(wrapper -> wrapper
                        .eq("dispatch_status", event.getDispatchStatus())
                        .or(expired -> expired
                                .eq("dispatch_status", STATUS_DISPATCHING)
                                .isNotNull("claim_expires_at")
                                .le("claim_expires_at", now)));
        applyIdentityPredicate(updateWrapper, event);
        int updated = outboxMapper.update(null, updateWrapper);
        if (updated > 0) {
            event.setDispatchStatus(STATUS_DISPATCHING);
            event.setClaimToken(claimToken);
            event.setClaimExpiresAt(claimExpiresAt);
        }
        return updated > 0;
    }

    public void markDelivered(PlatformEventOutboxEntity event) {
        if (event == null || event.getId() == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        UpdateWrapper<PlatformEventOutboxEntity> wrapper = new UpdateWrapper<PlatformEventOutboxEntity>()
                .set("dispatch_status", STATUS_DELIVERED)
                .set("delivered_at", now)
                .set("next_retry_at", null)
                .set("last_error", null)
                .set("claim_token", null)
                .set("claim_expires_at", null)
                .set("updated_at", now)
                .set("updated_by", trustedUserIdOrNull(event.getUpdatedBy()))
                .set("updated_by_uuid", trustedUserUuidOrNull(event))
                .eq("id", event.getId())
                .eq("source_type", MessageEventFactory.SOURCE_MESSAGE)
                .eq("deleted", 0)
                .eq("event_type", event.getEventType())
                .eq("event_key", event.getEventKey())
                .eq("dispatch_status", STATUS_DISPATCHING)
                .eq("claim_token", event.getClaimToken());
        applyRetryCountPredicate(wrapper, event.getRetryCount());
        applyIdentityPredicate(wrapper, event);
        int updated = outboxMapper.update(null, wrapper);
        if (updated > 0) {
            deliveredCounter.increment();
            invalidateDispatchableCountCache();
        } else {
            logger.warn("Message outbox delivery claim mismatch id={} eventType={}", event.getId(), event.getEventType());
            throw new IllegalStateException("Message outbox changed, please retry");
        }
    }

    public void markFailed(PlatformEventOutboxEntity event, RuntimeException exception) {
        if (event == null || event.getId() == null) {
            return;
        }

        int retryCount = event.getRetryCount() == null ? 0 : event.getRetryCount();
        int nextRetryCount = retryCount + 1;
        String nextStatus = nextRetryCount >= MAX_RETRY_COUNT ? STATUS_DEAD_LETTER : STATUS_FAILED;
        LocalDateTime now = LocalDateTime.now();
        UpdateWrapper<PlatformEventOutboxEntity> wrapper = new UpdateWrapper<PlatformEventOutboxEntity>()
                .set("dispatch_status", nextStatus)
                .set("retry_count", nextRetryCount)
                .set("next_retry_at", STATUS_DEAD_LETTER.equals(nextStatus) ? null : now.plusSeconds(calculateRetryDelaySeconds(nextRetryCount)))
                .set("last_error", truncateError(exception == null ? "unknown error" : exception.getMessage()))
                .set("claim_token", null)
                .set("claim_expires_at", null)
                .set("updated_at", now)
                .set("updated_by", trustedUserIdOrNull(event.getUpdatedBy()))
                .set("updated_by_uuid", trustedUserUuidOrNull(event))
                .eq("id", event.getId())
                .eq("source_type", MessageEventFactory.SOURCE_MESSAGE)
                .eq("deleted", 0)
                .eq("event_type", event.getEventType())
                .eq("event_key", event.getEventKey())
                .eq("dispatch_status", STATUS_DISPATCHING)
                .eq("claim_token", event.getClaimToken());
        applyRetryCountPredicate(wrapper, event.getRetryCount());
        applyIdentityPredicate(wrapper, event);
        int updated = outboxMapper.update(null, wrapper);
        if (updated > 0) {
            failedCounter.increment();
            invalidateDispatchableCountCache();
        } else {
            logger.warn("Message outbox failure claim mismatch id={} eventType={}", event.getId(), event.getEventType());
            throw new IllegalStateException("Message outbox changed, please retry");
        }
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

        if (!resetForReplay(event)) {
            return false;
        }
        replayCounter.increment();
        return dispatchSingle(event, deliveryService);
    }

    private boolean dispatchSingle(PlatformEventOutboxEntity event, MessageEventDeliveryService deliveryService) {
        if (!claimForDispatch(event)) {
            return false;
        }

        try {
            requireTrustedDispatchRow(event);
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

    private boolean resetForReplay(PlatformEventOutboxEntity event) {
        String previousStatus = event.getDispatchStatus();
        Integer previousRetryCount = event.getRetryCount();
        LocalDateTime now = LocalDateTime.now();
        Long trustedUpdatedBy = trustedUserIdOrNull(event.getUpdatedBy());
        String trustedUpdatedByUuid = trustedUserUuidOrNull(event);

        UpdateWrapper<PlatformEventOutboxEntity> wrapper = new UpdateWrapper<PlatformEventOutboxEntity>()
                .set("dispatch_status", STATUS_RECORDED)
                .set("retry_count", 0)
                .set("next_retry_at", null)
                .set("delivered_at", null)
                .set("last_error", null)
                .set("claim_token", null)
                .set("claim_expires_at", null)
                .set("updated_at", now)
                .set("updated_by", trustedUpdatedBy)
                .set("updated_by_uuid", trustedUpdatedByUuid)
                .eq("id", event.getId())
                .eq("source_type", MessageEventFactory.SOURCE_MESSAGE)
                .eq("deleted", 0)
                .eq("event_type", event.getEventType())
                .eq("event_key", event.getEventKey())
                .eq("dispatch_status", previousStatus);
        if (previousRetryCount == null) {
            wrapper.isNull("retry_count");
        } else {
            wrapper.eq("retry_count", previousRetryCount);
        }
        applyIdentityPredicate(wrapper, event);
        int updated = outboxMapper.update(null, wrapper);
        if (updated <= 0) {
            logger.warn("Message outbox replay reset mismatch id={} eventType={}", event.getId(), event.getEventType());
            return false;
        }
        event.setDispatchStatus(STATUS_RECORDED);
        event.setRetryCount(0);
        event.setNextRetryAt(null);
        event.setDeliveredAt(null);
        event.setLastError(null);
        event.setClaimToken(null);
        event.setClaimExpiresAt(null);
        event.setUpdatedAt(now);
        event.setUpdatedBy(trustedUpdatedBy);
        event.setUpdatedByUuid(trustedUpdatedByUuid);
        invalidateDispatchableCountCache();
        return true;
    }

    private void applyIdentityPredicate(UpdateWrapper<PlatformEventOutboxEntity> wrapper, PlatformEventOutboxEntity event) {
        if (event == null || event.getUserId() == null) {
            wrapper.isNull("user_id").isNull("user_uuid");
            return;
        }
        wrapper.eq("user_id", event.getUserId())
                .eq("user_uuid", normalizeUserUuidOrNull(event.getUserUuid()));
    }

    private void applyRetryCountPredicate(UpdateWrapper<PlatformEventOutboxEntity> wrapper, Integer retryCount) {
        if (retryCount == null) {
            wrapper.isNull("retry_count");
        } else {
            wrapper.eq("retry_count", retryCount);
        }
    }

    private String normalizeUserUuidOrNull(String userUuid) {
        return StringUtils.hasText(userUuid) ? userUuid.trim() : null;
    }

    private void invalidateDispatchableCountCache() {
        cachedDispatchableCount = null;
        cachedDispatchableCountUntilMillis = 0L;
    }

    private MessageEventDTO deserialize(String payloadJson) {
        if (payloadJson != null && payloadJson.length() > MAX_PAYLOAD_JSON_LENGTH) {
            throw new IllegalStateException("Message outbox payload is too large");
        }
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

    private void ensureMessageSource(String sourceType) {
        if (StringUtils.hasText(sourceType) && !MessageEventFactory.SOURCE_MESSAGE.equals(sourceType)) {
            throw new IllegalArgumentException("消息 outbox sourceType 必须为 MESSAGE");
        }
    }

    private String resolveEventType(MessageEventDTO event) {
        String eventType = StringUtils.hasText(event.getEventType()) ? event.getEventType() : "UNKNOWN";
        return requireBoundedText(eventType, "eventType", MAX_EVENT_TYPE_LENGTH);
    }

    private String resolveEventKey(MessageEventDTO event) {
        if (StringUtils.hasText(event.getEventKey())) {
            return requireBoundedText(event.getEventKey(), "eventKey", MAX_EVENT_KEY_LENGTH);
        }
        String userPart = event.getUserId() == null ? "all" : String.valueOf(event.getUserId());
        String versionPart = event.getVersion() == null ? "none" : String.valueOf(event.getVersion());
        return requireBoundedText(
                MessageEventFactory.SOURCE_MESSAGE + ":" + resolveEventType(event) + ":" + userPart + ":" + versionPart,
                "eventKey",
                MAX_EVENT_KEY_LENGTH
        );
    }

    private String requireBoundedText(String value, String fieldName, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Message outbox " + fieldName + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("Message outbox " + fieldName + " is too long");
        }
        if ("eventType".equals(fieldName) && !EVENT_TYPE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Message outbox eventType is invalid");
        }
        if ("eventKey".equals(fieldName)
                && (!EVENT_KEY_PATTERN.matcher(normalized).matches()
                || normalized.contains("..")
                || normalized.contains("//"))) {
            throw new IllegalArgumentException("Message outbox eventKey is invalid");
        }
        return normalized;
    }

    private void requireTrustedDispatchRow(PlatformEventOutboxEntity event) {
        if (!isTrustedDispatchRow(event)) {
            throw new IllegalArgumentException("Message outbox row is invalid");
        }
    }

    private boolean isTrustedDispatchRow(PlatformEventOutboxEntity event) {
        if (event == null
                || event.getId() == null
                || event.getId() <= 0
                || !MessageEventFactory.SOURCE_MESSAGE.equals(event.getSourceType())
                || !STATUS_DISPATCHING.equals(event.getDispatchStatus())
                || !StringUtils.hasText(event.getClaimToken())) {
            return false;
        }
        try {
            requireBoundedText(event.getEventType(), "eventType", MAX_EVENT_TYPE_LENGTH);
            requireBoundedText(event.getEventKey(), "eventKey", MAX_EVENT_KEY_LENGTH);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        if (!StringUtils.hasText(event.getPayloadJson()) || event.getPayloadJson().length() > MAX_PAYLOAD_JSON_LENGTH) {
            return false;
        }
        if (event.getUserId() != null) {
            if (event.getUserId() <= 0 || !payloadJsonHasTrustedUserIdentity(event)) {
                return false;
            }
        }
        Integer retryCount = event.getRetryCount();
        return retryCount == null || (retryCount >= 0 && retryCount <= MAX_RETRY_COUNT);
    }

    private boolean payloadJsonHasTrustedUserIdentity(PlatformEventOutboxEntity row) {
        try {
            MessageEventDTO event = objectMapper.readValue(row.getPayloadJson(), MessageEventDTO.class);
            return event != null
                    && event.getUserId() != null
                    && event.getUserId().equals(row.getUserId())
                    && StringUtils.hasText(row.getUserUuid())
                    && StringUtils.hasText(event.getUserUuid())
                    && row.getUserUuid().trim().equals(event.getUserUuid().trim());
        } catch (JsonProcessingException exception) {
            return false;
        }
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
