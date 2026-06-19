package com.lumira.saas.modules.iam.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.lumira.saas.common.annotation.RepeatSubmit;
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
                        "currentTenant:/tenants/current",
                        "myTenants:/tenants/mine",
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
                        "createTenant:/tenants",
                        "createUser:/users",
                        "exportUsers:/users/export",
                        "createRole:/roles",
                        "createMenu:/menus",
                        "createDepartment:/departments"
                );
        assertThat(methodsWith(PutMapping.class))
                .contains(
                        "updateTenant:/tenants/{id}",
                        "upsertTenantMember:/tenants/{tenantId}/members/{userId}",
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
                        "changeTenantStatus:/tenants/{id}/status",
                        "changeUserStatus:/users/{id}/status",
                        "updateMenuStatus:/menus/{id}/status"
                );
        assertThat(methodsWith(DeleteMapping.class))
                .contains(
                        "archiveTenant:/tenants/{id}",
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
