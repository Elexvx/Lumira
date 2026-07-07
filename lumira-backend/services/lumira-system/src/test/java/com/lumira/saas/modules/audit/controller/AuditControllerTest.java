package com.lumira.saas.modules.audit.controller;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.app.SystemManagementAppService;
import com.lumira.saas.modules.system.vo.SystemVO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

class AuditControllerTest {

    @Test
    void summaryShouldRequireAuditViewPermission() {
        SystemManagementAppService systemManagementAppService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        AuditController controller = new AuditController(systemManagementAppService, securityContextFacade, permissionGuard);
        CurrentUser currentUser = trustedCurrentUser();
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(systemManagementAppService.listLoginLogs(currentUser, null, 1, 1)).thenReturn(page(3L));
        when(systemManagementAppService.listOperationLogs(currentUser, null, 1, 1)).thenReturn(page(7L));

        var response = controller.summary();

        assertThat(response.getData()).containsEntry("loginCount", 3).containsEntry("operationCount", 7);
        verify(permissionGuard).requirePermission(currentUser, "audit:view");
        verify(systemManagementAppService).listLoginLogs(currentUser, null, 1, 1);
        verify(systemManagementAppService).listOperationLogs(currentUser, null, 1, 1);
    }

    @Test
    void summaryShouldRejectTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        SystemManagementAppService systemManagementAppService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        CurrentUser currentUser = trustedCurrentUser();
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        AuditController controller = new AuditController(
                systemManagementAppService,
                securityContextFacade,
                permissionGuard,
                null,
                null,
                null
        );

        assertThatThrownBy(controller::summary)
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user resolver is unavailable");
        verify(systemManagementAppService, never()).listLoginLogs(currentUser, null, 1, 1);
        verify(systemManagementAppService, never()).listOperationLogs(currentUser, null, 1, 1);
    }

    @Test
    void summaryShouldRejectWhenLiveUsernameIsBlank() {
        SystemManagementAppService systemManagementAppService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        CurrentUser currentUser = trustedCurrentUser();
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(systemInternalApi.findUserIdentityById(3001L))
                .thenReturn(userSnapshot(3001L, "user-uuid-3001", " ", "ENABLED"));
        AuditController controller = new AuditController(
                systemManagementAppService,
                securityContextFacade,
                permissionGuard,
                permissionSnapshotService,
                systemInternalApi,
                null
        );

        assertThatThrownBy(controller::summary)
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user username is unavailable");
        verify(systemManagementAppService, never()).listLoginLogs(currentUser, null, 1, 1);
        verify(systemManagementAppService, never()).listOperationLogs(currentUser, null, 1, 1);
    }

    @Test
    void refreshTrustedCurrentUserShouldNormalizeInvalidSimulatedRoleIdBeforeSnapshotLoad() throws Exception {
        SystemManagementAppService systemManagementAppService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        CurrentUser currentUser = trustedCurrentUser();
        currentUser.setSimulatedRoleId(0L);
        when(systemInternalApi.findUserIdentityById(3001L))
                .thenReturn(userSnapshot(3001L, "user-uuid-3001", "auditor-live", "ENABLED"));
        when(permissionSnapshotService.isTrustedActiveUser(3001L, "user-uuid-3001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(3001L, "user-uuid-3001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", java.util.Set.of("audit:view")));
        AuditController controller = new AuditController(
                systemManagementAppService,
                securityContextFacade,
                permissionGuard,
                permissionSnapshotService,
                systemInternalApi,
                null
        );
        Method method = AuditController.class.getDeclaredMethod("refreshTrustedCurrentUser", CurrentUser.class);
        method.setAccessible(true);

        method.invoke(controller, currentUser);

        assertThat(currentUser.getSimulatedRoleId()).isNull();
        verify(permissionSnapshotService).loadSnapshot(3001L, "user-uuid-3001");
        verify(permissionSnapshotService, never()).loadGrantedRoleSnapshot(any(), anyString(), any());
    }

    private CurrentUser trustedCurrentUser() {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(3001L);
        currentUser.setUsername("auditor");
        currentUser.setSessionId("session-3001");
        currentUser.setSessionVersion(1);
        currentUser.setUserUuid("user-uuid-3001");
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setAuthenticated(true);
        return currentUser;
    }

    private PageResponse<SystemVO.AuditLogVO> page(long total) {
        PageResponse<SystemVO.AuditLogVO> page = new PageResponse<>();
        page.setTotal(total);
        return page;
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
