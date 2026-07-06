package com.lumira.file.service;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.file.app.FileManagementAppService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileInternalApiServiceTest {

    @Test
    void internalUserFileOperationsUseTrustedPermissionSnapshot() {
        FileManagementAppService appService = mock(FileManagementAppService.class);
        FileInternalApiService service = service(appService, userSnapshot(42L, "alice", "ENABLED"));
        MultipartFile file = mock(MultipartFile.class);

        service.uploadDocumentForUser(file, "knowledge", "ai", "remark", null, 42L, "user-uuid-42", "alice");

        org.mockito.ArgumentCaptor<CurrentUser> userCaptor = org.mockito.ArgumentCaptor.forClass(CurrentUser.class);
        verify(appService).uploadDocument(userCaptor.capture(), eq(file), eq("knowledge"), eq("ai"), eq("remark"), any());
        CurrentUser currentUser = userCaptor.getValue();
        assertThat(currentUser.getUserId()).isEqualTo(42L);
        assertThat(currentUser.getUsername()).isEqualTo("alice");
        assertThat(currentUser.getUserUuid()).isEqualTo("user-uuid-42");
        assertThat(currentUser.getSessionId()).isEqualTo("internal-file-user-42");
        assertThat(currentUser.getSessionVersion()).isEqualTo(1);
        assertThat(currentUser.getPermissionsVersion()).isEqualTo("perm-v42");
        assertThat(currentUser.getPermissions()).containsExactlyInAnyOrder("system:file:view", "download:center:view");
        assertThat(currentUser.getRoleIds()).containsExactlyInAnyOrder(11L, 12L);
        assertThat(currentUser.getPrimaryDeptId()).isEqualTo(21L);
        assertThat(currentUser.getDeptIds()).containsExactlyInAnyOrder(21L, 22L);
        assertThat(currentUser.getDescendantDeptIds()).containsExactlyInAnyOrder(21L, 22L, 23L);
        assertThat(currentUser.getDefaultHomePath()).isEqualTo("/files");
    }

    @Test
    void contentReadsDefaultToPersonalScope() {
        FileManagementAppService appService = mock(FileManagementAppService.class);
        FileInternalApiService service = service(appService, userSnapshot(42L, "alice", "ENABLED"));

        service.readFileContentForUser(99L, 42L, "user-uuid-42", "alice");

        verify(appService).readFileContent(any(CurrentUser.class), eq(99L), eq(false), eq(false));
    }

    @Test
    void contentReadsRejectInvalidFileIdBeforeDelegatingToAppService() {
        FileManagementAppService appService = mock(FileManagementAppService.class);
        FileInternalApiService service = service(appService, userSnapshot(42L, "alice", "ENABLED"));

        assertThatThrownBy(() -> service.readFileContentForUser(0L, 42L, "user-uuid-42", "alice", false))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verify(appService, never()).readFileContent(any(), any(), any(Boolean.class), any(Boolean.class));
    }

    @Test
    void metadataReadsAllowSharedScopeOnlyWhenExplicitlyRequested() {
        FileManagementAppService appService = mock(FileManagementAppService.class);
        FileInternalApiService service = service(appService, userSnapshot(42L, "alice", "ENABLED"));

        service.getFileForUser(99L, 42L, "user-uuid-42", "alice", true, false);

        verify(appService).getFile(any(CurrentUser.class), eq(99L), eq(true), eq(false));
    }

    @Test
    void uploadImageForUserUsesActingUserWithoutWildcardPermission() {
        FileManagementAppService appService = mock(FileManagementAppService.class);
        FileInternalApiService service = service(appService, userSnapshot(42L, "alice", "ENABLED"));
        MultipartFile file = mock(MultipartFile.class);

        service.uploadImageForUser(file, "avatar", "profile", "avatar", 42L, "user-uuid-42", "alice");

        org.mockito.ArgumentCaptor<CurrentUser> userCaptor = org.mockito.ArgumentCaptor.forClass(CurrentUser.class);
        verify(appService).uploadPublicImage(userCaptor.capture(), eq(file), eq("avatar"), eq("profile"), eq("avatar"));
        assertThat(userCaptor.getValue().getUserId()).isEqualTo(42L);
        assertThat(userCaptor.getValue().getUsername()).isEqualTo("alice");
        assertThat(userCaptor.getValue().getUserUuid()).isEqualTo("user-uuid-42");
        assertThat(userCaptor.getValue().getSessionId()).isEqualTo("internal-file-user-42");
        assertThat(userCaptor.getValue().getSessionVersion()).isEqualTo(1);
        assertThat(userCaptor.getValue().getPermissionsVersion()).isEqualTo("perm-v42");
        assertThat(userCaptor.getValue().getPermissions()).containsExactlyInAnyOrder("system:file:view", "download:center:view");
        assertThat(userCaptor.getValue().getPermissions()).doesNotContain("*");
    }

    @Test
    void directUploadsRejectUntrustedCurrentUserBeforeDelegatingToAppService() {
        FileManagementAppService appService = mock(FileManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        CurrentUser currentUser = new CurrentUser(42L, "alice", null, "s1", 1, false, java.util.Set.of("system:file:upload"));
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        FileInternalApiService service = new FileInternalApiService(appService, securityContextFacade, provider(userSnapshot(42L, "alice", "ENABLED")));

        assertThatThrownBy(() -> service.uploadDocument(mock(MultipartFile.class), "knowledge", "ai", "remark", null))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verify(appService, never()).uploadDocument(any(), any(), any(), any(), any(), any());
    }

    @Test
    void directUploadsRejectMissingSessionIdBeforeDelegatingToAppService() {
        FileManagementAppService appService = mock(FileManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        CurrentUser currentUser = new CurrentUser(42L, "alice", null, null, 1, true, java.util.Set.of("system:file:upload"));
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        FileInternalApiService service = new FileInternalApiService(appService, securityContextFacade, provider(userSnapshot(42L, "alice", "ENABLED")));

        assertThatThrownBy(() -> service.uploadDocument(mock(MultipartFile.class), "knowledge", "ai", "remark", null))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verify(appService, never()).uploadDocument(any(), any(), any(), any(), any(), any());
    }

    @Test
    void artifactsRejectInvalidArtifactTypeBeforeDelegatingToAppService() {
        FileManagementAppService appService = mock(FileManagementAppService.class);
        FileInternalApiService service = service(appService, userSnapshot(42L, "alice", "ENABLED"));

        assertThatThrownBy(() -> service.readProcessingArtifactForUser(99L, 42L, "user-uuid-42", "alice", "../text", false))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verify(appService, never()).readProcessingArtifact(any(), any(), any(), any(Boolean.class), any(Boolean.class));
    }

    @Test
    void rejectsInvalidActingUserBeforeDelegatingToAppService() {
        FileManagementAppService appService = mock(FileManagementAppService.class);
        FileInternalApiService service = service(appService, userSnapshot(42L, "alice", "ENABLED"));
        MultipartFile file = mock(MultipartFile.class);

        assertThatThrownBy(() -> service.uploadDocumentForUser(file, "knowledge", "ai", "remark", null, 0L, " ", " "))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verify(appService, never()).uploadDocument(any(), any(), any(), any(), any(), any());
    }

    @Test
    void searchRejectsOversizedLimitBeforeDelegatingToAppService() {
        FileManagementAppService appService = mock(FileManagementAppService.class);
        FileInternalApiService service = service(appService, userSnapshot(42L, "alice", "ENABLED"));

        assertThatThrownBy(() -> service.searchFilesForUser(42L, "user-uuid-42", "alice", null, null, null, false, 101))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verify(appService, never()).searchFilesForInternalTool(any(), any(), any(), any(), eq(false), eq(101));
    }

    @Test
    void rejectsHeaderSuppliedUsernameWhenTrustedSnapshotDoesNotMatch() {
        FileManagementAppService appService = mock(FileManagementAppService.class);
        FileInternalApiService service = service(appService, userSnapshot(42L, "alice", "ENABLED"));

        assertThatThrownBy(() -> service.readFileContentForUser(99L, 42L, "user-uuid-42", "mallory", false))
                .isInstanceOf(com.lumira.common.exception.BizException.class)
                .hasMessageContaining("Acting user identity mismatch");

        verify(appService, never()).readFileContent(any(), any(), any(Boolean.class), any(Boolean.class));
    }

    @Test
    void rejectsHeaderSuppliedUserUuidWhenTrustedSnapshotDoesNotMatch() {
        FileManagementAppService appService = mock(FileManagementAppService.class);
        FileInternalApiService service = service(appService, userSnapshot(42L, "alice", "ENABLED"));

        assertThatThrownBy(() -> service.readFileContentForUser(99L, 42L, "other-user-uuid", "alice", false))
                .isInstanceOf(com.lumira.common.exception.BizException.class)
                .hasMessageContaining("Acting user identity mismatch");

        verify(appService, never()).readFileContent(any(), any(), any(Boolean.class), any(Boolean.class));
    }

    @Test
    void rejectsDisabledTrustedActingUserBeforeDelegatingToAppService() {
        FileManagementAppService appService = mock(FileManagementAppService.class);
        FileInternalApiService service = service(appService, userSnapshot(42L, "alice", "DISABLED"));

        assertThatThrownBy(() -> service.searchFilesForUser(42L, "user-uuid-42", "alice", null, null, null, false, 10))
                .isInstanceOf(com.lumira.common.exception.BizException.class)
                .hasMessageContaining("Acting user is disabled");

        verify(appService, never()).searchFilesForInternalTool(any(), any(), any(), any(), any(Boolean.class), any(Integer.class));
    }

    @Test
    void rejectsTrustedActingUserWithoutEnabledStatusBeforeDelegatingToAppService() {
        FileManagementAppService appService = mock(FileManagementAppService.class);
        FileInternalApiService service = service(appService, userSnapshot(42L, "alice", null));

        assertThatThrownBy(() -> service.searchFilesForUser(42L, "user-uuid-42", "alice", null, null, null, false, 10))
                .isInstanceOf(com.lumira.common.exception.BizException.class)
                .hasMessageContaining("Acting user is disabled");

        verify(appService, never()).searchFilesForInternalTool(any(), any(), any(), any(), any(Boolean.class), any(Integer.class));
    }

    @Test
    void rejectsActingUserWhenTrustedResolverIsUnavailable() {
        FileManagementAppService appService = mock(FileManagementAppService.class);
        FileInternalApiService service = new FileInternalApiService(appService, mock(SecurityContextFacade.class), unavailableProvider());

        assertThatThrownBy(() -> service.uploadImageForUser(mock(MultipartFile.class), "avatar", "remark", null, 42L, "user-uuid-42", "alice"))
                .isInstanceOf(com.lumira.common.exception.BizException.class)
                .hasMessageContaining("Trusted acting user resolver is unavailable");

        verify(appService, never()).uploadPublicImage(any(), any(), any(), any(), any());
    }

    private FileInternalApiService service(FileManagementAppService appService, SystemUserSnapshotDTO snapshot) {
        return new FileInternalApiService(appService, mock(SecurityContextFacade.class), provider(snapshot));
    }

    private SystemUserSnapshotDTO userSnapshot(Long userId, String username, String status) {
        return new SystemUserSnapshotDTO(userId, "user-uuid-" + userId, username, null, status, null, null, null, null, null, null, null, null, null, null, null);
    }

    private ObjectProvider<SystemInternalApi> provider(SystemUserSnapshotDTO snapshot) {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(snapshot.userId())).thenReturn(snapshot);
        when(systemInternalApi.permissionSnapshot(snapshot.userId(), snapshot.userUuid())).thenReturn(permissionSnapshot());
        return new ObjectProvider<>() {
            @Override
            public SystemInternalApi getObject(Object... args) {
                return systemInternalApi;
            }

            @Override
            public SystemInternalApi getIfAvailable() {
                return systemInternalApi;
            }

            @Override
            public SystemInternalApi getIfUnique() {
                return systemInternalApi;
            }

            @Override
            public SystemInternalApi getObject() {
                return systemInternalApi;
            }
        };
    }

    private PermissionSnapshotDTO permissionSnapshot() {
        return new PermissionSnapshotDTO(
                "perm-v42",
                List.of("system:file:view", "download:center:view"),
                List.of(11L, 12L),
                21L,
                List.of(21L, 22L),
                List.of(21L, 22L, 23L),
                List.of(),
                "/files"
        );
    }

    private ObjectProvider<SystemInternalApi> unavailableProvider() {
        return new ObjectProvider<>() {
            @Override
            public SystemInternalApi getObject(Object... args) {
                return null;
            }

            @Override
            public SystemInternalApi getIfAvailable() {
                return null;
            }

            @Override
            public SystemInternalApi getIfUnique() {
                return null;
            }

            @Override
            public SystemInternalApi getObject() {
                return null;
            }
        };
    }
}
