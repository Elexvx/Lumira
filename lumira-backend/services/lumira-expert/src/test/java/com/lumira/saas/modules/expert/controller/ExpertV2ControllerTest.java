package com.lumira.saas.modules.expert.controller;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.security.TrustedCurrentUserResolver;
import com.lumira.saas.modules.expert.app.ExpertManagementAppService;
import com.lumira.saas.modules.expert.dto.ExpertDTO;
import com.lumira.saas.modules.expert.vo.ExpertVO;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpertV2ControllerTest {

    @Test
    void createExpertUsesSharedTrustedCurrentUserResolver() {
        ExpertManagementAppService service = mock(ExpertManagementAppService.class);
        SecurityContextFacade security = mock(SecurityContextFacade.class);
        TrustedCurrentUserResolver resolver = mock(TrustedCurrentUserResolver.class);
        CurrentUser currentUser = user("expert:apply");
        when(security.getCurrentUser()).thenReturn(currentUser);
        when(resolver.resolve(currentUser)).thenReturn(currentUser);
        ExpertVO.Expert expert = new ExpertVO.Expert();
        expert.setId(9L);
        when(service.createExpert(any(), any())).thenReturn(expert);
        ExpertV2Controller controller = new ExpertV2Controller(service, security, new PermissionGuard(), resolver);

        assertThat(controller.createExpert(new ExpertDTO.ExpertUpsertRequest()).getData().getId()).isEqualTo(9L);

        verify(resolver).resolve(currentUser);
        verify(service).createExpert(eq(currentUser), any());
    }

    @Test
    void strictControllerRejectsMissingTrustedResolver() {
        ExpertManagementAppService service = mock(ExpertManagementAppService.class);
        SecurityContextFacade security = mock(SecurityContextFacade.class);
        CurrentUser currentUser = user("expert:apply");
        when(security.getCurrentUser()).thenReturn(currentUser);
        ExpertV2Controller controller = new ExpertV2Controller(service, security, new PermissionGuard(), null);

        assertThatThrownBy(() -> controller.createExpert(new ExpertDTO.ExpertUpsertRequest()))
                .isInstanceOf(BizException.class)
                .extracting(error -> ((BizException) error).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(service, never()).createExpert(any(), any());
    }

    private static CurrentUser user(String permission) {
        CurrentUser user = new CurrentUser();
        user.setUserId(4201L);
        user.setUserUuid("user-uuid-4201");
        user.setUsername("expert-admin");
        user.setSessionId("session-4201");
        user.setSessionVersion(1);
        user.setPermissionsVersion("permissions-1");
        user.setAuthenticated(true);
        user.setPermissions(Set.of(permission));
        return user;
    }
}
