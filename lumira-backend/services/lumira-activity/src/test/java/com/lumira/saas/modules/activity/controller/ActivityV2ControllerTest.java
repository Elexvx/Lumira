package com.lumira.saas.modules.activity.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.security.TrustedCurrentUserResolver;
import com.lumira.common.web.repeatsubmit.RepeatSubmit;
import com.lumira.saas.modules.activity.app.ActivityManagementAppService;
import com.lumira.saas.modules.activity.dto.ActivityDTO;
import com.lumira.saas.modules.activity.vo.ActivityVO;
import java.lang.reflect.Method;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

class ActivityV2ControllerTest {

    @Test
    void writeEndpointsUseSharedRepeatSubmitBoundary() throws Exception {
        Method create = ActivityV2Controller.class.getMethod("createActivity", ActivityDTO.ActivityUpsertRequest.class);
        Method update = ActivityV2Controller.class.getMethod("updateActivity", Long.class, ActivityDTO.ActivityUpsertRequest.class);
        Method delete = ActivityV2Controller.class.getMethod("deleteActivity", Long.class);

        assertThat(create.getAnnotation(RepeatSubmit.class)).isNotNull();
        assertThat(update.getAnnotation(RepeatSubmit.class)).isNotNull();
        assertThat(delete.getAnnotation(RepeatSubmit.class)).isNotNull();
        assertThat(ActivityV2Controller.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/api/v2/aiadc/activities");
    }

    @Test
    void strictRuntimeRejectsTrustedUserWhenResolverIsUnavailableBeforeDelegating() {
        ActivityManagementAppService appService = mock(ActivityManagementAppService.class);
        SecurityContextFacade securityContext = mock(SecurityContextFacade.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        when(securityContext.getCurrentUser()).thenReturn(user(Set.of("aiadc:activity:create")));
        ActivityV2Controller controller = new ActivityV2Controller(appService, securityContext, permissionGuard, null, true);

        assertThatThrownBy(() -> controller.createActivity(new ActivityDTO.ActivityUpsertRequest()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(permissionGuard, never()).requirePermission(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
        verify(appService, never()).createActivity(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void controllerUsesResolverCurrentIdentityBeforePermissionAndApplicationLayers() {
        ActivityManagementAppService appService = mock(ActivityManagementAppService.class);
        SecurityContextFacade securityContext = mock(SecurityContextFacade.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        TrustedCurrentUserResolver resolver = mock(TrustedCurrentUserResolver.class);
        CurrentUser requestUser = user(Set.of("aiadc:activity:view"));
        CurrentUser resolvedUser = user(Set.of("aiadc:activity:create"));
        resolvedUser.setUsername("live-alice");
        when(securityContext.getCurrentUser()).thenReturn(requestUser);
        when(resolver.resolve(requestUser)).thenReturn(resolvedUser);
        ActivityVO.Activity result = new ActivityVO.Activity();
        result.setId(9L);
        when(appService.createActivity(eq(resolvedUser), any())).thenReturn(result);
        ActivityV2Controller controller = new ActivityV2Controller(appService, securityContext, permissionGuard, resolver);

        assertThat(controller.createActivity(new ActivityDTO.ActivityUpsertRequest()).getData().getId()).isEqualTo(9L);

        verify(permissionGuard).requirePermission(resolvedUser, "aiadc:activity:create");
        verify(appService).createActivity(org.mockito.Mockito.eq(resolvedUser), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void resolverMustReturnTrustedUser() {
        ActivityManagementAppService appService = mock(ActivityManagementAppService.class);
        SecurityContextFacade securityContext = mock(SecurityContextFacade.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        TrustedCurrentUserResolver resolver = mock(TrustedCurrentUserResolver.class);
        CurrentUser currentUser = user(Set.of("aiadc:activity:view"));
        when(securityContext.getCurrentUser()).thenReturn(currentUser);
        when(resolver.resolve(currentUser)).thenReturn(new CurrentUser());
        ActivityV2Controller controller = new ActivityV2Controller(appService, securityContext, permissionGuard, resolver);

        assertThatThrownBy(() -> controller.activities(null, null, null, null, 1L, 10L))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(permissionGuard, never()).requirePermission(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }

    private CurrentUser user(Set<String> permissions) {
        CurrentUser user = new CurrentUser(1001L, "alice", "session-1", 1, true, permissions);
        user.setUserUuid("user-uuid-1001");
        user.setPermissionsVersion("permissions-1");
        return user;
    }
}
