package com.lumira.saas.modules.system.controller;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.app.SystemManagementAppService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DashboardControllerTest {

    @Test
    void summaryShouldRejectWhenLiveSnapshotMarksUserInactiveBeforeDelegating() {
        SystemManagementAppService appService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        CurrentUser currentUser = new CurrentUser(42L, "alice", "session-1", 1, true, Set.of("dashboard:view"));
        currentUser.setUserUuid("user-uuid-42");
        currentUser.setPermissionsVersion("permissions-1");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(permissionSnapshotService.isTrustedActiveUser(42L, "user-uuid-42")).thenReturn(false);
        DashboardController controller = new DashboardController(appService, securityContextFacade, permissionSnapshotService);

        assertThatThrownBy(controller::summary)
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(appService);
    }

    @Test
    void summaryShouldRejectRevokedSessionTicketBeforeDelegating() {
        SystemManagementAppService appService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        CurrentUser currentUser = new CurrentUser(42L, "alice", "session-1", 1, true, Set.of("dashboard:view"));
        currentUser.setUserUuid("user-uuid-42");
        currentUser.setPermissionsVersion("permissions-1");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(sessionAuthenticationService.authenticateSessionTicket(any(), any(), any(), any(), any(), any()))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Session expired"));
        DashboardController controller = new DashboardController(appService, securityContextFacade, null, sessionAuthenticationService);

        assertThatThrownBy(controller::summary)
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(appService);
    }

    @Test
    void summaryShouldRejectWhenTrustedPermissionSnapshotIsUnavailable() {
        SystemManagementAppService appService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        CurrentUser currentUser = new CurrentUser(42L, "alice", "session-1", 1, true, Set.of("dashboard:view"));
        currentUser.setUserUuid("user-uuid-42");
        currentUser.setPermissionsVersion("permissions-1");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(permissionSnapshotService.isTrustedActiveUser(42L, "user-uuid-42")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(42L, "user-uuid-42")).thenReturn(null);
        DashboardController controller = new DashboardController(appService, securityContextFacade, permissionSnapshotService, null, null);

        assertThatThrownBy(controller::summary)
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(exception.getMessage()).contains("Trusted user permission snapshot is unavailable");
                });

        verifyNoInteractions(appService);
    }

    @Test
    void summaryShouldRejectTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        SystemManagementAppService appService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        CurrentUser currentUser = new CurrentUser(42L, "alice", "session-1", 1, true, Set.of("dashboard:view"));
        currentUser.setUserUuid("user-uuid-42");
        currentUser.setPermissionsVersion("permissions-1");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        DashboardController controller = new DashboardController(appService, securityContextFacade, null, null, null);

        assertThatThrownBy(controller::summary)
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(appService);
    }

    @Test
    void summaryShouldRejectBlankLiveUsernameBeforeDelegating() {
        SystemManagementAppService appService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        CurrentUser currentUser = new CurrentUser(42L, "alice", "session-1", 1, true, Set.of("dashboard:view"));
        currentUser.setUserUuid("user-uuid-42");
        currentUser.setPermissionsVersion("permissions-1");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(permissionSnapshotService.isTrustedActiveUser(42L, "user-uuid-42")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(42L, "user-uuid-42"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("dashboard:view")));
        when(systemInternalApi.findUserIdentityById(42L))
                .thenReturn(userSnapshot(42L, "user-uuid-42", " ", "ENABLED"));
        DashboardController controller = new DashboardController(
                appService,
                securityContextFacade,
                permissionSnapshotService,
                systemInternalApi,
                null
        );

        assertThatThrownBy(controller::summary)
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(exception.getMessage()).contains("Trusted user username is unavailable");
                });

        verifyNoInteractions(appService);
    }

    @Test
    void refreshTrustedCurrentUserShouldNormalizeInvalidSimulatedRoleIdBeforeSnapshotLoad() throws Exception {
        SystemManagementAppService appService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        CurrentUser currentUser = new CurrentUser(42L, "alice", "session-1", 1, true, Set.of("dashboard:view"));
        currentUser.setUserUuid("user-uuid-42");
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setSimulatedRoleId(0L);
        when(permissionSnapshotService.isTrustedActiveUser(42L, "user-uuid-42")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(42L, "user-uuid-42"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("dashboard:view")));
        when(systemInternalApi.findUserIdentityById(42L))
                .thenReturn(userSnapshot(42L, "user-uuid-42", "alice-live", "ENABLED"));
        DashboardController controller = new DashboardController(
                appService,
                securityContextFacade,
                permissionSnapshotService,
                systemInternalApi,
                null
        );
        Method method = DashboardController.class.getDeclaredMethod("refreshTrustedCurrentUser", CurrentUser.class);
        method.setAccessible(true);

        method.invoke(controller, currentUser);

        assertThat(currentUser.getSimulatedRoleId()).isNull();
        verify(permissionSnapshotService).loadSnapshot(42L, "user-uuid-42");
        verify(permissionSnapshotService, never()).loadGrantedRoleSnapshot(any(), anyString(), any());
    }

    private SystemUserSnapshotDTO userSnapshot(Long userId, String userUuid, String username, String status) {
        return new SystemUserSnapshotDTO(
                userId,
                userUuid,
                username,
                null,
                status,
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
                null
        );
    }
}
