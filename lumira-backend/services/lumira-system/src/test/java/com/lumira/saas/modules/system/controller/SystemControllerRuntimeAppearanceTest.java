package com.lumira.saas.modules.system.controller;

import com.lumira.api.file.FileObjectDTO;
import com.lumira.api.client.FileInternalApi;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.app.SystemManagementAppService;
import com.lumira.saas.modules.system.dict.app.DictRuntimeService;
import com.lumira.saas.testfixture.ExportTaskService;
import com.lumira.saas.modules.system.user.app.UserExportAppService;
import com.lumira.saas.modules.system.vo.SystemVO;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemControllerRuntimeAppearanceTest {

    @Test
    void runtimeAppearanceSettingsRequiresConfigViewPermission() {
        SystemManagementAppService systemManagementAppService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        CurrentUser currentUser = trustedUser(Set.of());
        SystemVO.RuntimeAppearanceSettingsVO settings = new SystemVO.RuntimeAppearanceSettingsVO();
        settings.setBrandingSettings(new SystemVO.BrandingSettingsVO());
        settings.setWatermarkSettings(new SystemVO.WatermarkSettingsVO());
        settings.setFloatingWindowSettings(new SystemVO.FloatingWindowSettingsVO());

        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(systemManagementAppService.getRuntimeAppearanceSettings(currentUser)).thenReturn(settings);

        SystemController controller = new SystemController(
                systemManagementAppService,
                securityContextFacade,
                permissionGuard,
                mock(FileInternalApi.class),
                mock(UserExportAppService.class),
                mock(ExportTaskService.class),
                mock(DictRuntimeService.class)
        );

        var response = controller.runtimeAppearanceSettings();

        assertThat(response.getData()).isSameAs(settings);
        verify(permissionGuard).requirePermission(currentUser, "system:config:view");
        verify(systemManagementAppService).getRuntimeAppearanceSettings(currentUser);
    }

    @Test
    void uploadImageUsesPublicBrandingUploadBucket() {
        SystemManagementAppService systemManagementAppService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        MultipartFile file = mock(MultipartFile.class);
        CurrentUser currentUser = trustedUser(Set.of("system:config:update"));
        FileObjectDTO uploaded = new FileObjectDTO(
                10L,
                2001L,
                "user-uuid-2001",
                "alice",
                "logo.png",
                "2026/06/23/logo.png",
                "LOCAL",
                "local",
                "png",
                "image/png",
                128L,
                null,
                "2026/06/23/logo.png",
                "/api/uploads/2026/06/23/logo.png",
                "/api/uploads/2026/06/23/logo.png",
                "/api/uploads/2026/06/23/logo.png",
                "IMAGE",
                true,
                "系统图片",
                null,
                "系统配置图片上传",
                "ENABLED",
                null,
                null
        );
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(fileInternalApi.uploadImageForUser(eq(file), eq("系统图片"), eq("系统配置图片上传"), eq("local"), eq(2001L), eq("user-uuid-2001"), eq("alice"), org.mockito.ArgumentMatchers.isNull())).thenReturn(uploaded);

        SystemController controller = new SystemController(
                systemManagementAppService,
                securityContextFacade,
                permissionGuard,
                fileInternalApi,
                mock(UserExportAppService.class),
                mock(ExportTaskService.class),
                mock(DictRuntimeService.class)
        );

        var response = controller.uploadImage(file);

        assertThat(response.getData()).isEqualTo("/api/uploads/2026/06/23/logo.png");
        verify(permissionGuard).requirePermission(currentUser, "system:config:update");
        verify(fileInternalApi).uploadImageForUser(file, "系统图片", "系统配置图片上传", "local", 2001L, "user-uuid-2001", "alice", null);
    }

    @Test
    void uploadImageShouldRejectWhenLiveSnapshotRevokesConfigUpdatePermissionBeforeDelegating() {
        SystemManagementAppService systemManagementAppService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionGuard permissionGuard = new PermissionGuard();
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        MultipartFile file = mock(MultipartFile.class);
        CurrentUser currentUser = trustedUser(Set.of("system:config:update"));
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(permissionSnapshotService.isTrustedActiveUser(2001L, "user-uuid-2001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(2001L, "user-uuid-2001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("system:config:view")));

        SystemController controller = new SystemController(
                systemManagementAppService,
                securityContextFacade,
                permissionGuard,
                fileInternalApi,
                mock(UserExportAppService.class),
                mock(ExportTaskService.class),
                mock(DictRuntimeService.class),
                permissionSnapshotService
        );

        assertThatThrownBy(() -> controller.uploadImage(file))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(fileInternalApi, never()).uploadImageForUser(eq(file), eq("系统图片"), eq("系统配置图片上传"), eq("local"), eq(2001L), eq("user-uuid-2001"), eq("alice"), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void uploadImageShouldRejectRevokedSessionTicketBeforeDelegating() {
        SystemManagementAppService systemManagementAppService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        MultipartFile file = mock(MultipartFile.class);
        CurrentUser currentUser = trustedUser(Set.of("system:config:update"));
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(sessionAuthenticationService.authenticateSessionTicket("session-1", 2001L, "user-uuid-2001", null, 1, "permissions-1"))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Login required"));

        SystemController controller = new SystemController(
                systemManagementAppService,
                securityContextFacade,
                permissionGuard,
                fileInternalApi,
                mock(UserExportAppService.class),
                mock(ExportTaskService.class),
                mock(DictRuntimeService.class),
                permissionSnapshotService,
                sessionAuthenticationService
        );

        assertThatThrownBy(() -> controller.uploadImage(file))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(permissionGuard, never()).requirePermission(currentUser, "system:config:update");
        verify(fileInternalApi, never()).uploadImageForUser(eq(file), eq("系统图片"), eq("系统配置图片上传"), eq("local"), eq(2001L), eq("user-uuid-2001"), eq("alice"), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void uploadImageShouldRejectTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        SystemManagementAppService systemManagementAppService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        MultipartFile file = mock(MultipartFile.class);
        CurrentUser currentUser = trustedUser(Set.of("system:config:update"));
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);

        SystemController controller = new SystemController(
                systemManagementAppService,
                securityContextFacade,
                permissionGuard,
                fileInternalApi,
                mock(UserExportAppService.class),
                mock(ExportTaskService.class),
                mock(DictRuntimeService.class),
                null,
                null,
                null
        );

        assertThatThrownBy(() -> controller.uploadImage(file))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(permissionGuard, never()).requirePermission(currentUser, "system:config:update");
        verify(fileInternalApi, never()).uploadImageForUser(eq(file), eq("系统图片"), eq("系统配置图片上传"), eq("local"), eq(2001L), eq("user-uuid-2001"), eq("alice"), org.mockito.ArgumentMatchers.isNull());
    }

    private CurrentUser trustedUser(Set<String> permissions) {
        CurrentUser currentUser = new CurrentUser(2001L, "alice", 1001L, "session-1", 1, true, permissions);
        currentUser.setUserUuid("user-uuid-2001");
        currentUser.setPermissionsVersion("permissions-1");
        return currentUser;
    }
}
