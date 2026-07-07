package com.lumira.saas.modules.system.workorder.controller;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.workorder.app.WorkOrderFeedbackService;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

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

class WorkOrderFeedbackControllerTest {

    @Test
    void createShouldRejectWhenLiveSnapshotRevokesCreatePermissionBeforeDelegating() {
        WorkOrderFeedbackService workOrderFeedbackService = mock(WorkOrderFeedbackService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        CurrentUser currentUser = currentUser();
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("plugin:work-order-feedback:view")));
        WorkOrderFeedbackController controller = new WorkOrderFeedbackController(
                workOrderFeedbackService,
                securityContextFacade,
                new PermissionGuard(),
                permissionSnapshotService
        );

        assertThatThrownBy(() -> controller.create(null))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(workOrderFeedbackService, never()).create(currentUser, null);
    }

    @Test
    void createShouldRejectRevokedSessionTicketBeforeDelegating() {
        WorkOrderFeedbackService workOrderFeedbackService = mock(WorkOrderFeedbackService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        CurrentUser currentUser = currentUser();
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(sessionAuthenticationService.authenticateSessionTicket(anyString(), anyLong(), anyString(), any(), any(), any()))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Session expired"));
        WorkOrderFeedbackController controller = new WorkOrderFeedbackController(
                workOrderFeedbackService,
                securityContextFacade,
                new PermissionGuard(),
                null,
                sessionAuthenticationService
        );

        assertThatThrownBy(() -> controller.create(null))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        verify(workOrderFeedbackService, never()).create(currentUser, null);
    }

    @Test
    void createShouldRejectTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        WorkOrderFeedbackService workOrderFeedbackService = mock(WorkOrderFeedbackService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        CurrentUser currentUser = currentUser();
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        WorkOrderFeedbackController controller = new WorkOrderFeedbackController(
                workOrderFeedbackService,
                securityContextFacade,
                new PermissionGuard(),
                null,
                null,
                null
        );

        assertThatThrownBy(() -> controller.create(null))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user resolver is unavailable");
        verify(workOrderFeedbackService, never()).create(currentUser, null);
    }

    @Test
    void createShouldRejectWhenTrustedPermissionSnapshotIsUnavailable() {
        WorkOrderFeedbackService workOrderFeedbackService = mock(WorkOrderFeedbackService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        CurrentUser currentUser = currentUser();
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001")).thenReturn(null);
        WorkOrderFeedbackController controller = new WorkOrderFeedbackController(
                workOrderFeedbackService,
                securityContextFacade,
                new PermissionGuard(),
                permissionSnapshotService,
                null,
                null
        );

        assertThatThrownBy(() -> controller.create(null))
                .isInstanceOf(BizException.class)
                .satisfies(error -> {
                    BizException exception = (BizException) error;
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(exception.getMessage()).contains("Trusted user permission snapshot is unavailable");
                });
        verify(workOrderFeedbackService, never()).create(currentUser, null);
    }

    @Test
    void createShouldRejectTrustedUserWhenLiveUsernameIsUnavailable() {
        WorkOrderFeedbackService workOrderFeedbackService = mock(WorkOrderFeedbackService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        CurrentUser currentUser = currentUser();
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(systemInternalApi.findUserIdentityById(1001L)).thenReturn(
                new SystemUserSnapshotDTO(1001L, "user-uuid-1001", " ", null, "ENABLED", null, null, null, null, null, null, null, null, null, null, null)
        );
        WorkOrderFeedbackController controller = new WorkOrderFeedbackController(
                workOrderFeedbackService,
                securityContextFacade,
                new PermissionGuard(),
                permissionSnapshotService,
                systemInternalApi,
                null
        );

        assertThatThrownBy(() -> controller.create(null))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user username is unavailable");
        verify(workOrderFeedbackService, never()).create(currentUser, null);
        verify(permissionSnapshotService, never()).isTrustedActiveUser(1001L, "user-uuid-1001");
    }

    @Test
    void refreshTrustedCurrentUserShouldNormalizeInvalidSimulatedRoleIdBeforeSnapshotLoad() throws Exception {
        WorkOrderFeedbackService workOrderFeedbackService = mock(WorkOrderFeedbackService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        CurrentUser currentUser = currentUser();
        currentUser.setSimulatedRoleId(0L);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(systemInternalApi.findUserIdentityById(1001L)).thenReturn(
                new SystemUserSnapshotDTO(1001L, "user-uuid-1001", "alice-live", null, "ENABLED", null, null, null, null, null, null, null, null, null, null, null)
        );
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("plugin:work-order-feedback:create")));
        WorkOrderFeedbackController controller = new WorkOrderFeedbackController(
                workOrderFeedbackService,
                securityContextFacade,
                new PermissionGuard(),
                permissionSnapshotService,
                systemInternalApi,
                null
        );
        Method method = WorkOrderFeedbackController.class.getDeclaredMethod("refreshTrustedCurrentUser", CurrentUser.class);
        method.setAccessible(true);

        method.invoke(controller, currentUser);

        assertThat(currentUser.getSimulatedRoleId()).isNull();
        verify(permissionSnapshotService).loadSnapshot(1001L, "user-uuid-1001");
        verify(permissionSnapshotService, never()).loadGrantedRoleSnapshot(anyLong(), anyString(), anyLong());
    }

    private static CurrentUser currentUser() {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(1001L);
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setUsername("alice");
        currentUser.setAuthenticated(true);
        currentUser.setSessionId("session-1");
        currentUser.setSessionVersion(1);
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setPermissions(Set.of("plugin:work-order-feedback:create"));
        return currentUser;
    }
}
