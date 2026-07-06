package com.lumira.saas.modules.audit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.modules.system.app.SystemManagementAppService;
import com.lumira.saas.modules.system.vo.SystemVO;
import org.junit.jupiter.api.Test;

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
}
