package com.lumira.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.auth.model.AuthSession;
import com.lumira.common.constant.CacheKeyConstants;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.web.redis.SessionPayloadCas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Service("authAuthSessionStore")
public class AuthSessionStore {

    private static final Logger log = LoggerFactory.getLogger(AuthSessionStore.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AtomicLong saves = new AtomicLong();
    private final AtomicLong removes = new AtomicLong();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong corruptPayloads = new AtomicLong();

    public AuthSessionStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void save(AuthSession session, boolean publishChange) {
        Duration ttl = session.getExpireTime() == null ? Duration.ZERO : Duration.between(Instant.now(), session.getExpireTime());
        save(session, ttl, publishChange);
    }

    public void save(AuthSession session, Duration ttl, boolean publishChange) {
        Long originalRevision = session == null ? null : session.getMutationRevision();
        boolean payloadCommitted = false;
        try {
            AuthSessionTrustValidator.requireTrustedSession(session);
            Duration effectiveTtl = ttl == null || ttl.isZero() || ttl.isNegative() ? Duration.ofSeconds(1) : ttl;
            long expectedRevision = SessionPayloadCas.expectedRevision(originalRevision);
            long nextRevision = SessionPayloadCas.nextRevision(originalRevision);
            session.setMutationRevision(nextRevision);
            String payload = objectMapper.writeValueAsString(session);
            AuthSessionTrustValidator.requireTrustedPayload(payload);
            SessionPayloadCas.Result saveResult = SessionPayloadCas.compareAndSet(
                    redisTemplate,
                    CacheKeyConstants.sessionKey(session.getSessionId()),
                    expectedRevision,
                    nextRevision,
                    payload,
                    effectiveTtl
            );
            if (saveResult != SessionPayloadCas.Result.SAVED) {
                if (saveResult == SessionPayloadCas.Result.INVALID_CURRENT_PAYLOAD) {
                    log.warn("Refusing to replace invalid session payload. sessionId={}", session.getSessionId());
                }
                throw new BizException(ErrorCode.SESSION_EXPIRED, "Session changed concurrently");
            }
            payloadCommitted = true;
            redisTemplate.opsForValue().set(CacheKeyConstants.sessionOwnerKey(session.getSessionId()), sessionOwnerValue(session), effectiveTtl);
            redisTemplate.opsForValue().set(CacheKeyConstants.userSessionKey(session.getUserId(), session.getUserUuid(), session.getSessionId()), "1", effectiveTtl);
            redisTemplate.opsForZSet().add(CacheKeyConstants.onlineSessionUserKey(session.getUserId(), session.getUserUuid()), session.getSessionId(), score(session));
            redisTemplate.opsForZSet().add(CacheKeyConstants.onlineSessionKey(), session.getSessionId(), score(session));
            saves.incrementAndGet();
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "会话序列化失败: " + ex.getMessage());
        } finally {
            if (!payloadCommitted && session != null) {
                session.setMutationRevision(originalRevision);
            }
        }
    }

    public Optional<AuthSession> findBySessionId(String sessionId) {
        String normalizedSessionId = AuthSessionTrustValidator.trustedSessionIdOrNull(sessionId);
        if (normalizedSessionId == null) {
            misses.incrementAndGet();
            return Optional.empty();
        }
        String payload = redisTemplate.opsForValue().get(CacheKeyConstants.sessionKey(normalizedSessionId));
        if (!StringUtils.hasText(payload)) {
            misses.incrementAndGet();
            return Optional.empty();
        }
        try {
            AuthSessionTrustValidator.requireTrustedPayload(payload);
        } catch (IllegalArgumentException exception) {
            corruptPayloads.incrementAndGet();
            removeSessionReferences(normalizedSessionId);
            return Optional.empty();
        }
        try {
            hits.incrementAndGet();
            AuthSession session = deserializeSession(payload);
            AuthSessionTrustValidator.requireTrustedSession(session);
            return Optional.of(session);
        } catch (Exception ex) {
            corruptPayloads.incrementAndGet();
            log.warn(
                    "Session payload is corrupted, removing stale session cache. sessionId={}, reason={}",
                    normalizedSessionId,
                    ex.getMessage()
            );
            removeSessionReferences(normalizedSessionId);
            return Optional.empty();
        }
    }

    private AuthSession deserializeSession(String payload) throws JsonProcessingException {
        AuthSession session = objectMapper.readValue(payload, AuthSession.class);
        session.setMutationRevision(SessionPayloadCas.normalizeLoadedRevision(session.getMutationRevision()));
        return session;
    }

