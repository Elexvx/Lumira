package com.lumira.file.controller;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.file.app.FileManagementAppService;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileV2ControllerTest {

    @Test
    void fileV2Controller_shouldExposeFileOwnerManagementAdapter() {
        RequestMapping requestMapping = FileV2Controller.class.getAnnotation(RequestMapping.class);

        assertThat(requestMapping).isNotNull();
        assertThat(requestMapping.value()).containsExactly("/api/v2/files");

        Set<String> getEndpoints = Arrays.stream(FileV2Controller.class.getDeclaredMethods())
                .filter(method -> method.getAnnotation(GetMapping.class) != null)
                .map(method -> method.getName() + ":" + String.join(",", method.getAnnotation(GetMapping.class).value()))
                .collect(Collectors.toSet());
        Set<String> postEndpoints = Arrays.stream(FileV2Controller.class.getDeclaredMethods())
                .filter(method -> method.getAnnotation(PostMapping.class) != null)
                .map(method -> method.getName() + ":" + String.join(",", method.getAnnotation(PostMapping.class).value()))
                .collect(Collectors.toSet());
        Set<String> putEndpoints = Arrays.stream(FileV2Controller.class.getDeclaredMethods())
                .filter(method -> method.getAnnotation(PutMapping.class) != null)
                .map(method -> method.getName() + ":" + String.join(",", method.getAnnotation(PutMapping.class).value()))
                .collect(Collectors.toSet());
        Set<String> deleteEndpoints = Arrays.stream(FileV2Controller.class.getDeclaredMethods())
                .filter(method -> method.getAnnotation(DeleteMapping.class) != null)
                .map(method -> method.getName() + ":" + String.join(",", method.getAnnotation(DeleteMapping.class).value()))
                .collect(Collectors.toSet());

        assertThat(getEndpoints)
                .contains(
                        "list:",
                        "storageSpaceOptions:/storage-space-options",
                        "storageSpaces:/storage-spaces",
                        "storageSpace:/storage-spaces/{storageKey}",
                        "detail:/{id}",
                        "download:/{id}/download",
                        "preview:/{id}/preview",
                        "textPreview:/{id}/text-preview"
                );
        assertThat(postEndpoints)
                .contains(
                        "createStorageSpace:/storage-spaces",
                        "testStorageSpace:/storage-spaces/{id}/test",
                        "upload:/upload"
                );
        assertThat(putEndpoints).contains("updateStorageSpace:/storage-spaces/{id}");
        assertThat(deleteEndpoints).contains("deleteStorageSpace:/storage-spaces/{id}", "delete:/{id}");
    }

    @Test
    void storageSpacesShouldRejectTrustedUserWhenResolverIsUnavailable() {
        FileManagementAppService fileManagementAppService = mock(FileManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        FileUploadMetrics fileUploadMetrics = mock(FileUploadMetrics.class);
        FileV2Controller controller = new FileV2Controller(
                fileManagementAppService,
                securityContextFacade,
                permissionGuard,
                fileUploadMetrics,
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
    void storageSpacesShouldRejectTrustedUserWhenLiveUsernameIsUnavailable() {
        FileManagementAppService fileManagementAppService = mock(FileManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        FileUploadMetrics fileUploadMetrics = mock(FileUploadMetrics.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        FileV2Controller controller = new FileV2Controller(
                fileManagementAppService,
                securityContextFacade,
                permissionGuard,
                fileUploadMetrics,
                systemInternalApi
        );
        CurrentUser currentUser = trustedCurrentUser("system:file:manage");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(systemInternalApi.findUserIdentityById(100L)).thenReturn(
                new SystemUserSnapshotDTO(100L, "user-uuid-100", " ", null, "ENABLED", null, null, null, null, null, null, null, null, null, null, null)
        );

        assertThatThrownBy(() -> controller.storageSpaces(1L, 50L))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user username is unavailable");

        verify(permissionGuard, never()).requirePermission(currentUser, "system:file:manage");
        verify(fileManagementAppService, never()).listStorageSpaces(currentUser, 1L, 50L);
        verify(systemInternalApi, never()).permissionSnapshot(100L, "user-uuid-100");
    }

    private CurrentUser trustedCurrentUser(String permission) {
        CurrentUser currentUser = new CurrentUser(100L, "alice", 1001L, "session-1", 1, true, Set.of(permission));
        currentUser.setUserUuid("user-uuid-100");
        currentUser.setPermissionsVersion("permissions-1");
        return currentUser;
    }
}
