package com.lumira.saas.modules.system.controller;

import com.lumira.api.client.FileInternalApi;
import com.lumira.api.file.FileObjectDTO;
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

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProfileControllerTest {

    @Test
    void updateEmailShouldDelegateToAppService() {
        SystemManagementAppService appService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        CurrentUser currentUser = new CurrentUser(42L, "alice", null, "session-1", 1, true, Set.of("profile:update"));
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
        CurrentUser currentUser = new CurrentUser(42L, "alice", null, "session-1", 1, true, Set.of("profile:update"));
        currentUser.setUserUuid("user-uuid-42");
        currentUser.setPermissionsVersion("permissions-1");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(fileInternalApi.uploadImageForUser(eq(file), eq("头像"), eq("个人头像上传"), eq("avatar"), eq(42L), eq("user-uuid-42"), eq("alice")))
                .thenReturn(fileObject("/avatar.png"));
        ProfileController controller = new ProfileController(appService, securityContextFacade, fileInternalApi);

        var response = controller.uploadAvatar(file);

        assertThat(response.getData()).isEqualTo("/avatar.png");
        verify(fileInternalApi).uploadImageForUser(file, "头像", "个人头像上传", "avatar", 42L, "user-uuid-42", "alice");
    }

    @Test
    void uploadAvatarRejectsUnauthenticatedUserBeforeFileInternalCall() {
        SystemManagementAppService appService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        MultipartFile file = mock(MultipartFile.class);
        CurrentUser currentUser = new CurrentUser(42L, "alice", null, "session-1", 1, false, Set.of("profile:update"));
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
        CurrentUser currentUser = new CurrentUser(42L, "alice", null, "session-1", null, true, Set.of("profile:update"));
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
        CurrentUser currentUser = new CurrentUser(42L, "alice", null, "session-1", 1, true, Set.of("profile:update"));
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
        CurrentUser currentUser = new CurrentUser(42L, "alice", null, "session-1", 1, true, Set.of("profile:update"));
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
}
