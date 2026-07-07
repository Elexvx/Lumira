package com.lumira.saas.modules.workflow.controller;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.workflow.app.WorkflowAppService;
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

class WorkflowV2ControllerTest {

    @Test
    void publishShouldRejectTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        WorkflowAppService workflowAppService = mock(WorkflowAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        CurrentUser currentUser = trustedCurrentUser("workflow:config");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        WorkflowV2Controller controller = new WorkflowV2Controller(
                workflowAppService,
                securityContextFacade,
                new PermissionGuard(),
                null,
                null,
                null
        );

        assertThatThrownBy(() -> controller.publish("project-approval"))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user resolver is unavailable");
        verify(workflowAppService, never()).publish(any(), anyString());
    }

    @Test
    void publishShouldRejectWhenTrustedPermissionSnapshotIsUnavailable() {
        WorkflowAppService workflowAppService = mock(WorkflowAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        CurrentUser currentUser = trustedCurrentUser("workflow:config");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(permissionSnapshotService.isTrustedActiveUser(4001L, "user-uuid-4001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(4001L, "user-uuid-4001")).thenReturn(null);
        WorkflowV2Controller controller = new WorkflowV2Controller(
                workflowAppService,
                securityContextFacade,
                new PermissionGuard(),
                permissionSnapshotService,
                null,
                null
        );

        assertThatThrownBy(() -> controller.publish("project-approval"))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user permission snapshot is unavailable");
        verify(workflowAppService, never()).publish(any(), anyString());
    }

    @Test
    void publishShouldRejectTrustedUserWhenLiveUsernameIsUnavailable() {
        WorkflowAppService workflowAppService = mock(WorkflowAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        CurrentUser currentUser = trustedCurrentUser("workflow:config");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(systemInternalApi.findUserIdentityById(4001L)).thenReturn(
                new SystemUserSnapshotDTO(4001L, "user-uuid-4001", " ", null, "ENABLED", null, null, null, null, null, null, null, null, null, null, null)
        );
        WorkflowV2Controller controller = new WorkflowV2Controller(
                workflowAppService,
                securityContextFacade,
                new PermissionGuard(),
                permissionSnapshotService,
                systemInternalApi,
                null
        );

        assertThatThrownBy(() -> controller.publish("project-approval"))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user username is unavailable");
        verify(workflowAppService, never()).publish(any(), anyString());
        verify(permissionSnapshotService, never()).isTrustedActiveUser(4001L, "user-uuid-4001");
    }

    @Test
    void refreshTrustedCurrentUserShouldNormalizeInvalidSimulatedRoleIdBeforeSnapshotLoad() throws Exception {
        WorkflowAppService workflowAppService = mock(WorkflowAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        CurrentUser currentUser = trustedCurrentUser("workflow:view");
        currentUser.setSimulatedRoleId(0L);
        when(systemInternalApi.findUserIdentityById(4001L)).thenReturn(
                new SystemUserSnapshotDTO(4001L, "user-uuid-4001", "workflow-admin-live", null, "ENABLED", null, null, null, null, null, null, null, null, null, null, null)
        );
        when(permissionSnapshotService.isTrustedActiveUser(4001L, "user-uuid-4001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(4001L, "user-uuid-4001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("workflow:view")));
        WorkflowV2Controller controller = new WorkflowV2Controller(
                workflowAppService,
                securityContextFacade,
                new PermissionGuard(),
                permissionSnapshotService,
                systemInternalApi,
                null
        );
        Method method = WorkflowV2Controller.class.getDeclaredMethod("refreshTrustedCurrentUser", CurrentUser.class);
        method.setAccessible(true);

        method.invoke(controller, currentUser);

        assertThat(currentUser.getSimulatedRoleId()).isNull();
        verify(permissionSnapshotService).loadSnapshot(4001L, "user-uuid-4001");
        verify(permissionSnapshotService, never()).loadGrantedRoleSnapshot(any(), anyString(), any());
    }

    private CurrentUser trustedCurrentUser(String permission) {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(4001L);
        currentUser.setUsername("workflow-admin");
        currentUser.setSessionId("session-4001");
        currentUser.setSessionVersion(1);
        currentUser.setUserUuid("user-uuid-4001");
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setAuthenticated(true);
        currentUser.setPermissions(Set.of(permission));
        return currentUser;
    }
}
