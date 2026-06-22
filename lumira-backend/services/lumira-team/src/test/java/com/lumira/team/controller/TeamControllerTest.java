package com.lumira.team.controller;

import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.team.app.TeamAppService;
import com.lumira.team.app.TeamInviteService;
import com.lumira.team.dto.TeamDTO;
import com.lumira.team.vo.TeamVO;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamControllerTest {
    @Test
    void createTeamShouldRequireCurrentUserAndDelegate() {
        TeamAppService appService = mock(TeamAppService.class);
        TeamInviteService inviteService = mock(TeamInviteService.class);
        SecurityContextFacade security = mock(SecurityContextFacade.class);
        TeamV2Controller controller = new TeamV2Controller(appService, inviteService, security, mock(PermissionGuard.class));
        CurrentUser user = currentUser();
        TeamDTO.TeamCreateRequest request = new TeamDTO.TeamCreateRequest();
        request.setTeamName("Core Team");
        TeamVO.Team team = new TeamVO.Team();
        when(security.getCurrentUser()).thenReturn(user);
        when(appService.createTeam(user, request)).thenReturn(team);

        assertThat(controller.createTeam(request).getData()).isSameAs(team);
        verify(appService).createTeam(user, request);
    }

    @Test
    void unauthenticatedUserCannotCreateTeam() {
        TeamV2Controller controller = new TeamV2Controller(mock(TeamAppService.class), mock(TeamInviteService.class), unauthenticatedSecurity(), mock(PermissionGuard.class));
        TeamDTO.TeamCreateRequest request = new TeamDTO.TeamCreateRequest();
        request.setTeamName("Core Team");

        assertThatThrownBy(() -> controller.createTeam(request))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void privateTeamDetailDelegatesToServiceForMembershipCheck() {
        TeamAppService appService = mock(TeamAppService.class);
        SecurityContextFacade security = mock(SecurityContextFacade.class);
        TeamV2Controller controller = new TeamV2Controller(appService, mock(TeamInviteService.class), security, mock(PermissionGuard.class));
        CurrentUser user = currentUser();
        TeamVO.Team team = new TeamVO.Team();
        when(security.getCurrentUser()).thenReturn(user);
        when(appService.getTeam(user, 2001L)).thenReturn(team);

        assertThat(controller.team(2001L).getData()).isSameAs(team);
        verify(appService).getTeam(user, 2001L);
    }

    @Test
    void memberEndpointsDelegateWithAuthenticatedUser() {
        TeamAppService appService = mock(TeamAppService.class);
        SecurityContextFacade security = mock(SecurityContextFacade.class);
        TeamV2Controller controller = new TeamV2Controller(appService, mock(TeamInviteService.class), security, mock(PermissionGuard.class));
        CurrentUser user = currentUser();
        when(security.getCurrentUser()).thenReturn(user);
        when(appService.listMembers(user, 2001L)).thenReturn(List.of());

        assertThat(controller.members(2001L).getData()).isEmpty();
        verify(appService).listMembers(user, 2001L);
    }

    @Test
    void adminTeamListRequiresPermissionAndDelegates() {
        TeamAppService appService = mock(TeamAppService.class);
        SecurityContextFacade security = mock(SecurityContextFacade.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        TeamV2Controller controller = new TeamV2Controller(appService, mock(TeamInviteService.class), security, permissionGuard);
        CurrentUser user = currentUser();
        when(security.getCurrentUser()).thenReturn(user);
        when(appService.listTeamsForAdmin(user)).thenReturn(List.of());

        assertThat(controller.adminTeams().getData()).isEmpty();
        verify(permissionGuard).requirePermission(user, "team:view");
        verify(appService).listTeamsForAdmin(user);
    }

    private SecurityContextFacade unauthenticatedSecurity() {
        SecurityContextFacade security = mock(SecurityContextFacade.class);
        when(security.getCurrentUser()).thenThrow(new AuthenticationCredentialsNotFoundException("User not authenticated"));
        return security;
    }

    private CurrentUser currentUser() {
        CurrentUser user = new CurrentUser();
        user.setUserId(3001L);
        user.setUsername("admin");
        user.setCurrentTenantId(1001L);
        user.setAuthenticated(true);
        return user;
    }
}
