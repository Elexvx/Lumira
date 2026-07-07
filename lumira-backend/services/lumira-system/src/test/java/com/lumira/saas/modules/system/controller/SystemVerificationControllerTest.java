package com.lumira.saas.modules.system.controller;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.auth.dto.SecondFactorBindRequest;
import com.lumira.saas.modules.auth.dto.SecondFactorVerifyRequest;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.verification.SystemVerificationAppService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemVerificationControllerTest {

    @Test
    void updateSettingsShouldRejectUnauthenticatedUserEvenWhenPermissionSetContainsManageKey() {
        SystemVerificationAppService verificationAppService = mock(SystemVerificationAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        CurrentUser currentUser = currentUser();
        currentUser.setAuthenticated(false);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        SystemVerificationController controller = new SystemVerificationController(
                verificationAppService,
                securityContextFacade,
                new PermissionGuard()
        );

        assertThatThrownBy(() -> controller.updateVerificationSettings(new SystemDTO.VerificationSettingsRequest()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        verify(verificationAppService, never()).updateVerificationSettings(any(CurrentUser.class), any());
    }

    @Test
    void updateSettingsShouldRejectBlankUsernameEvenWhenPermissionSetContainsManageKey() {
        SystemVerificationAppService verificationAppService = mock(SystemVerificationAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        CurrentUser currentUser = currentUser();
        currentUser.setUsername(" ");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        SystemVerificationController controller = new SystemVerificationController(
                verificationAppService,
                securityContextFacade,
                new PermissionGuard()
        );

        assertThatThrownBy(() -> controller.updateVerificationSettings(new SystemDTO.VerificationSettingsRequest()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        verify(verificationAppService, never()).updateVerificationSettings(any(CurrentUser.class), any());
    }

    @Test
    void updateSettingsShouldRejectMissingSessionIdEvenWhenPermissionSetContainsManageKey() {
        SystemVerificationAppService verificationAppService = mock(SystemVerificationAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        CurrentUser currentUser = currentUser();
        currentUser.setSessionId(null);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        SystemVerificationController controller = new SystemVerificationController(
                verificationAppService,
                securityContextFacade,
                new PermissionGuard()
        );

        assertThatThrownBy(() -> controller.updateVerificationSettings(new SystemDTO.VerificationSettingsRequest()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        verify(verificationAppService, never()).updateVerificationSettings(any(CurrentUser.class), any());
    }

    @Test
    void updateSettingsShouldRejectMissingSessionVersionEvenWhenPermissionSetContainsManageKey() {
        SystemVerificationAppService verificationAppService = mock(SystemVerificationAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        CurrentUser currentUser = currentUser();
        currentUser.setSessionVersion(null);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        SystemVerificationController controller = new SystemVerificationController(
                verificationAppService,
                securityContextFacade,
                new PermissionGuard()
        );

        assertThatThrownBy(() -> controller.updateVerificationSettings(new SystemDTO.VerificationSettingsRequest()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        verify(verificationAppService, never()).updateVerificationSettings(any(CurrentUser.class), any());
    }

    @Test
    void updateSettingsShouldRejectWhenLiveSnapshotRevokesConfigUpdatePermissionBeforeDelegating() {
        SystemVerificationAppService verificationAppService = mock(SystemVerificationAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        CurrentUser currentUser = currentUser();
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("system:config:view")));
        SystemVerificationController controller = new SystemVerificationController(
                verificationAppService,
                securityContextFacade,
                new PermissionGuard(),
                permissionSnapshotService
        );

        assertThatThrownBy(() -> controller.updateVerificationSettings(new SystemDTO.VerificationSettingsRequest()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(verificationAppService, never()).updateVerificationSettings(any(CurrentUser.class), any());
    }

    @Test
    void updateSettingsShouldRejectRevokedSessionTicketBeforeDelegating() {
        SystemVerificationAppService verificationAppService = mock(SystemVerificationAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        CurrentUser currentUser = currentUser();
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(sessionAuthenticationService.authenticateSessionTicket(anyString(), anyLong(), anyString(), any(), any(), any()))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Session expired"));
        SystemVerificationController controller = new SystemVerificationController(
                verificationAppService,
                securityContextFacade,
                new PermissionGuard(),
                null,
                sessionAuthenticationService
        );

        assertThatThrownBy(() -> controller.updateVerificationSettings(new SystemDTO.VerificationSettingsRequest()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        verify(verificationAppService, never()).updateVerificationSettings(any(CurrentUser.class), any());
    }

    @Test
    void updateSettingsShouldRejectWhenTrustedPermissionSnapshotIsUnavailable() {
        SystemVerificationAppService verificationAppService = mock(SystemVerificationAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        CurrentUser currentUser = currentUser();
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001")).thenReturn(null);
        SystemVerificationController controller = new SystemVerificationController(
                verificationAppService,
                securityContextFacade,
                new PermissionGuard(),
                permissionSnapshotService,
                null,
                null
        );

        assertThatThrownBy(() -> controller.updateVerificationSettings(new SystemDTO.VerificationSettingsRequest()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> {
                    BizException exception = (BizException) error;
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(exception.getMessage()).contains("Trusted user permission snapshot is unavailable");
                });
        verify(verificationAppService, never()).updateVerificationSettings(any(CurrentUser.class), any());
    }

    @Test
    void updateSettingsShouldRejectTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        SystemVerificationAppService verificationAppService = mock(SystemVerificationAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        CurrentUser currentUser = currentUser();
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        SystemVerificationController controller = new SystemVerificationController(
                verificationAppService,
                securityContextFacade,
                new PermissionGuard(),
                null,
                null,
                null
        );

        assertThatThrownBy(() -> controller.updateVerificationSettings(new SystemDTO.VerificationSettingsRequest()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user resolver is unavailable");
        verify(verificationAppService, never()).updateVerificationSettings(any(CurrentUser.class), any());
    }

    @Test
    void updateSettingsShouldRejectBlankLiveUsernameBeforeDelegating() {
        SystemVerificationAppService verificationAppService = mock(SystemVerificationAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        CurrentUser currentUser = currentUser();
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("system:config:update")));
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", " ", "ENABLED"));
        SystemVerificationController controller = new SystemVerificationController(
                verificationAppService,
                securityContextFacade,
                new PermissionGuard(),
                permissionSnapshotService,
                systemInternalApi,
                null
        );

        assertThatThrownBy(() -> controller.updateVerificationSettings(new SystemDTO.VerificationSettingsRequest()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> {
                    BizException exception = (BizException) error;
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(exception.getMessage()).contains("Trusted user username is unavailable");
                });
        verify(verificationAppService, never()).updateVerificationSettings(any(CurrentUser.class), any());
    }

    @Test
    void unbindShouldRejectMismatchedFactorCodeBeforeDelegating() {
        SystemVerificationAppService verificationAppService = mock(SystemVerificationAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        CurrentUser currentUser = currentUser();
        currentUser.setPermissions(Set.of("system:verification:manage"));
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        SystemVerificationController controller = new SystemVerificationController(
                verificationAppService,
                securityContextFacade,
                new PermissionGuard()
        );
        SecondFactorVerifyRequest request = new SecondFactorVerifyRequest();
        request.setFactorCode("email");
        request.setChallengeId("challenge-1");
        request.setVerificationCode("123456");

        assertThatThrownBy(() -> controller.unbind("totp", request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        verify(verificationAppService, never()).unbindCurrentUser(any(CurrentUser.class), any(), any(), any());
    }

    @Test
    void bindShouldDelegateVerificationRequestForTrustedUser() {
        SystemVerificationAppService verificationAppService = mock(SystemVerificationAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        CurrentUser currentUser = currentUser();
        currentUser.setPermissions(Set.of("system:verification:manage"));
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(verificationAppService.bindCurrentUser(currentUser, "totp", "Password!23", null, null, null))
                .thenReturn(new com.lumira.saas.modules.system.vo.SystemVO.VerificationChallengeVO());
        SystemVerificationController controller = new SystemVerificationController(
                verificationAppService,
                securityContextFacade,
                new PermissionGuard()
        );
        SecondFactorBindRequest request = new SecondFactorBindRequest();
        request.setCurrentPassword("Password!23");

        controller.bind("totp", request);

        verify(verificationAppService).bindCurrentUser(currentUser, "totp", "Password!23", null, null, null);
    }

    @Test
    void unbindShouldDelegateVerifiedChallengeForTrustedUser() {
        SystemVerificationAppService verificationAppService = mock(SystemVerificationAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        CurrentUser currentUser = currentUser();
        currentUser.setPermissions(Set.of("system:verification:manage"));
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(verificationAppService.unbindCurrentUser(currentUser, "totp", "challenge-1", "123456")).thenReturn(true);
        SystemVerificationController controller = new SystemVerificationController(
                verificationAppService,
                securityContextFacade,
                new PermissionGuard()
        );
        SecondFactorVerifyRequest request = new SecondFactorVerifyRequest();
        request.setFactorCode("totp");
        request.setChallengeId("challenge-1");
        request.setVerificationCode("123456");

        assertThat(controller.unbind("totp", request).getData()).isTrue();

        verify(verificationAppService).unbindCurrentUser(currentUser, "totp", "challenge-1", "123456");
    }

    @Test
    void refreshTrustedCurrentUserShouldNormalizeInvalidSimulatedRoleIdBeforeSnapshotLoad() throws Exception {
        SystemVerificationAppService verificationAppService = mock(SystemVerificationAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        CurrentUser currentUser = currentUser();
        currentUser.setSimulatedRoleId(0L);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("system:verification:manage")));
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", "admin-live", "ENABLED"));
        SystemVerificationController controller = new SystemVerificationController(
                verificationAppService,
                securityContextFacade,
                new PermissionGuard(),
                permissionSnapshotService,
                systemInternalApi,
                null
        );
        Method method = SystemVerificationController.class.getDeclaredMethod("refreshTrustedCurrentUser", CurrentUser.class);
        method.setAccessible(true);

        method.invoke(controller, currentUser);

        assertThat(currentUser.getSimulatedRoleId()).isNull();
        verify(permissionSnapshotService).loadSnapshot(1001L, "user-uuid-1001");
        verify(permissionSnapshotService, never()).loadGrantedRoleSnapshot(anyLong(), anyString(), anyLong());
    }

    private static CurrentUser currentUser() {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(1001L);
        currentUser.setUsername("admin");
        currentUser.setSessionId("session-1");
        currentUser.setSessionVersion(1);
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setAuthenticated(true);
        currentUser.setPermissions(Set.of("system:config:update"));
        return currentUser;
    }

    private static SystemUserSnapshotDTO userSnapshot(Long userId, String userUuid, String username, String status) {
        return new SystemUserSnapshotDTO(
                userId,
                userUuid,
                username,
                null,
                status,
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
                null
        );
    }
}
