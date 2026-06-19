package com.lumira.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.auth.model.AuthSession;
import com.lumira.common.constant.CacheKeyConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthSessionStoreTest {

    private static final String USER_ONLINE_SESSION_KEY = CacheKeyConstants.onlineSessionUserKey(1L);

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
        authSessionStore = new AuthSessionStore(redisTemplate, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void saveShouldSerializeInstantFields() {
        AuthSession session = buildSession(Instant.now().plusSeconds(3600));

        assertDoesNotThrow(() -> authSessionStoreObjectMapper().writeValueAsString(session));
        authSessionStore.save(session, false);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(eq("saas:session:s-1"), payloadCaptor.capture(), ttlCaptor.capture());
        assertTrue(payloadCaptor.getValue().contains("\"sessionId\":\"s-1\""));
        assertTrue(payloadCaptor.getValue().contains("\"loginTime\""));
        assertTrue(ttlCaptor.getValue().compareTo(Duration.ZERO) > 0);
    }

    @Test
    void saveShouldNormalizeNonPositiveTtl() {
        AuthSession session = buildSession(Instant.now().minusSeconds(5));

        authSessionStore.save(session, false);

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(eq("saas:session:s-1"), any(String.class), ttlCaptor.capture());
        assertTrue(ttlCaptor.getValue().compareTo(Duration.ZERO) > 0);
    }

    @Test
    void sessionMetricsShouldTrackSavesHitsMissesAndRemoves() {
        AuthSession session = buildSession(Instant.now().plusSeconds(3600));
        when(valueOperations.get("saas:session:s-1")).thenReturn(null);

        authSessionStore.save(session, false);
        assertThat(authSessionStore.findBySessionId("s-1")).isEmpty();
        when(valueOperations.get("saas:session:s-1")).thenReturn("{\"sessionId\":\"s-1\",\"userId\":1,\"currentTenantId\":1001,\"sessionVersion\":1}");
        assertThat(authSessionStore.findBySessionId("s-1")).isPresent();
        authSessionStore.remove(session, false);

        assertThat(authSessionStore.saves()).isEqualTo(1L);
        assertThat(authSessionStore.misses()).isEqualTo(1L);
        assertThat(authSessionStore.hits()).isEqualTo(1L);
        assertThat(authSessionStore.hitRatio()).isEqualTo(0.5);
        assertThat(authSessionStore.removes()).isEqualTo(1L);
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

        var latest = authSessionStore.findLatestActiveUserSessionId(1L);

        assertThat(latest).contains("live");
        verify(zSetOperations).remove(USER_ONLINE_SESSION_KEY, "stale");
    }

    @Test
    void listActiveUserSessionIdsShouldCleanExpiredSessions() {
        when(zSetOperations.reverseRange(USER_ONLINE_SESSION_KEY, 0, -1)).thenReturn(Set.of("s1", "s2"));

        assertThat(authSessionStore.listActiveUserSessionIds(1L)).containsExactlyInAnyOrder("s1", "s2");

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

        assertThat(authSessionStore.findLatestActiveUserSessionId(1L)).contains("live");

        verify(zSetOperations).removeRangeByScore(eq(USER_ONLINE_SESSION_KEY), eq(Double.NEGATIVE_INFINITY), anyDouble());
    }

    @Test
    void findLatestActiveUserSessionIdShouldHandleBlankSessionIdGracefully() {
        when(zSetOperations.reverseRange(USER_ONLINE_SESSION_KEY, 0, 0))
                .thenReturn(Set.of(""))
                .thenReturn(Set.of());
        when(valueOperations.get("saas:session:")).thenReturn(null);

        assertThat(authSessionStore.findLatestActiveUserSessionId(1L)).isEmpty();
        verify(zSetOperations).remove(USER_ONLINE_SESSION_KEY, "");
    }

    @Test
    void findLatestActiveUserSessionIdShouldReturnEmptyWhenUserIdIsNull() {
        assertThat(authSessionStore.findLatestActiveUserSessionId(null)).isEmpty();
    }

    @Test
    void removeSessionReferencesShouldUseOwnerIndexWithoutKeyScan() {
        when(valueOperations.get(CacheKeyConstants.sessionOwnerKey("s-1"))).thenReturn("1|1001");

        authSessionStore.removeSessionReferences("s-1");

        verify(redisTemplate, never()).keys(anyString());
        verify(redisTemplate).delete(CacheKeyConstants.sessionOwnerKey("s-1"));
        verify(redisTemplate).delete(CacheKeyConstants.userSessionKey(1L, "s-1"));
        verify(zSetOperations).remove(CacheKeyConstants.onlineSessionUserKey(1L), "s-1");
        verify(zSetOperations).remove(CacheKeyConstants.onlineSessionTenantKey(1001L), "s-1");
    }

    private AuthSession buildSession(Instant expireTime) {
        AuthSession session = new AuthSession();
        session.setSessionId("s-1");
        session.setUserId(1L);
        session.setUsername("admin");
        session.setCurrentTenantId(1001L);
        session.setLoginTime(Instant.parse("2026-05-06T00:00:00Z"));
        session.setLastActivityAt(Instant.parse("2026-05-06T00:01:00Z"));
        session.setExpireTime(expireTime);
        session.setSessionVersion(1);
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

    private ObjectMapper authSessionStoreObjectMapper() {
        return objectMapper;
    }
}
