package com.lumira.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.auth.model.AuthSession;
import com.lumira.common.constant.CacheKeyConstants;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthSessionStoreTest {

    private static final String USER_ONLINE_SESSION_KEY = CacheKeyConstants.onlineSessionUserKey(1L, "user-uuid-1");

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private ZSetOperations<String, String> zSetOperations;
    private AuthSessionStore authSessionStore;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        zSetOperations = mock(ZSetOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);
        authSessionStore = new AuthSessionStore(redisTemplate, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void saveShouldSerializeInstantFields() {
        AuthSession session = buildSession(Instant.now().plusSeconds(3600));

        assertDoesNotThrow(() -> authSessionStoreObjectMapper().writeValueAsString(session));
        authSessionStore.save(session, false);

        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Object[]> argumentsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(redisTemplate).execute(any(RedisScript.class), keysCaptor.capture(), argumentsCaptor.capture());
        assertThat(keysCaptor.getValue()).containsExactly("saas:session:s-1");
        assertThat(argumentsCaptor.getValue()[0]).isEqualTo("-1");
        assertThat((String) argumentsCaptor.getValue()[1]).contains("\"sessionId\":\"s-1\"");
        assertThat((String) argumentsCaptor.getValue()[1]).contains("\"loginTime\"");
        assertThat((String) argumentsCaptor.getValue()[1]).contains("\"mutationRevision\":1");
        assertThat(Long.parseLong((String) argumentsCaptor.getValue()[2])).isPositive();
        assertThat(argumentsCaptor.getValue()[3]).isEqualTo("1");
        assertThat(session.getMutationRevision()).isEqualTo(1L);
    }

    @Test
    void saveShouldNormalizeNonPositiveTtl() {
        AuthSession session = buildSession(Instant.now().minusSeconds(5));

        authSessionStore.save(session, false);

        ArgumentCaptor<Object[]> argumentsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(redisTemplate).execute(any(RedisScript.class), anyList(), argumentsCaptor.capture());
        assertThat(Long.parseLong((String) argumentsCaptor.getValue()[2])).isPositive();
    }

    @Test
    void staleSessionSaveShouldBeRejected() {
        AuthSession persisted = buildSession(Instant.now().plusSeconds(3600));
        when(valueOperations.get(CacheKeyConstants.sessionKey(persisted.getSessionId())))
                .thenReturn(toPayload(persisted));
        AuthSession firstWriter = authSessionStore.findBySessionId(persisted.getSessionId()).orElseThrow();
        AuthSession staleWriter = authSessionStore.findBySessionId(persisted.getSessionId()).orElseThrow();
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(1L, 0L);

        authSessionStore.save(firstWriter, false);
        clearInvocations(redisTemplate, valueOperations, zSetOperations);
        BizException exception = assertThrows(BizException.class, () -> authSessionStore.save(staleWriter, false));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SESSION_EXPIRED);
        assertThat(staleWriter.getMutationRevision()).isZero();
        verify(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
        verify(zSetOperations, never()).add(anyString(), anyString(), anyDouble());
    }

    @Test
    void loadedRevisionShouldIncrementOnSave() {
        AuthSession persisted = buildSession(Instant.now().plusSeconds(3600));
        persisted.setMutationRevision(7L);
        when(valueOperations.get(CacheKeyConstants.sessionKey(persisted.getSessionId())))
                .thenReturn(toPayload(persisted));
        AuthSession loaded = authSessionStore.findBySessionId(persisted.getSessionId()).orElseThrow();

        authSessionStore.save(loaded, false);

        ArgumentCaptor<Object[]> argumentsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(redisTemplate).execute(any(RedisScript.class), anyList(), argumentsCaptor.capture());
        assertThat(argumentsCaptor.getValue()[0]).isEqualTo("7");
        assertThat(argumentsCaptor.getValue()[3]).isEqualTo("8");
        assertThat((String) argumentsCaptor.getValue()[1]).contains("\"mutationRevision\":8");
        assertThat(loaded.getMutationRevision()).isEqualTo(8L);
    }

    @Test
    void legacyPayloadShouldUseRevisionZeroOnSave() {
        AuthSession persisted = buildSession(Instant.now().plusSeconds(3600));
        when(valueOperations.get(CacheKeyConstants.sessionKey(persisted.getSessionId())))
                .thenReturn(legacyPayload(persisted));
        AuthSession loaded = authSessionStore.findBySessionId(persisted.getSessionId()).orElseThrow();

        assertThat(loaded.getMutationRevision()).isZero();
        authSessionStore.save(loaded, false);

        ArgumentCaptor<Object[]> argumentsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(redisTemplate).execute(any(RedisScript.class), anyList(), argumentsCaptor.capture());
        assertThat(argumentsCaptor.getValue()[0]).isEqualTo("0");
        assertThat(argumentsCaptor.getValue()[3]).isEqualTo("1");
        assertThat((String) argumentsCaptor.getValue()[1]).contains("\"mutationRevision\":1");
    }

    @Test
    void sessionMetricsShouldTrackSavesHitsMissesAndRemoves() {
        AuthSession session = buildSession(Instant.now().plusSeconds(3600));
        when(valueOperations.get("saas:session:s-1")).thenReturn(null);

        authSessionStore.save(session, false);
        assertThat(authSessionStore.findBySessionId("s-1")).isEmpty();
        when(valueOperations.get("saas:session:s-1")).thenReturn(toPayload(session));
        assertThat(authSessionStore.findBySessionId("s-1")).isPresent();
        authSessionStore.remove(session, false);

        assertThat(authSessionStore.saves()).isEqualTo(1L);
        assertThat(authSessionStore.misses()).isEqualTo(1L);
        assertThat(authSessionStore.hits()).isEqualTo(1L);
        assertThat(authSessionStore.hitRatio()).isEqualTo(0.5);
        assertThat(authSessionStore.removes()).isEqualTo(1L);
    }

    @Test
    void conditionalRemoveShouldLeaveNewerSessionAndIndexesUntouchedOnRevisionConflict() {
        AuthSession staleSession = buildSession(Instant.now().plusSeconds(3600));
        staleSession.setMutationRevision(7L);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(0L);

        boolean removed = authSessionStore.removeIfUnchanged(staleSession, true);

        assertThat(removed).isFalse();
        assertThat(authSessionStore.removes()).isZero();
        verify(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));
        verify(redisTemplate, never()).delete(anyString());
        verify(zSetOperations, never()).remove(anyString(), anyString());
    }

    @Test
    void conditionalRemoveShouldClearIndexesOnlyAfterMatchingRevisionWasDeleted() {
        AuthSession session = buildSession(Instant.now().plusSeconds(3600));
        session.setMutationRevision(7L);

        boolean removed = authSessionStore.removeIfUnchanged(session, true);

        assertThat(removed).isTrue();
        assertThat(authSessionStore.removes()).isEqualTo(1L);
        verify(redisTemplate).delete(CacheKeyConstants.sessionOwnerKey(session.getSessionId()));
        verify(redisTemplate).delete(CacheKeyConstants.userSessionKey(
                session.getUserId(),
                session.getUserUuid(),
                session.getSessionId()
        ));
        verify(zSetOperations).remove(USER_ONLINE_SESSION_KEY, session.getSessionId());
        verify(zSetOperations).remove(CacheKeyConstants.onlineSessionKey(), session.getSessionId());
    }

    @Test
    void findLatestActiveUserSessionIdShouldSkipMissingLatestSessionPayload() {
        AuthSession liveSession = buildSession(Instant.now().plusSeconds(3600));
        liveSession.setSessionId("live");
        when(zSetOperations.reverseRange(USER_ONLINE_SESSION_KEY, 0, 0))
                .thenReturn(Set.of("stale"))
                .thenReturn(Set.of("live"));
        when(valueOperations.get("saas:session:stale")).thenReturn(null);
        when(valueOperations.get("saas:session:live")).thenReturn(toPayload(liveSession));

        var latest = authSessionStore.findLatestActiveUserSessionId(1L, "user-uuid-1");

        assertThat(latest).contains("live");
        verify(zSetOperations).remove(USER_ONLINE_SESSION_KEY, "stale");
    }

    @Test
    void listActiveUserSessionIdsShouldCleanExpiredSessions() {
        AuthSession first = buildSession(Instant.now().plusSeconds(3600));
        first.setSessionId("s1");
        AuthSession second = buildSession(Instant.now().plusSeconds(3600));
        second.setSessionId("s2");
        when(zSetOperations.reverseRange(USER_ONLINE_SESSION_KEY, 0, -1)).thenReturn(Set.of("s1", "s2"));
        when(valueOperations.get(CacheKeyConstants.sessionKey("s1"))).thenReturn(toPayload(first));
        when(valueOperations.get(CacheKeyConstants.sessionKey("s2"))).thenReturn(toPayload(second));

        assertThat(authSessionStore.listActiveUserSessionIds(1L, "user-uuid-1")).containsExactlyInAnyOrder("s1", "s2");

        verify(zSetOperations).removeRangeByScore(eq(USER_ONLINE_SESSION_KEY), eq(Double.NEGATIVE_INFINITY), anyDouble());
    }

    @Test
    void findLatestActiveUserSessionIdShouldCallCleanupBeforeLookup() {
        AuthSession liveSession = buildSession(Instant.now().plusSeconds(3600));
        liveSession.setSessionId("live");
        when(zSetOperations.reverseRange(USER_ONLINE_SESSION_KEY, 0, 0))
                .thenReturn(Set.of("stale"))
                .thenReturn(Set.of("live"));
        when(valueOperations.get("saas:session:stale")).thenReturn(null);
        when(valueOperations.get("saas:session:live")).thenReturn(toPayload(liveSession));

        assertThat(authSessionStore.findLatestActiveUserSessionId(1L, "user-uuid-1")).contains("live");

        verify(zSetOperations).removeRangeByScore(eq(USER_ONLINE_SESSION_KEY), eq(Double.NEGATIVE_INFINITY), anyDouble());
    }

    @Test
    void findLatestActiveUserSessionIdShouldHandleBlankSessionIdGracefully() {
        when(zSetOperations.reverseRange(USER_ONLINE_SESSION_KEY, 0, 0))
                .thenReturn(Set.of(""))
                .thenReturn(Set.of());
        when(valueOperations.get("saas:session:")).thenReturn(null);

        assertThat(authSessionStore.findLatestActiveUserSessionId(1L, "user-uuid-1")).isEmpty();
        verify(zSetOperations).remove(USER_ONLINE_SESSION_KEY, "");
    }

    @Test
    void findLatestActiveUserSessionIdShouldReturnEmptyWhenUserIdIsNull() {
        assertThat(authSessionStore.findLatestActiveUserSessionId(null, "user-uuid-1")).isEmpty();
    }

    @Test
    void blankUuidUserSessionMethodsShouldFailClosedBeforeRedisAccess() {
        assertThat(authSessionStore.listActiveUserSessionIds(1L, " ")).isEmpty();
        assertThat(authSessionStore.findLatestActiveUserSessionId(1L, " ")).isEmpty();
        authSessionStore.revokeUserSessions(1L, " ", false);

        verify(zSetOperations, never()).reverseRange(anyString(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
        verify(zSetOperations, never()).removeRangeByScore(anyString(), anyDouble(), anyDouble());
        verify(valueOperations, never()).get(anyString());
    }

    @Test
    void saveShouldRejectIncompleteTrustedSessionBeforeRedisAccess() {
        AuthSession missingUserUuid = buildSession(Instant.now().plusSeconds(3600));
        missingUserUuid.setUserUuid(null);
        assertThrows(IllegalArgumentException.class, () -> authSessionStore.save(missingUserUuid, false));

        AuthSession missingPermissionsVersion = buildSession(Instant.now().plusSeconds(3600));
        missingPermissionsVersion.setPermissionsVersion(null);
        assertThrows(IllegalArgumentException.class, () -> authSessionStore.save(missingPermissionsVersion, false));

        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    void findBySessionIdShouldRemoveIncompleteSessionPayload() {
        AuthSession session = buildSession(Instant.now().plusSeconds(3600));
        session.setPermissionsVersion(null);
        when(valueOperations.get(CacheKeyConstants.sessionKey(session.getSessionId()))).thenReturn(toPayload(session));
        when(valueOperations.get(CacheKeyConstants.sessionOwnerKey(session.getSessionId()))).thenReturn("1|user-uuid-1");

        assertThat(authSessionStore.findBySessionId(session.getSessionId())).isEmpty();

        assertThat(authSessionStore.corruptPayloads()).isEqualTo(1L);
        verify(redisTemplate).delete(CacheKeyConstants.sessionKey(session.getSessionId()));
    }

    @Test
    void removeSessionReferencesShouldUseOwnerIndexWithoutKeyScan() {
        when(valueOperations.get(CacheKeyConstants.sessionOwnerKey("s-1"))).thenReturn("1|user-uuid-1");

        authSessionStore.removeSessionReferences("s-1");

        verify(redisTemplate, never()).keys(anyString());
        verify(redisTemplate).delete(CacheKeyConstants.sessionOwnerKey("s-1"));
        verify(redisTemplate).delete(CacheKeyConstants.userSessionKey(1L, "user-uuid-1", "s-1"));
        verify(zSetOperations).remove(CacheKeyConstants.onlineSessionUserKey(1L, "user-uuid-1"), "s-1");
        verify(zSetOperations).remove(CacheKeyConstants.onlineSessionKey(), "s-1");
    }

    private AuthSession buildSession(Instant expireTime) {
        AuthSession session = new AuthSession();
        session.setSessionId("s-1");
        session.setUserId(1L);
        session.setUserUuid("user-uuid-1");
        session.setUsername("admin");
        session.setLoginTime(Instant.parse("2026-05-06T00:00:00Z"));
        session.setLastActivityAt(Instant.parse("2026-05-06T00:01:00Z"));
        session.setExpireTime(expireTime);
        session.setSessionVersion(1);
        session.setPermissionsVersion("permissions-1");
        session.setClientType("WEB");
        session.setLoginIp("127.0.0.1");
        session.setUserAgent("Mozilla/5.0");
        session.setRefreshTokenId("refresh-1");
        return session;
    }

    private String toPayload(AuthSession session) {
        try {
            return objectMapper.writeValueAsString(session);
        } catch (Exception exception) {
            throw new AssertionError("failed to serialize auth session", exception);
        }
    }

    private String legacyPayload(AuthSession session) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode payload = objectMapper.valueToTree(session);
            payload.remove("mutationRevision");
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new AssertionError("failed to serialize legacy auth session", exception);
        }
    }

    private ObjectMapper authSessionStoreObjectMapper() {
        return objectMapper;
    }
}
