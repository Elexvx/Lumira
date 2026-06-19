package com.lumira.common.security;

import com.lumira.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionGuardTest {

    private final PermissionGuard permissionGuard = new PermissionGuard();

    @Test
    void rejectsBlankPermissionKey() {
        assertThatThrownBy(() -> permissionGuard.requirePermission(currentUser("system:user:view"), " "))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Missing permission configuration");
    }

    @Test
    void allowsExplicitPermission() {
        assertThatCode(() -> permissionGuard.requirePermission(currentUser("system:user:view"), "system:user:view"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingPermission() {
        assertThatThrownBy(() -> permissionGuard.requirePermission(currentUser("system:user:view"), "system:user:update"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Missing permission");
    }

    private CurrentUser currentUser(String permission) {
        return new CurrentUser(100L, "admin", 1001L, "session-1", 1, true, Set.of(permission));
    }
}
