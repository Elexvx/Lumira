package com.lumira.saas.infrastructure.security.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.saas.common.constant.CacheKeyConstants;
import com.lumira.saas.infrastructure.security.model.AuthSession;
import com.lumira.saas.modules.system.online.OnlineSessionEventPublisher;
import com.lumira.saas.infrastructure.redis.CacheTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class AuthSessionStoreTest {

    private static final String USER_KEY = CacheKeyConstants.onlineSessionUserKey(1001L);

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private CacheTemplate cacheTemplate;
    private OnlineSessionEventPublisher onlineSessionEventPublisher;
    private AuthSessionStore authSessionStore;

    @BeforeEach
    void setUp() {
        cacheTemplate = mock(CacheTemplate.class);
        onlineSessionEventPublisher = mock(OnlineSessionEventPublisher.class);
        authSessionStore = new AuthSessionStore(cacheTemplate, objectMapper, onlineSessionEventPublisher);
    }

    @Test
    void saveShouldNormalizeNonPositiveTtl() {
        AuthSession session = buildSession(Instant.now().minusSeconds(120));
        when(cacheTemplate.scan(anyString())).thenReturn(Set.of());

        authSessionStore.save(session, false);

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(cacheTemplate).put(eq(CacheKeyConstants.sessionKey(session.getSessionId())), anyString(), ttlCaptor.capture());
        assertThat(ttlCaptor.getValue()).isGreaterThan(Duration.ZERO);
    }

    @Test
    void sessionMetricsShouldTrackSavesHitsMissesAndRemoves() {
        AuthSession session = buildSession(Instant.now().plusSeconds(3600));
        when(cacheTemplate.scan(anyString())).thenReturn(Set.of());
        when(cacheTemplate.get(CacheKeyConstants.sessionKey(session.getSessionId()))).thenReturn(null)
                .thenReturn(payload(session));

        authSessionStore.save(session, false);
        authSessionStore.findBySessionId(session.getSessionId());
        authSessionStore.findBySessionId(session.getSessionId());
        authSessionStore.remove(session, false);

        assertThat(authSessionStore.saves()).isEqualTo(1L);
        assertThat(authSessionStore.misses()).isEqualTo(1L);
        assertThat(authSessionStore.hits()).isEqualTo(1L);
        assertThat(authSessionStore.removes()).isEqualTo(1L);
    }

    @Test
    void findLatestActiveUserSessionIdShouldSkipMissingLatestSessionPayload() {
        AuthSession liveSession = buildSession(Instant.now().plusSeconds(3600));
        liveSession.setSessionId("live");
        String staleSessionId = "stale";

        when(cacheTemplate.scan(anyString())).thenReturn(Set.of());
        when(cacheTemplate.reverseRange(USER_KEY, 0, 0))
                .thenReturn(Set.of(staleSessionId))
                .thenReturn(Set.of(liveSession.getSessionId()));
        when(cacheTemplate.get(CacheKeyConstants.sessionKey(staleSessionId))).thenReturn(null);
        when(cacheTemplate.get(CacheKeyConstants.sessionKey(liveSession.getSessionId()))).thenReturn(payload(liveSession));

        assertThat(authSessionStore.findLatestActiveUserSessionId(1001L)).hasValue(liveSession.getSessionId());

        verify(cacheTemplate).removeRangeByScore(eq(USER_KEY), eq(Double.NEGATIVE_INFINITY), anyDouble());
        verify(cacheTemplate).removeFromSortedSet(USER_KEY, staleSessionId);
    }

    @Test
    void findLatestActiveUserSessionIdShouldHandleBlankSessionIdGracefully() {
        when(cacheTemplate.scan(anyString())).thenReturn(Set.of());
        when(cacheTemplate.reverseRange(USER_KEY, 0, 0))
                .thenReturn(Set.of(""))
                .thenReturn(Set.of());

        assertThat(authSessionStore.findLatestActiveUserSessionId(1001L)).isEmpty();

        verify(cacheTemplate).removeFromSortedSet(USER_KEY, "");
    }

    @Test
    void findLatestActiveUserSessionIdShouldUseCachedLatestSessionWhenValid() {
        AuthSession liveSession = buildSession(Instant.now().plusSeconds(3600));
        when(cacheTemplate.get(CacheKeyConstants.onlineSessionLatestUserKey(1001L))).thenReturn(liveSession.getSessionId());
        when(cacheTemplate.get(CacheKeyConstants.sessionKey(liveSession.getSessionId()))).thenReturn(payload(liveSession));
        when(cacheTemplate.scan(anyString())).thenReturn(Set.of());

        assertThat(authSessionStore.findLatestActiveUserSessionId(1001L)).hasValue(liveSession.getSessionId());

        verify(cacheTemplate).get(CacheKeyConstants.onlineSessionLatestUserKey(1001L));
        verify(cacheTemplate, never()).reverseRange(USER_KEY, 0, 0);
    }

    @Test
    void removeShouldClearCachedLatestSessionIdWhenMatched() {
        AuthSession session = buildSession(Instant.now().plusSeconds(3600));
        when(cacheTemplate.get(CacheKeyConstants.onlineSessionLatestUserKey(1001L))).thenReturn(session.getSessionId());
        when(cacheTemplate.scan(anyString())).thenReturn(Set.of());

        authSessionStore.remove(session, false);

        verify(cacheTemplate).get(CacheKeyConstants.onlineSessionLatestUserKey(1001L));
        verify(cacheTemplate).remove(CacheKeyConstants.onlineSessionLatestUserKey(1001L));
    }

    @Test
    void findBySessionIdShouldSelfHealWhenPayloadIsCorrupted() {
        String sessionId = "corrupt-session";
        when(cacheTemplate.get(CacheKeyConstants.sessionKey(sessionId))).thenReturn("{not valid json");
        when(cacheTemplate.scan(anyString())).thenReturn(Set.of());

        assertThat(authSessionStore.findBySessionId(sessionId)).isEmpty();
        assertThat(authSessionStore.corruptPayloads()).isEqualTo(1L);
        verify(cacheTemplate).remove(CacheKeyConstants.sessionKey(sessionId));
        verify(cacheTemplate).scan(CacheKeyConstants.PREFIX + ":" + CacheKeyConstants.SESSION_USER + ":*:" + sessionId);
    }

    @Test
    void findBySessionIdsShouldUseBatchMultiGet() {
        AuthSession first = buildSession(Instant.now().plusSeconds(3600));
        first.setSessionId("session-a");
        AuthSession second = buildSession(Instant.now().plusSeconds(1800));
        second.setSessionId("session-b");
        when(cacheTemplate.multiGet(List.of(
                CacheKeyConstants.sessionKey("session-a"),
                CacheKeyConstants.sessionKey("session-b")
        ))).thenReturn(List.of(payload(first), payload(second)));

        Map<String, AuthSession> sessions = authSessionStore.findBySessionIds(List.of("session-a", "session-b"));

        assertThat(sessions).containsKeys("session-a", "session-b");
        verify(cacheTemplate).multiGet(List.of(
                CacheKeyConstants.sessionKey("session-a"),
                CacheKeyConstants.sessionKey("session-b")
        ));
        verify(cacheTemplate, never()).get(CacheKeyConstants.sessionKey("session-a"));
        verify(cacheTemplate, never()).get(CacheKeyConstants.sessionKey("session-b"));
    }

    @Test
    void findLatestActiveUserSessionIdShouldReturnEmptyWhenUserIdIsNull() {
        assertThat(authSessionStore.findLatestActiveUserSessionId(null)).isEmpty();
        verifyNoMoreInteractions(cacheTemplate);
    }

    private AuthSession buildSession(Instant expireTime) {
        AuthSession session = new AuthSession();
        session.setSessionId("session-1");
        session.setUserId(1001L);
        session.setUsername("admin");
        session.setCurrentTenantId(1001L);
        session.setLoginTime(Instant.now().minusSeconds(60));
        session.setLastActivityAt(Instant.now().minusSeconds(30));
        session.setExpireTime(expireTime);
        session.setSessionVersion(1);
        session.setClientType("WEB");
        session.setSessionId("session-1");
        return session;
    }

    private String payload(AuthSession session) {
        try {
            return objectMapper.writeValueAsString(session);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }
}
