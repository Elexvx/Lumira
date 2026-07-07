package com.lumira.saas.modules.expert.controller;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.saas.modules.expert.app.ExpertManagementAppService;
import com.lumira.saas.modules.expert.dto.ExpertDTO;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
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

class ExpertV2ControllerTest {

    @Test
    void createExpertShouldRejectTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        ExpertManagementAppService expertManagementAppService = mock(ExpertManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        CurrentUser currentUser = trustedCurrentUser("expert:create");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        ExpertV2Controller controller = new ExpertV2Controller(
                expertManagementAppService,
                securityContextFacade,
                new PermissionGuard(),
                null,
                null,
                null
        );

        assertThatThrownBy(() -> controller.createExpert(new ExpertDTO.ExpertUpsertRequest()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user resolver is unavailable");
        verify(expertManagementAppService, never()).createExpert(any(), any());
    }

    @Test
    void createExpertShouldRejectWhenLiveUsernameIsBlank() {
        ExpertManagementAppService expertManagementAppService = mock(ExpertManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        CurrentUser currentUser = trustedCurrentUser("expert:create");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(systemInternalApi.findUserIdentityById(4201L))
                .thenReturn(userSnapshot(4201L, "user-uuid-4201", " ", "ENABLED"));
        ExpertV2Controller controller = new ExpertV2Controller(
                expertManagementAppService,
                securityContextFacade,
                new PermissionGuard(),
                permissionSnapshotService,
                systemInternalApi,
                null
        );

        assertThatThrownBy(() -> controller.createExpert(new ExpertDTO.ExpertUpsertRequest()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user username is unavailable");
        verify(permissionSnapshotService, never()).isTrustedActiveUser(any(), any());
        verify(expertManagementAppService, never()).createExpert(any(), any());
    }

    @Test
    void refreshTrustedCurrentUserShouldNormalizeInvalidSimulatedRoleIdBeforeSnapshotLoad() throws Exception {
        ExpertManagementAppService expertManagementAppService = mock(ExpertManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        CurrentUser currentUser = trustedCurrentUser("expert:create");
        currentUser.setSimulatedRoleId(0L);
        when(permissionSnapshotService.isTrustedActiveUser(4201L, "user-uuid-4201")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(4201L, "user-uuid-4201"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("expert:create")));
        when(systemInternalApi.findUserIdentityById(4201L))
                .thenReturn(userSnapshot(4201L, "user-uuid-4201", "expert-admin-live", "ENABLED"));
        ExpertV2Controller controller = new ExpertV2Controller(
                expertManagementAppService,
                securityContextFacade,
                new PermissionGuard(),
                permissionSnapshotService,
                systemInternalApi,
                null
        );
        Method method = ExpertV2Controller.class.getDeclaredMethod("refreshTrustedCurrentUser", CurrentUser.class);
        method.setAccessible(true);

        method.invoke(controller, currentUser);

        assertThat(currentUser.getSimulatedRoleId()).isNull();
        verify(permissionSnapshotService).loadSnapshot(4201L, "user-uuid-4201");
        verify(permissionSnapshotService, never()).loadGrantedRoleSnapshot(anyLong(), anyString(), anyLong());
    }

    private CurrentUser trustedCurrentUser(String permission) {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(4201L);
        currentUser.setUsername("expert-admin");
        currentUser.setSessionId("session-4201");
        currentUser.setSessionVersion(1);
        currentUser.setUserUuid("user-uuid-4201");
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
