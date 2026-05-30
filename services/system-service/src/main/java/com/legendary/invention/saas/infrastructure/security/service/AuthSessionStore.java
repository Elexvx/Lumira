package com.legendary.invention.saas.infrastructure.security.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legendary.invention.saas.common.constant.CacheKeyConstants;
import com.legendary.invention.common.enums.ErrorCode;
import com.legendary.invention.common.exception.BizException;
import com.legendary.invention.saas.infrastructure.redis.CacheTemplate;
import com.legendary.invention.saas.infrastructure.security.model.AuthSession;
import com.legendary.invention.saas.modules.system.online.OnlineSessionEvent;
import com.legendary.invention.saas.modules.system.online.OnlineSessionEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Objects;

@Component
public class AuthSessionStore {

    private final CacheTemplate cacheTemplate;
    private final ObjectMapper objectMapper;
    private final OnlineSessionEventPublisher onlineSessionEventPublisher;

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
            cacheTemplate.put(CacheKeyConstants.sessionKey(session.getSessionId()), objectMapper.writeValueAsString(session), ttl);
            cacheTemplate.put(CacheKeyConstants.userSessionKey(session.getUserId(), session.getSessionId()), "1", ttl);
            cacheTemplate.addToSortedSet(CacheKeyConstants.onlineSessionUserKey(session.getUserId()), session.getSessionId(), toScore(session));
            if (session.getCurrentTenantId() != null) {
                cacheTemplate.addToSortedSet(CacheKeyConstants.onlineSessionTenantKey(session.getCurrentTenantId()), session.getSessionId(), toScore(session));
            }
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
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "会话序列化失败");
        }
    }

    public void save(AuthSession session) {
        save(session, false);
    }

    public Optional<AuthSession> findBySessionId(String sessionId) {
        String payload = cacheTemplate.get(CacheKeyConstants.sessionKey(sessionId));
        if (payload == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(payload, AuthSession.class));
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "会话反序列化失败");
        }
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
        List<String> sessionIds = listActiveUserSessionIds(userId);
        if (sessionIds.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(sessionIds.get(0));
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

    private void publishEvent(String action, AuthSession session) {
        OnlineSessionEvent event = new OnlineSessionEvent();
        event.setAction(action);
        event.setTenantId(session.getCurrentTenantId());
        event.setUserId(session.getUserId());
        event.setSessionId(session.getSessionId());
        event.setOccurredAt(Instant.now());
        onlineSessionEventPublisher.publish(event);
    }

    private void removeUserSessionReference(Long userId, String sessionId) {
        if (userId == null || !org.springframework.util.StringUtils.hasText(sessionId)) {
            return;
        }
        cacheTemplate.remove(CacheKeyConstants.userSessionKey(userId, sessionId));
        cacheTemplate.removeFromSortedSet(CacheKeyConstants.onlineSessionUserKey(userId), sessionId);
    }
}
