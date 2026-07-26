package com.lumira.file.controller;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.file.StorageSpaceOptionDTO;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.file.app.FileManagementAppService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileControllerTest {

    @Test
    void storageSpaceOptionsShouldRemainAvailableOnV1ForExistingClients() {
        FileManagementAppService fileManagementAppService = mock(FileManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        FileController controller = new FileController(
                fileManagementAppService,
                securityContextFacade,
                permissionGuard
        );
        CurrentUser currentUser = trustedCurrentUser("aiadc:competition:update");
        List<StorageSpaceOptionDTO> options = List.of(
                new StorageSpaceOptionDTO("默认空间", "default", true)
        );
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(fileManagementAppService.listStorageSpaceOptions(currentUser)).thenReturn(options);

        ApiResponse<List<StorageSpaceOptionDTO>> response = controller.storageSpaceOptions();

        assertThat(response.getData()).isEqualTo(options);
        verify(fileManagementAppService).listStorageSpaceOptions(currentUser);
    }

    @Test
    void storageSpacesShouldRejectTrustedUserWhenResolverIsUnavailable() {
        FileManagementAppService fileManagementAppService = mock(FileManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        FileController controller = new FileController(
                fileManagementAppService,
                securityContextFacade,
                permissionGuard,
                null
        );
        CurrentUser currentUser = trustedCurrentUser("system:file:manage");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);

        assertThatThrownBy(() -> controller.storageSpaces(1L, 50L))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user resolver is unavailable");

        verify(permissionGuard, never()).requirePermission(currentUser, "system:file:manage");
        verify(fileManagementAppService, never()).listStorageSpaces(currentUser, 1L, 50L);
    }

    @Test
    void storageSpacesShouldRejectWhenLiveUsernameIsBlank() {
        FileManagementAppService fileManagementAppService = mock(FileManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        FileController controller = new FileController(
                fileManagementAppService,
                securityContextFacade,
                permissionGuard,
                systemInternalApi
        );
        CurrentUser currentUser = trustedCurrentUser("system:file:manage");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(systemInternalApi.findUserIdentityById(100L))
                .thenReturn(userSnapshot(100L, "user-uuid-100", " ", "ENABLED"));

        assertThatThrownBy(() -> controller.storageSpaces(1L, 50L))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user username is unavailable");

        verify(permissionGuard, never()).requirePermission(currentUser, "system:file:manage");
        verify(fileManagementAppService, never()).listStorageSpaces(currentUser, 1L, 50L);
    }

    @Test
    void storageSpacesShouldUseSimulatedRolePermissionSnapshot() {
        FileManagementAppService fileManagementAppService = mock(FileManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        FileController controller = new FileController(
                fileManagementAppService,
                securityContextFacade,
                permissionGuard,
                systemInternalApi
        );
        CurrentUser currentUser = trustedCurrentUser("system:file:manage");
        currentUser.setSimulatedRoleId(9L);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(systemInternalApi.findUserIdentityById(100L))
                .thenReturn(userSnapshot(100L, "user-uuid-100", "alice", "ENABLED"));
        when(systemInternalApi.simulatedRolePermissionSnapshot(100L, "user-uuid-100", 9L))
                .thenReturn(permissionSnapshot("system:file:manage"));

        controller.storageSpaces(1L, 50L);

        verify(systemInternalApi, org.mockito.Mockito.atLeastOnce()).simulatedRolePermissionSnapshot(100L, "user-uuid-100", 9L);
        verify(systemInternalApi, never()).permissionSnapshot(100L, "user-uuid-100");
        verify(permissionGuard).requirePermission(currentUser, "system:file:manage");
        verify(fileManagementAppService).listStorageSpaces(currentUser, 1L, 50L);
    }

    private CurrentUser trustedCurrentUser(String permission) {
        CurrentUser currentUser = new CurrentUser(100L, "alice", 1001L, "session-1", 1, true, Set.of(permission));
        currentUser.setUserUuid("user-uuid-100");
        currentUser.setPermissionsVersion("permissions-1");
        return currentUser;
    }

    private SystemUserSnapshotDTO userSnapshot(Long userId, String userUuid, String username, String status) {
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

    private PermissionSnapshotDTO permissionSnapshot(String permission) {
        return new PermissionSnapshotDTO(
                "permissions-2",
                List.of(permission),
                List.of(9L),
                null,
                List.of(),
                List.of(),
                List.of(),
                "/files"
        );
    }
}
