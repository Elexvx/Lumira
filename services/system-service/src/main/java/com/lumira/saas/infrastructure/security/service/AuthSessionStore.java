package com.lumira.saas.infrastructure.security.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.saas.common.constant.CacheKeyConstants;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.infrastructure.redis.CacheTemplate;
import com.lumira.saas.infrastructure.security.model.AuthSession;
import com.lumira.saas.modules.system.online.OnlineSessionEvent;
import com.lumira.saas.modules.system.online.OnlineSessionEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Objects;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class AuthSessionStore {

    private static final Logger log = LoggerFactory.getLogger(AuthSessionStore.class);

    private final CacheTemplate cacheTemplate;
    private final ObjectMapper objectMapper;
    private final OnlineSessionEventPublisher onlineSessionEventPublisher;
    private final AtomicLong saves = new AtomicLong();
    private final AtomicLong removes = new AtomicLong();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong corruptPayloads = new AtomicLong();

    public AuthSessionStore(CacheTemplate cacheTemplate, ObjectMapper objectMapper, OnlineSessionEventPublisher onlineSessionEventPublisher) {
        this.cacheTemplate = cacheTemplate;
        this.objectMapper = objectMapper;
        this.onlineSessionEventPublisher = onlineSessionEventPublisher;
    }

    public void save(AuthSession session, Duration ttl) {
        save(session, ttl, false);
    }

    public void save(AuthSession session, boolean publishChange) {
        if (session.getExpireTime() == null) {
            save(session, Duration.ZERO, publishChange);
            return;
        }
        Duration ttl = Duration.between(Instant.now(), session.getExpireTime());
        save(session, ttl.isNegative() ? Duration.ZERO : ttl, publishChange);
    }

    public void save(AuthSession session, Duration ttl, boolean publishChange) {
        AuthSession previousSession = publishChange ? findBySessionId(session.getSessionId()).orElse(null) : null;
        try {
            Duration effectiveTtl = ttl == null || ttl.isZero() || ttl.isNegative()
                    ? Duration.ofSeconds(1)
                    : ttl;
            cacheTemplate.put(CacheKeyConstants.sessionKey(session.getSessionId()), objectMapper.writeValueAsString(session), effectiveTtl);
            cacheTemplate.put(CacheKeyConstants.userSessionKey(session.getUserId(), session.getSessionId()), "1", effectiveTtl);
            cacheTemplate.addToSortedSet(CacheKeyConstants.onlineSessionUserKey(session.getUserId()), session.getSessionId(), toScore(session));
            if (session.getCurrentTenantId() != null) {
                cacheTemplate.addToSortedSet(CacheKeyConstants.onlineSessionTenantKey(session.getCurrentTenantId()), session.getSessionId(), toScore(session));
            }
            cacheLatestSessionId(session, effectiveTtl);
            if (previousSession != null
                    && previousSession.getCurrentTenantId() != null
                    && !previousSession.getCurrentTenantId().equals(session.getCurrentTenantId())) {
                cacheTemplate.removeFromSortedSet(CacheKeyConstants.onlineSessionTenantKey(previousSession.getCurrentTenantId()), session.getSessionId());
            }
            if (publishChange) {
                if (previousSession != null
                        && previousSession.getCurrentTenantId() != null
                        && !Objects.equals(previousSession.getCurrentTenantId(), session.getCurrentTenantId())) {
                    publishEvent(OnlineSessionEvent.ACTION_REMOVED, previousSession);
                }
                publishEvent(OnlineSessionEvent.ACTION_UPSERT, session);
            }
            saves.incrementAndGet();
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "会话序列化失败");
        }
    }

    public void save(AuthSession session) {
        save(session, false);
    }

    public Optional<AuthSession> findBySessionId(String sessionId) {
        String payload = cacheTemplate.get(CacheKeyConstants.sessionKey(sessionId));
        if (!StringUtils.hasText(payload)) {
            misses.incrementAndGet();
            return Optional.empty();
        }

        try {
            hits.incrementAndGet();
            return Optional.of(objectMapper.readValue(payload, AuthSession.class));
        } catch (JsonProcessingException ex) {
            corruptPayloads.incrementAndGet();
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

    public Map<String, AuthSession> findBySessionIds(List<String> sessionIds) {
        if (CollectionUtils.isEmpty(sessionIds)) {
            return Map.of();
        }

        List<String> distinctSessionIds = sessionIds.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (distinctSessionIds.isEmpty()) {
            return Map.of();
        }

        List<String> payloads = cacheTemplate.multiGet(distinctSessionIds.stream()
                .map(CacheKeyConstants::sessionKey)
                .toList());
        Map<String, AuthSession> sessions = new LinkedHashMap<>();
        for (int i = 0; i < distinctSessionIds.size(); i++) {
            String sessionId = distinctSessionIds.get(i);
            String payload = i < payloads.size() ? payloads.get(i) : null;
            if (!StringUtils.hasText(payload)) {
                misses.incrementAndGet();
                continue;
            }
            try {
                hits.incrementAndGet();
                sessions.put(sessionId, objectMapper.readValue(payload, AuthSession.class));
            } catch (JsonProcessingException ex) {
                corruptPayloads.incrementAndGet();
                log.warn(
                        "Session payload is corrupted during batch lookup, removing stale session cache. sessionId={}, reason={}",
                        sessionId,
                        ex.getMessage()
                );
                removeSessionReferences(sessionId);
                removeSessionFromOnlineIndexes(sessionId);
            }
        }
        return sessions;
    }

    public void remove(AuthSession session) {
        remove(session, false);
    }

    public void remove(AuthSession session, boolean publishChange) {
        cacheTemplate.remove(CacheKeyConstants.sessionKey(session.getSessionId()));
        cacheTemplate.remove(CacheKeyConstants.userSessionKey(session.getUserId(), session.getSessionId()));
        cacheTemplate.removeFromSortedSet(CacheKeyConstants.onlineSessionUserKey(session.getUserId()), session.getSessionId());
        if (session.getCurrentTenantId() != null) {
            cacheTemplate.removeFromSortedSet(CacheKeyConstants.onlineSessionTenantKey(session.getCurrentTenantId()), session.getSessionId());
        }
        clearLatestSessionIdIfMatch(session);
        removes.incrementAndGet();
        if (publishChange) {
            publishEvent(OnlineSessionEvent.ACTION_REMOVED, session);
        }
    }

    public List<String> listActiveTenantSessionIds(Long tenantId, long start, long end) {
        if (tenantId == null) {
            return List.of();
        }
        cleanupExpiredTenantIndex(tenantId);
        Set<String> sessionIds = cacheTemplate.reverseRange(CacheKeyConstants.onlineSessionTenantKey(tenantId), start, end);
        if (CollectionUtils.isEmpty(sessionIds)) {
            return List.of();
        }
        return new ArrayList<>(sessionIds);
    }

    public long countActiveTenantSessions(Long tenantId) {
        if (tenantId == null) {
            return 0L;
        }
        cleanupExpiredTenantIndex(tenantId);
        Long count = cacheTemplate.sortedSetSize(CacheKeyConstants.onlineSessionTenantKey(tenantId));
        return count == null ? 0L : count;
    }

    public List<String> listActiveUserSessionIds(Long userId) {
        if (userId == null) {
            return List.of();
        }
        cleanupExpiredUserIndex(userId);
        Set<String> sessionIds = cacheTemplate.reverseRange(CacheKeyConstants.onlineSessionUserKey(userId), 0, -1);
        if (CollectionUtils.isEmpty(sessionIds)) {
            return List.of();
        }
        return new ArrayList<>(sessionIds);
    }

    public Optional<String> findLatestActiveUserSessionId(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        String latestCacheKey = CacheKeyConstants.onlineSessionLatestUserKey(userId);
        String latestSessionId = cacheTemplate.get(latestCacheKey);
        if (StringUtils.hasText(latestSessionId)) {
            Optional<AuthSession> cachedLatestSession = findBySessionId(latestSessionId);
            if (cachedLatestSession.isPresent()) {
                return Optional.of(latestSessionId);
            }
            cacheTemplate.remove(latestCacheKey);
        }

        String key = CacheKeyConstants.onlineSessionUserKey(userId);
        cleanupExpiredUserIndex(userId);
        while (true) {
            Set<String> sessionIds = cacheTemplate.reverseRange(key, 0, 0);
            if (CollectionUtils.isEmpty(sessionIds)) {
                return Optional.empty();
            }
            String sessionId = sessionIds.iterator().next();
            if (!StringUtils.hasText(sessionId)) {
                if (sessionId != null) {
                    cacheTemplate.removeFromSortedSet(key, sessionId);
                }
                continue;
            }
            Optional<AuthSession> latestSession = findBySessionId(sessionId);
            if (latestSession.isPresent()) {
                cacheLatestSessionId(latestSession.get(), sessionTtl(latestSession.get()));
                return Optional.of(sessionId);
            }
            cacheTemplate.removeFromSortedSet(key, sessionId);
        }
    }

    public void revokeUserSessions(Long userId, boolean publishChange) {
        revokeSessions(listActiveUserSessionIds(userId), publishChange);
    }

    public void revokeUserSessionsExcept(Long userId, String excludedSessionId, boolean publishChange) {
        if (userId == null) {
            return;
        }
        List<String> toRevoke = listActiveUserSessionIds(userId).stream()
                .filter(id -> id != null && !id.equals(excludedSessionId))
                .toList();
        revokeSessions(toRevoke, publishChange);
    }

    private void revokeSessions(List<String> sessionIds, boolean publishChange) {
        if (CollectionUtils.isEmpty(sessionIds)) {
            return;
        }

        List<String> sessionKeys = sessionIds.stream().map(CacheKeyConstants::sessionKey).toList();
        List<String> payloads = cacheTemplate.multiGet(sessionKeys);
        List<String> keysToDelete = new ArrayList<>();

        for (int i = 0; i < sessionIds.size(); i++) {
            String sessionId = sessionIds.get(i);
            String payload = payloads.get(i);
            if (payload == null) {
                removeSessionReferences(sessionId);
                continue;
            }
            try {
                AuthSession session = objectMapper.readValue(payload, AuthSession.class);
                keysToDelete.add(CacheKeyConstants.sessionKey(session.getSessionId()));
                keysToDelete.add(CacheKeyConstants.userSessionKey(session.getUserId(), session.getSessionId()));
                cacheTemplate.removeFromSortedSet(CacheKeyConstants.onlineSessionUserKey(session.getUserId()), session.getSessionId());
                if (session.getCurrentTenantId() != null) {
                    cacheTemplate.removeFromSortedSet(CacheKeyConstants.onlineSessionTenantKey(session.getCurrentTenantId()), session.getSessionId());
                }
                if (publishChange) {
                    publishEvent(OnlineSessionEvent.ACTION_REMOVED, session);
                }
            } catch (JsonProcessingException ex) {
                removeSessionReferences(sessionId);
            }
        }
        if (!keysToDelete.isEmpty()) {
            cacheTemplate.remove(keysToDelete);
        }
    }

    public void retainLatestSessionForEachUser() {
        // Use SCAN instead of KEYS so the cleanup job stays non-blocking as the
        // session population grows.
        Set<String> keys = cacheTemplate.scan(CacheKeyConstants.PREFIX + ":" + CacheKeyConstants.SESSION_USER + ":*");
        if (CollectionUtils.isEmpty(keys)) {
            return;
        }

        Set<Long> userIds = new LinkedHashSet<>();
        for (String key : keys) {
            String[] parts = key.split(":");
            if (parts.length >= 4) {
                try {
                    userIds.add(Long.parseLong(parts[2]));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        for (Long userId : userIds) {
            List<String> sessionIds = listActiveUserSessionIds(userId);
            if (sessionIds.size() <= 1) {
                continue;
            }
            List<String> toRevoke = sessionIds.subList(1, sessionIds.size());
            revokeSessions(toRevoke, true);
        }
    }

    public void refreshTenantSessionPayloads(Long tenantId) {
        if (tenantId == null) {
            return;
        }
        for (String sessionId : listActiveTenantSessionIds(tenantId, 0, -1)) {
            findBySessionId(sessionId).ifPresent(session -> save(session, false));
        }
    }

    public void removeSessionReferences(String sessionId) {
        cacheTemplate.remove(CacheKeyConstants.sessionKey(sessionId));
        Set<String> userSessionKeys = cacheTemplate.scan(CacheKeyConstants.PREFIX + ":" + CacheKeyConstants.SESSION_USER + ":*:" + sessionId);
        if (CollectionUtils.isEmpty(userSessionKeys)) {
            return;
        }

        for (String key : userSessionKeys) {
            String[] parts = key.split(":");
            if (parts.length >= 4) {
                try {
                    Long userId = Long.parseLong(parts[2]);
                    removeUserSessionReference(userId, sessionId);
                } catch (NumberFormatException ignored) {
                    cacheTemplate.remove(key);
                }
            } else {
                cacheTemplate.remove(key);
            }
        }
    }

    public void removeTenantSessionReference(Long tenantId, String sessionId) {
        if (tenantId == null || !org.springframework.util.StringUtils.hasText(sessionId)) {
            return;
        }
        cacheTemplate.removeFromSortedSet(CacheKeyConstants.onlineSessionTenantKey(tenantId), sessionId);
    }

    private void cleanupExpiredTenantIndex(Long tenantId) {
        cacheTemplate.removeRangeByScore(CacheKeyConstants.onlineSessionTenantKey(tenantId), Double.NEGATIVE_INFINITY, Instant.now().toEpochMilli());
    }

    private void cleanupExpiredUserIndex(Long userId) {
        cacheTemplate.removeRangeByScore(CacheKeyConstants.onlineSessionUserKey(userId), Double.NEGATIVE_INFINITY, Instant.now().toEpochMilli());
    }

    private double toScore(AuthSession session) {
        return session.getExpireTime() == null ? Instant.now().toEpochMilli() : session.getExpireTime().toEpochMilli();
    }

    private Duration sessionTtl(AuthSession session) {
        if (session == null || session.getExpireTime() == null) {
            return Duration.ofSeconds(1);
        }
        Duration ttl = Duration.between(Instant.now(), session.getExpireTime());
        return ttl.isNegative() ? Duration.ofSeconds(1) : ttl;
    }

    private void cacheLatestSessionId(AuthSession session) {
        cacheLatestSessionId(session, sessionTtl(session));
    }

    private void cacheLatestSessionId(AuthSession session, Duration ttl) {
        if (session == null || session.getUserId() == null || !StringUtils.hasText(session.getSessionId())) {
            return;
        }
        cacheTemplate.put(CacheKeyConstants.onlineSessionLatestUserKey(session.getUserId()), session.getSessionId(), ttl);
    }

    private void clearLatestSessionIdIfMatch(AuthSession session) {
        if (session == null || session.getUserId() == null || !StringUtils.hasText(session.getSessionId())) {
            return;
        }
        String latestKey = CacheKeyConstants.onlineSessionLatestUserKey(session.getUserId());
        String cachedLatest = cacheTemplate.get(latestKey);
        if (session.getSessionId().equals(cachedLatest)) {
            cacheTemplate.remove(latestKey);
        }
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

    private void publishEvent(String action, AuthSession session) {
        OnlineSessionEvent event = new OnlineSessionEvent();
        event.setAction(action);
        event.setTenantId(session.getCurrentTenantId());
        event.setUserId(session.getUserId());
        event.setSessionId(session.getSessionId());
        event.setOccurredAt(Instant.now());
        onlineSessionEventPublisher.publish(event);
    }

    private void removeSessionFromOnlineIndexes(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        Set<String> userIndexKeys = cacheTemplate.scan(
                CacheKeyConstants.PREFIX + ":" + CacheKeyConstants.ONLINE_SESSION_USER + ":*"
        );
        if (!CollectionUtils.isEmpty(userIndexKeys)) {
            for (String key : userIndexKeys) {
                cacheTemplate.removeFromSortedSet(key, sessionId);
            }
        }

        Set<String> tenantIndexKeys = cacheTemplate.scan(
                CacheKeyConstants.PREFIX + ":" + CacheKeyConstants.ONLINE_SESSION_TENANT + ":*"
        );
        if (!CollectionUtils.isEmpty(tenantIndexKeys)) {
            for (String key : tenantIndexKeys) {
                cacheTemplate.removeFromSortedSet(key, sessionId);
            }
        }
    }

    private void removeUserSessionReference(Long userId, String sessionId) {
        if (userId == null || !org.springframework.util.StringUtils.hasText(sessionId)) {
            return;
        }
        cacheTemplate.remove(CacheKeyConstants.userSessionKey(userId, sessionId));
        cacheTemplate.removeFromSortedSet(CacheKeyConstants.onlineSessionUserKey(userId), sessionId);
    }
}
