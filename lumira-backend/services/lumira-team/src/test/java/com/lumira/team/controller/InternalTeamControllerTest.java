package com.lumira.team.controller;

import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.team.api.TeamMemberDTO;
import com.lumira.team.api.TeamSummaryDTO;
import com.lumira.team.app.TeamInternalApiService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalTeamControllerTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getTeamDelegatesToLocalInternalApiService() {
        TeamInternalApiService teamInternalApiService = mock(TeamInternalApiService.class);
        InternalTeamController controller = new InternalTeamController(teamInternalApiService);
        TeamSummaryDTO summary = new TeamSummaryDTO();
        summary.setId(21L);
        summary.setTeamName("AI Team");
        authenticateInternalService();
        when(teamInternalApiService.getTeam(1001L, "user-uuid-1001", 21L)).thenReturn(summary);

        TeamSummaryDTO result = controller.getTeam(1001L, "user-uuid-1001", 21L);

        assertThat(result).isSameAs(summary);
        verify(teamInternalApiService).getTeam(1001L, "user-uuid-1001", 21L);
    }

    @Test
    void listActiveMembersRejectsMissingInternalServicePrincipalBeforeServiceLookup() {
        TeamInternalApiService teamInternalApiService = mock(TeamInternalApiService.class);
        InternalTeamController controller = new InternalTeamController(teamInternalApiService);

        assertThatThrownBy(() -> controller.listActiveMembers(1001L, "user-uuid-1001", 21L))
                .isInstanceOf(BizException.class);

        verify(teamInternalApiService, never()).listActiveMembers(1001L, "user-uuid-1001", 21L);
    }

    @Test
    void membershipChecksDelegateToLocalInternalApiService() {
        TeamInternalApiService teamInternalApiService = mock(TeamInternalApiService.class);
        InternalTeamController controller = new InternalTeamController(teamInternalApiService);
        TeamMemberDTO member = new TeamMemberDTO();
        member.setUserId(1001L);
        member.setRole("OWNER");
        authenticateInternalService();
        when(teamInternalApiService.requireActiveMember(21L, 1001L, "user-uuid-1001")).thenReturn(member);
        when(teamInternalApiService.isTeamOwner(21L, 1001L, "user-uuid-1001")).thenReturn(true);
        when(teamInternalApiService.isTeamAdmin(21L, 1001L, "user-uuid-1001")).thenReturn(true);
        when(teamInternalApiService.isTeamManager(21L, 1001L, "user-uuid-1001")).thenReturn(true);

        List<Object> results = List.of(
                controller.requireActiveMember(21L, 1001L, "user-uuid-1001"),
                controller.isTeamOwner(21L, 1001L, "user-uuid-1001"),
                controller.isTeamAdmin(21L, 1001L, "user-uuid-1001"),
                controller.isTeamManager(21L, 1001L, "user-uuid-1001")
        );

        assertThat(results).hasSize(4);
        verify(teamInternalApiService).requireActiveMember(21L, 1001L, "user-uuid-1001");
        verify(teamInternalApiService).isTeamOwner(21L, 1001L, "user-uuid-1001");
        verify(teamInternalApiService).isTeamAdmin(21L, 1001L, "user-uuid-1001");
        verify(teamInternalApiService).isTeamManager(21L, 1001L, "user-uuid-1001");
    }

    private void authenticateInternalService() {
        CurrentUser internalService = new CurrentUser(0L, "internal-service", "internal", 0, false, java.util.Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(internalService, "internal-token", java.util.Set.of())
        );
    }
}
