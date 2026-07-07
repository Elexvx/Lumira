package com.lumira.saas.modules.system.update.controller;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.update.app.PlatformUpdateAppService;
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

class PlatformUpdateControllerTest {

    @Test
    void statusShouldRejectWhenLiveSnapshotRevokesViewPermissionBeforeDelegating() {
        PlatformUpdateAppService platformUpdateAppService = mock(PlatformUpdateAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        CurrentUser currentUser = currentUser("system:update:view");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(permissionSnapshotService.isTrustedActiveUser(2001L, "user-uuid-2001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(2001L, "user-uuid-2001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("system:update:check")));
        PlatformUpdateController controller = new PlatformUpdateController(
                platformUpdateAppService,
                securityContextFacade,
                new PermissionGuard(),
                permissionSnapshotService
        );

        assertThatThrownBy(controller::status)
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(platformUpdateAppService, never()).getStatus(any());
    }

    @Test
    void statusShouldRejectWhenTrustedPermissionSnapshotIsUnavailable() {
        PlatformUpdateAppService platformUpdateAppService = mock(PlatformUpdateAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        CurrentUser currentUser = currentUser("system:update:view");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(permissionSnapshotService.isTrustedActiveUser(2001L, "user-uuid-2001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(2001L, "user-uuid-2001")).thenReturn(null);
        PlatformUpdateController controller = new PlatformUpdateController(
                platformUpdateAppService,
                securityContextFacade,
                new PermissionGuard(),
                permissionSnapshotService,
                null,
                null
        );

        assertThatThrownBy(controller::status)
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user permission snapshot is unavailable");
        verify(platformUpdateAppService, never()).getStatus(any());
    }

    @Test
    void statusShouldRejectRevokedSessionTicketBeforeDelegating() {
        PlatformUpdateAppService platformUpdateAppService = mock(PlatformUpdateAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        CurrentUser currentUser = currentUser("system:update:view");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(sessionAuthenticationService.authenticateSessionTicket(anyString(), anyLong(), anyString(), any(), any(), any()))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Session expired"));
        PlatformUpdateController controller = new PlatformUpdateController(
                platformUpdateAppService,
                securityContextFacade,
                new PermissionGuard(),
                null,
                sessionAuthenticationService
        );

        assertThatThrownBy(controller::status)
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        verify(platformUpdateAppService, never()).getStatus(any());
    }

    @Test
    void statusShouldRejectTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        PlatformUpdateAppService platformUpdateAppService = mock(PlatformUpdateAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        CurrentUser currentUser = currentUser("system:update:view");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        PlatformUpdateController controller = new PlatformUpdateController(
                platformUpdateAppService,
                securityContextFacade,
                new PermissionGuard(),
                null,
                null,
                null
        );

        assertThatThrownBy(controller::status)
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user resolver is unavailable");
        verify(platformUpdateAppService, never()).getStatus(any());
    }

    @Test
    void statusShouldRejectTrustedUserWhenLiveUsernameIsUnavailable() {
        PlatformUpdateAppService platformUpdateAppService = mock(PlatformUpdateAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        CurrentUser currentUser = currentUser("system:update:view");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(systemInternalApi.findUserIdentityById(2001L)).thenReturn(
                new SystemUserSnapshotDTO(2001L, "user-uuid-2001", " ", null, "ENABLED", null, null, null, null, null, null, null, null, null, null, null)
        );
        PlatformUpdateController controller = new PlatformUpdateController(
                platformUpdateAppService,
                securityContextFacade,
                new PermissionGuard(),
                permissionSnapshotService,
                systemInternalApi,
                null
        );

        assertThatThrownBy(controller::status)
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user username is unavailable");
        verify(platformUpdateAppService, never()).getStatus(any());
        verify(permissionSnapshotService, never()).isTrustedActiveUser(2001L, "user-uuid-2001");
    }

    @Test
    void refreshTrustedCurrentUserShouldNormalizeInvalidSimulatedRoleIdBeforeSnapshotLoad() throws Exception {
        PlatformUpdateAppService platformUpdateAppService = mock(PlatformUpdateAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        CurrentUser currentUser = currentUser("system:update:view");
        currentUser.setSimulatedRoleId(0L);
        when(systemInternalApi.findUserIdentityById(2001L)).thenReturn(
                new SystemUserSnapshotDTO(2001L, "user-uuid-2001", "admin-live", null, "ENABLED", null, null, null, null, null, null, null, null, null, null, null)
        );
        when(permissionSnapshotService.isTrustedActiveUser(2001L, "user-uuid-2001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(2001L, "user-uuid-2001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("system:update:view")));
        PlatformUpdateController controller = new PlatformUpdateController(
                platformUpdateAppService,
                securityContextFacade,
                new PermissionGuard(),
                permissionSnapshotService,
                systemInternalApi,
                null
        );
        Method method = PlatformUpdateController.class.getDeclaredMethod("refreshTrustedCurrentUser", CurrentUser.class);
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
}
