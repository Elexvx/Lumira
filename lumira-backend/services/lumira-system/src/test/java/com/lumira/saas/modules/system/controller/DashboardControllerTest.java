package com.lumira.saas.modules.system.controller;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.app.SystemManagementAppService;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DashboardControllerTest {

    @Test
    void summaryShouldRejectWhenLiveSnapshotMarksUserInactiveBeforeDelegating() {
        SystemManagementAppService appService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        CurrentUser currentUser = new CurrentUser(42L, "alice", null, "session-1", 1, true, Set.of("dashboard:view"));
        currentUser.setUserUuid("user-uuid-42");
        currentUser.setPermissionsVersion("permissions-1");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(permissionSnapshotService.isTrustedActiveUser(42L, "user-uuid-42")).thenReturn(false);
        DashboardController controller = new DashboardController(appService, securityContextFacade, permissionSnapshotService);

        assertThatThrownBy(controller::summary)
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(appService);
    }

    @Test
    void summaryShouldRejectRevokedSessionTicketBeforeDelegating() {
        SystemManagementAppService appService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        CurrentUser currentUser = new CurrentUser(42L, "alice", null, "session-1", 1, true, Set.of("dashboard:view"));
        currentUser.setUserUuid("user-uuid-42");
        currentUser.setPermissionsVersion("permissions-1");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(sessionAuthenticationService.authenticateSessionTicket(any(), any(), any(), any(), any(), any()))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Session expired"));
        DashboardController controller = new DashboardController(appService, securityContextFacade, null, sessionAuthenticationService);

        assertThatThrownBy(controller::summary)
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(appService);
    }
}
