package com.lumira.saas.modules.system.controller;

import com.lumira.api.client.FileInternalApi;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.file.FileObjectDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.app.SystemManagementAppService;
import com.lumira.saas.modules.system.dto.ProfileDTO;
import com.lumira.saas.modules.auth.vo.CurrentUserVO;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProfileControllerTest {

    @Test
    void updateEmailShouldDelegateToAppService() {
        SystemManagementAppService appService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        CurrentUser currentUser = new CurrentUser(42L, "alice", null, "session-1", 1, true, Set.of("profile:view"));
        currentUser.setUserUuid("user-uuid-42");
        currentUser.setPermissionsVersion("permissions-1");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        ProfileDTO.EmailUpdateRequest request = new ProfileDTO.EmailUpdateRequest();
        request.setEmail("alice+new@example.com");
        request.setChallengeId("challenge-email-1");
        request.setVerificationCode("123456");
        CurrentUserVO updated = new CurrentUserVO();
        updated.setUserId(42L);
        updated.setEmail("alice+new@example.com");
        when(appService.updateCurrentUserEmail(currentUser, request)).thenReturn(updated);
        ProfileController controller = new ProfileController(appService, securityContextFacade, fileInternalApi);

        var response = controller.updateEmail(request);

        assertThat(response.getData()).isSameAs(updated);
        verify(appService).updateCurrentUserEmail(currentUser, request);
    }

    @Test
    void uploadAvatarUsesCurrentUserIdentityForFileOwnership() {
        SystemManagementAppService appService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        MultipartFile file = mock(MultipartFile.class);
        CurrentUser currentUser = new CurrentUser(42L, "alice", null, "session-1", 1, true, Set.of("profile:view"));
        currentUser.setUserUuid("user-uuid-42");
        currentUser.setPermissionsVersion("permissions-1");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(fileInternalApi.uploadImageForUser(eq(file), eq("头像"), eq("个人头像上传"), eq("avatar"), eq(42L), eq("user-uuid-42"), eq("alice"), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(fileObject("/avatar.png"));
        ProfileController controller = new ProfileController(appService, securityContextFacade, fileInternalApi);

        var response = controller.uploadAvatar(file);

        assertThat(response.getData()).isEqualTo("/avatar.png");
        verify(fileInternalApi).uploadImageForUser(eq(file), anyString(), anyString(), eq("avatar"), eq(42L), eq("user-uuid-42"), eq("alice"), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void uploadAvatarRejectsUnauthenticatedUserBeforeFileInternalCall() {
        SystemManagementAppService appService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        MultipartFile file = mock(MultipartFile.class);
        CurrentUser currentUser = new CurrentUser(42L, "alice", null, "session-1", 1, false, Set.of("profile:view"));
        currentUser.setUserUuid("user-uuid-42");
        currentUser.setPermissionsVersion("permissions-1");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        ProfileController controller = new ProfileController(appService, securityContextFacade, fileInternalApi);

        assertThatThrownBy(() -> controller.uploadAvatar(file))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(fileInternalApi);
    }

    @Test
    void uploadAvatarRejectsMissingSessionVersionBeforeFileInternalCall() {
        SystemManagementAppService appService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        MultipartFile file = mock(MultipartFile.class);
        CurrentUser currentUser = new CurrentUser(42L, "alice", null, "session-1", null, true, Set.of("profile:view"));
        currentUser.setUserUuid("user-uuid-42");
        currentUser.setPermissionsVersion("permissions-1");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        ProfileController controller = new ProfileController(appService, securityContextFacade, fileInternalApi);

        assertThatThrownBy(() -> controller.uploadAvatar(file))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(fileInternalApi);
    }

    @Test
    void uploadAvatarRejectsDisabledUserFromLiveSnapshotBeforeFileInternalCall() {
        SystemManagementAppService appService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        MultipartFile file = mock(MultipartFile.class);
        CurrentUser currentUser = new CurrentUser(42L, "alice", null, "session-1", 1, true, Set.of("profile:view"));
        currentUser.setUserUuid("user-uuid-42");
        currentUser.setPermissionsVersion("permissions-1");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(permissionSnapshotService.isTrustedActiveUser(42L, "user-uuid-42")).thenReturn(false);
        ProfileController controller = new ProfileController(appService, securityContextFacade, fileInternalApi, permissionSnapshotService);

        assertThatThrownBy(() -> controller.uploadAvatar(file))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(fileInternalApi);
    }

    @Test
    void uploadAvatarRejectsRevokedSessionTicketBeforeFileInternalCall() {
        SystemManagementAppService appService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        MultipartFile file = mock(MultipartFile.class);
        CurrentUser currentUser = new CurrentUser(42L, "alice", null, "session-1", 1, true, Set.of("profile:view"));
        currentUser.setUserUuid("user-uuid-42");
        currentUser.setPermissionsVersion("permissions-1");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(sessionAuthenticationService.authenticateSessionTicket(any(), any(), any(), any(), any(), any()))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Session expired"));
        ProfileController controller = new ProfileController(appService, securityContextFacade, fileInternalApi, null, sessionAuthenticationService);

        assertThatThrownBy(() -> controller.uploadAvatar(file))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(fileInternalApi);
    }

    @Test
    void uploadAvatarRejectsTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        SystemManagementAppService appService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        MultipartFile file = mock(MultipartFile.class);
        CurrentUser currentUser = new CurrentUser(42L, "alice", null, "session-1", 1, true, Set.of("profile:view"));
        currentUser.setUserUuid("user-uuid-42");
        currentUser.setPermissionsVersion("permissions-1");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        ProfileController controller = new ProfileController(appService, securityContextFacade, fileInternalApi, null, null, null);

        assertThatThrownBy(() -> controller.uploadAvatar(file))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(fileInternalApi);
    }

    @Test
    void uploadAvatarRejectsWhenTrustedPermissionSnapshotIsUnavailable() {
        SystemManagementAppService appService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        MultipartFile file = mock(MultipartFile.class);
        CurrentUser currentUser = new CurrentUser(42L, "alice", null, "session-1", 1, true, Set.of("profile:view"));
        currentUser.setUserUuid("user-uuid-42");
        currentUser.setPermissionsVersion("permissions-1");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(permissionSnapshotService.isTrustedActiveUser(42L, "user-uuid-42")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(42L, "user-uuid-42")).thenReturn(null);
        ProfileController controller = new ProfileController(appService, securityContextFacade, fileInternalApi, permissionSnapshotService, null, null);

        assertThatThrownBy(() -> controller.uploadAvatar(file))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(exception.getMessage()).contains("Trusted user permission snapshot is unavailable");
                });

        verifyNoInteractions(fileInternalApi);
    }

    @Test
    void uploadAvatarRejectsBlankLiveUsernameBeforeFileInternalCall() {
        SystemManagementAppService appService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        MultipartFile file = mock(MultipartFile.class);
        CurrentUser currentUser = new CurrentUser(42L, "alice", null, "session-1", 1, true, Set.of("profile:view"));
        currentUser.setUserUuid("user-uuid-42");
        currentUser.setPermissionsVersion("permissions-1");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(permissionSnapshotService.isTrustedActiveUser(42L, "user-uuid-42")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(42L, "user-uuid-42"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("profile:view")));
        when(systemInternalApi.findUserIdentityById(42L))
                .thenReturn(userSnapshot(42L, "user-uuid-42", "   ", "ENABLED"));
        ProfileController controller = new ProfileController(
                appService,
                securityContextFacade,
                fileInternalApi,
                permissionSnapshotService,
                systemInternalApi,
                null
        );

        assertThatThrownBy(() -> controller.uploadAvatar(file))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(exception.getMessage()).contains("Trusted user username is unavailable");
                });

        verifyNoInteractions(fileInternalApi);
    }

    @Test
    void refreshTrustedCurrentUserShouldNormalizeInvalidSimulatedRoleIdBeforeSnapshotLoad() throws Exception {
        SystemManagementAppService appService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        CurrentUser currentUser = new CurrentUser(42L, "alice", null, "session-1", 1, true, Set.of("profile:view"));
        currentUser.setUserUuid("user-uuid-42");
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setSimulatedRoleId(0L);
        when(permissionSnapshotService.isTrustedActiveUser(42L, "user-uuid-42")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(42L, "user-uuid-42"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("profile:view")));
        when(systemInternalApi.findUserIdentityById(42L))
                .thenReturn(userSnapshot(42L, "user-uuid-42", "alice-live", "ENABLED"));
        ProfileController controller = new ProfileController(
                appService,
                securityContextFacade,
                fileInternalApi,
                permissionSnapshotService,
                systemInternalApi,
                null
        );
        Method method = ProfileController.class.getDeclaredMethod("refreshTrustedCurrentUser", CurrentUser.class);
        method.setAccessible(true);

        method.invoke(controller, currentUser);

        assertThat(currentUser.getSimulatedRoleId()).isNull();
        verify(permissionSnapshotService).loadSnapshot(42L, "user-uuid-42");
        verify(permissionSnapshotService, never()).loadGrantedRoleSnapshot(any(), anyString(), any());
    }

    private FileObjectDTO fileObject(String publicUrl) {
        return new FileObjectDTO(
                1L,
                42L,
                "user-uuid-42",
                "alice",
                "avatar.png",
                "avatar.png",
                "LOCAL",
                "avatar",
                "png",
                "image/png",
                12L,
                "12 B",
                "storage/avatar.png",
                publicUrl,
                null,
                publicUrl,
                "IMAGE",
                true,
                "avatar",
                null,
                null,
                "ENABLED",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
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
}
