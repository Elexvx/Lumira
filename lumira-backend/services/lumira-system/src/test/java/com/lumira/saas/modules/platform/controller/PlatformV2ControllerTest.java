package com.lumira.saas.modules.platform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lumira.saas.common.annotation.RepeatSubmit;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.saas.modules.architecture.application.OwnerRuntimeMetrics;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.platform.app.PlatformBootstrapService;
import com.lumira.saas.modules.system.app.OnlineSessionManagementAppService;
import com.lumira.saas.modules.system.app.SystemManagementAppService;
import com.lumira.saas.modules.system.verification.SystemVerificationAppService;
import com.lumira.saas.modules.system.vo.SystemVO;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformV2ControllerTest {

    @Test
    void platformV2Controller_shouldExposePlatformOwnerManagementAdapter() {
        RequestMapping requestMapping = PlatformV2Controller.class.getAnnotation(RequestMapping.class);

        assertThat(requestMapping).isNotNull();
        assertThat(requestMapping.value()).containsExactly("/api/v2/platform");

        Set<String> getEndpoints = methodsWith(GetMapping.class);
        Set<String> postEndpoints = methodsWith(PostMapping.class);
        Set<String> putEndpoints = methodsWith(PutMapping.class);
        Set<String> patchEndpoints = methodsWith(PatchMapping.class);
        Set<String> deleteEndpoints = methodsWith(DeleteMapping.class);

        assertThat(getEndpoints)
                .contains(
                        "publicBootstrap:/public/bootstrap",
                        "configs:/configs",
                        "config:/configs/{id}",
                        "dictTypes:/dict-types",
                        "dictType:/dict-types/{id}",
                        "dictItems:/dict-types/{id}/items",
                        "runtimeAppearanceSettings:/runtime-appearance-settings",
                        "brandingSettings:/branding-settings",
                        "agreementSettings:/agreement-settings",
                        "watermarkSettings:/watermark-settings",
                        "floatingWindowSettings:/floating-window-settings",
                        "securitySettings:/security-settings",
                        "smtpSettings:/smtp-settings",
                        "wechatOfficialAccountSettings:/notification/wechat-official-settings",
                        "auditSummary:/audit/summary",
                        "loginLogs:/audit/login-logs",
                        "operationLogs:/audit/operation-logs",
                        "verificationLogs:/audit/verification-logs",
                        "dashboardSummary:/monitoring/dashboard/summary",
                        "onlineUsers:/monitoring/online-users",
                        "onlineUserEvents:/monitoring/online-users/events"
                );
        assertThat(postEndpoints)
                .contains(
                        "createConfig:/configs",
                        "createDictType:/dict-types",
                        "createDictItem:/dict-types/{id}/items",
                        "testSmtpSettings:/smtp-settings/test"
                );
        assertThat(putEndpoints)
                .contains(
                        "updateConfig:/configs/{id}",
                        "updateDictType:/dict-types/{id}",
                        "updateDictItem:/dict-types/{dictTypeId}/items/{itemId}",
                        "updateBrandingSettings:/branding-settings",
                        "updateAgreementSettings:/agreement-settings",
                        "updateWatermarkSettings:/watermark-settings",
                        "updateFloatingWindowSettings:/floating-window-settings",
                        "updateSecuritySettings:/security-settings",
                        "updateSmtpSettings:/smtp-settings",
                        "updateWechatOfficialAccountSettings:/notification/wechat-official-settings"
                );
        assertThat(patchEndpoints)
                .contains(
                        "banOnlineUser:/monitoring/online-users/{userId}/ban"
                );
        assertThat(deleteEndpoints)
                .contains(
                        "deleteDictType:/dict-types/{id}",
                        "deleteDictItem:/dict-types/{dictTypeId}/items/{itemId}",
                        "resetSmtpSettings:/smtp-settings",
                        "kickOnlineUser:/monitoring/online-users/{sessionId}"
                );
    }

    @Test
    void writeEndpoints_shouldKeepRepeatSubmitProtection() {
        for (Method method : PlatformV2Controller.class.getDeclaredMethods()) {
            if (method.getAnnotation(PostMapping.class) != null
                    || method.getAnnotation(PutMapping.class) != null
                    || method.getAnnotation(PatchMapping.class) != null
                    || method.getAnnotation(DeleteMapping.class) != null) {
                assertThat(method.getAnnotation(RepeatSubmit.class))
                        .as(method.getName())
                        .isNotNull();
            }
        }
    }

    @Test
    void agreementSettingsShouldUsePublicAgreementPath() {
        SystemManagementAppService systemManagementAppService = mock(SystemManagementAppService.class);
        SystemVO.AgreementSettingsVO agreementSettings = new SystemVO.AgreementSettingsVO();
        when(systemManagementAppService.getPublicAgreementSettings()).thenReturn(agreementSettings);
        PlatformV2Controller controller = new PlatformV2Controller(
                systemManagementAppService,
                mock(SystemVerificationAppService.class),
                mock(OnlineSessionManagementAppService.class),
                mock(PlatformBootstrapService.class),
                mock(OwnerRuntimeMetrics.class),
                mock(SecurityContextFacade.class),
                new PermissionGuard()
        );

        var response = controller.agreementSettings();

        assertThat(response.getData()).isSameAs(agreementSettings);
        verify(systemManagementAppService).getPublicAgreementSettings();
        verify(systemManagementAppService, never()).getAgreementSettings(any());
    }

    @Test
    void createConfigShouldRejectTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        SystemManagementAppService systemManagementAppService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        CurrentUser currentUser = trustedCurrentUser("system:config:update");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        PlatformV2Controller controller = new PlatformV2Controller(
                systemManagementAppService,
                mock(SystemVerificationAppService.class),
                mock(OnlineSessionManagementAppService.class),
                mock(PlatformBootstrapService.class),
                mock(OwnerRuntimeMetrics.class),
                securityContextFacade,
                new PermissionGuard(),
                null,
                null,
                null
        );

        assertThatThrownBy(() -> controller.createConfig(null))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user resolver is unavailable");
        verify(systemManagementAppService, never()).createConfig(any(), any());
    }

    @Test
    void createConfigShouldRejectWhenTrustedPermissionSnapshotIsUnavailable() {
        SystemManagementAppService systemManagementAppService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        CurrentUser currentUser = trustedCurrentUser("system:config:update");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(permissionSnapshotService.isTrustedActiveUser(3001L, "user-uuid-3001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(3001L, "user-uuid-3001")).thenReturn(null);
        PlatformV2Controller controller = new PlatformV2Controller(
                systemManagementAppService,
                mock(SystemVerificationAppService.class),
                mock(OnlineSessionManagementAppService.class),
                mock(PlatformBootstrapService.class),
                mock(OwnerRuntimeMetrics.class),
                securityContextFacade,
                new PermissionGuard(),
                permissionSnapshotService,
                null,
                null
        );

        assertThatThrownBy(() -> controller.createConfig(null))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user permission snapshot is unavailable");
        verify(systemManagementAppService, never()).createConfig(any(), any());
    }

    @Test
    void createConfigShouldRejectWhenLiveUsernameIsBlank() {
        SystemManagementAppService systemManagementAppService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        com.lumira.api.client.SystemInternalApi systemInternalApi = mock(com.lumira.api.client.SystemInternalApi.class);
        CurrentUser currentUser = trustedCurrentUser("system:config:update");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(systemInternalApi.findUserIdentityById(3001L))
                .thenReturn(userSnapshot(3001L, "user-uuid-3001", " ", "ENABLED"));
        PlatformV2Controller controller = new PlatformV2Controller(
                systemManagementAppService,
                mock(SystemVerificationAppService.class),
                mock(OnlineSessionManagementAppService.class),
                mock(PlatformBootstrapService.class),
                mock(OwnerRuntimeMetrics.class),
                securityContextFacade,
                new PermissionGuard(),
                permissionSnapshotService,
                systemInternalApi,
                null
        );

        assertThatThrownBy(() -> controller.createConfig(null))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user username is unavailable");
        verify(permissionSnapshotService, never()).isTrustedActiveUser(any(), any());
        verify(systemManagementAppService, never()).createConfig(any(), any());
    }

    @Test
    void refreshTrustedCurrentUserShouldNormalizeInvalidSimulatedRoleIdBeforeSnapshotLoad() throws Exception {
        SystemManagementAppService systemManagementAppService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        com.lumira.api.client.SystemInternalApi systemInternalApi = mock(com.lumira.api.client.SystemInternalApi.class);
        CurrentUser currentUser = trustedCurrentUser("system:config:update");
        currentUser.setSimulatedRoleId(0L);
        when(permissionSnapshotService.isTrustedActiveUser(3001L, "user-uuid-3001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(3001L, "user-uuid-3001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("system:config:update")));
        when(systemInternalApi.findUserIdentityById(3001L))
                .thenReturn(userSnapshot(3001L, "user-uuid-3001", "platform-admin-live", "ENABLED"));
        PlatformV2Controller controller = new PlatformV2Controller(
                systemManagementAppService,
                mock(SystemVerificationAppService.class),
                mock(OnlineSessionManagementAppService.class),
                mock(PlatformBootstrapService.class),
                mock(OwnerRuntimeMetrics.class),
                securityContextFacade,
                new PermissionGuard(),
                permissionSnapshotService,
                systemInternalApi,
                null
        );
        Method method = PlatformV2Controller.class.getDeclaredMethod("refreshTrustedCurrentUser", CurrentUser.class);
        method.setAccessible(true);

        method.invoke(controller, currentUser);

        assertThat(currentUser.getSimulatedRoleId()).isNull();
        verify(permissionSnapshotService).loadSnapshot(3001L, "user-uuid-3001");
        verify(permissionSnapshotService, never()).loadGrantedRoleSnapshot(anyLong(), anyString(), anyLong());
    }

    private CurrentUser trustedCurrentUser(String permission) {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(3001L);
        currentUser.setUsername("platform-admin");
        currentUser.setSessionId("session-3001");
        currentUser.setSessionVersion(1);
        currentUser.setUserUuid("user-uuid-3001");
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setAuthenticated(true);
        currentUser.setPermissions(Set.of(permission));
        return currentUser;
    }

    private com.lumira.api.system.SystemUserSnapshotDTO userSnapshot(Long userId, String userUuid, String username, String status) {
        return new com.lumira.api.system.SystemUserSnapshotDTO(
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

    private Set<String> methodsWith(Class<? extends java.lang.annotation.Annotation> annotationClass) {
        return Arrays.stream(PlatformV2Controller.class.getDeclaredMethods())
                .filter(method -> method.getAnnotation(annotationClass) != null)
                .map(method -> method.getName() + ":" + String.join(",", values(method.getAnnotation(annotationClass))))
                .collect(Collectors.toSet());
    }

    private String[] values(java.lang.annotation.Annotation annotation) {
        if (annotation instanceof GetMapping mapping) {
            return mapping.value();
        }
        if (annotation instanceof PostMapping mapping) {
            return mapping.value();
        }
        if (annotation instanceof PutMapping mapping) {
            return mapping.value();
        }
        if (annotation instanceof PatchMapping mapping) {
            return mapping.value();
        }
        if (annotation instanceof DeleteMapping mapping) {
            return mapping.value();
        }
        return new String[0];
    }
}
