package com.lumira.saas.modules.workflow.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lumira.api.workflow.WorkflowUserAccessPort;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.saas.modules.workflow.app.WorkflowAppService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkflowV2ControllerTest {

    @Test
    void publishRejectsAnUntrustedSecurityContextBeforeCallingPortsOrApplicationService() {
        WorkflowAppService workflowAppService = mock(WorkflowAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        WorkflowUserAccessPort userAccessPort = mock(WorkflowUserAccessPort.class);
        when(securityContextFacade.getCurrentUser()).thenReturn(new CurrentUser());
        WorkflowV2Controller controller = controller(workflowAppService, securityContextFacade, userAccessPort);

        assertThatThrownBy(() -> controller.publish("project-approval"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(userAccessPort, never()).refreshTrustedUser(any());
        verify(workflowAppService, never()).publish(any(), anyString());
    }

    @Test
    void publishRejectsWhenTheSystemOwnedUserAccessPortCannotReturnATrustedPrincipal() {
        WorkflowAppService workflowAppService = mock(WorkflowAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        WorkflowUserAccessPort userAccessPort = mock(WorkflowUserAccessPort.class);
        when(securityContextFacade.getCurrentUser()).thenReturn(trustedCurrentUser("workflow:config"));
        when(userAccessPort.refreshTrustedUser(any(CurrentUser.class))).thenReturn(new CurrentUser());
        WorkflowV2Controller controller = controller(workflowAppService, securityContextFacade, userAccessPort);

        assertThatThrownBy(() -> controller.publish("project-approval"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(workflowAppService, never()).publish(any(), anyString());
    }

    @Test
    void publishUsesTheRefreshedPrincipalForPermissionAndApplicationCalls() {
        WorkflowAppService workflowAppService = mock(WorkflowAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        WorkflowUserAccessPort userAccessPort = mock(WorkflowUserAccessPort.class);
        CurrentUser stale = trustedCurrentUser("workflow:view");
        CurrentUser refreshed = trustedCurrentUser("workflow:config");
        refreshed.setUsername("workflow-admin-live");
        when(securityContextFacade.getCurrentUser()).thenReturn(stale);
        when(userAccessPort.refreshTrustedUser(stale)).thenReturn(refreshed);
        WorkflowV2Controller controller = controller(workflowAppService, securityContextFacade, userAccessPort);

        controller.publish("project-approval");

        verify(workflowAppService).publish(refreshed, "project-approval");
    }

    @Test
    void controllerKeepsTheExistingApiRouteAndDependsOnlyOnSharedUserAccessPort() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/workflow/controller/WorkflowV2Controller.java"));

        assertThat(source)
                .contains("@RequestMapping(\"/api/v2/workflows\")", "WorkflowUserAccessPort")
                .doesNotContain("SystemInternalApi", "PermissionSnapshotService", "SessionAuthenticationService")
                .doesNotContain("com.lumira.saas.common.annotation.RepeatSubmit");
    }

    private WorkflowV2Controller controller(
            WorkflowAppService workflowAppService,
            SecurityContextFacade securityContextFacade,
            WorkflowUserAccessPort userAccessPort
    ) {
        return new WorkflowV2Controller(workflowAppService, securityContextFacade, new PermissionGuard(), userAccessPort);
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
