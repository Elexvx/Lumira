package com.lumira.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.auth.model.AuthSession;
import com.lumira.common.constant.CacheKeyConstants;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
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
        Duration effectiveTtl = ttl == null || ttl.isZero() || ttl.isNegative() ? Duration.ofSeconds(1) : ttl;
        try {
            String payload = objectMapper.writeValueAsString(session);
            redisTemplate.opsForValue().set(CacheKeyConstants.sessionKey(session.getSessionId()), payload, effectiveTtl);
            redisTemplate.opsForValue().set(CacheKeyConstants.sessionOwnerKey(session.getSessionId()), sessionOwnerValue(session), effectiveTtl);
            redisTemplate.opsForValue().set(CacheKeyConstants.userSessionKey(session.getUserId(), session.getSessionId()), "1", effectiveTtl);
            redisTemplate.opsForZSet().add(CacheKeyConstants.onlineSessionUserKey(session.getUserId()), session.getSessionId(), score(session));
            if (session.getCurrentTenantId() != null) {
                redisTemplate.opsForZSet().add(CacheKeyConstants.onlineSessionTenantKey(session.getCurrentTenantId()), session.getSessionId(), score(session));
            }
            saves.incrementAndGet();
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "会话序列化失败: " + ex.getMessage());
        }
    }

    public Optional<AuthSession> findBySessionId(String sessionId) {
        String payload = redisTemplate.opsForValue().get(CacheKeyConstants.sessionKey(sessionId));
        if (!StringUtils.hasText(payload)) {
            misses.incrementAndGet();
            return Optional.empty();
        }
        try {
            hits.incrementAndGet();
            return Optional.of(objectMapper.readValue(payload, AuthSession.class));
        } catch (Exception ex) {
            corruptPayloads.incrementAndGet();
            log.warn(
                    "Session payload is corrupted, removing stale session cache. sessionId={}, reason={}",
                    sessionId,
                    ex.getMessage()
            );
            removeSessionReferences(sessionId);
            return Optional.empty();
        }
    }

    public void remove(AuthSession session, boolean publishChange) {
        redisTemplate.delete(CacheKeyConstants.sessionKey(session.getSessionId()));
        redisTemplate.delete(CacheKeyConstants.sessionOwnerKey(session.getSessionId()));
        redisTemplate.delete(CacheKeyConstants.userSessionKey(session.getUserId(), session.getSessionId()));
        redisTemplate.opsForZSet().remove(CacheKeyConstants.onlineSessionUserKey(session.getUserId()), session.getSessionId());
        if (session.getCurrentTenantId() != null) {
            redisTemplate.opsForZSet().remove(CacheKeyConstants.onlineSessionTenantKey(session.getCurrentTenantId()), session.getSessionId());
        }
        removes.incrementAndGet();
    }

    public void revokeUserSessions(Long userId, boolean publishChange) {
        cleanupExpiredUserIndex(userId);
        for (String sessionId : listActiveUserSessionIds(userId)) {
            findBySessionId(sessionId).ifPresent(session -> remove(session, publishChange));
        }
    }

    public List<String> listActiveUserSessionIds(Long userId) {
        if (userId == null) {
            return List.of();
        }
        cleanupExpiredUserIndex(userId);
        Set<String> values = redisTemplate.opsForZSet().reverseRange(CacheKeyConstants.onlineSessionUserKey(userId), 0, -1);
        if (CollectionUtils.isEmpty(values)) {
            return List.of();
        }
        return new ArrayList<>(values);
    }

    public List<String> listActiveTenantSessionIds(Long tenantId, long start, long end) {
        if (tenantId == null) {
            return List.of();
        }
        Set<String> values = redisTemplate.opsForZSet().reverseRange(CacheKeyConstants.onlineSessionTenantKey(tenantId), start, end);
        if (CollectionUtils.isEmpty(values)) {
            return List.of();
        }
        return new ArrayList<>(values);
    }

    public Optional<String> findLatestActiveUserSessionId(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        String key = CacheKeyConstants.onlineSessionUserKey(userId);
        cleanupExpiredUserIndex(userId);
        while (true) {
            Set<String> values = redisTemplate.opsForZSet().reverseRange(key, 0, 0);
            if (CollectionUtils.isEmpty(values)) {
                return Optional.empty();
            }
            String sessionId = values.iterator().next();
            if (!StringUtils.hasText(sessionId)) {
                if (sessionId != null) {
                    redisTemplate.opsForZSet().remove(key, sessionId);
                }
                continue;
            }
            if (findBySessionId(sessionId).isPresent()) {
                return Optional.of(sessionId);
            }
            redisTemplate.opsForZSet().remove(key, sessionId);
        }
    }

    private void cleanupExpiredUserIndex(Long userId) {
        if (userId == null) {
            return;
        }
        redisTemplate.opsForZSet().removeRangeByScore(
                CacheKeyConstants.onlineSessionUserKey(userId),
                Double.NEGATIVE_INFINITY,
                Instant.now().toEpochMilli()
        );
    }

    public void removeSessionReferences(String sessionId) {
        redisTemplate.delete(CacheKeyConstants.sessionKey(sessionId));
        String owner = redisTemplate.opsForValue().get(CacheKeyConstants.sessionOwnerKey(sessionId));
        redisTemplate.delete(CacheKeyConstants.sessionOwnerKey(sessionId));
        if (!StringUtils.hasText(owner)) {
            return;
        }
        SessionOwner sessionOwner = parseSessionOwner(owner);
        if (sessionOwner == null || sessionOwner.userId() == null) {
            return;
        }
        redisTemplate.delete(CacheKeyConstants.userSessionKey(sessionOwner.userId(), sessionId));
        redisTemplate.opsForZSet().remove(CacheKeyConstants.onlineSessionUserKey(sessionOwner.userId()), sessionId);
        if (sessionOwner.tenantId() != null) {
            redisTemplate.opsForZSet().remove(CacheKeyConstants.onlineSessionTenantKey(sessionOwner.tenantId()), sessionId);
        }
    }

    public void removeTenantSessionReference(Long tenantId, String sessionId) {
        if (tenantId == null || !StringUtils.hasText(sessionId)) {
            return;
        }
        redisTemplate.opsForZSet().remove(CacheKeyConstants.onlineSessionTenantKey(tenantId), sessionId);
    }

    private double score(AuthSession session) {
        return session.getExpireTime() == null ? Instant.now().toEpochMilli() : session.getExpireTime().toEpochMilli();
    }

    private String sessionOwnerValue(AuthSession session) {
        Long userId = session.getUserId();
        Long tenantId = session.getCurrentTenantId();
        return (userId == null ? "" : userId) + "|" + (tenantId == null ? "" : tenantId);
    }

    private SessionOwner parseSessionOwner(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String[] parts = value.split("\\|", 2);
        Long userId = parseLong(parts.length > 0 ? parts[0] : null);
        Long tenantId = parseLong(parts.length > 1 ? parts[1] : null);
        return new SessionOwner(userId, tenantId);
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

    private record SessionOwner(Long userId, Long tenantId) {
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
