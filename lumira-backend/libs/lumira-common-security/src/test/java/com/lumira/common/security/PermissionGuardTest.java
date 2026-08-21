package com.lumira.common.security;

import com.lumira.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void rejectsUnauthenticatedUserEvenWhenPermissionIsPresent() {
        CurrentUser currentUser = currentUser("system:user:view");
        currentUser.setAuthenticated(false);

        assertThatThrownBy(() -> permissionGuard.requirePermission(currentUser, "system:user:view"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Missing permission");
    }

    @Test
    void rejectsInvalidUserIdEvenWhenPermissionIsPresent() {
        CurrentUser currentUser = currentUser("system:user:view");
        currentUser.setUserId(0L);

        assertThatThrownBy(() -> permissionGuard.requirePermission(currentUser, "system:user:view"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Missing permission");
    }

    @Test
    void rejectsBlankUsernameEvenWhenPermissionIsPresent() {
        CurrentUser currentUser = currentUser("system:user:view");
        currentUser.setUsername(" ");

        assertThatThrownBy(() -> permissionGuard.requirePermission(currentUser, "system:user:view"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Missing permission");
    }

    @Test
    void rejectsMissingSessionVersionEvenWhenPermissionIsPresent() {
        CurrentUser currentUser = currentUser("system:user:view");
        currentUser.setSessionVersion(null);

        assertThatThrownBy(() -> permissionGuard.requirePermission(currentUser, "system:user:view"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Missing permission");
    }

    @Test
    void rejectsMissingUserUuidEvenWhenPermissionIsPresent() {
        CurrentUser currentUser = currentUser("system:user:view");
        currentUser.setUserUuid(null);

        assertThatThrownBy(() -> permissionGuard.requirePermission(currentUser, "system:user:view"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Missing permission");
    }

    @Test
    void rejectsMissingPermissionsVersionEvenWhenPermissionIsPresent() {
        CurrentUser currentUser = currentUser("system:user:view");
        currentUser.setPermissionsVersion(null);

        assertThatThrownBy(() -> permissionGuard.requirePermission(currentUser, "system:user:view"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Missing permission");
    }

    @Test
    void hasPermissionReturnsTrueForExplicitPermission() {
        assertThat(permissionGuard.hasPermission(currentUser("system:user:view"), "system:user:view")).isTrue();
    }

    @Test
    void hasPermissionReturnsFalseForUnauthenticatedUser() {
        CurrentUser currentUser = currentUser("system:user:view");
        currentUser.setAuthenticated(false);

        assertThat(permissionGuard.hasPermission(currentUser, "system:user:view")).isFalse();
    }

    private CurrentUser currentUser(String permission) {
        CurrentUser currentUser = new CurrentUser(100L, "admin", "session-1", 1, true, Set.of(permission));
        currentUser.setUserUuid("user-uuid-100");
        currentUser.setPermissionsVersion("permissions-1");
        return currentUser;
    }
}
