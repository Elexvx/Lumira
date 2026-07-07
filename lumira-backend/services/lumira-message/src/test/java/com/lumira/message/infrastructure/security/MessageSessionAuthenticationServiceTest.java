package com.lumira.message.infrastructure.security;

import com.lumira.api.auth.CurrentUserDTO;
import com.lumira.api.client.AuthInternalApi;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.JwtTokenClaims;
import com.lumira.common.security.JwtTokenType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageSessionAuthenticationServiceTest {

    private JwtTokenService jwtTokenService;
    private AuthInternalApi authInternalApi;
    private MessageSessionAuthenticationService service;

    @BeforeEach
    void setUp() {
        jwtTokenService = mock(JwtTokenService.class);
        authInternalApi = mock(AuthInternalApi.class);
        service = new MessageSessionAuthenticationService(jwtTokenService, authInternalApi);
    }

    @Test
    void rejectsIncompleteAccessClaimsBeforeInternalHydration() {
        JwtTokenClaims claims = new JwtTokenClaims();
        claims.setTokenType(JwtTokenType.ACCESS);
        claims.setUserId(1001L);
        claims.setSessionVersion(1);
        when(jwtTokenService.parseToken("access-token")).thenReturn(claims);

        assertThrows(BizException.class, () -> service.authenticateAccessToken("access-token"));

        verify(authInternalApi, never()).currentUser(any(), anyLong(), anyString(), anyInt(), anyString(), any());
    }

    @Test
    void rejectsBlankUsernameAccessClaimsBeforeInternalHydration() {
        JwtTokenClaims claims = trustedClaims();
        claims.setUsername(" ");
        when(jwtTokenService.parseToken("access-token")).thenReturn(claims);

        assertThrows(BizException.class, () -> service.authenticateAccessToken("access-token"));

        verify(authInternalApi, never()).currentUser(any(), anyLong(), anyString(), anyInt(), anyString(), any());
    }

    @Test
    void rejectsBlankUsernameSnapshot() {
        JwtTokenClaims claims = trustedClaims();
        when(jwtTokenService.parseToken("access-token")).thenReturn(claims);
        when(authInternalApi.currentUser("session-1", 1001L, "user-uuid-1001", 1, "v1", 9L))
                .thenReturn(currentUser("session-1", 1001L, " ", 9L, 1));

        assertThrows(BizException.class, () -> service.authenticateAccessToken("access-token"));
    }

    @Test
    void rejectsSnapshotThatDoesNotMatchAccessClaims() {
        JwtTokenClaims claims = trustedClaims();
        when(jwtTokenService.parseToken("access-token")).thenReturn(claims);
        when(authInternalApi.currentUser("session-1", 1001L, "user-uuid-1001", 1, "v1", 9L))
                .thenReturn(currentUser("other-session", 1001L, 9L, 1));

        assertThrows(BizException.class, () -> service.authenticateAccessToken("access-token"));
    }

    @Test
    void rejectsOversizedAccessTokenBeforeParsing() {
        assertThrows(BizException.class, () -> service.authenticateAccessToken("a".repeat(8 * 1024 + 1)));

        verify(jwtTokenService, never()).parseToken(anyString());
        verify(authInternalApi, never()).currentUser(any(), anyLong(), anyString(), anyInt(), anyString(), any());
    }

    @Test
    void rejectsUnsafeClaimSessionIdBeforeInternalHydration() {
        JwtTokenClaims claims = trustedClaims();
        claims.setSessionId("../session");
        when(jwtTokenService.parseToken("access-token")).thenReturn(claims);

        assertThrows(BizException.class, () -> service.authenticateAccessToken("access-token"));

        verify(authInternalApi, never()).currentUser(any(), anyLong(), anyString(), anyInt(), anyString(), any());
    }

    @Test
    void rejectsUnsafeSessionTicketBeforeInternalHydration() {
        assertThrows(BizException.class, () -> service.authenticateSessionTicket("../session", 1001L, "user-uuid-1001", 9L, 1, "v1"));

        verify(authInternalApi, never()).currentUser(any(), anyLong(), anyString(), anyInt(), anyString(), any());
    }

    @Test
    void rejectsInvalidSessionTicketUserBeforeInternalHydration() {
        assertThrows(BizException.class, () -> service.authenticateSessionTicket("session-1", 0L, "user-uuid-1001", 9L, 1, "v1"));

        verify(authInternalApi, never()).currentUser(any(), anyLong(), anyString(), anyInt(), anyString(), any());
    }

    @Test
    void rejectsSessionTicketWithUserUuidMismatch() {
        when(authInternalApi.currentUser("session-1", 1001L, "other-uuid", 1, "v1", 9L))
                .thenReturn(currentUser("session-1", 1001L, 9L, 1));

        assertThrows(BizException.class, () -> service.authenticateSessionTicket("session-1", 1001L, "other-uuid", 9L, 1, "v1"));
    }

    @Test
    void rejectsSessionTicketWithPermissionsVersionMismatch() {
        when(authInternalApi.currentUser("session-1", 1001L, "user-uuid-1001", 1, "stale", 9L))
                .thenReturn(currentUser("session-1", 1001L, 9L, 1));

        assertThrows(BizException.class, () -> service.authenticateSessionTicket("session-1", 1001L, "user-uuid-1001", 9L, 1, "stale"));
    }

    @Test
    void isTrustedSessionShouldReturnFalseInsteadOfThrowingOnInvalidTicket() {
        when(authInternalApi.currentUser("session-1", 1001L, "user-uuid-1001", 1, "stale", 9L))
                .thenReturn(currentUser("session-1", 1001L, 9L, 1));

        org.junit.jupiter.api.Assertions.assertFalse(
                service.isTrustedSession("session-1", 1001L, "user-uuid-1001", 9L, 1, "stale")
        );
    }

    @Test
    void isTrustedSessionShouldReturnTrueForMatchingSnapshot() {
        when(authInternalApi.currentUser("session-1", 1001L, "user-uuid-1001", 1, "v1", 9L))
                .thenReturn(currentUser("session-1", 1001L, 9L, 1));

        org.junit.jupiter.api.Assertions.assertTrue(
                service.isTrustedSession("session-1", 1001L, "user-uuid-1001", 9L, 1, "v1")
        );
    }

    @Test
    void authenticateSessionTicketShouldTrimTrustedUsernameFromSnapshot() {
        when(authInternalApi.currentUser("session-1", 1001L, "user-uuid-1001", 1, "v1", 9L))
                .thenReturn(currentUser("session-1", 1001L, " admin ", 9L, 1));

        MessageSessionAuthenticationService.AuthenticatedAccess access =
                service.authenticateSessionTicket("session-1", 1001L, "user-uuid-1001", 9L, 1, "v1");

        assertThat(access.currentUser().getUsername()).isEqualTo("admin");
    }

    @Test
    void rejectsSnapshotWithSimulatedRoleMismatch() {
        JwtTokenClaims claims = trustedClaims();
        when(jwtTokenService.parseToken("access-token")).thenReturn(claims);
        when(authInternalApi.currentUser("session-1", 1001L, "user-uuid-1001", 1, "v1", 9L))
                .thenReturn(currentUser("session-1", 1001L, 7L, 1));

        assertThrows(BizException.class, () -> service.authenticateAccessToken("access-token"));
    }

    private static JwtTokenClaims trustedClaims() {
        JwtTokenClaims claims = new JwtTokenClaims();
        claims.setTokenType(JwtTokenType.ACCESS);
        claims.setSessionId("session-1");
        claims.setUserId(1001L);
        claims.setUserUuid("user-uuid-1001");
        claims.setUsername("admin");
        claims.setSimulatedRoleId(9L);
        claims.setSessionVersion(1);
        claims.setPermissionsVersion("v1");
        return claims;
    }

    private static CurrentUserDTO currentUser(String sessionId, Long userId, Integer sessionVersion) {
        return currentUser(sessionId, userId, "admin", null, sessionVersion);
    }

    private static CurrentUserDTO currentUser(String sessionId, Long userId, Long simulatedRoleId, Integer sessionVersion) {
        return currentUser(sessionId, userId, "admin", simulatedRoleId, sessionVersion);
    }

    private static CurrentUserDTO currentUser(String sessionId, Long userId, String username, Long simulatedRoleId, Integer sessionVersion) {
        return new CurrentUserDTO(
                userId,
                "user-uuid-" + userId,
                username,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                simulatedRoleId,
                List.of(),
                sessionId,
                "v1",
                sessionVersion,
                List.of("message:read"),
                List.of(1L),
                null,
                List.of(),
                List.of(),
                List.of(),
                false,
                null
        );
    }
}