    public void remove(AuthSession session, boolean publishChange) {
        try {
            AuthSessionTrustValidator.requireTrustedSession(session);
        } catch (IllegalArgumentException exception) {
            return;
        }
        redisTemplate.delete(CacheKeyConstants.sessionKey(session.getSessionId()));
        removeSessionIndexes(session);
    }

    public boolean removeIfUnchanged(AuthSession session, boolean publishChange) {
        try {
            AuthSessionTrustValidator.requireTrustedSession(session);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        if (session.getMutationRevision() == null) {
            return false;
        }
        SessionPayloadCas.DeleteResult deleteResult = SessionPayloadCas.compareAndDelete(
                redisTemplate,
                CacheKeyConstants.sessionKey(session.getSessionId()),
                session.getMutationRevision()
        );
        if (deleteResult != SessionPayloadCas.DeleteResult.DELETED) {
            if (deleteResult == SessionPayloadCas.DeleteResult.INVALID_CURRENT_PAYLOAD) {
                log.warn("Refusing conditional cleanup of invalid session payload. sessionId={}", session.getSessionId());
            }
            return false;
        }
        removeSessionIndexes(session);
        return true;
    }

    private void removeSessionIndexes(AuthSession session) {
        redisTemplate.delete(CacheKeyConstants.sessionOwnerKey(session.getSessionId()));
        redisTemplate.delete(CacheKeyConstants.userSessionKey(session.getUserId(), session.getUserUuid(), session.getSessionId()));
        redisTemplate.delete(CacheKeyConstants.userSessionKey(session.getUserId(), session.getSessionId()));
        redisTemplate.opsForZSet().remove(CacheKeyConstants.onlineSessionUserKey(session.getUserId(), session.getUserUuid()), session.getSessionId());
        redisTemplate.opsForZSet().remove(CacheKeyConstants.onlineSessionUserKey(session.getUserId()), session.getSessionId());
        redisTemplate.opsForZSet().remove(CacheKeyConstants.onlineSessionKey(), session.getSessionId());
        removes.incrementAndGet();
    }

    public void revokeUserSessions(Long userId, String userUuid, boolean publishChange) {
        if (userId == null || !StringUtils.hasText(userUuid)) {
            return;
        }
        cleanupExpiredUserIndex(userId, userUuid);
        for (String sessionId : listActiveUserSessionIds(userId, userUuid)) {
            findBySessionId(sessionId)
                    .filter(session -> belongsToUser(session, userId, userUuid))
                    .ifPresent(session -> remove(session, publishChange));
        }
    }

    public List<String> listActiveUserSessionIds(Long userId, String userUuid) {
        if (userId == null || !StringUtils.hasText(userUuid)) {
            return List.of();
        }
        cleanupExpiredUserIndex(userId, userUuid);
        Set<String> values = redisTemplate.opsForZSet().reverseRange(onlineSessionUserKey(userId, userUuid), 0, -1);
        if (CollectionUtils.isEmpty(values)) {
            return List.of();
        }
        return values.stream()
                .filter(sessionId -> belongsToUser(sessionId, userId, userUuid))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    public List<String> listActiveSessionIds(long start, long end) {
        cleanupExpiredSessionIndex();
        Set<String> values = redisTemplate.opsForZSet().reverseRange(CacheKeyConstants.onlineSessionKey(), start, end);
        if (CollectionUtils.isEmpty(values)) {
            return List.of();
        }
        return new ArrayList<>(values);
    }

    public Optional<String> findLatestActiveUserSessionId(Long userId, String userUuid) {
        if (userId == null || !StringUtils.hasText(userUuid)) {
            return Optional.empty();
        }
        String key = onlineSessionUserKey(userId, userUuid);
        cleanupExpiredUserIndex(userId, userUuid);
        while (true) {
            Set<String> values = redisTemplate.opsForZSet().reverseRange(key, 0, 0);
            if (CollectionUtils.isEmpty(values)) {
                return Optional.empty();
            }
            String sessionId = values.iterator().next();
            String normalizedSessionId = AuthSessionTrustValidator.trustedSessionIdOrNull(sessionId);
            if (normalizedSessionId == null) {
                if (sessionId != null) {
                    redisTemplate.opsForZSet().remove(key, sessionId);
                }
                continue;
            }
            if (findBySessionId(normalizedSessionId)
                    .filter(session -> belongsToUser(session, userId, userUuid))
                    .isPresent()) {
                return Optional.of(normalizedSessionId);
            }
            redisTemplate.opsForZSet().remove(key, normalizedSessionId);
        }
    }

    private void cleanupExpiredUserIndex(Long userId) {
        cleanupExpiredUserIndex(userId, null);
    }

    private void cleanupExpiredUserIndex(Long userId, String userUuid) {
        if (userId == null || !StringUtils.hasText(userUuid)) {
            return;
        }
        redisTemplate.opsForZSet().removeRangeByScore(
                onlineSessionUserKey(userId, userUuid),
                Double.NEGATIVE_INFINITY,
                Instant.now().toEpochMilli()
        );
    }

    public void removeSessionReferences(String sessionId) {
        String normalizedSessionId = AuthSessionTrustValidator.trustedSessionIdOrNull(sessionId);
        if (normalizedSessionId == null) {
            return;
        }
        redisTemplate.delete(CacheKeyConstants.sessionKey(normalizedSessionId));
        String owner = redisTemplate.opsForValue().get(CacheKeyConstants.sessionOwnerKey(normalizedSessionId));
        redisTemplate.delete(CacheKeyConstants.sessionOwnerKey(normalizedSessionId));
        if (!StringUtils.hasText(owner)) {
            return;
        }
        SessionOwner sessionOwner = parseSessionOwner(owner);
        if (sessionOwner == null || sessionOwner.userId() == null) {
            return;
        }
        if (StringUtils.hasText(sessionOwner.userUuid())) {
            redisTemplate.delete(CacheKeyConstants.userSessionKey(sessionOwner.userId(), sessionOwner.userUuid(), normalizedSessionId));
            redisTemplate.opsForZSet().remove(CacheKeyConstants.onlineSessionUserKey(sessionOwner.userId(), sessionOwner.userUuid()), normalizedSessionId);
        }
        redisTemplate.delete(CacheKeyConstants.userSessionKey(sessionOwner.userId(), normalizedSessionId));
        redisTemplate.opsForZSet().remove(CacheKeyConstants.onlineSessionUserKey(sessionOwner.userId()), normalizedSessionId);
        redisTemplate.opsForZSet().remove(CacheKeyConstants.onlineSessionKey(), normalizedSessionId);
    }

    private double score(AuthSession session) {
        return session.getExpireTime() == null ? Instant.now().toEpochMilli() : session.getExpireTime().toEpochMilli();
    }

    private String sessionOwnerValue(AuthSession session) {
        Long userId = session.getUserId();
        return userId == null ? "" : userId + "|" + session.getUserUuid();
    }

    private void cleanupExpiredSessionIndex() {
        redisTemplate.opsForZSet().removeRangeByScore(
                CacheKeyConstants.onlineSessionKey(),
                Double.NEGATIVE_INFINITY,
                Instant.now().toEpochMilli()
        );
    }

    private SessionOwner parseSessionOwner(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String[] parts = value.split("\\|", 2);
        Long userId = parseLong(parts.length > 0 ? parts[0] : null);
        String userUuid = parts.length > 1 && StringUtils.hasText(parts[1]) ? parts[1].trim() : null;
        return new SessionOwner(userId, userUuid);
    }

    private String onlineSessionUserKey(Long userId, String userUuid) {
        if (!StringUtils.hasText(userUuid)) {
            throw new IllegalArgumentException("Full user identity is required");
        }
        return CacheKeyConstants.onlineSessionUserKey(userId, userUuid.trim());
    }

    private boolean belongsToUser(String sessionId, Long userId, String userUuid) {
        return findBySessionId(sessionId)
                .filter(session -> belongsToUser(session, userId, userUuid))
                .isPresent();
    }

    private boolean belongsToUser(AuthSession session, Long userId, String userUuid) {
        if (session == null || userId == null || !userId.equals(session.getUserId())) {
            return false;
        }
        return StringUtils.hasText(userUuid) && userUuid.trim().equals(session.getUserUuid());
    }

    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record SessionOwner(Long userId, String userUuid) {
    }

    public long saves() {
        return saves.get();
    }

    public long removes() {
        return removes.get();
    }

    public long hits() {
        return hits.get();
    }

    public long misses() {
        return misses.get();
    }

    public long corruptPayloads() {
        return corruptPayloads.get();
    }

    public double hitRatio() {
        long hitCount = hits.get();
        long total = hitCount + misses.get();
        return total == 0 ? 0.0 : (double) hitCount / total;
    }
}
