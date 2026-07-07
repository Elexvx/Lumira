package com.lumira.saas.modules.system.controller;

import com.lumira.api.client.FileInternalApi;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.app.SystemManagementAppService;
import com.lumira.saas.modules.system.dict.app.DictRuntimeService;
import com.lumira.saas.modules.system.export.ExportTaskService;
import com.lumira.saas.modules.system.user.app.UserExportAppService;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SystemControllerTest {

    @Test
    void permissionsShouldRejectWhenTrustedPermissionSnapshotIsUnavailable() {
        SystemManagementAppService systemManagementAppService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        CurrentUser currentUser = currentUser();
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(permissionSnapshotService.isTrustedActiveUser(42L, "user-uuid-42")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(42L, "user-uuid-42")).thenReturn(null);
        SystemController controller = new SystemController(
                systemManagementAppService,
                securityContextFacade,
                new PermissionGuard(),
                mock(FileInternalApi.class),
                mock(UserExportAppService.class),
                mock(ExportTaskService.class),
                mock(DictRuntimeService.class),
                permissionSnapshotService,
                null,
                null
        );

        assertThatThrownBy(controller::permissions)
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(exception.getMessage()).contains("Trusted user permission snapshot is unavailable");
                });

        verifyNoInteractions(systemManagementAppService);
    }

    @Test
    void permissionsShouldRejectTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        SystemManagementAppService systemManagementAppService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        CurrentUser currentUser = currentUser();
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        SystemController controller = new SystemController(
                systemManagementAppService,
                securityContextFacade,
                new PermissionGuard(),
                mock(FileInternalApi.class),
                mock(UserExportAppService.class),
                mock(ExportTaskService.class),
                mock(DictRuntimeService.class),
                null,
                null,
                null
        );

        assertThatThrownBy(controller::permissions)
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(systemManagementAppService);
    }

    @Test
    void permissionsShouldRejectRevokedSessionTicketBeforeDelegating() {
        SystemManagementAppService systemManagementAppService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        CurrentUser currentUser = currentUser();
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(sessionAuthenticationService.authenticateSessionTicket(any(), any(), any(), any(), any(), any()))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Session expired"));
        SystemController controller = new SystemController(
                systemManagementAppService,
                securityContextFacade,
                new PermissionGuard(),
                mock(FileInternalApi.class),
                mock(UserExportAppService.class),
                mock(ExportTaskService.class),
                mock(DictRuntimeService.class),
                null,
                sessionAuthenticationService
        );

        assertThatThrownBy(controller::permissions)
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(systemManagementAppService);
    }

    @Test
    void permissionsShouldRejectBlankLiveUsernameBeforeDelegating() {
        SystemManagementAppService systemManagementAppService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        CurrentUser currentUser = currentUser();
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(permissionSnapshotService.isTrustedActiveUser(42L, "user-uuid-42")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(42L, "user-uuid-42"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("system:role:view")));
        when(systemInternalApi.findUserIdentityById(42L))
                .thenReturn(userSnapshot(42L, "user-uuid-42", " ", "ENABLED"));
        SystemController controller = new SystemController(
                systemManagementAppService,
                securityContextFacade,
                new PermissionGuard(),
                mock(FileInternalApi.class),
                mock(UserExportAppService.class),
                mock(ExportTaskService.class),
                mock(DictRuntimeService.class),
                permissionSnapshotService,
                systemInternalApi,
                null
        );

        assertThatThrownBy(controller::permissions)
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(exception.getMessage()).contains("Trusted user username is unavailable");
                });

        verifyNoInteractions(systemManagementAppService);
    }

    @Test
    void refreshTrustedCurrentUserShouldNormalizeInvalidSimulatedRoleIdBeforeSnapshotLoad() throws Exception {
        SystemManagementAppService systemManagementAppService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        CurrentUser currentUser = currentUser();
        currentUser.setSimulatedRoleId(0L);
        when(permissionSnapshotService.isTrustedActiveUser(42L, "user-uuid-42")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(42L, "user-uuid-42"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("system:role:view")));
        when(systemInternalApi.findUserIdentityById(42L))
                .thenReturn(userSnapshot(42L, "user-uuid-42", "alice-live", "ENABLED"));
        SystemController controller = new SystemController(
                systemManagementAppService,
                securityContextFacade,
                new PermissionGuard(),
                mock(FileInternalApi.class),
                mock(UserExportAppService.class),
                mock(ExportTaskService.class),
                mock(DictRuntimeService.class),
                permissionSnapshotService,
                systemInternalApi,
                null
        );
        Method method = SystemController.class.getDeclaredMethod("refreshTrustedCurrentUser", CurrentUser.class);
        method.setAccessible(true);

        method.invoke(controller, currentUser);

        assertThat(currentUser.getSimulatedRoleId()).isNull();
        verify(permissionSnapshotService).loadSnapshot(42L, "user-uuid-42");
        verify(permissionSnapshotService, never()).loadGrantedRoleSnapshot(anyLong(), anyString(), anyLong());
    }

    private static CurrentUser currentUser() {
        CurrentUser currentUser = new CurrentUser(42L, "alice", null, "session-1", 1, true, Set.of("system:role:view"));
        currentUser.setUserUuid("user-uuid-42");
        currentUser.setPermissionsVersion("permissions-1");
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
