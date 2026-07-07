package com.lumira.saas.modules.competition.controller;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.competition.app.CompetitionManagementAppService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CompetitionV2ControllerTest {

    @Test
    void competitionSettingsShouldRejectTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        CompetitionManagementAppService appService = mock(CompetitionManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        when(securityContextFacade.getCurrentUser()).thenReturn(trustedCurrentUser());
        CompetitionV2Controller controller = new CompetitionV2Controller(
                appService,
                securityContextFacade,
                mock(PermissionGuard.class),
                null,
                null,
                null
        );

        BizException exception = assertThrows(BizException.class, () -> controller.competitionSettings("competition-uuid"));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        verifyNoInteractions(appService);
    }

    @Test
    void competitionSettingsShouldRejectWhenTrustedPermissionSnapshotIsUnavailable() {
        CompetitionManagementAppService appService = mock(CompetitionManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(securityContextFacade.getCurrentUser()).thenReturn(trustedCurrentUser());
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001")).thenReturn(null);
        CompetitionV2Controller controller = new CompetitionV2Controller(
                appService,
                securityContextFacade,
                mock(PermissionGuard.class),
                permissionSnapshotService,
                null,
                null
        );

        BizException exception = assertThrows(BizException.class, () -> controller.competitionSettings("competition-uuid"));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        assertThat(exception.getMessage()).contains("Trusted user permission snapshot is unavailable");
        verifyNoInteractions(appService);
    }

    @Test
    void competitionSettingsShouldRejectTrustedUserWhenLiveUsernameIsUnavailable() {
        CompetitionManagementAppService appService = mock(CompetitionManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(securityContextFacade.getCurrentUser()).thenReturn(trustedCurrentUser());
        when(systemInternalApi.findUserIdentityById(1001L)).thenReturn(
                new SystemUserSnapshotDTO(1001L, "user-uuid-1001", " ", null, "ENABLED", null, null, null, null, null, null, null, null, null, null, null)
        );
        CompetitionV2Controller controller = new CompetitionV2Controller(
                appService,
                securityContextFacade,
                mock(PermissionGuard.class),
                permissionSnapshotService,
                systemInternalApi,
                null
        );

        BizException exception = assertThrows(BizException.class, () -> controller.competitionSettings("competition-uuid"));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        assertThat(exception.getMessage()).contains("Trusted user username is unavailable");
        verifyNoInteractions(appService);
    }

    @Test
    void refreshTrustedCurrentUserShouldNormalizeInvalidSimulatedRoleIdBeforeSnapshotLoad() throws Exception {
        CompetitionManagementAppService appService = mock(CompetitionManagementAppService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("aiadc:competition:view")));
        CompetitionV2Controller controller = new CompetitionV2Controller(
                appService,
                mock(SecurityContextFacade.class),
                mock(PermissionGuard.class),
                permissionSnapshotService,
                null,
                null
        );
        CurrentUser currentUser = trustedCurrentUser();
        currentUser.setSimulatedRoleId(0L);

        Method method = CompetitionV2Controller.class.getDeclaredMethod("refreshTrustedCurrentUser", CurrentUser.class);
        method.setAccessible(true);
        method.invoke(controller, currentUser);

        assertThat(currentUser.getSimulatedRoleId()).isNull();
        assertThat(currentUser.getPermissionsVersion()).isEqualTo("permissions-2");
        verify(permissionSnapshotService).loadSnapshot(1001L, "user-uuid-1001");
        verify(permissionSnapshotService, never()).loadGrantedRoleSnapshot(1001L, "user-uuid-1001", 0L);
    }

    private static CurrentUser trustedCurrentUser() {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(1001L);
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setUsername("operator");
        currentUser.setSessionId("session-1");
        currentUser.setSessionVersion(1);
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setAuthenticated(true);
        currentUser.setPermissions(Set.of("aiadc:competition:view"));
        return currentUser;
    }
}
