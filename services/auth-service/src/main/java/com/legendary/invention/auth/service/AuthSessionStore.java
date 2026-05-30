package com.legendary.invention.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legendary.invention.auth.model.AuthSession;
import com.legendary.invention.common.constant.CacheKeyConstants;
import com.legendary.invention.common.enums.ErrorCode;
import com.legendary.invention.common.exception.BizException;
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

@Service("authAuthSessionStore")
public class AuthSessionStore {

    private static final Logger log = LoggerFactory.getLogger(AuthSessionStore.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

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
            redisTemplate.opsForValue().set(CacheKeyConstants.userSessionKey(session.getUserId(), session.getSessionId()), "1", effectiveTtl);
            redisTemplate.opsForZSet().add(CacheKeyConstants.onlineSessionUserKey(session.getUserId()), session.getSessionId(), score(session));
            if (session.getCurrentTenantId() != null) {
                redisTemplate.opsForZSet().add(CacheKeyConstants.onlineSessionTenantKey(session.getCurrentTenantId()), session.getSessionId(), score(session));
            }
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "会话序列化失败: " + ex.getMessage());
        }
    }

    public Optional<AuthSession> findBySessionId(String sessionId) {
        String payload = redisTemplate.opsForValue().get(CacheKeyConstants.sessionKey(sessionId));
        if (!StringUtils.hasText(payload)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(payload, AuthSession.class));
        } catch (Exception ex) {
            log.warn(
                    "Session payload is corrupted, removing stale session cache. sessionId={}, reason={}",
                    sessionId,
                    ex.getMessage()
            );
            removeSessionReferences(sessionId);
            removeSessionFromOnlineIndexes(sessionId);
            return Optional.empty();
        }
    }

    public void remove(AuthSession session, boolean publishChange) {
        redisTemplate.delete(CacheKeyConstants.sessionKey(session.getSessionId()));
        redisTemplate.delete(CacheKeyConstants.userSessionKey(session.getUserId(), session.getSessionId()));
        redisTemplate.opsForZSet().remove(CacheKeyConstants.onlineSessionUserKey(session.getUserId()), session.getSessionId());
        if (session.getCurrentTenantId() != null) {
            redisTemplate.opsForZSet().remove(CacheKeyConstants.onlineSessionTenantKey(session.getCurrentTenantId()), session.getSessionId());
        }
    }

    public void revokeUserSessions(Long userId, boolean publishChange) {
        for (String sessionId : listActiveUserSessionIds(userId)) {
            findBySessionId(sessionId).ifPresent(session -> remove(session, publishChange));
        }
    }

    public List<String> listActiveUserSessionIds(Long userId) {
        if (userId == null) {
            return List.of();
        }
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
        List<String> sessionIds = listActiveUserSessionIds(userId);
        if (sessionIds.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(sessionIds.get(0));
    }

    public void removeSessionReferences(String sessionId) {
        redisTemplate.delete(CacheKeyConstants.sessionKey(sessionId));
        Set<String> keys = redisTemplate.keys(CacheKeyConstants.PREFIX + ":" + CacheKeyConstants.SESSION_USER + ":*:" + sessionId);
        if (CollectionUtils.isEmpty(keys)) {
            return;
        }
        for (String key : keys) {
            redisTemplate.delete(key);
        }
    }

    public void removeTenantSessionReference(Long tenantId, String sessionId) {
        if (tenantId == null || !StringUtils.hasText(sessionId)) {
            return;
        }
        redisTemplate.opsForZSet().remove(CacheKeyConstants.onlineSessionTenantKey(tenantId), sessionId);
    }

    private void removeSessionFromOnlineIndexes(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        Set<String> userIndexKeys = redisTemplate.keys(CacheKeyConstants.PREFIX + ":" + CacheKeyConstants.ONLINE_SESSION_USER + ":*");
        if (!CollectionUtils.isEmpty(userIndexKeys)) {
            for (String key : userIndexKeys) {
                redisTemplate.opsForZSet().remove(key, sessionId);
            }
        }
        Set<String> tenantIndexKeys = redisTemplate.keys(CacheKeyConstants.PREFIX + ":" + CacheKeyConstants.ONLINE_SESSION_TENANT + ":*");
        if (!CollectionUtils.isEmpty(tenantIndexKeys)) {
            for (String key : tenantIndexKeys) {
                redisTemplate.opsForZSet().remove(key, sessionId);
            }
        }
    }

    private double score(AuthSession session) {
        return session.getExpireTime() == null ? Instant.now().toEpochMilli() : session.getExpireTime().toEpochMilli();
    }
}
