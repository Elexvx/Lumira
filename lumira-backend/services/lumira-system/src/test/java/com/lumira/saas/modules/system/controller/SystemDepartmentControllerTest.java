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
import com.lumira.saas.modules.system.department.app.SystemDepartmentAppService;
import com.lumira.saas.modules.system.department.dto.DepartmentUpsertRequest;
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

class SystemDepartmentControllerTest {

    @Test
    void createShouldRejectWhenLiveSnapshotRevokesCreatePermissionBeforeDelegating() {
        SystemDepartmentAppService departmentAppService = mock(SystemDepartmentAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        CurrentUser currentUser = currentUser("system:department:create");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(permissionSnapshotService.isTrustedActiveUser(2001L, "user-uuid-2001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(2001L, "user-uuid-2001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("system:department:view")));
        SystemDepartmentController controller = new SystemDepartmentController(
                departmentAppService,
                securityContextFacade,
                new PermissionGuard(),
                permissionSnapshotService
        );

        assertThatThrownBy(() -> controller.create(new DepartmentUpsertRequest()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(departmentAppService, never()).createDepartment(any(), any());
    }

    @Test
    void createShouldRejectRevokedSessionTicketBeforeDelegating() {
        SystemDepartmentAppService departmentAppService = mock(SystemDepartmentAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        CurrentUser currentUser = currentUser("system:department:create");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(sessionAuthenticationService.authenticateSessionTicket(anyString(), anyLong(), anyString(), any(), any(), any()))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Session expired"));
        SystemDepartmentController controller = new SystemDepartmentController(
                departmentAppService,
                securityContextFacade,
                new PermissionGuard(),
                null,
                sessionAuthenticationService
        );

        assertThatThrownBy(() -> controller.create(new DepartmentUpsertRequest()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        verify(departmentAppService, never()).createDepartment(any(), any());
    }

    @Test
    void createShouldRejectWhenTrustedPermissionSnapshotIsUnavailable() {
        SystemDepartmentAppService departmentAppService = mock(SystemDepartmentAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        CurrentUser currentUser = currentUser("system:department:create");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(permissionSnapshotService.isTrustedActiveUser(2001L, "user-uuid-2001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(2001L, "user-uuid-2001")).thenReturn(null);
        SystemDepartmentController controller = new SystemDepartmentController(
                departmentAppService,
                securityContextFacade,
                new PermissionGuard(),
                permissionSnapshotService,
                null,
                null
        );

        assertThatThrownBy(() -> controller.create(new DepartmentUpsertRequest()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> {
                    BizException exception = (BizException) error;
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(exception.getMessage()).contains("Trusted user permission snapshot is unavailable");
                });
        verify(departmentAppService, never()).createDepartment(any(), any());
    }

    @Test
    void createShouldRejectTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        SystemDepartmentAppService departmentAppService = mock(SystemDepartmentAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        CurrentUser currentUser = currentUser("system:department:create");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        SystemDepartmentController controller = new SystemDepartmentController(
                departmentAppService,
                securityContextFacade,
                new PermissionGuard(),
                null,
                null,
                null
        );

        assertThatThrownBy(() -> controller.create(new DepartmentUpsertRequest()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user resolver is unavailable");
        verify(departmentAppService, never()).createDepartment(any(), any());
    }

    @Test
    void createShouldRejectBlankLiveUsernameBeforeDelegating() {
        SystemDepartmentAppService departmentAppService = mock(SystemDepartmentAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        CurrentUser currentUser = currentUser("system:department:create");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(permissionSnapshotService.isTrustedActiveUser(2001L, "user-uuid-2001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(2001L, "user-uuid-2001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("system:department:create")));
        when(systemInternalApi.findUserIdentityById(2001L))
                .thenReturn(userSnapshot(2001L, "user-uuid-2001", " ", "ENABLED"));
        SystemDepartmentController controller = new SystemDepartmentController(
                departmentAppService,
                securityContextFacade,
                new PermissionGuard(),
                permissionSnapshotService,
                systemInternalApi,
                null
        );

        assertThatThrownBy(() -> controller.create(new DepartmentUpsertRequest()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> {
                    BizException exception = (BizException) error;
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(exception.getMessage()).contains("Trusted user username is unavailable");
                });
        verify(departmentAppService, never()).createDepartment(any(), any());
    }

    @Test
    void refreshTrustedCurrentUserShouldNormalizeInvalidSimulatedRoleIdBeforeSnapshotLoad() throws Exception {
        SystemDepartmentAppService departmentAppService = mock(SystemDepartmentAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        CurrentUser currentUser = currentUser("system:department:create");
        currentUser.setSimulatedRoleId(0L);
        when(systemInternalApi.findUserIdentityById(2001L))
                .thenReturn(userSnapshot(2001L, "user-uuid-2001", "admin-live", "ENABLED"));
        when(permissionSnapshotService.isTrustedActiveUser(2001L, "user-uuid-2001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(2001L, "user-uuid-2001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("system:department:create")));
        SystemDepartmentController controller = new SystemDepartmentController(
                departmentAppService,
                securityContextFacade,
                new PermissionGuard(),
                permissionSnapshotService,
                systemInternalApi,
                null
        );
        Method method = SystemDepartmentController.class.getDeclaredMethod("refreshTrustedCurrentUser", CurrentUser.class);
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
