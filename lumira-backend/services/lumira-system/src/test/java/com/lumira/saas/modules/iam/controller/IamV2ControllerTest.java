package com.lumira.saas.modules.iam.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.saas.common.annotation.RepeatSubmit;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.app.SystemManagementAppService;
import com.lumira.saas.modules.system.department.app.SystemDepartmentAppService;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.export.ExportTaskService;
import com.lumira.saas.modules.system.user.app.UserExportAppService;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class IamV2ControllerTest {

    @Test
    void iamV2Controller_shouldExposeIamOwnerManagementAdapter() {
        RequestMapping requestMapping = IamV2Controller.class.getAnnotation(RequestMapping.class);

        assertThat(requestMapping).isNotNull();
        assertThat(requestMapping.value()).containsExactly("/api/v2/iam");

        assertThat(methodsWith(GetMapping.class))
                .contains(
                        "permissions:/permissions",
                        "permissionTree:/permissions/tree",
                        "users:/users",
                        "user:/users/{id}",
                        "userRoles:/users/{id}/roles",
                        "userExportFields:/users/export-fields",
                        "exportTask:/export-tasks/{taskId}",
                        "roles:/roles",
                        "role:/roles/{id}",
                        "defaultRegistrationRole:/roles/default-registration-role",
                        "menus:/menus",
                        "menu:/menus/{id}",
                        "departments:/departments",
                        "department:/departments/{id}"
                );
        assertThat(methodsWith(PostMapping.class))
                .contains(
                        "createUser:/users",
                        "exportUsers:/users/export",
                        "createRole:/roles",
                        "createMenu:/menus",
                        "createDepartment:/departments"
                );
        assertThat(methodsWith(PutMapping.class))
                .contains(
                        "updateUser:/users/{id}",
                        "updateDefaultRegistrationRole:/roles/default-registration-role",
                        "updateRole:/roles/{id}",
                        "updateRolePermissions:/roles/{id}/permissions",
                        "updateMenu:/menus/{id}",
                        "reorderMenus:/menus/reorder",
                        "updateDepartment:/departments/{id}"
                );
        assertThat(methodsWith(PatchMapping.class))
                .contains(
                        "changeUserStatus:/users/{id}/status",
                        "updateMenuStatus:/menus/{id}/status"
                );
        assertThat(methodsWith(DeleteMapping.class))
                .contains(
                        "deleteUser:/users/{id}",
                        "deleteRole:/roles/{id}",
                        "deleteMenu:/menus/{id}",
                        "deleteDepartment:/departments/{id}"
                );
    }

    @Test
    void writeEndpoints_shouldKeepRepeatSubmitProtection() {
        for (Method method : IamV2Controller.class.getDeclaredMethods()) {
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
    void updateRolePermissionsShouldRequireRoleGrantPermission() {
        SystemManagementAppService systemManagementAppService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        IamV2Controller controller = new IamV2Controller(
                systemManagementAppService,
                mock(SystemDepartmentAppService.class),
                mock(UserExportAppService.class),
                mock(ExportTaskService.class),
                securityContextFacade,
                permissionGuard
        );
        CurrentUser user = new CurrentUser();
        user.setUserId(3001L);
        user.setUsername("admin");
        user.setSessionId("session-3001");
        user.setSessionVersion(1);
        user.setUserUuid("user-uuid-3001");
        user.setPermissionsVersion("permissions-1");
        user.setAuthenticated(true);
        SystemDTO.RolePermissionRequest request = new SystemDTO.RolePermissionRequest();
        request.setPermissionKeys(List.of("system:user:view"));
        when(securityContextFacade.getCurrentUser()).thenReturn(user);
        when(systemManagementAppService.updateRolePermissions(user, 2001L, request.getPermissionKeys())).thenReturn(true);

        assertThat(controller.updateRolePermissions(2001L, request).getData()).isTrue();

        verify(permissionGuard).requirePermission(user, "system:role:grant");
        verify(systemManagementAppService).updateRolePermissions(user, 2001L, request.getPermissionKeys());
    }

    @Test
    void createUserShouldRejectTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        SystemManagementAppService systemManagementAppService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        CurrentUser user = trustedCurrentUser("system:user:create");
        when(securityContextFacade.getCurrentUser()).thenReturn(user);
        IamV2Controller controller = new IamV2Controller(
                systemManagementAppService,
                mock(SystemDepartmentAppService.class),
                mock(UserExportAppService.class),
                mock(ExportTaskService.class),
                securityContextFacade,
                new PermissionGuard(),
                null,
                null,
                null
        );

        assertThatThrownBy(() -> controller.createUser(new SystemDTO.UserUpsertRequest()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user resolver is unavailable");
        verify(systemManagementAppService, never()).createUser(any(), any());
    }

    @Test
    void createUserShouldRejectWhenTrustedPermissionSnapshotIsUnavailable() {
        SystemManagementAppService systemManagementAppService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        CurrentUser user = trustedCurrentUser("system:user:create");
        when(securityContextFacade.getCurrentUser()).thenReturn(user);
        when(permissionSnapshotService.isTrustedActiveUser(3001L, "user-uuid-3001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(3001L, "user-uuid-3001")).thenReturn(null);
        IamV2Controller controller = new IamV2Controller(
                systemManagementAppService,
                mock(SystemDepartmentAppService.class),
                mock(UserExportAppService.class),
                mock(ExportTaskService.class),
                securityContextFacade,
                new PermissionGuard(),
                permissionSnapshotService,
                null,
                null
        );

        assertThatThrownBy(() -> controller.createUser(new SystemDTO.UserUpsertRequest()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user permission snapshot is unavailable");
        verify(systemManagementAppService, never()).createUser(any(), any());
    }

    @Test
    void createUserShouldRejectWhenLiveUsernameIsBlank() {
        SystemManagementAppService systemManagementAppService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        com.lumira.api.client.SystemInternalApi systemInternalApi = mock(com.lumira.api.client.SystemInternalApi.class);
        CurrentUser user = trustedCurrentUser("system:user:create");
        when(securityContextFacade.getCurrentUser()).thenReturn(user);
        when(systemInternalApi.findUserIdentityById(3001L))
                .thenReturn(userSnapshot(3001L, "user-uuid-3001", " ", "ENABLED"));
        IamV2Controller controller = new IamV2Controller(
                systemManagementAppService,
                mock(SystemDepartmentAppService.class),
                mock(UserExportAppService.class),
                mock(ExportTaskService.class),
                securityContextFacade,
                new PermissionGuard(),
                permissionSnapshotService,
                systemInternalApi,
                null
        );

        assertThatThrownBy(() -> controller.createUser(new SystemDTO.UserUpsertRequest()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user username is unavailable");
        verify(permissionSnapshotService, never()).isTrustedActiveUser(any(), any());
        verify(systemManagementAppService, never()).createUser(any(), any());
    }

    @Test
    void refreshTrustedCurrentUserShouldNormalizeInvalidSimulatedRoleIdBeforeSnapshotLoad() throws Exception {
        SystemManagementAppService systemManagementAppService = mock(SystemManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        com.lumira.api.client.SystemInternalApi systemInternalApi = mock(com.lumira.api.client.SystemInternalApi.class);
        CurrentUser user = trustedCurrentUser("system:user:create");
        user.setSimulatedRoleId(0L);
        when(permissionSnapshotService.isTrustedActiveUser(3001L, "user-uuid-3001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(3001L, "user-uuid-3001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("system:user:create")));
        when(systemInternalApi.findUserIdentityById(3001L))
                .thenReturn(userSnapshot(3001L, "user-uuid-3001", "admin-live", "ENABLED"));
        IamV2Controller controller = new IamV2Controller(
                systemManagementAppService,
                mock(SystemDepartmentAppService.class),
                mock(UserExportAppService.class),
                mock(ExportTaskService.class),
                securityContextFacade,
                new PermissionGuard(),
                permissionSnapshotService,
                systemInternalApi,
                null
        );
        Method method = IamV2Controller.class.getDeclaredMethod("refreshTrustedCurrentUser", CurrentUser.class);
        method.setAccessible(true);

        method.invoke(controller, user);

        assertThat(user.getSimulatedRoleId()).isNull();
        verify(permissionSnapshotService).loadSnapshot(3001L, "user-uuid-3001");
        verify(permissionSnapshotService, never()).loadGrantedRoleSnapshot(anyLong(), anyString(), anyLong());
    }

    private CurrentUser trustedCurrentUser(String permission) {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(3001L);
        currentUser.setUsername("admin");
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
        return Arrays.stream(IamV2Controller.class.getDeclaredMethods())
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
