package com.lumira.saas.infrastructure.security.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.web.redis.SessionPayloadCas;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class AuthSessionStoreTest {

    private static final String USER_KEY = CacheKeyConstants.onlineSessionUserKey(1001L, "user-uuid-1001");

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private CacheTemplate cacheTemplate;
    private OnlineSessionEventPublisher onlineSessionEventPublisher;
    private AuthSessionStore authSessionStore;

    @BeforeEach
    void setUp() {
        cacheTemplate = mock(CacheTemplate.class);
        onlineSessionEventPublisher = mock(OnlineSessionEventPublisher.class);
        when(cacheTemplate.compareAndSetSessionPayload(
                anyString(),
                anyLong(),
                anyLong(),
                anyString(),
                org.mockito.ArgumentMatchers.any(Duration.class)
        )).thenReturn(SessionPayloadCas.Result.SAVED);
        authSessionStore = new AuthSessionStore(cacheTemplate, objectMapper, onlineSessionEventPublisher);
    }

    @Test
    void saveShouldNormalizeNonPositiveTtl() {
        AuthSession session = buildSession(Instant.now().minusSeconds(120));
        when(cacheTemplate.scan(anyString())).thenReturn(Set.of());

        authSessionStore.save(session, false);

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(cacheTemplate).compareAndSetSessionPayload(
                eq(CacheKeyConstants.sessionKey(session.getSessionId())),
                eq(-1L),
                eq(1L),
                anyString(),
                ttlCaptor.capture()
        );
        assertThat(ttlCaptor.getValue()).isGreaterThan(Duration.ZERO);
        assertThat(session.getMutationRevision()).isEqualTo(1L);
    }

    @Test
    void conditionalRemoveShouldLeaveNewerSessionAndIndexesUntouchedOnRevisionConflict() {
        AuthSession staleSession = buildSession(Instant.now().plusSeconds(3600));
        staleSession.setMutationRevision(7L);
        when(cacheTemplate.compareAndDeleteSessionPayload(
                CacheKeyConstants.sessionKey(staleSession.getSessionId()),
                7L
        )).thenReturn(SessionPayloadCas.DeleteResult.CONFLICT);

        boolean removed = authSessionStore.removeIfUnchanged(staleSession, true);

        assertThat(removed).isFalse();
        assertThat(authSessionStore.removes()).isZero();
        verify(cacheTemplate, never()).remove(anyString());
        verify(cacheTemplate, never()).removeFromSortedSet(anyString(), anyString());
        verify(onlineSessionEventPublisher, never()).publish(any());
    }

    @Test
    void conditionalRemoveShouldClearIndexesAndPublishOnlyAfterMatchingRevisionWasDeleted() {
        AuthSession session = buildSession(Instant.now().plusSeconds(3600));
        session.setMutationRevision(7L);
        when(cacheTemplate.compareAndDeleteSessionPayload(
                CacheKeyConstants.sessionKey(session.getSessionId()),
                7L
        )).thenReturn(SessionPayloadCas.DeleteResult.DELETED);

        boolean removed = authSessionStore.removeIfUnchanged(session, true);

        assertThat(removed).isTrue();
        assertThat(authSessionStore.removes()).isEqualTo(1L);
        verify(cacheTemplate).remove(CacheKeyConstants.userSessionKey(
                session.getUserId(),
                session.getUserUuid(),
                session.getSessionId()
        ));
        verify(cacheTemplate).removeFromSortedSet(USER_KEY, session.getSessionId());
        verify(cacheTemplate).removeFromSortedSet(CacheKeyConstants.onlineSessionKey(), session.getSessionId());
        verify(onlineSessionEventPublisher).publish(any());
    }

    @Test
    void saveShouldRejectInvalidSessionIdBeforeRedisAccess() {
        AuthSession session = buildSession(Instant.now().plusSeconds(3600));
        session.setSessionId("../session");

        assertThrows(IllegalArgumentException.class, () -> authSessionStore.save(session, false));

        verify(cacheTemplate, never()).compareAndSetSessionPayload(
                anyString(),
                anyLong(),
                anyLong(),
                anyString(),
                org.mockito.ArgumentMatchers.any(Duration.class)
        );
    }

    @Test
    void saveShouldRejectIncompleteTrustedSessionBeforeRedisAccess() {
        AuthSession missingUserUuid = buildSession(Instant.now().plusSeconds(3600));
        missingUserUuid.setUserUuid(null);
        assertThrows(IllegalArgumentException.class, () -> authSessionStore.save(missingUserUuid, false));

        AuthSession missingPermissionsVersion = buildSession(Instant.now().plusSeconds(3600));
        missingPermissionsVersion.setPermissionsVersion(null);
        assertThrows(IllegalArgumentException.class, () -> authSessionStore.save(missingPermissionsVersion, false));

        verify(cacheTemplate, never()).put(anyString(), anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void staleSessionSaveShouldBeRejected() {
        AuthSession persisted = buildSession(Instant.now().plusSeconds(3600));
        when(cacheTemplate.get(CacheKeyConstants.sessionKey(persisted.getSessionId())))
                .thenReturn(payload(persisted));
        AuthSession firstWriter = authSessionStore.findBySessionId(persisted.getSessionId()).orElseThrow();
        AuthSession staleWriter = authSessionStore.findBySessionId(persisted.getSessionId()).orElseThrow();
        when(cacheTemplate.compareAndSetSessionPayload(
                anyString(),
                anyLong(),
                anyLong(),
                anyString(),
                org.mockito.ArgumentMatchers.any(Duration.class)
        )).thenReturn(SessionPayloadCas.Result.SAVED, SessionPayloadCas.Result.CONFLICT);

        authSessionStore.save(firstWriter, false);
        clearInvocations(cacheTemplate, onlineSessionEventPublisher);
        BizException exception = assertThrows(BizException.class, () -> authSessionStore.save(staleWriter, false));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SESSION_EXPIRED);
        assertThat(staleWriter.getMutationRevision()).isZero();
        verify(cacheTemplate).compareAndSetSessionPayload(
                anyString(),
                eq(0L),
                eq(1L),
                anyString(),
                org.mockito.ArgumentMatchers.any(Duration.class)
        );
        verify(cacheTemplate, never()).put(anyString(), anyString(), org.mockito.ArgumentMatchers.any(Duration.class));
        verify(cacheTemplate, never()).addToSortedSet(anyString(), anyString(), anyDouble());
        verify(onlineSessionEventPublisher, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void loadedRevisionShouldIncrementOnSave() {
        AuthSession persisted = buildSession(Instant.now().plusSeconds(3600));
        persisted.setMutationRevision(7L);
        when(cacheTemplate.get(CacheKeyConstants.sessionKey(persisted.getSessionId())))
                .thenReturn(payload(persisted));
        AuthSession loaded = authSessionStore.findBySessionId(persisted.getSessionId()).orElseThrow();

        authSessionStore.save(loaded, false);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(cacheTemplate).compareAndSetSessionPayload(
                eq(CacheKeyConstants.sessionKey(loaded.getSessionId())),
                eq(7L),
                eq(8L),
                payloadCaptor.capture(),
                org.mockito.ArgumentMatchers.any(Duration.class)
        );
        assertThat(payloadCaptor.getValue()).contains("\"mutationRevision\":8");
        assertThat(loaded.getMutationRevision()).isEqualTo(8L);
    }

    @Test
    void legacyPayloadShouldUseRevisionZeroOnSave() {
        AuthSession persisted = buildSession(Instant.now().plusSeconds(3600));
        when(cacheTemplate.get(CacheKeyConstants.sessionKey(persisted.getSessionId())))
                .thenReturn(legacyPayload(persisted));
        AuthSession loaded = authSessionStore.findBySessionId(persisted.getSessionId()).orElseThrow();

        assertThat(loaded.getMutationRevision()).isZero();
        authSessionStore.save(loaded, false);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(cacheTemplate).compareAndSetSessionPayload(
                eq(CacheKeyConstants.sessionKey(loaded.getSessionId())),
                eq(0L),
                eq(1L),
                payloadCaptor.capture(),
                org.mockito.ArgumentMatchers.any(Duration.class)
        );
        assertThat(payloadCaptor.getValue()).contains("\"mutationRevision\":1");
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

        assertThat(authSessionStore.findLatestActiveUserSessionId(1001L, "user-uuid-1001")).hasValue(liveSession.getSessionId());

        verify(cacheTemplate).removeRangeByScore(eq(USER_KEY), eq(Double.NEGATIVE_INFINITY), anyDouble());
        verify(cacheTemplate).removeFromSortedSet(USER_KEY, staleSessionId);
    }

    @Test
    void findLatestActiveUserSessionIdShouldHandleBlankSessionIdGracefully() {
        when(cacheTemplate.scan(anyString())).thenReturn(Set.of());
        when(cacheTemplate.reverseRange(USER_KEY, 0, 0))
                .thenReturn(Set.of(""))
                .thenReturn(Set.of());

        assertThat(authSessionStore.findLatestActiveUserSessionId(1001L, "user-uuid-1001")).isEmpty();

        verify(cacheTemplate).removeFromSortedSet(USER_KEY, "");
    }

    @Test
    void findLatestActiveUserSessionIdShouldUseCachedLatestSessionWhenValid() {
        AuthSession liveSession = buildSession(Instant.now().plusSeconds(3600));
        when(cacheTemplate.get(CacheKeyConstants.onlineSessionLatestUserKey(1001L, "user-uuid-1001"))).thenReturn(liveSession.getSessionId());
        when(cacheTemplate.get(CacheKeyConstants.sessionKey(liveSession.getSessionId()))).thenReturn(payload(liveSession));
        when(cacheTemplate.scan(anyString())).thenReturn(Set.of());

        assertThat(authSessionStore.findLatestActiveUserSessionId(1001L, "user-uuid-1001")).hasValue(liveSession.getSessionId());

        verify(cacheTemplate).get(CacheKeyConstants.onlineSessionLatestUserKey(1001L, "user-uuid-1001"));
        verify(cacheTemplate, never()).reverseRange(USER_KEY, 0, 0);
    }

    @Test
    void removeShouldClearCachedLatestSessionIdWhenMatched() {
        AuthSession session = buildSession(Instant.now().plusSeconds(3600));
        when(cacheTemplate.get(CacheKeyConstants.onlineSessionLatestUserKey(1001L, "user-uuid-1001"))).thenReturn(session.getSessionId());
        when(cacheTemplate.scan(anyString())).thenReturn(Set.of());

        authSessionStore.remove(session, false);

        verify(cacheTemplate).get(CacheKeyConstants.onlineSessionLatestUserKey(1001L, "user-uuid-1001"));
        verify(cacheTemplate).remove(CacheKeyConstants.onlineSessionLatestUserKey(1001L, "user-uuid-1001"));
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
    void findBySessionIdShouldRejectInvalidIdBeforeRedisAccess() {
        assertThat(authSessionStore.findBySessionId("../session")).isEmpty();

        verify(cacheTemplate, never()).get(anyString());
    }

    @Test
    void findBySessionIdShouldRemoveIncompleteSessionPayload() {
        AuthSession session = buildSession(Instant.now().plusSeconds(3600));
        session.setUserUuid(null);
        when(cacheTemplate.get(CacheKeyConstants.sessionKey(session.getSessionId()))).thenReturn(payload(session));
        when(cacheTemplate.scan(anyString())).thenReturn(Set.of());

        assertThat(authSessionStore.findBySessionId(session.getSessionId())).isEmpty();

        assertThat(authSessionStore.corruptPayloads()).isEqualTo(1L);
        verify(cacheTemplate).remove(CacheKeyConstants.sessionKey(session.getSessionId()));
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
    void findBySessionIdsShouldFilterInvalidIdsBeforeBatchLookup() {
        AuthSession first = buildSession(Instant.now().plusSeconds(3600));
        first.setSessionId("session-a");
        when(cacheTemplate.multiGet(List.of(CacheKeyConstants.sessionKey("session-a"))))
                .thenReturn(List.of(payload(first)));

        Map<String, AuthSession> sessions = authSessionStore.findBySessionIds(List.of("session-a", "../session"));

        assertThat(sessions).containsOnlyKeys("session-a");
        verify(cacheTemplate).multiGet(List.of(CacheKeyConstants.sessionKey("session-a")));
    }

    @Test
    void removeShouldIgnoreInvalidSessionBeforeRedisAccess() {
        AuthSession session = buildSession(Instant.now().plusSeconds(3600));
        session.setSessionId("../session");

        authSessionStore.remove(session, false);

        verify(cacheTemplate, never()).remove(anyString());
    }

    @Test
    void blankUuidUserSessionMethodsShouldFailClosedBeforeRedisAccess() {
        assertThat(authSessionStore.listActiveUserSessionIds(1001L, " ")).isEmpty();
        assertThat(authSessionStore.findLatestActiveUserSessionId(1001L, " ")).isEmpty();
        authSessionStore.revokeUserSessions(1001L, " ", false);
        authSessionStore.revokeUserSessionsExcept(1001L, " ", "session-1", false);
        authSessionStore.markPasswordChangeResolved(1001L, " ", "session-1", false);

        verifyNoMoreInteractions(cacheTemplate);
    }

    private AuthSession buildSession(Instant expireTime) {
        AuthSession session = new AuthSession();
        session.setSessionId("session-1");
        session.setUserId(1001L);
        session.setUserUuid("user-uuid-1001");
        session.setUsername("admin");
        session.setLoginTime(Instant.now().minusSeconds(60));
        session.setLastActivityAt(Instant.now().minusSeconds(30));
        session.setExpireTime(expireTime);
        session.setSessionVersion(1);
        session.setPermissionsVersion("permissions-1");
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

    private String legacyPayload(AuthSession session) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode payload = objectMapper.valueToTree(session);
            payload.remove("mutationRevision");
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }
}
