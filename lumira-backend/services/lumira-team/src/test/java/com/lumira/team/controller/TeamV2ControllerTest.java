package com.lumira.team.controller;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.team.app.TeamAppService;
import com.lumira.team.app.TeamInviteService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamV2ControllerTest {

    @Test
    void myTeamsShouldRejectTrustedUserWhenResolverIsUnavailable() {
        TeamAppService teamAppService = mock(TeamAppService.class);
        TeamInviteService teamInviteService = mock(TeamInviteService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        TeamV2Controller controller = new TeamV2Controller(
                teamAppService,
                teamInviteService,
                securityContextFacade,
                permissionGuard,
                null
        );
        CurrentUser currentUser = trustedCurrentUser();
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);

        assertThatThrownBy(controller::myTeams)
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user resolver is unavailable");

        verify(teamAppService, never()).myTeams(currentUser);
    }

    @Test
    void myTeamsShouldRejectTrustedUserWhenLiveUsernameIsUnavailable() {
        TeamAppService teamAppService = mock(TeamAppService.class);
        TeamInviteService teamInviteService = mock(TeamInviteService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        TeamV2Controller controller = new TeamV2Controller(
                teamAppService,
                teamInviteService,
                securityContextFacade,
                permissionGuard,
                systemInternalApi
        );
        CurrentUser currentUser = trustedCurrentUser();
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(systemInternalApi.findUserIdentityById(3001L)).thenReturn(
                new SystemUserSnapshotDTO(3001L, "user-uuid-3001", " ", null, "ENABLED", null, null, null, null, null, null, null, null, null, null, null)
        );

        assertThatThrownBy(controller::myTeams)
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user username is unavailable");

        verify(teamAppService, never()).myTeams(currentUser);
        verify(systemInternalApi, never()).permissionSnapshot(3001L, "user-uuid-3001");
    }

    @Test
    void myTeamsShouldUseSimulatedRolePermissionSnapshot() {
        TeamAppService teamAppService = mock(TeamAppService.class);
        TeamInviteService teamInviteService = mock(TeamInviteService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        TeamV2Controller controller = new TeamV2Controller(
                teamAppService,
                teamInviteService,
                securityContextFacade,
                permissionGuard,
                systemInternalApi
        );
        CurrentUser currentUser = trustedCurrentUser();
        currentUser.setSimulatedRoleId(9L);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(systemInternalApi.findUserIdentityById(3001L)).thenReturn(
                new SystemUserSnapshotDTO(3001L, "user-uuid-3001", "alice", null, "ENABLED", null, null, null, null, null, null, null, null, null, null, null)
        );
        when(systemInternalApi.simulatedRolePermissionSnapshot(3001L, "user-uuid-3001", 9L))
                .thenReturn(permissionSnapshot("team:view"));

        controller.myTeams();

        verify(systemInternalApi).simulatedRolePermissionSnapshot(3001L, "user-uuid-3001", 9L);
        verify(systemInternalApi, never()).permissionSnapshot(3001L, "user-uuid-3001");
        verify(teamAppService).myTeams(currentUser);
    }

    private CurrentUser trustedCurrentUser() {
        CurrentUser currentUser = new CurrentUser(3001L, "alice", "session-1", 1, true, Set.of("team:view"));
        currentUser.setUserUuid("user-uuid-3001");
        currentUser.setPermissionsVersion("permissions-1");
        return currentUser;
    }

    private PermissionSnapshotDTO permissionSnapshot(String permission) {
        return new PermissionSnapshotDTO(
                "permissions-2",
                List.of(permission),
                List.of(9L),
                null,
                List.of(),
                List.of(),
                List.of(),
                "/teams"
        );
    }
}
