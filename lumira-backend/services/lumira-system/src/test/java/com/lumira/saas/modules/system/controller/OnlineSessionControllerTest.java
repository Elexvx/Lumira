package com.lumira.saas.modules.system.controller;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.app.OnlineSessionManagementAppService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OnlineSessionControllerTest {

    @Test
    void listShouldRejectWhenLiveSnapshotRevokesViewPermissionBeforeDelegating() {
        OnlineSessionManagementAppService onlineSessionManagementAppService = mock(OnlineSessionManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        CurrentUser currentUser = currentUser("system:online-user:view");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(permissionSnapshotService.isTrustedActiveUser(2001L, "user-uuid-2001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(2001L, "user-uuid-2001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("system:online-user:kick")));
        OnlineSessionController controller = new OnlineSessionController(
                onlineSessionManagementAppService,
                securityContextFacade,
                new PermissionGuard(),
                permissionSnapshotService
        );

        assertThatThrownBy(() -> controller.list(1, 10))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(onlineSessionManagementAppService, never()).listOnlineSessions(any(), anyLong(), anyLong());
    }

    @Test
    void listShouldRejectRevokedSessionTicketBeforeDelegating() {
        OnlineSessionManagementAppService onlineSessionManagementAppService = mock(OnlineSessionManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        CurrentUser currentUser = currentUser("system:online-user:view");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(sessionAuthenticationService.authenticateSessionTicket(anyString(), anyLong(), anyString(), any(), any(), any()))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Session expired"));
        OnlineSessionController controller = new OnlineSessionController(
                onlineSessionManagementAppService,
                securityContextFacade,
                new PermissionGuard(),
                null,
                sessionAuthenticationService
        );

        assertThatThrownBy(() -> controller.list(1, 10))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        verify(onlineSessionManagementAppService, never()).listOnlineSessions(any(), anyLong(), anyLong());
    }

    @Test
    void listShouldRejectWhenTrustedPermissionSnapshotIsUnavailable() {
        OnlineSessionManagementAppService onlineSessionManagementAppService = mock(OnlineSessionManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        CurrentUser currentUser = currentUser("system:online-user:view");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(permissionSnapshotService.isTrustedActiveUser(2001L, "user-uuid-2001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(2001L, "user-uuid-2001")).thenReturn(null);
        OnlineSessionController controller = new OnlineSessionController(
                onlineSessionManagementAppService,
                securityContextFacade,
                new PermissionGuard(),
                permissionSnapshotService,
                null,
                null
        );

        assertThatThrownBy(() -> controller.list(1, 10))
                .isInstanceOf(BizException.class)
                .satisfies(error -> {
                    BizException exception = (BizException) error;
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(exception.getMessage()).contains("Trusted user permission snapshot is unavailable");
                });
        verify(onlineSessionManagementAppService, never()).listOnlineSessions(any(), anyLong(), anyLong());
    }

    @Test
    void listShouldRejectTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        OnlineSessionManagementAppService onlineSessionManagementAppService = mock(OnlineSessionManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        CurrentUser currentUser = currentUser("system:online-user:view");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        OnlineSessionController controller = new OnlineSessionController(
                onlineSessionManagementAppService,
                securityContextFacade,
                new PermissionGuard(),
                null,
                null,
                null
        );

        assertThatThrownBy(() -> controller.list(1, 10))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        verify(onlineSessionManagementAppService, never()).listOnlineSessions(any(), anyLong(), anyLong());
    }

    @Test
    void listShouldRejectBlankLiveUsernameBeforeDelegating() {
        OnlineSessionManagementAppService onlineSessionManagementAppService = mock(OnlineSessionManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        CurrentUser currentUser = currentUser("system:online-user:view");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(permissionSnapshotService.isTrustedActiveUser(2001L, "user-uuid-2001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(2001L, "user-uuid-2001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("system:online-user:view")));
        when(systemInternalApi.findUserIdentityById(2001L))
                .thenReturn(userSnapshot(2001L, "user-uuid-2001", " ", "ENABLED"));
        OnlineSessionController controller = new OnlineSessionController(
                onlineSessionManagementAppService,
                securityContextFacade,
                new PermissionGuard(),
                permissionSnapshotService,
                systemInternalApi,
                null
        );

        assertThatThrownBy(() -> controller.list(1, 10))
                .isInstanceOf(BizException.class)
                .satisfies(error -> {
                    BizException exception = (BizException) error;
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(exception.getMessage()).contains("Trusted user username is unavailable");
                });
        verify(onlineSessionManagementAppService, never()).listOnlineSessions(any(), anyLong(), anyLong());
    }

    @Test
    void refreshTrustedCurrentUserShouldNormalizeInvalidSimulatedRoleIdBeforeSnapshotLoad() throws Exception {
        OnlineSessionManagementAppService onlineSessionManagementAppService = mock(OnlineSessionManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        CurrentUser currentUser = currentUser("system:online-user:view");
        currentUser.setSimulatedRoleId(0L);
        when(systemInternalApi.findUserIdentityById(2001L))
                .thenReturn(userSnapshot(2001L, "user-uuid-2001", "admin-live", "ENABLED"));
        when(permissionSnapshotService.isTrustedActiveUser(2001L, "user-uuid-2001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(2001L, "user-uuid-2001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("system:online-user:view")));
        OnlineSessionController controller = new OnlineSessionController(
                onlineSessionManagementAppService,
                securityContextFacade,
                new PermissionGuard(),
                permissionSnapshotService,
                systemInternalApi,
                null
        );
        Method method = OnlineSessionController.class.getDeclaredMethod("refreshTrustedCurrentUser", CurrentUser.class);
        method.setAccessible(true);

        method.invoke(controller, currentUser);

        assertThat(currentUser.getSimulatedRoleId()).isNull();
        verify(permissionSnapshotService).loadSnapshot(2001L, "user-uuid-2001");
        verify(permissionSnapshotService, never()).loadGrantedRoleSnapshot(anyLong(), anyString(), anyLong());
    }

    private static CurrentUser currentUser(String permission) {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(2001L);
        currentUser.setUserUuid("user-uuid-2001");
        currentUser.setUsername("admin");
        currentUser.setSessionId("session-1");
        currentUser.setSessionVersion(1);
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setAuthenticated(true);
        currentUser.setPermissions(Set.of(permission));
        return currentUser;
    }

    private static SystemUserSnapshotDTO userSnapshot(Long userId, String userUuid, String username, String status) {
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
