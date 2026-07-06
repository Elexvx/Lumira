package com.lumira.asyncruntime;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.file.app.FileManagementAppService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LocalFileInternalApiAdapterTest {

    @Test
    void userScopedInternalCallsUseTrustedPermissionSnapshot() {
        FileManagementAppService fileManagementAppService = mock(FileManagementAppService.class);
        LocalFileInternalApiAdapter adapter = new LocalFileInternalApiAdapter(
                fileManagementAppService,
                mock(SecurityContextFacade.class),
                provider(userSnapshot(42L, "alice", "ENABLED"))
        );

        adapter.readFileContentForUser(99L, 42L, "user-uuid-42", "alice");

        var userCaptor = org.mockito.ArgumentCaptor.forClass(CurrentUser.class);
        verify(fileManagementAppService).readFileContent(userCaptor.capture(), eq(99L), eq(false), eq(false));
        assertThat(userCaptor.getValue().getUserId()).isEqualTo(42L);
        assertThat(userCaptor.getValue().getUsername()).isEqualTo("alice");
        assertThat(userCaptor.getValue().getUserUuid()).isEqualTo("user-uuid-42");
        assertThat(userCaptor.getValue().getSessionId()).isEqualTo("internal-file-user-42");
        assertThat(userCaptor.getValue().getSessionVersion()).isEqualTo(1);
        assertThat(userCaptor.getValue().getPermissionsVersion()).isEqualTo("perm-v42");
        assertThat(userCaptor.getValue().getPermissions())
                .containsExactlyInAnyOrder("system:file:view", "download:center:view")
                .doesNotContain("*");
        assertThat(userCaptor.getValue().getRoleIds()).containsExactlyInAnyOrder(11L, 12L);
        assertThat(userCaptor.getValue().getDeptIds()).containsExactlyInAnyOrder(21L, 22L);
    }

    @Test
    void sharedContentReadsMustBeRequestedExplicitly() {
        FileManagementAppService fileManagementAppService = mock(FileManagementAppService.class);
        LocalFileInternalApiAdapter adapter = new LocalFileInternalApiAdapter(
                fileManagementAppService,
                mock(SecurityContextFacade.class),
                provider(userSnapshot(42L, "alice", "ENABLED"))
        );

        adapter.readFileContentForUser(99L, 42L, "user-uuid-42", "alice", true);

        verify(fileManagementAppService).readFileContent(any(CurrentUser.class), eq(99L), eq(true), eq(false));
    }

    @Test
    void directUploadsKeepAuthenticatedCurrentUser() {
        FileManagementAppService fileManagementAppService = mock(FileManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        CurrentUser currentUser = new CurrentUser(7L, "uploader", null, "s1", 1, true, java.util.Set.of("system:file:upload"));
        currentUser.setUserUuid("user-uuid-7");
        currentUser.setPermissionsVersion("permissions-1");
        org.mockito.Mockito.when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        LocalFileInternalApiAdapter adapter = new LocalFileInternalApiAdapter(
                fileManagementAppService,
                securityContextFacade,
                provider(userSnapshot(7L, "uploader", "ENABLED"))
        );

        adapter.uploadDocument(mock(org.springframework.web.multipart.MultipartFile.class), "doc", "tag", "remark", "bucket");

        verify(fileManagementAppService).uploadDocument(eq(currentUser), any(), eq("doc"), eq("tag"), eq("remark"), eq("bucket"));
    }

    @Test
    void directUploadsRejectUnauthenticatedCurrentUserBeforeFileServiceCall() {
        FileManagementAppService fileManagementAppService = mock(FileManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        CurrentUser currentUser = new CurrentUser(7L, "uploader", null, "s1", 1, false, java.util.Set.of("system:file:upload"));
        org.mockito.Mockito.when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        LocalFileInternalApiAdapter adapter = new LocalFileInternalApiAdapter(
                fileManagementAppService,
                securityContextFacade,
                provider(userSnapshot(7L, "uploader", "ENABLED"))
        );

        assertThatThrownBy(() -> adapter.uploadDocument(mock(org.springframework.web.multipart.MultipartFile.class), "doc", "tag", "remark", "bucket"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void directUploadsRejectBlankUsernameBeforeFileServiceCall() {
        FileManagementAppService fileManagementAppService = mock(FileManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        CurrentUser currentUser = new CurrentUser(7L, " ", null, "s1", 1, true, java.util.Set.of("system:file:upload"));
        org.mockito.Mockito.when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        LocalFileInternalApiAdapter adapter = new LocalFileInternalApiAdapter(
                fileManagementAppService,
                securityContextFacade,
                provider(userSnapshot(7L, "uploader", "ENABLED"))
        );

        assertThatThrownBy(() -> adapter.uploadDocument(mock(org.springframework.web.multipart.MultipartFile.class), "doc", "tag", "remark", "bucket"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void directUploadsRejectMissingSessionVersionBeforeFileServiceCall() {
        FileManagementAppService fileManagementAppService = mock(FileManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        CurrentUser currentUser = new CurrentUser(7L, "uploader", null, "s1", null, true, java.util.Set.of("system:file:upload"));
        org.mockito.Mockito.when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        LocalFileInternalApiAdapter adapter = new LocalFileInternalApiAdapter(
                fileManagementAppService,
                securityContextFacade,
                provider(userSnapshot(7L, "uploader", "ENABLED"))
        );

        assertThatThrownBy(() -> adapter.uploadDocument(mock(org.springframework.web.multipart.MultipartFile.class), "doc", "tag", "remark", "bucket"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        org.mockito.Mockito.verifyNoInteractions(fileManagementAppService);
    }

    @Test
    void userScopedInternalCallsRejectInvalidUserIdBeforeFileServiceCall() {
        FileManagementAppService fileManagementAppService = mock(FileManagementAppService.class);
        LocalFileInternalApiAdapter adapter = new LocalFileInternalApiAdapter(
                fileManagementAppService,
                mock(SecurityContextFacade.class),
                provider(userSnapshot(42L, "alice", "ENABLED"))
        );

        assertThatThrownBy(() -> adapter.readFileContentForUser(99L, 0L, "user-uuid-42", "alice"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void userScopedInternalCallsRejectInvalidFileIdBeforeFileServiceCall() {
        FileManagementAppService fileManagementAppService = mock(FileManagementAppService.class);
        LocalFileInternalApiAdapter adapter = new LocalFileInternalApiAdapter(
                fileManagementAppService,
                mock(SecurityContextFacade.class),
                provider(userSnapshot(42L, "alice", "ENABLED"))
        );

        assertThatThrownBy(() -> adapter.readFileContentForUser(0L, 42L, "user-uuid-42", "alice"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        org.mockito.Mockito.verifyNoInteractions(fileManagementAppService);
    }

    @Test
    void userScopedInternalCallsRejectBlankUsernameBeforeFileServiceCall() {
        FileManagementAppService fileManagementAppService = mock(FileManagementAppService.class);
        LocalFileInternalApiAdapter adapter = new LocalFileInternalApiAdapter(
                fileManagementAppService,
                mock(SecurityContextFacade.class),
                provider(userSnapshot(42L, "alice", "ENABLED"))
        );

        assertThatThrownBy(() -> adapter.readFileContentForUser(99L, 42L, "user-uuid-42", " "))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void imageUploadsForUserUseActingUser() {
        FileManagementAppService fileManagementAppService = mock(FileManagementAppService.class);
        LocalFileInternalApiAdapter adapter = new LocalFileInternalApiAdapter(
                fileManagementAppService,
                mock(SecurityContextFacade.class),
                provider(userSnapshot(42L, "alice", "ENABLED"))
        );

        adapter.uploadImageForUser(mock(org.springframework.web.multipart.MultipartFile.class), "avatar", "profile", "avatar", 42L, "user-uuid-42", "alice");

        var userCaptor = org.mockito.ArgumentCaptor.forClass(CurrentUser.class);
        verify(fileManagementAppService).uploadPublicImage(userCaptor.capture(), any(), eq("avatar"), eq("profile"), eq("avatar"));
        assertThat(userCaptor.getValue().getUserId()).isEqualTo(42L);
        assertThat(userCaptor.getValue().getUsername()).isEqualTo("alice");
        assertThat(userCaptor.getValue().getSessionId()).isEqualTo("internal-file-user-42");
        assertThat(userCaptor.getValue().getSessionVersion()).isEqualTo(1);
        assertThat(userCaptor.getValue().getPermissions()).doesNotContain("*");
    }

    @Test
    void artifactsRejectInvalidArtifactTypeBeforeFileServiceCall() {
        FileManagementAppService fileManagementAppService = mock(FileManagementAppService.class);
        LocalFileInternalApiAdapter adapter = new LocalFileInternalApiAdapter(
                fileManagementAppService,
                mock(SecurityContextFacade.class),
                provider(userSnapshot(42L, "alice", "ENABLED"))
        );

        assertThatThrownBy(() -> adapter.readProcessingArtifactForUser(99L, 42L, "user-uuid-42", "alice", "../text"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        org.mockito.Mockito.verifyNoInteractions(fileManagementAppService);
    }

    @Test
    void userScopedInternalCallsRejectUsernameMismatchBeforeFileServiceCall() {
        FileManagementAppService fileManagementAppService = mock(FileManagementAppService.class);
        LocalFileInternalApiAdapter adapter = new LocalFileInternalApiAdapter(
                fileManagementAppService,
                mock(SecurityContextFacade.class),
                provider(userSnapshot(42L, "alice", "ENABLED"))
        );

        assertThatThrownBy(() -> adapter.readFileContentForUser(99L, 42L, "user-uuid-42", "mallory"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Acting user identity mismatch");

        org.mockito.Mockito.verifyNoInteractions(fileManagementAppService);
    }

    @Test
    void userScopedInternalCallsRejectUserUuidMismatchBeforeFileServiceCall() {
        FileManagementAppService fileManagementAppService = mock(FileManagementAppService.class);
        LocalFileInternalApiAdapter adapter = new LocalFileInternalApiAdapter(
                fileManagementAppService,
                mock(SecurityContextFacade.class),
                provider(userSnapshot(42L, "alice", "ENABLED"))
        );

        assertThatThrownBy(() -> adapter.readFileContentForUser(99L, 42L, "other-user-uuid", "alice"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Acting user identity mismatch");

        org.mockito.Mockito.verifyNoInteractions(fileManagementAppService);
    }

    @Test
    void userScopedInternalCallsRejectMissingTrustedStatusBeforeFileServiceCall() {
        FileManagementAppService fileManagementAppService = mock(FileManagementAppService.class);
        LocalFileInternalApiAdapter adapter = new LocalFileInternalApiAdapter(
                fileManagementAppService,
                mock(SecurityContextFacade.class),
                provider(userSnapshot(42L, "alice", " "))
        );

        assertThatThrownBy(() -> adapter.readFileContentForUser(99L, 42L, "user-uuid-42", "alice"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Acting user is disabled");

        org.mockito.Mockito.verifyNoInteractions(fileManagementAppService);
    }

    @Test
    void userScopedInternalCallsRejectMissingTrustedResolverBeforeFileServiceCall() {
        FileManagementAppService fileManagementAppService = mock(FileManagementAppService.class);
        LocalFileInternalApiAdapter adapter = new LocalFileInternalApiAdapter(
                fileManagementAppService,
                mock(SecurityContextFacade.class),
                unavailableProvider()
        );

        assertThatThrownBy(() -> adapter.uploadImageForUser(mock(org.springframework.web.multipart.MultipartFile.class), "avatar", "profile", null, 42L, "user-uuid-42", "alice"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted acting user resolver is unavailable");

        org.mockito.Mockito.verifyNoInteractions(fileManagementAppService);
    }

    private SystemUserSnapshotDTO userSnapshot(Long userId, String username, String status) {
        return new SystemUserSnapshotDTO(userId, "user-uuid-" + userId, username, null, status, null, null, null, null, null, null, null, null, null, null, null);
    }

    private ObjectProvider<SystemInternalApi> provider(SystemUserSnapshotDTO snapshot) {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        org.mockito.Mockito.when(systemInternalApi.findUserIdentityById(snapshot.userId())).thenReturn(snapshot);
        org.mockito.Mockito.when(systemInternalApi.permissionSnapshot(snapshot.userId(), snapshot.userUuid())).thenReturn(permissionSnapshot());
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
