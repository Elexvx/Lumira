package com.lumira.team.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamControllerTest {
    @Test
    void createTeamShouldRequireCurrentUserAndDelegate() {
        TeamAppService appService = mock(TeamAppService.class);
        TeamInviteService inviteService = mock(TeamInviteService.class);
        SecurityContextFacade security = mock(SecurityContextFacade.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        TeamV2Controller controller = new TeamV2Controller(appService, inviteService, security, permissionGuard);
        CurrentUser user = currentUser();
        TeamDTO.TeamCreateRequest request = new TeamDTO.TeamCreateRequest();
        request.setTeamName("Core Team");
        TeamVO.Team team = new TeamVO.Team();
        when(security.getCurrentUser()).thenReturn(user);
        when(appService.createTeam(user, request)).thenReturn(team);

        assertThat(controller.createTeam(request).getData()).isSameAs(team);
        verify(permissionGuard).requirePermission(user, "team:create");
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
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        TeamV2Controller controller = new TeamV2Controller(appService, mock(TeamInviteService.class), security, permissionGuard);
        CurrentUser user = currentUser();
        when(security.getCurrentUser()).thenReturn(user);
        when(appService.listMembers(user, 2001L)).thenReturn(List.of());

        assertThat(controller.members(2001L).getData()).isEmpty();
        verify(permissionGuard).requirePermission(user, "team:member:view");
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

    @Test
    void adminTeamListDoesNotExposeOwnerUserId() throws Exception {
        TeamAppService appService = mock(TeamAppService.class);
        SecurityContextFacade security = mock(SecurityContextFacade.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        TeamV2Controller controller = new TeamV2Controller(appService, mock(TeamInviteService.class), security, permissionGuard);
        CurrentUser user = currentUser();
        TeamVO.Team team = new TeamVO.Team();
        team.setId(2001L);
        team.setOwnerUserId(1001L);
        when(security.getCurrentUser()).thenReturn(user);
        when(appService.listTeamsForAdmin(user)).thenReturn(List.of(team));

        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(controller.adminTeams());

        assertThat(json).doesNotContain("ownerUserId");
        assertThat(team.getOwnerUserId()).isEqualTo(1001L);
    }

    @Test
    void teamWriteEndpointsRequireActionPermissions() {
        TeamAppService appService = mock(TeamAppService.class);
        SecurityContextFacade security = mock(SecurityContextFacade.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        TeamV2Controller controller = new TeamV2Controller(appService, mock(TeamInviteService.class), security, permissionGuard);
        CurrentUser user = currentUser();
        when(security.getCurrentUser()).thenReturn(user);

        TeamDTO.TeamUpdateRequest updateRequest = new TeamDTO.TeamUpdateRequest();
        TeamVO.Team team = new TeamVO.Team();
        when(appService.updateTeam(user, 2001L, updateRequest)).thenReturn(team);
        when(appService.updateTeamForAdmin(user, 2001L, updateRequest)).thenReturn(team);
        when(appService.deleteTeam(user, 2001L)).thenReturn(true);
        when(appService.deleteTeamForAdmin(user, 2001L)).thenReturn(true);

        assertThat(controller.updateTeam(2001L, updateRequest).getData()).isSameAs(team);
        assertThat(controller.adminUpdateTeam(2001L, updateRequest).getData()).isSameAs(team);
        assertThat(controller.deleteTeam(2001L).getData()).isTrue();
        assertThat(controller.adminDeleteTeam(2001L).getData()).isTrue();

        verify(permissionGuard, times(2)).requirePermission(user, "team:update");
        verify(permissionGuard, times(2)).requirePermission(user, "team:delete");
        verify(appService).updateTeam(user, 2001L, updateRequest);
        verify(appService).updateTeamForAdmin(user, 2001L, updateRequest);
        verify(appService).deleteTeam(user, 2001L);
        verify(appService).deleteTeamForAdmin(user, 2001L);
    }

    @Test
    void memberWriteEndpointsRequireMemberActionPermissions() {
        TeamAppService appService = mock(TeamAppService.class);
        SecurityContextFacade security = mock(SecurityContextFacade.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        TeamV2Controller controller = new TeamV2Controller(appService, mock(TeamInviteService.class), security, permissionGuard);
        CurrentUser user = currentUser();
        when(security.getCurrentUser()).thenReturn(user);

        TeamDTO.MemberRoleRequest roleRequest = new TeamDTO.MemberRoleRequest();
        roleRequest.setRole("MANAGER");
        TeamDTO.MemberCreateRequest createRequest = new TeamDTO.MemberCreateRequest();
        createRequest.setMemberName("Draft Member");
        TeamDTO.TransferOwnerRequest transferRequest = new TeamDTO.TransferOwnerRequest();
        transferRequest.setMemberId(3002L);
        TeamVO.Member member = new TeamVO.Member();
        TeamVO.Team team = new TeamVO.Team();
        when(appService.addMember(user, 2001L, createRequest)).thenReturn(member);
        when(appService.updateMemberRole(user, 2001L, 3002L, roleRequest)).thenReturn(member);
        when(appService.removeMember(user, 2001L, 3002L)).thenReturn(true);
        when(appService.transferOwner(user, 2001L, transferRequest)).thenReturn(team);

        assertThat(controller.addMember(2001L, createRequest).getData()).isSameAs(member);
        assertThat(controller.updateMemberRole(2001L, 3002L, roleRequest).getData()).isSameAs(member);
        assertThat(controller.removeMember(2001L, 3002L).getData()).isTrue();
        assertThat(controller.transferOwner(2001L, transferRequest).getData()).isSameAs(team);

        verify(permissionGuard, times(2)).requirePermission(user, "team:member:role-update");
        verify(permissionGuard).requirePermission(user, "team:member:invite");
        verify(permissionGuard).requirePermission(user, "team:member:remove");
        verify(appService).addMember(user, 2001L, createRequest);
        verify(appService).updateMemberRole(user, 2001L, 3002L, roleRequest);
        verify(appService).removeMember(user, 2001L, 3002L);
        verify(appService).transferOwner(user, 2001L, transferRequest);
    }

    @Test
    void inviteReviewEndpointsRequireInvitePermission() {
        TeamInviteService inviteService = mock(TeamInviteService.class);
        SecurityContextFacade security = mock(SecurityContextFacade.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        TeamV2Controller controller = new TeamV2Controller(mock(TeamAppService.class), inviteService, security, permissionGuard);
        CurrentUser user = currentUser();
        when(security.getCurrentUser()).thenReturn(user);

        TeamDTO.InviteCreateRequest inviteRequest = new TeamDTO.InviteCreateRequest();
        TeamDTO.JoinReviewRequest reviewRequest = new TeamDTO.JoinReviewRequest();
        TeamVO.Invite invite = new TeamVO.Invite();
        TeamVO.JoinRequest joinRequest = new TeamVO.JoinRequest();
        when(inviteService.createInvite(user, 2001L, inviteRequest)).thenReturn(invite);
        when(inviteService.listInvites(user, 2001L)).thenReturn(List.of(invite));
        when(inviteService.disableInvite(user, 2001L, 4001L)).thenReturn(true);
        when(inviteService.listJoinRequests(user, 2001L)).thenReturn(List.of(joinRequest));
        when(inviteService.approveJoinRequest(user, 2001L, 5001L, reviewRequest)).thenReturn(joinRequest);
        when(inviteService.rejectJoinRequest(user, 2001L, 5001L, reviewRequest)).thenReturn(joinRequest);

        assertThat(controller.createInvite(2001L, inviteRequest).getData()).isSameAs(invite);
        assertThat(controller.invites(2001L).getData()).containsExactly(invite);
        assertThat(controller.disableInvite(2001L, 4001L).getData()).isTrue();
        assertThat(controller.joinRequests(2001L).getData()).containsExactly(joinRequest);
        assertThat(controller.approveJoinRequest(2001L, 5001L, reviewRequest).getData()).isSameAs(joinRequest);
        assertThat(controller.rejectJoinRequest(2001L, 5001L, reviewRequest).getData()).isSameAs(joinRequest);

        verify(permissionGuard, times(6)).requirePermission(user, "team:member:invite");
        verify(inviteService).createInvite(user, 2001L, inviteRequest);
        verify(inviteService).listInvites(user, 2001L);
        verify(inviteService).disableInvite(user, 2001L, 4001L);
        verify(inviteService).listJoinRequests(user, 2001L);
        verify(inviteService).approveJoinRequest(user, 2001L, 5001L, reviewRequest);
        verify(inviteService).rejectJoinRequest(user, 2001L, 5001L, reviewRequest);
    }

    private SecurityContextFacade unauthenticatedSecurity() {
        SecurityContextFacade security = mock(SecurityContextFacade.class);
        when(security.getCurrentUser()).thenThrow(new AuthenticationCredentialsNotFoundException("User not authenticated"));
        return security;
    }

    private CurrentUser currentUser() {
        CurrentUser user = new CurrentUser();
        user.setUserId(3001L);
        user.setUserUuid("user-uuid-3001");
        user.setUsername("admin");
        user.setSessionId("session-3001");
        user.setSessionVersion(1);
        user.setPermissionsVersion("permissions-1");
        user.setAuthenticated(true);
        return user;
    }
}
