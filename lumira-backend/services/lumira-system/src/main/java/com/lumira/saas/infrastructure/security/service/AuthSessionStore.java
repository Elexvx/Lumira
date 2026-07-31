package com.lumira.saas.infrastructure.security.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.saas.common.constant.CacheKeyConstants;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.web.redis.SessionPayloadCas;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
        Long originalRevision = session == null ? null : session.getMutationRevision();
        boolean payloadCommitted = false;
        try {
            AuthSessionTrustValidator.requireTrustedSession(session);
            Duration effectiveTtl = ttl == null || ttl.isZero() || ttl.isNegative()
                    ? Duration.ofSeconds(1)
                    : ttl;
            long expectedRevision = SessionPayloadCas.expectedRevision(originalRevision);
            long nextRevision = SessionPayloadCas.nextRevision(originalRevision);
            session.setMutationRevision(nextRevision);
            String payload = objectMapper.writeValueAsString(session);
            AuthSessionTrustValidator.requireTrustedPayload(payload);
            SessionPayloadCas.Result saveResult = cacheTemplate.compareAndSetSessionPayload(
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
            cacheTemplate.put(CacheKeyConstants.userSessionKey(session.getUserId(), session.getUserUuid(), session.getSessionId()), "1", effectiveTtl);
            cacheTemplate.addToSortedSet(CacheKeyConstants.onlineSessionUserKey(session.getUserId(), session.getUserUuid()), session.getSessionId(), toScore(session));
            cacheTemplate.addToSortedSet(CacheKeyConstants.onlineSessionKey(), session.getSessionId(), toScore(session));
            cacheLatestSessionId(session, effectiveTtl);
            if (publishChange) {
                publishEvent(OnlineSessionEvent.ACTION_UPSERT, session);
            }
            saves.incrementAndGet();
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "会话序列化失败");
        } finally {
            if (!payloadCommitted && session != null) {
                session.setMutationRevision(originalRevision);
            }
        }
    }

    public void save(AuthSession session) {
        save(session, false);
    }

    public Optional<AuthSession> findBySessionId(String sessionId) {
        String normalizedSessionId = AuthSessionTrustValidator.trustedSessionIdOrNull(sessionId);
        if (normalizedSessionId == null) {
            misses.incrementAndGet();
            return Optional.empty();
        }
        String payload = cacheTemplate.get(CacheKeyConstants.sessionKey(normalizedSessionId));
        if (!StringUtils.hasText(payload)) {
            misses.incrementAndGet();
            return Optional.empty();
        }
        try {
            AuthSessionTrustValidator.requireTrustedPayload(payload);
        } catch (IllegalArgumentException exception) {
            corruptPayloads.incrementAndGet();
            removeSessionReferences(normalizedSessionId);
            removeSessionFromOnlineIndexes(normalizedSessionId);
            return Optional.empty();
        }

        try {
            hits.incrementAndGet();
            AuthSession session = deserializeSession(payload);
            AuthSessionTrustValidator.requireTrustedSession(session);
            return Optional.of(session);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            corruptPayloads.incrementAndGet();
            log.warn(
                    "Session payload is corrupted, removing stale session cache. sessionId={}, reason={}",
                    normalizedSessionId,
                    ex.getMessage()
            );
            removeSessionReferences(normalizedSessionId);
            removeSessionFromOnlineIndexes(normalizedSessionId);
            return Optional.empty();
        }
    }

    public Map<String, AuthSession> findBySessionIds(List<String> sessionIds) {
        if (CollectionUtils.isEmpty(sessionIds)) {
            return Map.of();
        }

        List<String> distinctSessionIds = sessionIds.stream()
                .map(AuthSessionTrustValidator::trustedSessionIdOrNull)
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
                AuthSessionTrustValidator.requireTrustedPayload(payload);
                hits.incrementAndGet();
                AuthSession session = deserializeSession(payload);
                AuthSessionTrustValidator.requireTrustedSession(session);
                sessions.put(sessionId, session);
            } catch (JsonProcessingException | IllegalArgumentException ex) {
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

    private AuthSession deserializeSession(String payload) throws JsonProcessingException {
        AuthSession session = objectMapper.readValue(payload, AuthSession.class);
        session.setMutationRevision(SessionPayloadCas.normalizeLoadedRevision(session.getMutationRevision()));
        return session;
    }

    public void remove(AuthSession session) {
        remove(session, false);
    }

    public void remove(AuthSession session, boolean publishChange) {
        try {
            AuthSessionTrustValidator.requireTrustedSession(session);
        } catch (IllegalArgumentException exception) {
            return;
        }
        cacheTemplate.remove(CacheKeyConstants.sessionKey(session.getSessionId()));
        removeSessionIndexes(session, publishChange);
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
        SessionPayloadCas.DeleteResult deleteResult = cacheTemplate.compareAndDeleteSessionPayload(
                CacheKeyConstants.sessionKey(session.getSessionId()),
                session.getMutationRevision()
        );
        if (deleteResult != SessionPayloadCas.DeleteResult.DELETED) {
            if (deleteResult == SessionPayloadCas.DeleteResult.INVALID_CURRENT_PAYLOAD) {
                log.warn("Refusing conditional cleanup of invalid session payload. sessionId={}", session.getSessionId());
            }
            return false;
        }
        removeSessionIndexes(session, publishChange);
        return true;
    }

    private void removeSessionIndexes(AuthSession session, boolean publishChange) {
        cacheTemplate.remove(CacheKeyConstants.userSessionKey(session.getUserId(), session.getUserUuid(), session.getSessionId()));
        cacheTemplate.remove(CacheKeyConstants.userSessionKey(session.getUserId(), session.getSessionId()));
        cacheTemplate.removeFromSortedSet(CacheKeyConstants.onlineSessionUserKey(session.getUserId(), session.getUserUuid()), session.getSessionId());
        cacheTemplate.removeFromSortedSet(CacheKeyConstants.onlineSessionUserKey(session.getUserId()), session.getSessionId());
        cacheTemplate.removeFromSortedSet(CacheKeyConstants.onlineSessionKey(), session.getSessionId());
        clearLatestSessionIdIfMatch(session);
        removes.incrementAndGet();
        if (publishChange) {
            publishEvent(OnlineSessionEvent.ACTION_REMOVED, session);
        }
    }

    public List<String> listActiveSessionIds(long start, long end) {
        cleanupExpiredSessionIndex();
        Set<String> sessionIds = cacheTemplate.reverseRange(CacheKeyConstants.onlineSessionKey(), start, end);
        if (CollectionUtils.isEmpty(sessionIds)) {
            return List.of();
        }
        return sessionIds.stream()
                .map(AuthSessionTrustValidator::trustedSessionIdOrNull)
                .filter(StringUtils::hasText)
                .toList();
    }

    public long countActiveSessions() {
        cleanupExpiredSessionIndex();
        Long count = cacheTemplate.sortedSetSize(CacheKeyConstants.onlineSessionKey());
        return count == null ? 0L : count;
    }

    public List<String> listActiveUserSessionIds(Long userId, String userUuid) {
        if (userId == null || userId <= 0 || !StringUtils.hasText(userUuid)) {
            return List.of();
        }
        cleanupExpiredUserIndex(userId, userUuid);
        Set<String> sessionIds = cacheTemplate.reverseRange(onlineSessionUserKey(userId, userUuid), 0, -1);
        if (CollectionUtils.isEmpty(sessionIds)) {
            return List.of();
        }
        return sessionIds.stream()
                .map(AuthSessionTrustValidator::trustedSessionIdOrNull)
                .filter(StringUtils::hasText)
                .filter(sessionId -> belongsToUser(sessionId, userId, userUuid))
                .toList();
    }

    public Optional<String> findLatestActiveUserSessionId(Long userId, String userUuid) {
        if (userId == null || userId <= 0 || !StringUtils.hasText(userUuid)) {
            return Optional.empty();
        }
        String latestCacheKey = onlineSessionLatestUserKey(userId, userUuid);
        String latestSessionId = cacheTemplate.get(latestCacheKey);
        if (StringUtils.hasText(latestSessionId)) {
            Optional<AuthSession> cachedLatestSession = findBySessionId(latestSessionId)
                    .filter(session -> belongsToUser(session, userId, userUuid));
            if (cachedLatestSession.isPresent()) {
                return Optional.of(latestSessionId);
            }
            cacheTemplate.remove(latestCacheKey);
        }

        String key = onlineSessionUserKey(userId, userUuid);
        cleanupExpiredUserIndex(userId, userUuid);
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
            Optional<AuthSession> latestSession = findBySessionId(sessionId)
                    .filter(session -> belongsToUser(session, userId, userUuid));
            if (latestSession.isPresent()) {
                cacheLatestSessionId(latestSession.get(), sessionTtl(latestSession.get()));
                return Optional.of(sessionId);
            }
            cacheTemplate.removeFromSortedSet(key, sessionId);
        }
    }

    public void revokeUserSessions(Long userId, String userUuid, boolean publishChange) {
        revokeSessions(listActiveUserSessionIds(userId, userUuid), publishChange);
    }

    public void revokeUserSessionsExcept(Long userId, String userUuid, String excludedSessionId, boolean publishChange) {
        if (userId == null || !StringUtils.hasText(userUuid)) {
            return;
        }
        String normalizedExcludedSessionId = AuthSessionTrustValidator.trustedSessionIdOrNull(excludedSessionId);
        List<String> toRevoke = listActiveUserSessionIds(userId, userUuid).stream()
                .filter(id -> id != null && !id.equals(normalizedExcludedSessionId))
                .toList();
        revokeSessions(toRevoke, publishChange);
    }

    public void markPasswordChangeResolved(Long userId, String userUuid, String sessionId, boolean publishChange) {
        String normalizedSessionId = AuthSessionTrustValidator.trustedSessionIdOrNull(sessionId);
        if (userId == null || !StringUtils.hasText(userUuid) || normalizedSessionId == null) {
            return;
        }
        findBySessionId(normalizedSessionId)
                .filter(session -> belongsToUser(session, userId, userUuid))
                .ifPresent(session -> {
                    session.setRequiresPasswordChange(Boolean.FALSE);
                    save(session, publishChange);
                });
    }

    private void revokeSessions(List<String> sessionIds, boolean publishChange) {
        if (CollectionUtils.isEmpty(sessionIds)) {
            return;
        }
        List<String> trustedSessionIds = sessionIds.stream()
                .map(AuthSessionTrustValidator::trustedSessionIdOrNull)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (trustedSessionIds.isEmpty()) {
            return;
        }

        List<String> sessionKeys = trustedSessionIds.stream().map(CacheKeyConstants::sessionKey).toList();
        List<String> payloads = cacheTemplate.multiGet(sessionKeys);
        List<String> keysToDelete = new ArrayList<>();

        for (int i = 0; i < trustedSessionIds.size(); i++) {
            String sessionId = trustedSessionIds.get(i);
            String payload = payloads.get(i);
            if (payload == null) {
                removeSessionReferences(sessionId);
                continue;
            }
            try {
                AuthSessionTrustValidator.requireTrustedPayload(payload);
                AuthSession session = deserializeSession(payload);
                AuthSessionTrustValidator.requireTrustedSession(session);
                keysToDelete.add(CacheKeyConstants.sessionKey(session.getSessionId()));
                keysToDelete.add(CacheKeyConstants.userSessionKey(session.getUserId(), session.getUserUuid(), session.getSessionId()));
                keysToDelete.add(CacheKeyConstants.userSessionKey(session.getUserId(), session.getSessionId()));
                cacheTemplate.removeFromSortedSet(CacheKeyConstants.onlineSessionUserKey(session.getUserId(), session.getUserUuid()), session.getSessionId());
                cacheTemplate.removeFromSortedSet(CacheKeyConstants.onlineSessionUserKey(session.getUserId()), session.getSessionId());
                cacheTemplate.removeFromSortedSet(CacheKeyConstants.onlineSessionKey(), session.getSessionId());
                if (publishChange) {
                    publishEvent(OnlineSessionEvent.ACTION_REMOVED, session);
                }
            } catch (JsonProcessingException | IllegalArgumentException ex) {
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

        Map<UserIdentity, List<String>> sessionsByUser = new LinkedHashMap<>();
        for (String key : keys) {
            String[] parts = key.split(":");
            if (parts.length < 4) {
                continue;
            }
            String sessionId = parts[parts.length - 1];
            Optional<AuthSession> session = findBySessionId(sessionId)
                    .filter(item -> item.getUserId() != null && StringUtils.hasText(item.getUserUuid()));
            if (session.isPresent()) {
                AuthSession authSession = session.get();
                sessionsByUser
                        .computeIfAbsent(new UserIdentity(authSession.getUserId(), authSession.getUserUuid().trim()), ignored -> new ArrayList<>())
                        .add(authSession.getSessionId());
            }
        }

        for (Map.Entry<UserIdentity, List<String>> entry : sessionsByUser.entrySet()) {
            UserIdentity identity = entry.getKey();
            List<String> sessionIds = listActiveUserSessionIds(identity.userId(), identity.userUuid());
            if (sessionIds.size() <= 1) {
                continue;
            }
            List<String> toRevoke = sessionIds.subList(1, sessionIds.size());
            revokeSessions(toRevoke, true);
        }
    }

    public void refreshAllSessionPayloads() {
        Set<String> sessionKeys = cacheTemplate.scan(CacheKeyConstants.PREFIX + ":" + CacheKeyConstants.SESSION + ":*");
        if (CollectionUtils.isEmpty(sessionKeys)) {
            return;
        }
        String prefix = CacheKeyConstants.PREFIX + ":" + CacheKeyConstants.SESSION + ":";
        for (String key : sessionKeys) {
            if (!StringUtils.hasText(key) || !key.startsWith(prefix)) {
                continue;
            }
            String sessionId = key.substring(prefix.length());
            if (StringUtils.hasText(sessionId)) {
                findBySessionId(sessionId).ifPresent(session -> save(session, false));
            }
        }
    }

    public void removeSessionReferences(String sessionId) {
        String normalizedSessionId = AuthSessionTrustValidator.trustedSessionIdOrNull(sessionId);
        if (normalizedSessionId == null) {
            return;
        }
        cacheTemplate.remove(CacheKeyConstants.sessionKey(normalizedSessionId));
        Set<String> userSessionKeys = cacheTemplate.scan(CacheKeyConstants.PREFIX + ":" + CacheKeyConstants.SESSION_USER + ":*:" + normalizedSessionId);
        if (CollectionUtils.isEmpty(userSessionKeys)) {
            removeSessionFromOnlineIndexes(normalizedSessionId);
            return;
        }

        for (String key : userSessionKeys) {
            String[] parts = key.split(":");
            if (parts.length >= 5) {
                Long userId = parseLong(parts[2]);
                String userUuid = parts[3];
                if (userId != null && StringUtils.hasText(userUuid)) {
                    removeUserSessionReference(userId, userUuid, normalizedSessionId);
                } else {
                    cacheTemplate.remove(key);
                }
            } else if (parts.length >= 4) {
                try {
                    Long userId = Long.parseLong(parts[2]);
                    removeUserSessionReference(userId, normalizedSessionId);
                } catch (NumberFormatException ignored) {
                    cacheTemplate.remove(key);
                }
            } else {
                cacheTemplate.remove(key);
            }
        }
        removeSessionFromOnlineIndexes(normalizedSessionId);
    }

    private void cleanupExpiredSessionIndex() {
        cacheTemplate.removeRangeByScore(CacheKeyConstants.onlineSessionKey(), Double.NEGATIVE_INFINITY, Instant.now().toEpochMilli());
    }

    private void cleanupExpiredUserIndex(Long userId) {
        cleanupExpiredUserIndex(userId, null);
    }

    private void cleanupExpiredUserIndex(Long userId, String userUuid) {
        if (userId == null || !StringUtils.hasText(userUuid)) {
            return;
        }
        cacheTemplate.removeRangeByScore(onlineSessionUserKey(userId, userUuid), Double.NEGATIVE_INFINITY, Instant.now().toEpochMilli());
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
        if (session == null || session.getUserId() == null || !StringUtils.hasText(session.getUserUuid()) || !StringUtils.hasText(session.getSessionId())) {
            return;
        }
        cacheTemplate.put(CacheKeyConstants.onlineSessionLatestUserKey(session.getUserId(), session.getUserUuid()), session.getSessionId(), ttl);
    }

    private void clearLatestSessionIdIfMatch(AuthSession session) {
        if (session == null || session.getUserId() == null || !StringUtils.hasText(session.getUserUuid()) || !StringUtils.hasText(session.getSessionId())) {
            return;
        }
        String latestKey = CacheKeyConstants.onlineSessionLatestUserKey(session.getUserId(), session.getUserUuid());
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
        event.setUserId(session.getUserId());
        event.setUserUuid(session.getUserUuid());
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

        cacheTemplate.removeFromSortedSet(CacheKeyConstants.onlineSessionKey(), sessionId);
    }

    private void removeUserSessionReference(Long userId, String sessionId) {
        if (userId == null || !org.springframework.util.StringUtils.hasText(sessionId)) {
            return;
        }
        cacheTemplate.remove(CacheKeyConstants.userSessionKey(userId, sessionId));
        cacheTemplate.removeFromSortedSet(CacheKeyConstants.onlineSessionUserKey(userId), sessionId);
    }

    private void removeUserSessionReference(Long userId, String userUuid, String sessionId) {
        if (userId == null || !StringUtils.hasText(userUuid) || !StringUtils.hasText(sessionId)) {
            return;
        }
        cacheTemplate.remove(CacheKeyConstants.userSessionKey(userId, userUuid, sessionId));
        cacheTemplate.removeFromSortedSet(CacheKeyConstants.onlineSessionUserKey(userId, userUuid), sessionId);
    }

    private String onlineSessionUserKey(Long userId, String userUuid) {
        if (!StringUtils.hasText(userUuid)) {
            throw new IllegalArgumentException("Full user identity is required");
        }
        return CacheKeyConstants.onlineSessionUserKey(userId, userUuid.trim());
    }

    private String onlineSessionLatestUserKey(Long userId, String userUuid) {
        if (!StringUtils.hasText(userUuid)) {
            throw new IllegalArgumentException("Full user identity is required");
        }
        return CacheKeyConstants.onlineSessionLatestUserKey(userId, userUuid.trim());
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
        return StringUtils.hasText(userUuid)
                && StringUtils.hasText(session.getUserUuid())
                && userUuid.trim().equals(session.getUserUuid().trim());
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

    private record UserIdentity(Long userId, String userUuid) {
    }
}
