package com.lumira.saas.modules.project.controller;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.project.app.ProjectManagementAppService;
import com.lumira.saas.modules.project.dto.ProjectDTO;
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
import static org.mockito.Mockito.when;

class ProjectV2ControllerTest {

    @Test
    void createProjectShouldRejectTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        ProjectManagementAppService projectManagementAppService = mock(ProjectManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        CurrentUser currentUser = trustedCurrentUser("aiadc:project:create");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        ProjectV2Controller controller = new ProjectV2Controller(
                projectManagementAppService,
                securityContextFacade,
                new PermissionGuard(),
                null,
                null,
                null
        );

        assertThatThrownBy(() -> controller.createProject(new ProjectDTO.ProjectUpsertRequest()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user resolver is unavailable");
        verify(projectManagementAppService, never()).createProject(any(), any());
    }

    @Test
    void createProjectShouldRejectWhenTrustedPermissionSnapshotIsUnavailable() {
        ProjectManagementAppService projectManagementAppService = mock(ProjectManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        CurrentUser currentUser = trustedCurrentUser("aiadc:project:create");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(permissionSnapshotService.isTrustedActiveUser(4301L, "user-uuid-4301")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(4301L, "user-uuid-4301")).thenReturn(null);
        ProjectV2Controller controller = new ProjectV2Controller(
                projectManagementAppService,
                securityContextFacade,
                new PermissionGuard(),
                permissionSnapshotService,
                null,
                null
        );

        assertThatThrownBy(() -> controller.createProject(new ProjectDTO.ProjectUpsertRequest()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user permission snapshot is unavailable");
        verify(projectManagementAppService, never()).createProject(any(), any());
    }

    @Test
    void createProjectShouldRejectWhenLiveUsernameIsBlank() {
        ProjectManagementAppService projectManagementAppService = mock(ProjectManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        CurrentUser currentUser = trustedCurrentUser("aiadc:project:create");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(systemInternalApi.findUserIdentityById(4301L))
                .thenReturn(userSnapshot(4301L, "user-uuid-4301", " ", "ENABLED"));
        ProjectV2Controller controller = new ProjectV2Controller(
                projectManagementAppService,
                securityContextFacade,
                new PermissionGuard(),
                permissionSnapshotService,
                systemInternalApi,
                null
        );

        assertThatThrownBy(() -> controller.createProject(new ProjectDTO.ProjectUpsertRequest()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user username is unavailable");
        verify(permissionSnapshotService, never()).isTrustedActiveUser(any(), any());
        verify(projectManagementAppService, never()).createProject(any(), any());
    }

    @Test
    void refreshTrustedCurrentUserShouldNormalizeInvalidSimulatedRoleIdBeforeSnapshotLoad() throws Exception {
        ProjectManagementAppService projectManagementAppService = mock(ProjectManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        CurrentUser currentUser = trustedCurrentUser("aiadc:project:view");
        currentUser.setSimulatedRoleId(0L);
        when(systemInternalApi.findUserIdentityById(4301L))
                .thenReturn(userSnapshot(4301L, "user-uuid-4301", "project-admin-live", "ENABLED"));
        when(permissionSnapshotService.isTrustedActiveUser(4301L, "user-uuid-4301")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(4301L, "user-uuid-4301"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("aiadc:project:view")));
        ProjectV2Controller controller = new ProjectV2Controller(
                projectManagementAppService,
                securityContextFacade,
                new PermissionGuard(),
                permissionSnapshotService,
                systemInternalApi,
                null
        );
        Method method = ProjectV2Controller.class.getDeclaredMethod("refreshTrustedCurrentUser", CurrentUser.class);
        method.setAccessible(true);

        method.invoke(controller, currentUser);

        assertThat(currentUser.getSimulatedRoleId()).isNull();
        verify(permissionSnapshotService).loadSnapshot(4301L, "user-uuid-4301");
        verify(permissionSnapshotService, never()).loadGrantedRoleSnapshot(any(), anyString(), any());
    }

    private CurrentUser trustedCurrentUser(String permission) {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(4301L);
        currentUser.setUsername("project-admin");
        currentUser.setSessionId("session-4301");
        currentUser.setSessionVersion(1);
        currentUser.setUserUuid("user-uuid-4301");
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setAuthenticated(true);
        currentUser.setPermissions(Set.of(permission));
        return currentUser;
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
