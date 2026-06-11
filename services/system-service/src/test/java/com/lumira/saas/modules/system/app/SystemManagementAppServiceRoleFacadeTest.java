package com.lumira.saas.modules.system.app;

import com.lumira.saas.common.vo.PageResponse;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.modules.system.role.app.SystemRoleManagementAppService;
import com.lumira.saas.modules.system.vo.SystemVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemManagementAppServiceRoleFacadeTest {

    @Test
    void shouldDelegateListRolesToRoleManagementService() {
        SystemRoleManagementAppService roleManagementAppService = mock(SystemRoleManagementAppService.class);
        SystemManagementAppService service = buildService(roleManagementAppService);
        CurrentUser currentUser = new CurrentUser();
        PageResponse<SystemVO.RoleVO> expected = new PageResponse<>();
        expected.setRecords(List.of(new SystemVO.RoleVO()));
        when(roleManagementAppService.listRoles(currentUser, "code", "name", "type", 1, 10)).thenReturn(expected);

        PageResponse<SystemVO.RoleVO> actual = service.listRoles(currentUser, "code", "name", "type", 1, 10);

        assertSame(expected, actual);
        verify(roleManagementAppService).listRoles(currentUser, "code", "name", "type", 1, 10);
    }

    @Test
    void shouldDelegateGetRoleToRoleManagementService() {
        SystemRoleManagementAppService roleManagementAppService = mock(SystemRoleManagementAppService.class);
        SystemManagementAppService service = buildService(roleManagementAppService);
        CurrentUser currentUser = new CurrentUser();
        SystemVO.RoleDetailVO expected = new SystemVO.RoleDetailVO();
        when(roleManagementAppService.getRole(currentUser, 2001L)).thenReturn(expected);

        SystemVO.RoleDetailVO actual = service.getRole(currentUser, 2001L);

        assertSame(expected, actual);
        verify(roleManagementAppService).getRole(currentUser, 2001L);
    }

    private SystemManagementAppService buildService(SystemRoleManagementAppService roleManagementAppService) {
        return new SystemManagementAppService(
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
                null,
                null,
                null,
                null,
                null,
                null,
                roleManagementAppService
        );
    }
}
