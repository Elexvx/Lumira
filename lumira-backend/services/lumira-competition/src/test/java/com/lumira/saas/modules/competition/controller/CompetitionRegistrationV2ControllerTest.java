package com.lumira.saas.modules.competition.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lumira.api.client.FileInternalApi;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.saas.modules.competition.app.CompetitionRegistrationAppService;
import org.junit.jupiter.api.Test;

class CompetitionRegistrationV2ControllerTest {

    @Test
    void legacyStageReviewEndpointsAreRetiredByDefault() {
        CompetitionRegistrationAppService appService = mock(CompetitionRegistrationAppService.class);
        CompetitionRegistrationV2Controller controller = new CompetitionRegistrationV2Controller(
                appService,
                mock(SecurityContextFacade.class),
                mock(PermissionGuard.class)
        );

        BizException exception = assertThrows(BizException.class, () -> controller.reviewCandidates(20L));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
        assertThat(exception.getMessage()).contains("review workbench");
        verifyNoInteractions(appService);
    }

    @Test
    void registrationsRejectTrustedUserWhenSharedResolverCannotRefreshIt() {
        CompetitionRegistrationAppService appService = mock(CompetitionRegistrationAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        when(securityContextFacade.getCurrentUser()).thenReturn(trustedCurrentUser());
        CompetitionRegistrationV2Controller controller = new CompetitionRegistrationV2Controller(
                appService,
                securityContextFacade,
                mock(PermissionGuard.class),
                mock(FileInternalApi.class),
                ignored -> null
        );

        BizException exception = assertThrows(BizException.class, () -> controller.registrations(1, 10));

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
        currentUser.setPermissions(java.util.Set.of("aiadc:registration:view"));
        return currentUser;
    }
}
