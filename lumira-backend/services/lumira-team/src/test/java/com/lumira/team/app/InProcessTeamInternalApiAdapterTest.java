package com.lumira.team.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lumira.common.security.AuthenticationTrustSupport;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class InProcessTeamInternalApiAdapterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void establishesInternalPrincipalAndRestoresCallerContext() {
        TeamInternalApiService delegate = mock(TeamInternalApiService.class);
        InProcessTeamInternalApiAdapter adapter = new InProcessTeamInternalApiAdapter(delegate);
        Authentication caller = new UsernamePasswordAuthenticationToken("human-user", "credentials");
        SecurityContextHolder.getContext().setAuthentication(caller);
        when(delegate.listActiveTeamIdsForUser(3001L, "user-uuid-3001")).thenAnswer(invocation -> {
            assertThat(AuthenticationTrustSupport.isInternalServiceAuthentication(
                    SecurityContextHolder.getContext().getAuthentication()
            )).isTrue();
            return List.of(2001L);
        });

        assertThat(adapter.listActiveTeamIdsForUser(3001L, "user-uuid-3001")).containsExactly(2001L);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(caller);
    }

    @Test
    void restoresCallerContextWhenDelegateFails() {
        TeamInternalApiService delegate = mock(TeamInternalApiService.class);
        InProcessTeamInternalApiAdapter adapter = new InProcessTeamInternalApiAdapter(delegate);
        Authentication caller = new UsernamePasswordAuthenticationToken("human-user", "credentials");
        SecurityContextHolder.getContext().setAuthentication(caller);
        when(delegate.getTeam(3001L, "user-uuid-3001", 2001L))
                .thenThrow(new IllegalStateException("team lookup failed"));

        assertThatThrownBy(() -> adapter.getTeam(3001L, "user-uuid-3001", 2001L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("team lookup failed");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(caller);
    }
}
