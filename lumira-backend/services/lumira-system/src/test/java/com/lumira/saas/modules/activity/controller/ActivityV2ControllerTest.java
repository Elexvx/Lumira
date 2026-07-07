package com.lumira.saas.modules.activity.controller;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.saas.modules.activity.app.ActivityManagementAppService;
import com.lumira.saas.modules.activity.dto.ActivityDTO;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
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

class ActivityV2ControllerTest {

    @Test
    void createActivityShouldRejectTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        ActivityManagementAppService activityManagementAppService = mock(ActivityManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        CurrentUser currentUser = trustedCurrentUser("aiadc:activity:create");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        ActivityV2Controller controller = new ActivityV2Controller(
                activityManagementAppService,
                securityContextFacade,
                new PermissionGuard(),
                null,
                null,
                null
        );

        assertThatThrownBy(() -> controller.createActivity(new ActivityDTO.ActivityUpsertRequest()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user resolver is unavailable");
        verify(activityManagementAppService, never()).createActivity(any(), any());
    }

    @Test
    void createActivityShouldRejectTrustedUserWhenLiveUsernameIsUnavailable() {
        ActivityManagementAppService activityManagementAppService = mock(ActivityManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        CurrentUser currentUser = trustedCurrentUser("aiadc:activity:create");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(systemInternalApi.findUserIdentityById(4101L)).thenReturn(
                new SystemUserSnapshotDTO(4101L, "user-uuid-4101", " ", null, "ENABLED", null, null, null, null, null, null, null, null, null, null, null)
        );
        ActivityV2Controller controller = new ActivityV2Controller(
                activityManagementAppService,
                securityContextFacade,
                new PermissionGuard(),
                permissionSnapshotService,
                systemInternalApi,
                null
        );

        assertThatThrownBy(() -> controller.createActivity(new ActivityDTO.ActivityUpsertRequest()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user username is unavailable");
        verify(activityManagementAppService, never()).createActivity(any(), any());
        verify(permissionSnapshotService, never()).isTrustedActiveUser(4101L, "user-uuid-4101");
    }

    @Test
    void refreshTrustedCurrentUserShouldNormalizeInvalidSimulatedRoleIdBeforeSnapshotLoad() throws Exception {
        ActivityManagementAppService activityManagementAppService = mock(ActivityManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        CurrentUser currentUser = trustedCurrentUser("aiadc:activity:view");
        currentUser.setSimulatedRoleId(0L);
        when(systemInternalApi.findUserIdentityById(4101L))
                .thenReturn(new SystemUserSnapshotDTO(4101L, "user-uuid-4101", "activity-admin-live", null, "ENABLED", null, null, null, null, null, null, null, null, null, null, null));
        when(permissionSnapshotService.isTrustedActiveUser(4101L, "user-uuid-4101")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(4101L, "user-uuid-4101"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("aiadc:activity:view")));
        ActivityV2Controller controller = new ActivityV2Controller(
                activityManagementAppService,
                securityContextFacade,
                new PermissionGuard(),
                permissionSnapshotService,
                systemInternalApi,
                null
        );
        Method method = ActivityV2Controller.class.getDeclaredMethod("refreshTrustedCurrentUser", CurrentUser.class);
        method.setAccessible(true);

        method.invoke(controller, currentUser);

        assertThat(currentUser.getSimulatedRoleId()).isNull();
        verify(permissionSnapshotService).loadSnapshot(4101L, "user-uuid-4101");
        verify(permissionSnapshotService, never()).loadGrantedRoleSnapshot(any(), anyString(), any());
    }

    private CurrentUser trustedCurrentUser(String permission) {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(4101L);
        currentUser.setUsername("activity-admin");
        currentUser.setSessionId("session-4101");
        currentUser.setSessionVersion(1);
        currentUser.setUserUuid("user-uuid-4101");
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setAuthenticated(true);
        currentUser.setPermissions(Set.of(permission));
        return currentUser;
    }
}
