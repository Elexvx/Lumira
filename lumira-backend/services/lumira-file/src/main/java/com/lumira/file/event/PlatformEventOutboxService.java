package com.lumira.file.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.web.TraceContext;
import com.lumira.file.mapper.FilePlatformEventOutboxMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service("filePlatformEventOutboxService")
public class PlatformEventOutboxService {

    public static final String STATUS_RECORDED = "RECORDED";
    public static final String STATUS_DISPATCHING = "DISPATCHING";
    public static final String STATUS_DELIVERED = "DELIVERED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_DEAD_LETTER = "DEAD_LETTER";
    private static final Logger logger = LoggerFactory.getLogger(PlatformEventOutboxService.class);
    public static final int MAX_DISPATCH_LIMIT = 200;
    private static final int MAX_RETRY_COUNT = 8;
    private static final int MAX_RETRY_DELAY_SECONDS = 300;
    private static final int MAX_ERROR_LENGTH = 512;
    private static final int MAX_SOURCE_TYPE_LENGTH = 64;
    private static final int MAX_EVENT_TYPE_LENGTH = 128;
    private static final int MAX_EVENT_KEY_LENGTH = 128;
    private static final int MAX_PAYLOAD_JSON_LENGTH = 256 * 1024;
    private static final long SNAPSHOT_CACHE_TTL_MS = 15_000L;
    private static final long CLAIM_LEASE_MINUTES = 15L;
    private static final Pattern EVENT_TYPE_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_.-]{1,127}$");
    private static final Pattern EVENT_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9._:@/-]{1,128}$");

    private final ObjectMapper objectMapper;
    private final FilePlatformEventOutboxMapper platformEventOutboxMapper;
    private final JdbcTemplate jdbcTemplate;
    private final SystemInternalApi systemInternalApi;
    private volatile OutboxMetricsSnapshot cachedSnapshot;
    private volatile long cachedSnapshotUntilMillis;

    @Autowired
    public PlatformEventOutboxService(
            ObjectMapper objectMapper,
            FilePlatformEventOutboxMapper platformEventOutboxMapper,
            JdbcTemplate jdbcTemplate,
            SystemInternalApi systemInternalApi
    ) {
        this.objectMapper = objectMapper;
        this.platformEventOutboxMapper = platformEventOutboxMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.systemInternalApi = systemInternalApi;
    }

    public PlatformEventOutboxService(
            ObjectMapper objectMapper,
            FilePlatformEventOutboxMapper platformEventOutboxMapper,
            JdbcTemplate jdbcTemplate
    ) {
        this(objectMapper, platformEventOutboxMapper, jdbcTemplate, null);
    }

    public PlatformEventOutboxService(ObjectMapper objectMapper, FilePlatformEventOutboxMapper platformEventOutboxMapper) {
        this.objectMapper = objectMapper;
        this.platformEventOutboxMapper = platformEventOutboxMapper;
        this.jdbcTemplate = null;
        this.systemInternalApi = null;
    }

    public PlatformEventOutboxEntity record(String sourceType, String eventType, Long userId, String eventKey, Object payload) {
        LocalDateTime now = LocalDateTime.now();
        Long normalizedUserId = normalizeUserId(userId);
        String normalizedSourceType = requireFileSource(sourceType);
        String normalizedEventType = requireBoundedText(eventType, "eventType", MAX_EVENT_TYPE_LENGTH);
        String normalizedEventKey = requireBoundedText(eventKey, "eventKey", MAX_EVENT_KEY_LENGTH);
        String normalizedUserUuid = requireUserUuidWhenUserPresent(normalizedUserId, payload);
        String payloadJson = serialize(payload);
        if (payloadJson.length() > MAX_PAYLOAD_JSON_LENGTH) {
            throw new IllegalArgumentException("File outbox payload_json is too large");
        }
        PlatformEventOutboxEntity entity = new PlatformEventOutboxEntity();
        entity.setUserId(normalizedUserId);
        entity.setUserUuid(normalizedUserUuid);
        entity.setSourceType(normalizedSourceType);
        entity.setEventType(normalizedEventType);
        entity.setEventKey(normalizedEventKey);
        entity.setPayloadJson(payloadJson);
        entity.setDispatchStatus(STATUS_RECORDED);
        entity.setRetryCount(0);
        entity.setTraceId(TraceContext.getTraceId());
        entity.setRequestId(TraceContext.getRequestId());
        entity.setCreatedBy(normalizedUserId);
        entity.setCreatedByUuid(normalizedUserUuid);
        entity.setUpdatedBy(normalizedUserId);
        entity.setUpdatedByUuid(normalizedUserUuid);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setDeleted(0);
        int inserted = platformEventOutboxMapper.insert(entity);
        if (inserted != 1) {
            throw new IllegalStateException("File outbox changed, please retry");
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

    private String requireUserUuidWhenUserPresent(Long userId, Object payload) {
        if (userId == null) {
            return null;
        }
        String payloadUserUuid = extractPayloadUserUuid(payload);
        if (!StringUtils.hasText(payloadUserUuid)) {
            throw new IllegalArgumentException("userUuid must be present when userId is present");
        }
        return requireMatchingResolvedUserUuid(userId, payloadUserUuid);
    }

    private String extractPayloadUserUuid(Object payload) {
        JsonNode node = objectMapper.valueToTree(payload);
        String topLevel = textOrNull(node.path("userUuid"));
        if (topLevel != null) {
            return topLevel;
        }
        return textOrNull(node.path("attributes").path("userUuid"));
    }

    private String requireMatchingResolvedUserUuid(Long userId, String payloadUserUuid) {
        String resolvedUserUuid = resolveActiveUserUuid(userId);
        if (!StringUtils.hasText(resolvedUserUuid)) {
            throw new IllegalArgumentException("File outbox userUuid cannot be verified");
        }
        if (!resolvedUserUuid.trim().equals(payloadUserUuid.trim())) {
            throw new IllegalArgumentException("File outbox userUuid does not match userId");
        }
        return resolvedUserUuid.trim();
    }

    private String resolveUserUuid(Long userId) {
        if (userId == null || userId <= 0 || systemInternalApi == null) {
            return null;
        }
        try {
            SystemUserSnapshotDTO user = systemInternalApi.findUserIdentityById(userId);
            if (user == null || !userId.equals(user.userId()) || !StringUtils.hasText(user.userUuid())) {
                return null;
            }
            return user.userUuid().trim();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String resolveActiveUserUuid(Long userId) {
        if (userId == null || userId <= 0 || systemInternalApi == null) {
            return null;
        }
        try {
            String userUuid = systemInternalApi.findTargetUserUuidById(userId);
            return StringUtils.hasText(userUuid) ? userUuid.trim() : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private boolean hasText(JsonNode node) {
        return node != null && node.isTextual() && StringUtils.hasText(node.asText());
    }

    private String textOrNull(JsonNode node) {
        if (!hasText(node)) {
            return null;
        }
        return node.asText().trim();
    }

    private Long trustedUserIdOrNull(Long userId) {
        return userId == null || userId <= 0 ? null : userId;
    }

    private String trustedUserUuidOrNull(PlatformEventOutboxEntity row) {
        if (row == null || trustedUserIdOrNull(row.getUpdatedBy()) == null) {
            return null;
        }
        if (StringUtils.hasText(row.getUpdatedByUuid())) {
            return row.getUpdatedByUuid().trim();
        }
        return StringUtils.hasText(row.getUserUuid()) ? row.getUserUuid().trim() : null;
    }

    private String normalizeUserUuidOrNull(String userUuid) {
        return StringUtils.hasText(userUuid) ? userUuid.trim() : null;
    }

    public int dispatchPending(FileOutboxDispatcher dispatcher, int limit) {
        if (dispatcher == null || jdbcTemplate == null) {
            return 0;
        }
        validateDispatchLimit(limit);
        int delivered = 0;
        for (PlatformEventOutboxEntity row : claimForDispatchBatch(limit)) {
            try {
                requireTrustedDispatchRow(row);
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
        if (id == null || id <= 0 || dispatcher == null || jdbcTemplate == null) {
            return false;
        }
        PlatformEventOutboxEntity row = findById(id);
        if (row == null) {
            return false;
        }
        if (!resetForReplay(row)) {
            return false;
        }
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
                            t.updated_by = ?,
                            t.updated_by_uuid = null
                        where t.deleted = 0
                          and t.source_type = ?
                        """,
                FilePlatformEventTypes.SOURCE_FILE,
                STATUS_RECORDED,
                STATUS_FAILED,
                now,
                STATUS_DISPATCHING,
                now,
                limit,
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
                        select id, user_id as userId, user_uuid as userUuid, source_type as sourceType,
                               event_type as eventType, event_key as eventKey, payload_json as payloadJson,
                               dispatch_status as dispatchStatus, retry_count as retryCount,
                               next_retry_at as nextRetryAt, delivered_at as deliveredAt, last_error as lastError,
                               trace_id as traceId, request_id as requestId, created_by as createdBy,
                               created_by_uuid as createdByUuid,
                               claim_token as claimToken, claim_expires_at as claimExpiresAt,
                               created_at as createdAt, updated_by as updatedBy,
                               updated_by_uuid as updatedByUuid, updated_at as updatedAt, deleted
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
                            select id, user_id as userId, user_uuid as userUuid, source_type as sourceType,
                                   event_type as eventType, event_key as eventKey, payload_json as payloadJson,
                                   dispatch_status as dispatchStatus, retry_count as retryCount,
                                   next_retry_at as nextRetryAt, delivered_at as deliveredAt, last_error as lastError,
                                   trace_id as traceId, request_id as requestId, created_by as createdBy,
                                   created_by_uuid as createdByUuid,
                                   claim_token as claimToken, claim_expires_at as claimExpiresAt,
                                   created_at as createdAt, updated_by as updatedBy,
                                   updated_by_uuid as updatedByUuid, updated_at as updatedAt, deleted
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
                        set dispatch_status = ?, claim_token = ?, claim_expires_at = ?,
                            updated_at = ?, updated_by = ?, updated_by_uuid = ?
                        where id = ? and deleted = 0 and source_type = ?
                          and event_type = ?
                          and event_key = ?
                          and ((user_id is null and ? is null and user_uuid is null) or (user_id = ? and user_uuid = ?))
                          and ((retry_count is null and ? is null) or retry_count = ?)
                          and (
                                dispatch_status = ?
                                or (dispatch_status = ? and claim_expires_at is not null and claim_expires_at <= ?)
                          )
                """,
                STATUS_DISPATCHING,
                claimToken,
                claimExpiresAt,
                now,
                trustedUserIdOrNull(row.getUpdatedBy()),
                trustedUserUuidOrNull(row),
                row.getId(),
                FilePlatformEventTypes.SOURCE_FILE,
                row.getEventType(),
                row.getEventKey(),
                row.getUserId(),
                row.getUserId(),
                normalizeUserUuidOrNull(row.getUserUuid()),
                row.getRetryCount(),
                row.getRetryCount(),
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
                            claim_token = null, claim_expires_at = null,
                            updated_at = ?, updated_by = ?, updated_by_uuid = ?
                        where id = ? and deleted = 0 and source_type = ? and event_type = ? and event_key = ? and dispatch_status = ? and claim_token = ?
                          and ((user_id is null and ? is null and user_uuid is null) or (user_id = ? and user_uuid = ?))
                          and ((retry_count is null and ? is null) or retry_count = ?)
                        """,
                STATUS_DELIVERED,
                LocalDateTime.now(),
                LocalDateTime.now(),
                trustedUserIdOrNull(row.getUpdatedBy()),
                trustedUserUuidOrNull(row),
                row.getId(),
                FilePlatformEventTypes.SOURCE_FILE,
                row.getEventType(),
                row.getEventKey(),
                STATUS_DISPATCHING,
                row.getClaimToken(),
                row.getUserId(),
                row.getUserId(),
                normalizeUserUuidOrNull(row.getUserUuid()),
                row.getRetryCount(),
                row.getRetryCount()
        );
        logClaimMismatchIfNeeded(updated, row, "markDelivered");
        requireOutboxWrite(updated, "File outbox changed, please retry");
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
                            claim_token = null, claim_expires_at = null,
                            updated_at = ?, updated_by = ?, updated_by_uuid = ?
                        where id = ? and deleted = 0 and source_type = ? and event_type = ? and event_key = ? and dispatch_status = ? and claim_token = ?
                          and ((user_id is null and ? is null and user_uuid is null) or (user_id = ? and user_uuid = ?))
                          and ((retry_count is null and ? is null) or retry_count = ?)
                        """,
                deadLetter ? STATUS_DEAD_LETTER : STATUS_FAILED,
                nextRetryCount,
                deadLetter ? null : now.plusSeconds(calculateRetryDelaySeconds(nextRetryCount)),
                truncate(exception == null ? "unknown error" : exception.getMessage()),
                now,
                trustedUserIdOrNull(row.getUpdatedBy()),
                trustedUserUuidOrNull(row),
                row.getId(),
                FilePlatformEventTypes.SOURCE_FILE,
                row.getEventType(),
                row.getEventKey(),
                STATUS_DISPATCHING,
                row.getClaimToken(),
                row.getUserId(),
                row.getUserId(),
                normalizeUserUuidOrNull(row.getUserUuid()),
                row.getRetryCount(),
                row.getRetryCount()
        );
        logClaimMismatchIfNeeded(updated, row, "markFailed");
        requireOutboxWrite(updated, "File outbox changed, please retry");
    }

    private boolean resetForReplay(PlatformEventOutboxEntity row) {
        int updated = jdbcTemplate.update(
                """
                        update platform_event_outbox
                        set dispatch_status = ?, retry_count = 0, next_retry_at = null, last_error = null,
                            claim_token = null, claim_expires_at = null,
                            updated_at = ?, updated_by = ?, updated_by_uuid = ?
                        where id = ? and deleted = 0 and source_type = ?
                          and event_type = ?
                          and event_key = ?
                          and dispatch_status = ?
                          and ((user_id is null and ? is null and user_uuid is null) or (user_id = ? and user_uuid = ?))
                        """,
                STATUS_RECORDED,
                LocalDateTime.now(),
                trustedUserIdOrNull(row.getUpdatedBy()),
                trustedUserUuidOrNull(row),
                row.getId(),
                FilePlatformEventTypes.SOURCE_FILE,
                row.getEventType(),
                row.getEventKey(),
                row.getDispatchStatus(),
                row.getUserId(),
                row.getUserId(),
                normalizeUserUuidOrNull(row.getUserUuid())
        );
        if (updated <= 0) {
            logClaimMismatchIfNeeded(updated, row, "resetForReplay");
            return false;
        }
        row.setDispatchStatus(STATUS_RECORDED);
        row.setRetryCount(0);
        row.setNextRetryAt(null);
        row.setLastError(null);
        row.setClaimToken(null);
        row.setClaimExpiresAt(null);
        return true;
    }

    private boolean dispatchSingle(PlatformEventOutboxEntity row, FileOutboxDispatcher dispatcher) {
        if (row == null || dispatcher == null) {
            return false;
        }
        if (!claimForDispatch(row)) {
            return false;
        }

        try {
            requireTrustedDispatchRow(row);
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

    private void requireOutboxWrite(int updated, String message) {
        if (updated <= 0) {
            throw new IllegalStateException(message);
        }
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("文件事件 outbox payload 序列化失败", exception);
        }
    }

    private String requireFileSource(String sourceType) {
        String normalizedSourceType = requireBoundedText(sourceType, "sourceType", MAX_SOURCE_TYPE_LENGTH);
        if (!FilePlatformEventTypes.SOURCE_FILE.equals(normalizedSourceType)) {
            throw new IllegalArgumentException("File outbox sourceType must be FILE");
        }
        return normalizedSourceType;
    }

    private String requireBoundedText(String value, String fieldName, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("File outbox " + fieldName + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("File outbox " + fieldName + " is too long");
        }
        if ("eventType".equals(fieldName) && !EVENT_TYPE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("File outbox eventType is invalid");
        }
        if ("eventKey".equals(fieldName)
                && (!EVENT_KEY_PATTERN.matcher(normalized).matches()
                || normalized.contains("..")
                || normalized.contains("//"))) {
            throw new IllegalArgumentException("File outbox eventKey is invalid");
        }
        return normalized;
    }

    private void requireTrustedDispatchRow(PlatformEventOutboxEntity row) {
        if (!isTrustedDispatchRow(row)) {
            throw new IllegalArgumentException("File outbox row is invalid");
        }
    }

    private boolean isTrustedDispatchRow(PlatformEventOutboxEntity row) {
        if (row == null
                || row.getId() == null
                || row.getId() <= 0
                || !FilePlatformEventTypes.SOURCE_FILE.equals(row.getSourceType())
                || !STATUS_DISPATCHING.equals(row.getDispatchStatus())
                || !StringUtils.hasText(row.getClaimToken())) {
            return false;
        }
        try {
            requireBoundedText(row.getEventType(), "eventType", MAX_EVENT_TYPE_LENGTH);
            requireBoundedText(row.getEventKey(), "eventKey", MAX_EVENT_KEY_LENGTH);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        if (!StringUtils.hasText(row.getPayloadJson()) || row.getPayloadJson().length() > MAX_PAYLOAD_JSON_LENGTH) {
            return false;
        }
        if (row.getUserId() != null) {
            if (row.getUserId() <= 0 || !payloadJsonHasTrustedUserUuid(row.getUserId(), row.getUserUuid(), row.getPayloadJson())) {
                return false;
            }
        }
        Integer retryCount = row.getRetryCount();
        return retryCount == null || (retryCount >= 0 && retryCount <= MAX_RETRY_COUNT);
    }

    private boolean payloadJsonHasTrustedUserUuid(Long userId, String rowUserUuid, String payloadJson) {
        if (!StringUtils.hasText(rowUserUuid)) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(payloadJson);
            String payloadUserUuid = textOrNull(node.path("userUuid"));
            if (payloadUserUuid == null) {
                payloadUserUuid = textOrNull(node.path("attributes").path("userUuid"));
            }
            if (payloadUserUuid == null) {
                return false;
            }
            if (!rowUserUuid.trim().equals(payloadUserUuid.trim())) {
                return false;
            }
            String resolvedUserUuid = resolveUserUuid(userId);
            return StringUtils.hasText(resolvedUserUuid) && resolvedUserUuid.trim().equals(rowUserUuid.trim());
        } catch (JsonProcessingException exception) {
            return false;
        }
    }

    private void validateDispatchLimit(int limit) {
        if (limit < 1 || limit > MAX_DISPATCH_LIMIT) {
            throw new IllegalArgumentException("File outbox dispatch limit must be between 1 and " + MAX_DISPATCH_LIMIT);
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
