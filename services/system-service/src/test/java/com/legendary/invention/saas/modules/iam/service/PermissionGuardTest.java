package com.legendary.invention.saas.modules.iam.service;

import com.legendary.invention.saas.common.exception.BizException;
import com.legendary.invention.saas.infrastructure.security.CurrentUser;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionGuardTest {

    private final PermissionGuard permissionGuard = new PermissionGuard();

    @Test
    void allowsExactPermission() {
        CurrentUser currentUser = userWithPermissions(Set.of("system:file:view"));

        assertThatCode(() -> permissionGuard.requirePermission(currentUser, "system:file:view"))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsWildcardPermission() {
        CurrentUser currentUser = userWithPermissions(Set.of("*"));

        assertThatCode(() -> permissionGuard.requirePermission(currentUser, "system:file:view"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingPermission() {
        CurrentUser currentUser = userWithPermissions(Set.of("system:menu:view"));

        assertThatThrownBy(() -> permissionGuard.requirePermission(currentUser, "system:file:view"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("缺少权限: system:file:view");
    }

    private CurrentUser userWithPermissions(Set<String> permissions) {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setPermissions(permissions);
        return currentUser;
    }
}
