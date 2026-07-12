package com.lumira.saas.infrastructure.event;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.Map;
import java.util.UUID;

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
    private static final long SNAPSHOT_CACHE_TTL_MS = 15_000L;
    private static final long CLAIM_LEASE_MINUTES = 5L;
    private static final String SYSTEM_SOURCE_TYPE = PlatformEventTypes.SOURCE_SYSTEM;

    private static final String SQL_LIST_DISPATCHABLE = """
            select id, user_id as userId, user_uuid as userUuid, source_type as sourceType,
                   event_type as eventType, event_key as eventKey, payload_json as payloadJson,
                   dispatch_status as dispatchStatus, retry_count as retryCount,
                   next_retry_at as nextRetryAt, delivered_at as deliveredAt, last_error as lastError,
                   trace_id as traceId, request_id as requestId, created_by as createdBy,
                   created_by_uuid as createdByUuid, created_at as createdAt, updated_by as updatedBy,
                   updated_by_uuid as updatedByUuid, updated_at as updatedAt, deleted
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
            select id, user_id as userId, user_uuid as userUuid, source_type as sourceType,
                   event_type as eventType, event_key as eventKey, payload_json as payloadJson,
                   dispatch_status as dispatchStatus, retry_count as retryCount,
                   next_retry_at as nextRetryAt, delivered_at as deliveredAt, last_error as lastError,
                   trace_id as traceId, request_id as requestId, created_by as createdBy,
                   created_by_uuid as createdByUuid, created_at as createdAt, updated_by as updatedBy,
                   updated_by_uuid as updatedByUuid, updated_at as updatedAt, deleted
            from platform_event_outbox
            where id = ? and deleted = 0 and source_type = ?
            limit 1
            """;

    private static final String SQL_CLAIM_FOR_DISPATCH = """
            update platform_event_outbox
            set dispatch_status = ?, claimed_by = ?, claim_token = ?, claim_expires_at = ?,
                updated_at = ?, updated_by = ?, updated_by_uuid = ?
            where deleted = 0 and source_type = ? and id = ? and dispatch_status = ?
              and event_type = ?
              and event_key = ?
              and ((user_id is null and ? is null and user_uuid is null) or (user_id = ? and user_uuid = ?))
            """;

    private static final String SQL_MARK_DELIVERED = """
            update platform_event_outbox
            set dispatch_status = ?, delivered_at = ?, next_retry_at = null, last_error = null,
                claim_token = null, claim_expires_at = null, updated_at = ?, updated_by = ?, updated_by_uuid = ?
            where deleted = 0 and source_type = ? and id = ? and dispatch_status = ? and claim_token = ?
              and event_type = ?
              and ((event_key is null and ? is null) or event_key = ?)
              and ((user_id is null and ? is null and user_uuid is null) or (user_id = ? and user_uuid = ?))
              and ((retry_count is null and ? is null) or retry_count = ?)
            """;

    private static final String SQL_MARK_FAILED = """
            update platform_event_outbox
            set dispatch_status = ?, retry_count = ?, next_retry_at = ?, last_error = ?,
                claim_token = null, claim_expires_at = null, updated_at = ?, updated_by = ?, updated_by_uuid = ?
            where deleted = 0 and source_type = ? and id = ? and dispatch_status = ? and claim_token = ?
              and event_type = ?
              and ((event_key is null and ? is null) or event_key = ?)
              and ((user_id is null and ? is null and user_uuid is null) or (user_id = ? and user_uuid = ?))
              and ((retry_count is null and ? is null) or retry_count = ?)
            """;

    private static final String SQL_RESET_FOR_REPLAY = """
            update platform_event_outbox
            set dispatch_status = ?, retry_count = 0, next_retry_at = null, last_error = null,
                delivered_at = null, claim_token = null, claim_expires_at = null, updated_at = ?, updated_by = ?, updated_by_uuid = ?
            where deleted = 0 and source_type = ? and id = ?
              and event_type = ?
              and event_key = ?
              and dispatch_status = ?
              and ((user_id is null and ? is null and user_uuid is null) or (user_id = ? and user_uuid = ?))
            """;

    private final ObjectMapper objectMapper;
    private final PlatformEventOutboxMapper platformEventOutboxMapper;
    private final MyBatisQueryOperations queryOperations;
    private final BeanPropertyRowMapper<PlatformEventOutboxEntity> rowMapper = new BeanPropertyRowMapper<>(PlatformEventOutboxEntity.class);
    private volatile OutboxMetricsSnapshot cachedSnapshot;
    private volatile long cachedSnapshotUntilMillis;

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
            Long userId,
            String eventKey,
            Object payload
    ) {
        record(sourceType, eventType, userId, eventKey, payload);
    }

    public PlatformEventOutboxEntity record(
            String sourceType,
            String eventType,
            Long userId,
            String eventKey,
            Object payload
    ) {
        ensureSystemSource(sourceType);
        Long normalizedUserId = normalizeUserId(userId);
        PlatformEventOutboxEntity entity = new PlatformEventOutboxEntity();
        String payloadUserUuid = extractPayloadUserUuid(payload);
        String userUuid = resolveTrustedUserUuid(normalizedUserId, payloadUserUuid);
        entity.setUserId(normalizedUserId);
        entity.setUserUuid(userUuid);
        entity.setSourceType(SYSTEM_SOURCE_TYPE);
        entity.setEventType(normalizeEventType(eventType));
        entity.setEventKey(normalizeEventKey(eventKey));
        entity.setPayloadJson(serialize(enrichPayload(payload, userUuid)));
        entity.setDispatchStatus(STATUS_RECORDED);
        entity.setRetryCount(0);
        entity.setTraceId(TraceContext.getTraceId());
        entity.setRequestId(TraceContext.getRequestId());
        entity.setCreatedBy(normalizedUserId);
        entity.setCreatedByUuid(userUuid);
        entity.setUpdatedBy(normalizedUserId);
        entity.setUpdatedByUuid(userUuid);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setDeleted(0);
        int inserted = platformEventOutboxMapper.insert(entity);
        if (inserted != 1) {
            throw new IllegalStateException("Platform event outbox changed, please retry");
        }
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

    private Long trustedUserIdOrNull(Long userId) {
        return userId == null || userId <= 0 ? null : userId;
    }

    private String trustedUserUuidOrNull(PlatformEventOutboxEntity event) {
        if (event == null || trustedUserIdOrNull(event.getUpdatedBy()) == null) {
            return null;
        }
        String userUuid = normalizeUserUuid(event.getUpdatedByUuid());
        if (userUuid != null) {
            return userUuid;
        }
        return normalizeUserUuid(event.getUserUuid());
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

    public PlatformEventOutboxEntity findById(Long id) {
        if (id == null || id <= 0) {
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
                SYSTEM_SOURCE_TYPE
        );
        return new OutboxMetricsSnapshot(
                longValue(row.get("pending_backlog")),
                longValue(row.get("failed_backlog")),
                longValue(row.get("dead_letter_count")),
                longValue(row.get("dispatchable_backlog"))
        );
    }

    private Map<String, Object> firstRow(String sql, Object... args) {
        if (queryOperations == null) {
            return Map.of();
        }
        List<Map<String, Object>> rows = queryOperations.queryForList(sql, args);
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

    public boolean claimForDispatch(PlatformEventOutboxEntity event) {
        if (event == null || event.getId() == null) {
            return false;
        }
        String claimToken = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime claimExpiresAt = now.plusMinutes(CLAIM_LEASE_MINUTES);

        if (queryOperations != null) {
            int updated = queryOperations.update(
                    SQL_CLAIM_FOR_DISPATCH,
                    STATUS_DISPATCHING,
                    workerId(),
                    claimToken,
                    claimExpiresAt,
                    now,
                    trustedUserIdOrNull(event.getUpdatedBy()),
                    trustedUserUuidOrNull(event),
                    SYSTEM_SOURCE_TYPE,
                    event.getId(),
                    event.getDispatchStatus(),
                    event.getEventType(),
                    event.getEventKey(),
                    event.getUserId(),
                    event.getUserId(),
                    normalizeUserUuid(event.getUserUuid())
            );
            if (updated > 0) {
                event.setDispatchStatus(STATUS_DISPATCHING);
                event.setClaimedBy(workerId());
                event.setClaimToken(claimToken);
                event.setClaimExpiresAt(claimExpiresAt);
            }
            return updated > 0;
        }

        PlatformEventOutboxEntity update = new PlatformEventOutboxEntity();
        update.setDispatchStatus(STATUS_DISPATCHING);
        update.setClaimedBy(workerId());
        update.setClaimToken(claimToken);
        update.setClaimExpiresAt(claimExpiresAt);
        update.setUpdatedAt(now);
        update.setUpdatedBy(trustedUserIdOrNull(event.getUpdatedBy()));
        update.setUpdatedByUuid(trustedUserUuidOrNull(event));

        int updated = platformEventOutboxMapper.update(update, new UpdateWrapper<PlatformEventOutboxEntity>()
                .eq("id", event.getId())
                .eq("deleted", 0)
                .eq("source_type", SYSTEM_SOURCE_TYPE)
                .eq("event_type", event.getEventType())
                .eq("event_key", event.getEventKey())
                .nested(wrapper -> {
                    if (event.getUserId() == null) {
                        wrapper.isNull("user_id").isNull("user_uuid");
                    } else {
                        wrapper.eq("user_id", event.getUserId()).eq("user_uuid", normalizeUserUuid(event.getUserUuid()));
                    }
                })
                .eq("dispatch_status", event.getDispatchStatus()));
        if (updated > 0) {
            event.setDispatchStatus(STATUS_DISPATCHING);
            event.setClaimedBy(workerId());
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

        if (queryOperations != null) {
            int updated = queryOperations.update(
                    SQL_MARK_DELIVERED,
                    STATUS_DELIVERED,
                    now,
                    now,
                    trustedUserIdOrNull(event.getUpdatedBy()),
                    trustedUserUuidOrNull(event),
                    SYSTEM_SOURCE_TYPE,
                    event.getId(),
                    STATUS_DISPATCHING,
                    event.getClaimToken(),
                    event.getEventType(),
                    event.getEventKey(),
                    event.getEventKey(),
                    event.getUserId(),
                    event.getUserId(),
                    normalizeUserUuid(event.getUserUuid()),
                    event.getRetryCount(),
                    event.getRetryCount()
            );
            requireOutboxWrite(updated, event, "markDelivered");
            return;
        }

        UpdateWrapper<PlatformEventOutboxEntity> wrapper = new UpdateWrapper<PlatformEventOutboxEntity>()
                .eq("id", event.getId())
                .eq("deleted", 0)
                .eq("source_type", SYSTEM_SOURCE_TYPE)
                .eq("dispatch_status", STATUS_DISPATCHING)
                .eq("claim_token", event.getClaimToken())
                .eq("event_type", event.getEventType())
                .set("dispatch_status", STATUS_DELIVERED)
                .set("delivered_at", now)
                .set("next_retry_at", null)
                .set("last_error", null)
                .set("updated_at", now)
                .set("updated_by", trustedUserIdOrNull(event.getUpdatedBy()))
                .set("updated_by_uuid", trustedUserUuidOrNull(event));
        applyEventKeyPredicate(wrapper, event);
        applyIdentityPredicate(wrapper, event);
        applyRetryCountPredicate(wrapper, event.getRetryCount());
        int updated = platformEventOutboxMapper.update(null, wrapper);
        requireOutboxWrite(updated, event, "markDelivered");
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
            int updated = queryOperations.update(
                    SQL_MARK_FAILED,
                    nextStatus,
                    nextRetryCount,
                    STATUS_DEAD_LETTER.equals(nextStatus) ? null : now.plusSeconds(calculateRetryDelaySeconds(nextRetryCount)),
                    truncateError(exception == null ? "unknown error" : exception.getMessage()),
                    now,
                    trustedUserIdOrNull(event.getUpdatedBy()),
                    trustedUserUuidOrNull(event),
                    SYSTEM_SOURCE_TYPE,
                    event.getId(),
                    STATUS_DISPATCHING,
                    event.getClaimToken(),
                    event.getEventType(),
                    event.getEventKey(),
                    event.getEventKey(),
                    event.getUserId(),
                    event.getUserId(),
                    normalizeUserUuid(event.getUserUuid()),
                    event.getRetryCount(),
                    event.getRetryCount()
            );
            requireOutboxWrite(updated, event, "markFailed");
            return;
        }

        UpdateWrapper<PlatformEventOutboxEntity> wrapper = new UpdateWrapper<PlatformEventOutboxEntity>()
                .eq("id", event.getId())
                .eq("deleted", 0)
                .eq("source_type", SYSTEM_SOURCE_TYPE)
                .eq("dispatch_status", STATUS_DISPATCHING)
                .eq("claim_token", event.getClaimToken())
                .eq("event_type", event.getEventType())
                .set("dispatch_status", nextStatus)
                .set("retry_count", nextRetryCount)
                .set("next_retry_at", STATUS_DEAD_LETTER.equals(nextStatus) ? null : now.plusSeconds(calculateRetryDelaySeconds(nextRetryCount)))
                .set("last_error", truncateError(exception == null ? "unknown error" : exception.getMessage()))
                .set("updated_at", now)
                .set("updated_by", trustedUserIdOrNull(event.getUpdatedBy()))
                .set("updated_by_uuid", trustedUserUuidOrNull(event));
        applyEventKeyPredicate(wrapper, event);
        applyIdentityPredicate(wrapper, event);
        applyRetryCountPredicate(wrapper, event.getRetryCount());
        int updated = platformEventOutboxMapper.update(null, wrapper);
        requireOutboxWrite(updated, event, "markFailed");
    }

    public int dispatchPending(PlatformEventDispatcher dispatcher, int limit) {
        if (dispatcher == null) {
            return 0;
        }

        int delivered = 0;
        for (PlatformEventOutboxEntity event : claimForDispatchBatch(limit)) {
            try {
                requireTrustedDispatchRow(event);
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

    public List<PlatformEventOutboxEntity> claimForDispatchBatch(int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit, 200));
        if (queryOperations == null) {
            List<PlatformEventOutboxEntity> events = listDispatchable(normalizedLimit);
            return events.stream().filter(this::claimForDispatch).toList();
        }
        LocalDateTime now = LocalDateTime.now();
        String claimToken = UUID.randomUUID().toString();
        LocalDateTime claimExpiresAt = now.plusMinutes(5);
        queryOperations.update(
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
                            t.claimed_by = ?,
                            t.claim_token = ?,
                            t.claim_expires_at = ?,
                            t.updated_at = ?
                        where t.deleted = 0 and t.source_type = ?
                        """,
                SYSTEM_SOURCE_TYPE,
                STATUS_RECORDED,
                STATUS_FAILED,
                now,
                STATUS_DISPATCHING,
                now,
                normalizedLimit,
                STATUS_DISPATCHING,
                workerId(),
                claimToken,
                claimExpiresAt,
                now,
                SYSTEM_SOURCE_TYPE
        );
        return queryOperations.query(
                """
                        select id, user_id as userId, user_uuid as userUuid, source_type as sourceType,
                               event_type as eventType, event_key as eventKey, payload_json as payloadJson,
                               dispatch_status as dispatchStatus, retry_count as retryCount,
                               next_retry_at as nextRetryAt, delivered_at as deliveredAt, last_error as lastError,
                               trace_id as traceId, request_id as requestId, created_by as createdBy,
                               created_by_uuid as createdByUuid, created_at as createdAt, updated_by as updatedBy,
                               updated_by_uuid as updatedByUuid, updated_at as updatedAt, deleted,
                               claimed_by as claimedBy, claim_token as claimToken, claim_expires_at as claimExpiresAt
                        from platform_event_outbox
                        where deleted = 0
                          and source_type = ?
                          and claim_token = ?
                        order by created_at asc, id asc
                        """,
                rowMapper,
                SYSTEM_SOURCE_TYPE,
                claimToken
        );
    }

    public int recoverStuckDispatchingEvents() {
        if (queryOperations == null) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        return queryOperations.update(
                """
                        update platform_event_outbox
                        set dispatch_status = ?, next_retry_at = ?, claim_token = null, claim_expires_at = null,
                            updated_at = ?
                        where deleted = 0
                          and source_type = ?
                          and dispatch_status = ?
                          and claim_expires_at is not null
                          and claim_expires_at <= ?
                          and retry_count < ?
                        """,
                STATUS_FAILED,
                now,
                now,
                SYSTEM_SOURCE_TYPE,
                STATUS_DISPATCHING,
                now,
                MAX_RETRY_COUNT
        );
    }

    public boolean replayById(Long eventId, PlatformEventDispatcher dispatcher) {
        PlatformEventOutboxEntity event = findById(eventId);
        if (event == null || dispatcher == null) {
            return false;
        }

        if (!resetForReplay(event)) {
            return false;
        }
        return dispatchSingle(event, dispatcher);
    }

    private boolean dispatchSingle(PlatformEventOutboxEntity event, PlatformEventDispatcher dispatcher) {
        if (!claimForDispatch(event)) {
            return false;
        }

        try {
            requireTrustedDispatchRow(event);
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

    private boolean resetForReplay(PlatformEventOutboxEntity event) {
        LocalDateTime now = LocalDateTime.now();
        String previousStatus = event.getDispatchStatus();
        Long trustedUpdatedBy = trustedUserIdOrNull(event.getUpdatedBy());
        String trustedUpdatedByUuid = trustedUserUuidOrNull(event);

        if (queryOperations != null) {
            int updated = queryOperations.update(
                    SQL_RESET_FOR_REPLAY,
                    STATUS_RECORDED,
                    now,
                    trustedUpdatedBy,
                    trustedUpdatedByUuid,
                    SYSTEM_SOURCE_TYPE,
                    event.getId(),
                    event.getEventType(),
                    event.getEventKey(),
                    previousStatus,
                    event.getUserId(),
                    event.getUserId(),
                    normalizeUserUuid(event.getUserUuid())
            );
            if (updated <= 0) {
                logClaimMismatchIfNeeded(updated, event, "resetForReplay");
                return false;
            }
            applyReplayReset(event, now, trustedUpdatedBy, trustedUpdatedByUuid);
            return true;
        }

        UpdateWrapper<PlatformEventOutboxEntity> wrapper = new UpdateWrapper<PlatformEventOutboxEntity>()
                .eq("id", event.getId())
                .eq("deleted", 0)
                .eq("source_type", SYSTEM_SOURCE_TYPE)
                .eq("event_type", event.getEventType())
                .eq("event_key", event.getEventKey())
                .eq("dispatch_status", previousStatus)
                .nested(boundary -> {
                    if (event.getUserId() == null) {
                        boundary.isNull("user_id").isNull("user_uuid");
                    } else {
                        boundary.eq("user_id", event.getUserId()).eq("user_uuid", normalizeUserUuid(event.getUserUuid()));
                    }
                })
                .set("dispatch_status", STATUS_RECORDED)
                .set("retry_count", 0)
                .set("next_retry_at", null)
                .set("delivered_at", null)
                .set("last_error", null)
                .set("claim_token", null)
                .set("claim_expires_at", null)
                .set("updated_at", now)
                .set("updated_by", trustedUpdatedBy)
                .set("updated_by_uuid", trustedUpdatedByUuid);
        int updated = platformEventOutboxMapper.update(null, wrapper);
        if (updated <= 0) {
            logClaimMismatchIfNeeded(updated, event, "resetForReplay");
            return false;
        }
        applyReplayReset(event, now, trustedUpdatedBy, trustedUpdatedByUuid);
        return true;
    }

    private void applyReplayReset(PlatformEventOutboxEntity event, LocalDateTime now, Long trustedUpdatedBy, String trustedUpdatedByUuid) {
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
    }

    private void applyEventKeyPredicate(UpdateWrapper<PlatformEventOutboxEntity> wrapper, PlatformEventOutboxEntity event) {
        if (event == null || event.getEventKey() == null) {
            wrapper.isNull("event_key");
            return;
        }
        wrapper.eq("event_key", event.getEventKey());
    }

    private void applyIdentityPredicate(UpdateWrapper<PlatformEventOutboxEntity> wrapper, PlatformEventOutboxEntity event) {
        if (event == null || event.getUserId() == null) {
            wrapper.isNull("user_id").isNull("user_uuid");
            return;
        }
        wrapper.eq("user_id", event.getUserId()).eq("user_uuid", normalizeUserUuid(event.getUserUuid()));
    }

    private void applyRetryCountPredicate(UpdateWrapper<PlatformEventOutboxEntity> wrapper, Integer retryCount) {
        if (retryCount == null) {
            wrapper.isNull("retry_count");
            return;
        }
        wrapper.eq("retry_count", retryCount);
    }

    private void logClaimMismatchIfNeeded(int updated, PlatformEventOutboxEntity event, String operation) {
        if (updated > 0) {
            return;
        }
        logger.warn("Platform event outbox claim mismatch operation={} id={} eventType={}",
                operation, event.getId(), event.getEventType());
    }

    private void requireOutboxWrite(int updated, PlatformEventOutboxEntity event, String operation) {
        if (updated > 0) {
            return;
        }
        logClaimMismatchIfNeeded(updated, event, operation);
        throw new IllegalStateException("Platform event outbox changed, please retry");
    }

    private String serialize(Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            PlatformEventTrustValidator.requireTrustedPayload(json);
            return json;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("平台事件 outbox payload 序列化失败", exception);
        }
    }

    private String normalizeEventType(String eventType) {
        return PlatformEventTrustValidator.requireTrustedEventType(eventType);
    }

    private String normalizeEventKey(String eventKey) {
        return PlatformEventTrustValidator.requireTrustedEventKey(eventKey);
    }

    private void requireTrustedDispatchRow(PlatformEventOutboxEntity event) {
        PlatformEventTrustValidator.requireTrustedSystemEvent(event);
        if (!STATUS_DISPATCHING.equals(event.getDispatchStatus()) || event.getClaimToken() == null || event.getClaimToken().isBlank()) {
            throw new IllegalArgumentException("platform event dispatch claim is required");
        }
        Integer retryCount = event.getRetryCount();
        if (retryCount != null && (retryCount < 0 || retryCount > MAX_RETRY_COUNT)) {
            throw new IllegalArgumentException("platform event retry count is invalid");
        }
    }

    private Object enrichPayload(Object payload, String userUuid) {
        if (payload instanceof Map<?, ?> map && userUuid != null && map.get("userUuid") == null) {
            Map<String, Object> enriched = new java.util.LinkedHashMap<>();
            map.forEach((key, value) -> enriched.put(String.valueOf(key), value));
            enriched.put("userUuid", userUuid);
            return enriched;
        }
        return payload;
    }

    private String resolveTrustedUserUuid(Long userId, String payloadUserUuid) {
        if (userId == null) {
            return null;
        }
        if (payloadUserUuid == null) {
            throw new IllegalArgumentException("platform event userUuid is required when userId is present");
        }
        String resolvedUserUuid = normalizeUserUuid(resolveActiveUserUuid(userId));
        if (resolvedUserUuid == null) {
            throw new IllegalArgumentException("platform event userUuid cannot be verified");
        }
        if (!resolvedUserUuid.equals(payloadUserUuid)) {
            throw new IllegalArgumentException("platform event userUuid does not match userId");
        }
        return resolvedUserUuid;
    }

    private String extractPayloadUserUuid(Object payload) {
        if (payload instanceof Map<?, ?> map) {
            String topLevel = normalizeUserUuid(map.get("userUuid"));
            if (topLevel != null) {
                return topLevel;
            }
            Object attributes = map.get("attributes");
            if (attributes instanceof Map<?, ?> attributesMap) {
                return normalizeUserUuid(attributesMap.get("userUuid"));
            }
            return null;
        }
        if (payload instanceof String json && !json.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(json);
                String topLevel = normalizeUserUuid(root.path("userUuid").asText(null));
                if (topLevel != null) {
                    return topLevel;
                }
                JsonNode attributes = root.path("attributes");
                if (attributes.isObject()) {
                    return normalizeUserUuid(attributes.path("userUuid").asText(null));
                }
            } catch (JsonProcessingException ignored) {
                return null;
            }
        }
        return null;
    }

    private String normalizeUserUuid(Object value) {
        if (value instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        return null;
    }

    private String resolveUserUuid(Long userId) {
        if (userId == null || queryOperations == null) {
            return null;
        }
        try {
            return queryOperations.queryForObject(
                    "select uuid from sys_user where id = ? and deleted = 0 limit 1",
                    String.class,
                    userId
            );
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String resolveActiveUserUuid(Long userId) {
        if (userId == null || queryOperations == null) {
            return null;
        }
        try {
            return queryOperations.queryForObject(
                    "select uuid from sys_user where id = ? and deleted = 0 and status = 'ENABLED' limit 1",
                    String.class,
                    userId
            );
        } catch (RuntimeException ignored) {
            return null;
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

    private String workerId() {
        return System.getProperty("lumira.worker.id", java.net.InetAddress.getLoopbackAddress().getHostName());
    }

    private void ensureSystemSource(String sourceType) {
        if (!SYSTEM_SOURCE_TYPE.equals(sourceType)) {
            throw new IllegalArgumentException("平台事件 outbox sourceType 必须为 SYSTEM");
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
