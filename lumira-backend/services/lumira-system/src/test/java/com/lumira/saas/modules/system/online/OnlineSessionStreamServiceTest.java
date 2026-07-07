package com.lumira.saas.modules.system.online;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.security.model.AuthSession;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OnlineSessionStreamServiceTest {

    @Test
    void openStreamShouldRejectUnauthenticatedUserEvenWhenSessionIdExists() {
        OnlineSessionStreamService service = service(mock(SessionAuthenticationService.class));
        CurrentUser currentUser = new CurrentUser(1001L, "alice", null, "session-1", 1, false, Set.of("system:online-user:view"));
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setPermissionsVersion("permissions-1");

        assertThatThrownBy(() -> service.openStream(currentUser))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void openStreamShouldRejectBlankUsernameEvenWhenSessionIdExists() {
        OnlineSessionStreamService service = service(mock(SessionAuthenticationService.class));
        CurrentUser currentUser = new CurrentUser(1001L, " ", null, "session-1", 1, true, Set.of("system:online-user:view"));
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setPermissionsVersion("permissions-1");

        assertThatThrownBy(() -> service.openStream(currentUser))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void openStreamShouldTrustLivePermissionSnapshotOverLocalPermissions() {
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        when(sessionAuthenticationService.authenticateSessionTicket(anyString(), anyLong(), anyString(), anyLong(), anyInt(), anyString()))
                .thenReturn(authenticatedAccess(Set.of("system:online-user:view")));
        OnlineSessionStreamService service = new OnlineSessionStreamService(
                new ObjectMapper(),
                sessionAuthenticationService,
                Clock.fixed(Instant.parse("2026-07-06T12:00:00Z"), ZoneOffset.UTC),
                Duration.ofSeconds(30)
        );
        CurrentUser currentUser = new CurrentUser(1001L, "alice", null, "session-1", 1, true, Set.of("system:config:view"));
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setSimulatedRoleId(9L);
        currentUser.setPermissionsVersion("permissions-1");

        var emitter = service.openStream(currentUser);

        assertThat(emitter).isNotNull();
        assertThat(service.subscriberCount()).isEqualTo(1);
    }

    @Test
    void openStreamShouldRejectBlankSessionId() {
        OnlineSessionStreamService service = service(mock(SessionAuthenticationService.class));
        CurrentUser currentUser = new CurrentUser(1001L, "alice", null, " ", 1, true, Set.of("system:online-user:view"));
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setPermissionsVersion("permissions-1");

        assertThatThrownBy(() -> service.openStream(currentUser))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void openStreamShouldRejectMissingSessionVersion() {
        OnlineSessionStreamService service = service(mock(SessionAuthenticationService.class));
        CurrentUser currentUser = new CurrentUser(1001L, "alice", null, "session-1", null, true, Set.of("system:online-user:view"));
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setPermissionsVersion("permissions-1");

        assertThatThrownBy(() -> service.openStream(currentUser))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void openStreamShouldRejectUnsafeSessionId() {
        OnlineSessionStreamService service = service(mock(SessionAuthenticationService.class));
        CurrentUser currentUser = new CurrentUser(1001L, "alice", null, "../session", 1, true, Set.of("system:online-user:view"));
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setPermissionsVersion("permissions-1");

        assertThatThrownBy(() -> service.openStream(currentUser))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void heartbeatShouldCloseSubscriberWhenTrustedSessionExpires() {
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        MutableClock clock = new MutableClock(Instant.parse("2026-07-06T12:00:00Z"));
        OnlineSessionStreamService service = new OnlineSessionStreamService(
                new ObjectMapper(),
                sessionAuthenticationService,
                clock,
                Duration.ofSeconds(30)
        );
        when(sessionAuthenticationService.authenticateSessionTicket(anyString(), anyLong(), anyString(), anyLong(), anyInt(), anyString()))
                .thenReturn(authenticatedAccess(Set.of("system:online-user:view")));
        service.openStream(trustedCurrentUser());
        when(sessionAuthenticationService.authenticateSessionTicket(anyString(), anyLong(), anyString(), anyLong(), anyInt(), anyString()))
                .thenThrow(new BizException(ErrorCode.SESSION_EXPIRED, "expired"));

        clock.setInstant(clock.instant().plusSeconds(31));
        service.heartbeat();

        assertThat(service.subscriberCount()).isZero();
    }

    @Test
    void heartbeatShouldCloseSubscriberWhenViewPermissionIsRevoked() {
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        MutableClock clock = new MutableClock(Instant.parse("2026-07-06T12:00:00Z"));
        OnlineSessionStreamService service = new OnlineSessionStreamService(
                new ObjectMapper(),
                sessionAuthenticationService,
                clock,
                Duration.ZERO
        );
        when(sessionAuthenticationService.authenticateSessionTicket(anyString(), anyLong(), anyString(), anyLong(), anyInt(), anyString()))
                .thenReturn(authenticatedAccess(Set.of("system:online-user:view")));
        service.openStream(trustedCurrentUser());
        when(sessionAuthenticationService.authenticateSessionTicket(anyString(), anyLong(), anyString(), anyLong(), anyInt(), anyString()))
                .thenReturn(authenticatedAccess(Set.of("system:config:view")));

        service.heartbeat();

        assertThat(service.subscriberCount()).isZero();
    }

    @Test
    void heartbeatShouldSkipImmediateRevalidationWithinTrustWindow() {
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        OnlineSessionStreamService service = new OnlineSessionStreamService(
                new ObjectMapper(),
                sessionAuthenticationService,
                Clock.fixed(Instant.parse("2026-07-06T12:00:00Z"), ZoneOffset.UTC),
                Duration.ofSeconds(30)
        );
        when(sessionAuthenticationService.authenticateSessionTicket(anyString(), anyLong(), anyString(), anyLong(), anyInt(), anyString()))
                .thenReturn(authenticatedAccess(Set.of("system:online-user:view")));
        service.openStream(trustedCurrentUser());

        service.heartbeat();

        verify(sessionAuthenticationService, times(1)).authenticateSessionTicket(anyString(), anyLong(), anyString(), anyLong(), anyInt(), anyString());
        assertThat(service.subscriberCount()).isEqualTo(1);
    }

    @Test
    void dispatchShouldRevalidatePermissionBeforeSendingPrivilegedEvents() {
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        OnlineSessionStreamService service = new OnlineSessionStreamService(
                new ObjectMapper(),
                sessionAuthenticationService,
                Clock.fixed(Instant.parse("2026-07-06T12:00:00Z"), ZoneOffset.UTC),
                Duration.ofSeconds(30)
        );
        when(sessionAuthenticationService.authenticateSessionTicket(anyString(), anyLong(), anyString(), anyLong(), anyInt(), anyString()))
                .thenReturn(authenticatedAccess(Set.of("system:online-user:view")));
        service.openStream(trustedCurrentUser());
        when(sessionAuthenticationService.authenticateSessionTicket(anyString(), anyLong(), anyString(), anyLong(), anyInt(), anyString()))
                .thenReturn(authenticatedAccess(Set.of("system:config:view")));

        service.dispatch(event("session-2"));

        verify(sessionAuthenticationService, times(2)).authenticateSessionTicket(anyString(), anyLong(), anyString(), anyLong(), anyInt(), anyString());
        assertThat(service.subscriberCount()).isZero();
    }

    @Test
    void openStreamShouldRejectWhenLivePermissionIsAlreadyRevoked() {
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        when(sessionAuthenticationService.authenticateSessionTicket(anyString(), anyLong(), anyString(), anyLong(), anyInt(), anyString()))
                .thenReturn(authenticatedAccess(Set.of("system:config:view")));
        OnlineSessionStreamService service = new OnlineSessionStreamService(
                new ObjectMapper(),
                sessionAuthenticationService,
                Clock.fixed(Instant.parse("2026-07-06T12:00:00Z"), ZoneOffset.UTC),
                Duration.ofSeconds(30)
        );

        assertThatThrownBy(() -> service.openStream(trustedCurrentUser()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void openStreamShouldPassSimulatedRoleIdIntoTrustedRevalidation() {
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        when(sessionAuthenticationService.authenticateSessionTicket(anyString(), anyLong(), anyString(), anyLong(), anyInt(), anyString()))
                .thenReturn(authenticatedAccess(Set.of("system:online-user:view")));
        OnlineSessionStreamService service = new OnlineSessionStreamService(
                new ObjectMapper(),
                sessionAuthenticationService,
                Clock.fixed(Instant.parse("2026-07-06T12:00:00Z"), ZoneOffset.UTC),
                Duration.ofSeconds(30)
        );

        service.openStream(trustedCurrentUser());

        verify(sessionAuthenticationService).authenticateSessionTicket(
                eq("session-1"),
                eq(1001L),
                eq("user-uuid-1001"),
                eq(9L),
                eq(1),
                eq("permissions-1")
        );
    }

    private OnlineSessionStreamService service(SessionAuthenticationService sessionAuthenticationService) {
        when(sessionAuthenticationService.authenticateSessionTicket(anyString(), anyLong(), anyString(), anyLong(), anyInt(), anyString()))
                .thenReturn(authenticatedAccess(Set.of("system:online-user:view")));
        return new OnlineSessionStreamService(
                new ObjectMapper(),
                sessionAuthenticationService,
                Clock.fixed(Instant.parse("2026-07-06T12:00:00Z"), ZoneOffset.UTC),
                Duration.ofSeconds(30)
        );
    }

    private CurrentUser trustedCurrentUser() {
        CurrentUser currentUser = new CurrentUser(1001L, "alice", null, "session-1", 1, true, Set.of("system:online-user:view"));
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setSimulatedRoleId(9L);
        currentUser.setPermissionsVersion("permissions-1");
        return currentUser;
    }

    private SessionAuthenticationService.AuthenticatedAccess authenticatedAccess(Set<String> permissions) {
        CurrentUser currentUser = trustedCurrentUser();
        currentUser.setPermissions(permissions);
        AuthSession session = new AuthSession();
        session.setSessionId("session-1");
        session.setUserId(1001L);
        session.setUserUuid("user-uuid-1001");
        session.setUsername("alice");
        session.setSessionVersion(1);
        session.setSimulatedRoleId(9L);
        session.setPermissionsVersion("permissions-1");
        return new SessionAuthenticationService.AuthenticatedAccess(currentUser, session, false);
    }

    private OnlineSessionEvent event(String sessionId) {
        OnlineSessionEvent event = new OnlineSessionEvent();
        event.setAction(OnlineSessionEvent.ACTION_UPSERT);
        event.setUserId(1001L);
        event.setUserUuid("user-uuid-1001");
        event.setSessionId(sessionId);
        event.setOperatorUsername("alice");
        event.setOccurredAt(Instant.parse("2026-07-06T12:00:05Z"));
        return event;
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void setInstant(Instant instant) {
            this.instant = instant;
        }
    }
}
