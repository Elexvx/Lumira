package com.lumira.saas.modules.system.sensitive.controller;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.sensitive.app.SensitiveWordService;
import com.lumira.saas.modules.system.sensitive.vo.SensitiveWordVO;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SensitiveWordControllerTest {

    @Test
    void updateStatusShouldForwardNullBodyToServiceWithoutDereferencingRequest() {
        Fixtures fixtures = fixtures();

        fixtures.controller.updateStatus(1001L, null);

        verify(fixtures.permissionGuard).requirePermission(fixtures.currentUser, "plugin:sensitive-words:manage");
        verify(fixtures.sensitiveWordService).updateStatus(fixtures.currentUser, 1001L, null);
    }

    @Test
    void checkShouldForwardNullBodyToServiceWithoutDereferencingRequest() {
        Fixtures fixtures = fixtures();
        when(fixtures.sensitiveWordService.checkTextForSubmission(fixtures.currentUser, null, null))
                .thenReturn(new SensitiveWordVO.CheckResult(false, List.of()));

        fixtures.controller.check(null);

        verify(fixtures.permissionGuard, never()).requirePermission(fixtures.currentUser, "plugin:sensitive-words:view");
        verify(fixtures.sensitiveWordService).checkTextForSubmission(fixtures.currentUser, null, null);
    }

    @Test
    void createShouldRejectWhenLiveSnapshotRevokesManagePermissionBeforeDelegating() {
        SensitiveWordService sensitiveWordService = mock(SensitiveWordService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        CurrentUser currentUser = currentUser();
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(permissionSnapshotService.isTrustedActiveUser(2001L, "user-uuid-2001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(2001L, "user-uuid-2001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("plugin:sensitive-words:view")));
        SensitiveWordController controller = new SensitiveWordController(
                sensitiveWordService,
                securityContextFacade,
                new PermissionGuard(),
                permissionSnapshotService
        );

        assertThatThrownBy(() -> controller.create(null))
                .isInstanceOf(BizException.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(sensitiveWordService, never()).createWord(currentUser, null);
    }

    @Test
    void createShouldRejectRevokedSessionTicketBeforeDelegating() {
        SensitiveWordService sensitiveWordService = mock(SensitiveWordService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        CurrentUser currentUser = currentUser();
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(sessionAuthenticationService.authenticateSessionTicket(anyString(), anyLong(), anyString(), any(), any(), any()))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Session expired"));
        SensitiveWordController controller = new SensitiveWordController(
                sensitiveWordService,
                securityContextFacade,
                new PermissionGuard(),
                null,
                sessionAuthenticationService
        );

        assertThatThrownBy(() -> controller.create(null))
                .isInstanceOf(BizException.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        verify(sensitiveWordService, never()).createWord(currentUser, null);
    }

    @Test
    void createShouldRejectTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        SensitiveWordService sensitiveWordService = mock(SensitiveWordService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        CurrentUser currentUser = currentUser();
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        SensitiveWordController controller = new SensitiveWordController(
                sensitiveWordService,
                securityContextFacade,
                new PermissionGuard(),
                null,
                null,
                null
        );

        assertThatThrownBy(() -> controller.create(null))
                .isInstanceOf(BizException.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user resolver is unavailable");
        verify(sensitiveWordService, never()).createWord(currentUser, null);
    }

    @Test
    void createShouldRejectWhenTrustedPermissionSnapshotIsUnavailable() {
        SensitiveWordService sensitiveWordService = mock(SensitiveWordService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        CurrentUser currentUser = currentUser();
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(permissionSnapshotService.isTrustedActiveUser(2001L, "user-uuid-2001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(2001L, "user-uuid-2001")).thenReturn(null);
        SensitiveWordController controller = new SensitiveWordController(
                sensitiveWordService,
                securityContextFacade,
                new PermissionGuard(),
                permissionSnapshotService,
                null,
                null
        );

        assertThatThrownBy(() -> controller.create(null))
                .isInstanceOf(BizException.class)
                .satisfies(error -> {
                    BizException exception = (BizException) error;
                    org.assertj.core.api.Assertions.assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    org.assertj.core.api.Assertions.assertThat(exception.getMessage()).contains("Trusted user permission snapshot is unavailable");
                });
        verify(sensitiveWordService, never()).createWord(currentUser, null);
    }

    @Test
    void createShouldRejectBlankLiveUsernameBeforeDelegating() {
        SensitiveWordService sensitiveWordService = mock(SensitiveWordService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        CurrentUser currentUser = currentUser();
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(permissionSnapshotService.isTrustedActiveUser(2001L, "user-uuid-2001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(2001L, "user-uuid-2001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("*")));
        when(systemInternalApi.findUserIdentityById(2001L))
                .thenReturn(userSnapshot(2001L, "user-uuid-2001", " ", "ENABLED"));
        SensitiveWordController controller = new SensitiveWordController(
                sensitiveWordService,
                securityContextFacade,
                new PermissionGuard(),
                permissionSnapshotService,
                systemInternalApi,
                null
        );

        assertThatThrownBy(() -> controller.create(null))
                .isInstanceOf(BizException.class)
                .satisfies(error -> {
                    BizException exception = (BizException) error;
                    org.assertj.core.api.Assertions.assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    org.assertj.core.api.Assertions.assertThat(exception.getMessage()).contains("Trusted user username is unavailable");
                });
        verify(sensitiveWordService, never()).createWord(currentUser, null);
    }

    @Test
    void refreshTrustedCurrentUserShouldNormalizeInvalidSimulatedRoleIdBeforeSnapshotLoad() throws Exception {
        SensitiveWordService sensitiveWordService = mock(SensitiveWordService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        CurrentUser currentUser = currentUser();
        currentUser.setSimulatedRoleId(0L);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(systemInternalApi.findUserIdentityById(2001L))
                .thenReturn(userSnapshot(2001L, "user-uuid-2001", "admin-live", "ENABLED"));
        when(permissionSnapshotService.isTrustedActiveUser(2001L, "user-uuid-2001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(2001L, "user-uuid-2001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("*")));
        SensitiveWordController controller = new SensitiveWordController(
                sensitiveWordService,
                securityContextFacade,
                new PermissionGuard(),
                permissionSnapshotService,
                systemInternalApi,
                null
        );
        Method method = SensitiveWordController.class.getDeclaredMethod("refreshTrustedCurrentUser", CurrentUser.class);
        method.setAccessible(true);

        method.invoke(controller, currentUser);

        org.assertj.core.api.Assertions.assertThat(currentUser.getSimulatedRoleId()).isNull();
        verify(permissionSnapshotService).loadSnapshot(2001L, "user-uuid-2001");
        verify(permissionSnapshotService, never()).loadGrantedRoleSnapshot(anyLong(), anyString(), anyLong());
    }

    private static Fixtures fixtures() {
        SensitiveWordService sensitiveWordService = mock(SensitiveWordService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        CurrentUser currentUser = currentUser();
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        return new Fixtures(
                new SensitiveWordController(sensitiveWordService, securityContextFacade, permissionGuard),
                sensitiveWordService,
                permissionGuard,
                currentUser
        );
    }

    private static CurrentUser currentUser() {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(2001L);
        currentUser.setUsername("admin");
        currentUser.setSessionId("session-2001");
        currentUser.setSessionVersion(1);
        currentUser.setUserUuid("user-uuid-2001");
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setAuthenticated(true);
        currentUser.setPermissions(Set.of("*"));
        return currentUser;
    }

    private static SystemUserSnapshotDTO userSnapshot(Long userId, String userUuid, String username, String status) {
        return new SystemUserSnapshotDTO(userId, userUuid, username, null, status, null, null, null, null, null, null, null, null, null, null, null);
    }

    private record Fixtures(
            SensitiveWordController controller,
            SensitiveWordService sensitiveWordService,
            PermissionGuard permissionGuard,
            CurrentUser currentUser
    ) {
    }
}
