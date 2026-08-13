package com.lumira.saas.modules.competition.app;

import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.TrustedCurrentUserResolver;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CompetitionAuthenticationTrustTest {

    @Test
    void asyncExportSnapshotDoesNotResolveEphemeralSessionAgainstRedis() {
        CurrentUser currentUser = trustedUser();
        currentUser.setSessionId(CompetitionAuthenticationTrust.asyncExportSessionId(9001L));
        TrustedCurrentUserResolver resolver = mock(TrustedCurrentUserResolver.class);

        CompetitionAuthenticationTrust.refresh(currentUser, resolver, true);

        verifyNoInteractions(resolver);
        assertThat(currentUser.getSessionId()).isEqualTo("internal-registration-export-task-9001");
    }

    @Test
    void regularSessionStillRefreshesThroughTrustedResolver() {
        CurrentUser currentUser = trustedUser();
        CurrentUser refreshed = trustedUser();
        refreshed.setUsername("refreshed-operator");
        TrustedCurrentUserResolver resolver = mock(TrustedCurrentUserResolver.class);
        when(resolver.resolve(currentUser)).thenReturn(refreshed);

        CompetitionAuthenticationTrust.refresh(currentUser, resolver, true);

        verify(resolver).resolve(currentUser);
        assertThat(currentUser.getUsername()).isEqualTo("refreshed-operator");
    }

    @Test
    void malformedAsyncExportSessionDoesNotBypassTrustedResolver() {
        CurrentUser currentUser = trustedUser();
        currentUser.setSessionId("internal-registration-export-task-not-a-task");
        TrustedCurrentUserResolver resolver = mock(TrustedCurrentUserResolver.class);
        when(resolver.resolve(currentUser)).thenReturn(currentUser);

        CompetitionAuthenticationTrust.refresh(currentUser, resolver, true);

        verify(resolver).resolve(currentUser);
    }

    private CurrentUser trustedUser() {
        CurrentUser user = new CurrentUser();
        user.setUserId(1001L);
        user.setUserUuid("user-uuid-1001");
        user.setUsername("operator");
        user.setSessionId("session-1");
        user.setSessionVersion(1);
        user.setPermissionsVersion("permissions-1");
        user.setAuthenticated(true);
        user.setPermissions(Set.of("registration:dataset:export"));
        return user;
    }
}
