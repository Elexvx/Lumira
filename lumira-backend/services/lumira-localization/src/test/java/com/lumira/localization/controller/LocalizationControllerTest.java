package com.lumira.localization.controller;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.localization.app.LocalizationManagementAppService;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalizationControllerTest {

    @Test
    void listLanguagesShouldRejectTrustedUserWhenResolverIsUnavailable() {
        LocalizationManagementAppService localizationManagementAppService = mock(LocalizationManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        LocalizationController controller = new LocalizationController(
                localizationManagementAppService,
                securityContextFacade,
                permissionGuard,
                null
        );
        CurrentUser currentUser = trustedCurrentUser("localization:view");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);

        assertThatThrownBy(controller::listLanguages)
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user resolver is unavailable");

        verify(permissionGuard, never()).requirePermission(currentUser, "localization:view");
        verify(localizationManagementAppService, never()).listLanguages(currentUser);
    }

    @Test
    void listLanguagesShouldRejectBlankLiveUsernameBeforePermissionCheck() {
        LocalizationManagementAppService localizationManagementAppService = mock(LocalizationManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        LocalizationController controller = new LocalizationController(
                localizationManagementAppService,
                securityContextFacade,
                permissionGuard,
                systemInternalApi
        );
        CurrentUser currentUser = trustedCurrentUser("localization:view");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(systemInternalApi.findUserIdentityById(100L))
                .thenReturn(userSnapshot(100L, "user-uuid-100", " ", "ENABLED"));
        when(systemInternalApi.permissionSnapshot(100L, "user-uuid-100"))
                .thenReturn(permissionSnapshot(java.util.List.of("localization:view")));

        assertThatThrownBy(controller::listLanguages)
                .isInstanceOf(BizException.class)
                .satisfies(error -> {
                    BizException exception = (BizException) error;
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(exception.getMessage()).contains("Trusted user username is unavailable");
                });

        verify(permissionGuard, never()).requirePermission(currentUser, "localization:view");
        verify(localizationManagementAppService, never()).listLanguages(currentUser);
    }

    private CurrentUser trustedCurrentUser(String permission) {
        CurrentUser currentUser = new CurrentUser(100L, "alice", 1001L, "session-1", 1, true, Set.of(permission));
        currentUser.setUserUuid("user-uuid-100");
        currentUser.setPermissionsVersion("permissions-1");
        return currentUser;
    }

    private PermissionSnapshotDTO permissionSnapshot(java.util.List<String> permissions) {
        return new PermissionSnapshotDTO("permissions-2", permissions, java.util.List.of(3L), 5L, java.util.List.of(5L), java.util.List.of(5L), java.util.List.of(), "/localization");
    }

    private SystemUserSnapshotDTO userSnapshot(Long userId, String userUuid, String username, String status) {
        return new SystemUserSnapshotDTO(userId, userUuid, username, null, status, null, null, null, null, null, null, null, null, null, null, null);
    }
}
