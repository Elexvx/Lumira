package com.lumira.saas.infrastructure.security.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lumira.common.security.CurrentUser;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SessionTrustedCurrentUserResolverTest {

    @Test
    void resolvesTrustedUserThroughTheExistingSessionTicketPath() {
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        CurrentUser requestUser = trustedUser();
        CurrentUser resolvedUser = trustedUser();
        resolvedUser.setUsername("refreshed-user");
        when(sessionAuthenticationService.authenticateSessionTicket(
                "session-1", 1001L, "user-uuid", 7L, 3, "permissions-v3"))
                .thenReturn(new SessionAuthenticationService.AuthenticatedAccess(resolvedUser, null, false));

        CurrentUser actual = new SessionTrustedCurrentUserResolver(sessionAuthenticationService).resolve(requestUser);

        assertThat(actual).isSameAs(resolvedUser);
        verify(sessionAuthenticationService).authenticateSessionTicket(
                "session-1", 1001L, "user-uuid", 7L, 3, "permissions-v3");
    }

    @Test
    void leavesUntrustedInputForTheCallerToRejectWithoutOpeningASessionLookup() {
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        CurrentUser anonymous = new CurrentUser(0L, "anonymous", null, 0, false, Set.of());

        CurrentUser actual = new SessionTrustedCurrentUserResolver(sessionAuthenticationService).resolve(anonymous);

        assertThat(actual).isSameAs(anonymous);
        verifyNoInteractions(sessionAuthenticationService);
    }

    private static CurrentUser trustedUser() {
        CurrentUser user = new CurrentUser(1001L, "user", "session-1", 3, true, Set.of("aiadc:activity:view"));
        user.setUserUuid("user-uuid");
        user.setSimulatedRoleId(7L);
        user.setPermissionsVersion("permissions-v3");
        return user;
    }
}
