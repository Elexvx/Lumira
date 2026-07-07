package com.lumira.saas.modules.plugin.controller;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.saas.modules.plugin.app.PluginManagementAppService;
import com.lumira.saas.modules.plugin.runtime.PluginRuntimeSecurityPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PluginManagementControllerTest {

    private PluginManagementAppService pluginManagementAppService;
    private SecurityContextFacade securityContextFacade;
    private PluginManagementController controller;

    @BeforeEach
    void setUp() {
        pluginManagementAppService = mock(PluginManagementAppService.class);
        securityContextFacade = mock(SecurityContextFacade.class);
        controller = new PluginManagementController(
                pluginManagementAppService,
                securityContextFacade,
                mock(PermissionGuard.class),
                mock(PluginRuntimeSecurityPolicy.class)
        );
    }

    @Test
    void currentBootstrapUsesEmptyPermissionSnapshotForAnonymousUser() {
        Map<String, Object> bootstrap = Map.of("menuTree", List.of(), "availablePlugins", List.of());
        when(securityContextFacade.getCurrentUser()).thenReturn(null);
        when(pluginManagementAppService.currentBootstrap(List.of(), null)).thenReturn(bootstrap);

        var response = controller.currentBootstrap();

        assertThat(response.getData()).isSameAs(bootstrap);
        verify(pluginManagementAppService).currentBootstrap(List.of(), null);
    }

    @Test
    void currentAvailableUsesEmptyPermissionSnapshotForAnonymousUser() {
        when(securityContextFacade.getCurrentUser()).thenReturn(null);
        when(pluginManagementAppService.currentAvailablePlugins(List.of())).thenReturn(List.of());

        var response = controller.currentAvailable();

        assertThat(response.getData()).isEmpty();
        verify(pluginManagementAppService).currentAvailablePlugins(List.of());
    }

    @Test
    void currentMenusUsesSortedPermissionSnapshotForCurrentUser() {
        CurrentUser currentUser = new CurrentUser(100L, "alice", 2002L, "session-1", 3, true, Set.of("plugin:sms:view", "dashboard:view"));
        currentUser.setUserUuid("user-uuid-100");
        currentUser.setPermissionsVersion("v11:data-scope-cache-v4");
        List<Map<String, Object>> menus = List.of(Map.of("menuCode", "plugin.sms"));
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(pluginManagementAppService.currentMenus(List.of("dashboard:view", "plugin:sms:view"), "v11:data-scope-cache-v4"))
                .thenReturn(menus);

        var response = controller.currentMenus();

        assertThat(response.getData()).isSameAs(menus);
        verify(pluginManagementAppService).currentMenus(List.of("dashboard:view", "plugin:sms:view"), "v11:data-scope-cache-v4");
    }

    @Test
    void currentMenusUsesEmptySnapshotForUnauthenticatedUser() {
        CurrentUser currentUser = new CurrentUser(100L, "alice", 2002L, "session-1", 3, false, Set.of("plugin:sms:view", "dashboard:view"));
        currentUser.setPermissionsVersion("v11:data-scope-cache-v4");
        List<Map<String, Object>> menus = List.of();
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(pluginManagementAppService.currentMenus(List.of(), null)).thenReturn(menus);

        var response = controller.currentMenus();

        assertThat(response.getData()).isSameAs(menus);
        verify(pluginManagementAppService).currentMenus(List.of(), null);
    }

    @Test
    void currentMenusUsesEmptySnapshotForBlankUsername() {
        CurrentUser currentUser = new CurrentUser(100L, " ", 2002L, "session-1", 3, true, Set.of("plugin:sms:view", "dashboard:view"));
        currentUser.setPermissionsVersion("v11:data-scope-cache-v4");
        List<Map<String, Object>> menus = List.of();
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(pluginManagementAppService.currentMenus(List.of(), null)).thenReturn(menus);

        var response = controller.currentMenus();

        assertThat(response.getData()).isSameAs(menus);
        verify(pluginManagementAppService).currentMenus(List.of(), null);
    }

    @Test
    void currentMenusUsesEmptySnapshotForMissingSessionVersion() {
        CurrentUser currentUser = new CurrentUser(100L, "alice", 2002L, "session-1", null, true, Set.of("plugin:sms:view", "dashboard:view"));
        currentUser.setPermissionsVersion("v11:data-scope-cache-v4");
        List<Map<String, Object>> menus = List.of();
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(pluginManagementAppService.currentMenus(List.of(), null)).thenReturn(menus);

        var response = controller.currentMenus();

        assertThat(response.getData()).isSameAs(menus);
        verify(pluginManagementAppService).currentMenus(List.of(), null);
    }

    @Test
    void currentPermissionsReturnsEmptyListForAnonymousUser() {
        when(securityContextFacade.getCurrentUser()).thenReturn(null);

        var response = controller.currentPermissions();

        assertThat(response.getData()).isEmpty();
    }

    @Test
    void currentPermissionsReturnsEmptyListForUnauthenticatedUser() {
        CurrentUser currentUser = new CurrentUser(100L, "alice", 2002L, "session-1", 3, false, Set.of("plugin:sms:view"));
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);

        var response = controller.currentPermissions();

        assertThat(response.getData()).isEmpty();
    }

    @Test
    void currentPermissionsReturnsEmptyListForBlankUsername() {
        CurrentUser currentUser = new CurrentUser(100L, " ", 2002L, "session-1", 3, true, Set.of("plugin:sms:view"));
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);

        var response = controller.currentPermissions();

        assertThat(response.getData()).isEmpty();
    }

    @Test
    void currentPermissionsShouldRejectTrustedUserWhenResolverIsUnavailable() {
        PluginManagementController strictController = new PluginManagementController(
                pluginManagementAppService,
                securityContextFacade,
                mock(PermissionGuard.class),
                mock(PluginRuntimeSecurityPolicy.class),
                null
        );
        CurrentUser currentUser = new CurrentUser(100L, "alice", 2002L, "session-1", 3, true, Set.of("plugin:sms:view"));
        currentUser.setUserUuid("user-uuid-100");
        currentUser.setPermissionsVersion("v11:data-scope-cache-v4");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);

        assertThatThrownBy(strictController::currentPermissions)
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user resolver is unavailable");
    }

    @Test
    void currentPermissionsShouldRejectWhenLiveUsernameIsBlank() {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        PluginManagementController strictController = new PluginManagementController(
                pluginManagementAppService,
                securityContextFacade,
                mock(PermissionGuard.class),
                mock(PluginRuntimeSecurityPolicy.class),
                systemInternalApi
        );
        CurrentUser currentUser = new CurrentUser(100L, "alice", 2002L, "session-1", 3, true, Set.of("plugin:sms:view"));
        currentUser.setUserUuid("user-uuid-100");
        currentUser.setPermissionsVersion("v11:data-scope-cache-v4");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(systemInternalApi.findUserIdentityById(100L))
                .thenReturn(userSnapshot(100L, "user-uuid-100", " ", "ENABLED"));

        assertThatThrownBy(strictController::currentPermissions)
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user username is unavailable");
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
