package com.lumira.saas.modules.system.app;

import com.lumira.common.security.CurrentUser;
import com.lumira.saas.modules.system.user.app.SystemUserManagementAppService;
import com.lumira.saas.modules.system.vo.SystemVO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemManagementAppServiceUserFacadeTest {

    @Test
    void shouldDelegateGetUserToUserManagementService() {
        SystemUserManagementAppService userManagementAppService = mock(SystemUserManagementAppService.class);
        SystemManagementAppService service = new SystemManagementAppService(
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
                userManagementAppService
        );
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(1001L);
        SystemVO.UserDetailVO expected = new SystemVO.UserDetailVO();
        when(userManagementAppService.getUser(currentUser, 2001L)).thenReturn(expected);

        SystemVO.UserDetailVO actual = service.getUser(currentUser, 2001L);

        assertSame(expected, actual);
        verify(userManagementAppService).getUser(currentUser, 2001L);
    }
}
