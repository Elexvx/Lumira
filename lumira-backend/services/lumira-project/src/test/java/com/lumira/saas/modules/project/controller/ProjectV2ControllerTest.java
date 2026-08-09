package com.lumira.saas.modules.project.controller;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.saas.modules.project.app.ProjectManagementAppService;
import com.lumira.saas.modules.project.dto.ProjectDTO;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectV2ControllerTest {

    @Test
    void strictControllerRejectsWhenTrustedResolverIsUnavailable() {
        ProjectManagementAppService appService = mock(ProjectManagementAppService.class);
        SecurityContextFacade securityContext = mock(SecurityContextFacade.class);
        when(securityContext.getCurrentUser()).thenReturn(trustedCurrentUser("aiadc:project:create"));
        ProjectV2Controller controller = new ProjectV2Controller(
                appService, securityContext, new PermissionGuard(), null, true);

        assertThatThrownBy(() -> controller.createProject(new ProjectDTO.ProjectUpsertRequest()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user resolver is unavailable");
        verify(appService, never()).createProject(any(), any());
    }

    @Test
    void strictControllerRejectsWhenResolverRejectsTheLiveSession() {
        ProjectManagementAppService appService = mock(ProjectManagementAppService.class);
        SecurityContextFacade securityContext = mock(SecurityContextFacade.class);
        when(securityContext.getCurrentUser()).thenReturn(trustedCurrentUser("aiadc:project:create"));
        ProjectV2Controller controller = new ProjectV2Controller(
                appService, securityContext, new PermissionGuard(), ignored -> null, true);

        assertThatThrownBy(() -> controller.createProject(new ProjectDTO.ProjectUpsertRequest()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        verify(appService, never()).createProject(any(), any());
    }

    @Test
    void controllerUsesResolvedCurrentUserForPermissionAndApplicationCall() {
        ProjectManagementAppService appService = mock(ProjectManagementAppService.class);
        SecurityContextFacade securityContext = mock(SecurityContextFacade.class);
        CurrentUser staleUser = trustedCurrentUser("aiadc:project:view");
        CurrentUser resolvedUser = trustedCurrentUser("aiadc:project:create");
        when(securityContext.getCurrentUser()).thenReturn(staleUser);
        ProjectV2Controller controller = new ProjectV2Controller(
                appService, securityContext, new PermissionGuard(), ignored -> resolvedUser, true);
        ProjectDTO.ProjectUpsertRequest request = new ProjectDTO.ProjectUpsertRequest();

        controller.createProject(request);

        verify(appService).createProject(resolvedUser, request);
    }

    @Test
    void controllerAllowsRegistrationCreateForCompatibilityRoute() {
        ProjectManagementAppService appService = mock(ProjectManagementAppService.class);
        SecurityContextFacade securityContext = mock(SecurityContextFacade.class);
        CurrentUser user = trustedCurrentUser("aiadc:registration:create");
        when(securityContext.getCurrentUser()).thenReturn(user);
        ProjectV2Controller controller = new ProjectV2Controller(
                appService, securityContext, new PermissionGuard(), currentUser -> currentUser, true);
        ProjectDTO.ProjectUpsertRequest request = new ProjectDTO.ProjectUpsertRequest();

        controller.createProject(request);

        verify(appService).createProject(user, request);
    }

    private static CurrentUser trustedCurrentUser(String permission) {
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
}
