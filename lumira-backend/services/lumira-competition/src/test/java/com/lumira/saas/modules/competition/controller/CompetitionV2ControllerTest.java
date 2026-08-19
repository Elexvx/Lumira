package com.lumira.saas.modules.competition.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.saas.modules.competition.app.CompetitionManagementAppService;
import com.lumira.common.vo.PageResponse;
import org.junit.jupiter.api.Test;

class CompetitionV2ControllerTest {

    @Test
    void listPublishedCompetitionsAllowsExpertViewPermission() {
        CompetitionManagementAppService appService = mock(CompetitionManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        CurrentUser currentUser = trustedCurrentUser();
        currentUser.setPermissions(java.util.Set.of("expert:view"));
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(permissionGuard.hasPermission(currentUser, "expert:view")).thenReturn(true);
        when(appService.listCompetitions(currentUser, null, null, "published", null, null, 1, 10))
                .thenReturn(new PageResponse<>());
        CompetitionV2Controller controller = new CompetitionV2Controller(
                appService,
                securityContextFacade,
                permissionGuard
        );

        controller.competitions(null, null, "published", null, null, 1, 10);

        verify(appService).listCompetitions(currentUser, null, null, "published", null, null, 1, 10);
    }

    @Test
    void competitionAllowsRegistrationCreatePermissionForPublishedCompetitionLookup() {
        CompetitionManagementAppService appService = mock(CompetitionManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        when(securityContextFacade.getCurrentUser()).thenReturn(registrationCurrentUser());
        when(appService.getCompetition(org.mockito.ArgumentMatchers.any(CurrentUser.class), org.mockito.ArgumentMatchers.eq(11L)))
                .thenReturn(new com.lumira.saas.modules.competition.vo.CompetitionVO.Competition());
        CompetitionV2Controller controller = new CompetitionV2Controller(
                appService,
                securityContextFacade,
                mock(PermissionGuard.class)
        );

        controller.competition(11L);

        verify(appService).getCompetition(org.mockito.ArgumentMatchers.any(CurrentUser.class), org.mockito.ArgumentMatchers.eq(11L));
    }

    @Test
    void settingsRejectsTrustedUserWhenSharedResolverCannotRefreshIt() {
        CompetitionManagementAppService appService = mock(CompetitionManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        when(securityContextFacade.getCurrentUser()).thenReturn(trustedCurrentUser());
        CompetitionV2Controller controller = new CompetitionV2Controller(
                appService,
                securityContextFacade,
                mock(PermissionGuard.class),
                ignored -> null
        );

        BizException exception = assertThrows(BizException.class, () -> controller.competitionSettings("competition-uuid"));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        verifyNoInteractions(appService);
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
        currentUser.setPermissions(java.util.Set.of("aiadc:competition:view"));
        return currentUser;
    }

    private static CurrentUser registrationCurrentUser() {
        CurrentUser currentUser = trustedCurrentUser();
        currentUser.setPermissions(java.util.Set.of("aiadc:registration:create"));
        return currentUser;
    }
}
