package com.lumira.saas.modules.plugin.controller;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.saas.modules.plugin.app.PluginManagementAppService;
import com.lumira.saas.modules.plugin.dto.PluginDTO;
import com.lumira.saas.modules.plugin.runtime.PluginRuntimeSecurityPolicy;
import com.lumira.saas.modules.plugin.vo.PluginVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PluginV2ControllerTest {

    private PluginManagementAppService pluginManagementAppService;
    private SecurityContextFacade securityContextFacade;
    private PermissionGuard permissionGuard;
    private PluginRuntimeSecurityPolicy runtimeSecurityPolicy;
    private PluginV2Controller controller;

    @BeforeEach
    void setUp() {
        pluginManagementAppService = mock(PluginManagementAppService.class);
        securityContextFacade = mock(SecurityContextFacade.class);
        permissionGuard = mock(PermissionGuard.class);
        runtimeSecurityPolicy = mock(PluginRuntimeSecurityPolicy.class);
        controller = new PluginV2Controller(
                pluginManagementAppService,
                securityContextFacade,
                permissionGuard,
                runtimeSecurityPolicy
        );
    }

    @Test
    void definitions_shouldCheckPermissionAndDelegateToApplicationService() {
        CurrentUser currentUser = currentUser("plugin:management:view");
        PluginVO.PluginDefinitionVO definition = new PluginVO.PluginDefinitionVO();
        definition.setPluginCode("sms");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(pluginManagementAppService.listDefinitions()).thenReturn(List.of(definition));

        var response = controller.definitions();

        assertThat(response.getData()).containsExactly(definition);
        verify(permissionGuard).requirePermission(currentUser, "plugin:management:view");
        verify(pluginManagementAppService).listDefinitions();
    }

    @Test
    void enable_shouldDelegateToApplicationServiceWithCurrentUser() {
        CurrentUser currentUser = currentUser("plugin:management:enable");
        PluginDTO.EnableRequest request = new PluginDTO.EnableRequest();
        request.setPluginCode("sms");
        request.setVersion("1.0.0");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);

        var response = controller.enable(request);

        assertThat(response.getData()).isTrue();
        verify(permissionGuard).requirePermission(currentUser, "plugin:management:enable");
        verify(pluginManagementAppService).enable(request, currentUser);
    }

    @Test
    void currentBootstrap_shouldUseSortedPermissionSnapshot() {
        CurrentUser currentUser = currentUser("plugin:sms:view", "dashboard:view");
        currentUser.setPermissionsVersion("v10:data-scope-cache-v4");
        Map<String, Object> bootstrap = Map.of("plugins", List.of());
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(pluginManagementAppService.currentBootstrap(List.of("dashboard:view", "plugin:sms:view"), "v10:data-scope-cache-v4"))
                .thenReturn(bootstrap);

        var response = controller.currentBootstrap();

        assertThat(response.getData()).isSameAs(bootstrap);
        verify(pluginManagementAppService).currentBootstrap(List.of("dashboard:view", "plugin:sms:view"), "v10:data-scope-cache-v4");
    }

    @Test
    void currentAvailable_shouldUseSortedPermissionSnapshot() {
        CurrentUser currentUser = currentUser("plugin:sms:view", "dashboard:view");
        PluginVO.PluginAvailabilityVO plugin = new PluginVO.PluginAvailabilityVO();
        plugin.setPluginCode("sms");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(pluginManagementAppService.currentAvailablePlugins(List.of("dashboard:view", "plugin:sms:view")))
                .thenReturn(List.of(plugin));

        var response = controller.currentAvailable();

        assertThat(response.getData()).containsExactly(plugin);
        verify(pluginManagementAppService).currentAvailablePlugins(List.of("dashboard:view", "plugin:sms:view"));
    }

    @Test
    void currentAvailable_shouldUseEmptyPermissionSnapshotForUnauthenticatedUser() {
        CurrentUser currentUser = currentUser("plugin:sms:view", "dashboard:view");
        currentUser.setAuthenticated(false);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(pluginManagementAppService.currentAvailablePlugins(List.of())).thenReturn(List.of());

        var response = controller.currentAvailable();

        assertThat(response.getData()).isEmpty();
        verify(pluginManagementAppService).currentAvailablePlugins(List.of());
    }

    @Test
    void currentAvailable_shouldUseEmptyPermissionSnapshotForBlankUsername() {
        CurrentUser currentUser = currentUser("plugin:sms:view", "dashboard:view");
        currentUser.setUsername(" ");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(pluginManagementAppService.currentAvailablePlugins(List.of())).thenReturn(List.of());

        var response = controller.currentAvailable();

        assertThat(response.getData()).isEmpty();
        verify(pluginManagementAppService).currentAvailablePlugins(List.of());
    }

    @Test
    void currentAvailable_shouldUseEmptyPermissionSnapshotForMissingSessionId() {
        CurrentUser currentUser = currentUser("plugin:sms:view", "dashboard:view");
        currentUser.setSessionId(null);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(pluginManagementAppService.currentAvailablePlugins(List.of())).thenReturn(List.of());

        var response = controller.currentAvailable();

        assertThat(response.getData()).isEmpty();
        verify(pluginManagementAppService).currentAvailablePlugins(List.of());
    }

    @Test
    void currentMenus_shouldDelegateWithPermissionSnapshot() {
        CurrentUser currentUser = currentUser("plugin:sms:view");
        currentUser.setPermissionsVersion("v11:data-scope-cache-v4");
        List<Map<String, Object>> menus = List.of(Map.of("menuCode", "plugin.sms"));
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(pluginManagementAppService.currentMenus(List.of("plugin:sms:view"), "v11:data-scope-cache-v4")).thenReturn(menus);

        var response = controller.currentMenus();

        assertThat(response.getData()).isSameAs(menus);
        verify(pluginManagementAppService).currentMenus(List.of("plugin:sms:view"), "v11:data-scope-cache-v4");
    }

    @Test
    void currentBootstrap_shouldDropPermissionVersionForUnauthenticatedUser() {
        CurrentUser currentUser = currentUser("plugin:sms:view");
        currentUser.setAuthenticated(false);
        currentUser.setPermissionsVersion("v11:data-scope-cache-v4");
        Map<String, Object> bootstrap = Map.of("plugins", List.of());
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(pluginManagementAppService.currentBootstrap(List.of(), null)).thenReturn(bootstrap);

        var response = controller.currentBootstrap();

        assertThat(response.getData()).isSameAs(bootstrap);
        verify(pluginManagementAppService).currentBootstrap(List.of(), null);
    }

    @Test
    void currentBootstrap_shouldDropPermissionVersionForBlankUsername() {
        CurrentUser currentUser = currentUser("plugin:sms:view");
        currentUser.setUsername(" ");
        currentUser.setPermissionsVersion("v11:data-scope-cache-v4");
        Map<String, Object> bootstrap = Map.of("plugins", List.of());
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(pluginManagementAppService.currentBootstrap(List.of(), null)).thenReturn(bootstrap);

        var response = controller.currentBootstrap();

        assertThat(response.getData()).isSameAs(bootstrap);
        verify(pluginManagementAppService).currentBootstrap(List.of(), null);
    }

    @Test
    void runtimeSecurityPolicy_shouldExposeDeterministicSnapshot() {
        CurrentUser currentUser = currentUser("plugin:management:view");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(runtimeSecurityPolicy.snapshot()).thenReturn(new PluginRuntimeSecurityPolicy.RuntimePolicySnapshot(
                1024L,
                true,
                Set.of("POST", "GET"),
                Set.of("authorization", "cookie")
        ));

        var response = controller.runtimeSecurityPolicy();

        assertThat(response.getData().getMaxGatewayBodyBytes()).isEqualTo(1024L);
        assertThat(response.getData().getRequireHttpPermission()).isTrue();
        assertThat(response.getData().getAllowedMethods()).containsExactly("GET", "POST");
        assertThat(response.getData().getBlockedHeaders()).containsExactly("authorization", "cookie");
        verify(permissionGuard).requirePermission(currentUser, "plugin:management:view");
    }

    @Test
    void definitions_shouldRejectMissingPermissionBeforeApplicationService() {
        CurrentUser currentUser = currentUser("plugin:other:view");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        doThrow(new BizException(com.lumira.common.enums.ErrorCode.FORBIDDEN, "missing permission: plugin:management:view"))
                .when(permissionGuard).requirePermission(currentUser, "plugin:management:view");

        assertThatThrownBy(() -> controller.definitions())
                .isInstanceOf(BizException.class)
                .hasMessageContaining("plugin:management:view");
    }

    @Test
    void currentPermissionsShouldRejectTrustedUserWhenResolverIsUnavailable() {
        PluginV2Controller strictController = new PluginV2Controller(
                pluginManagementAppService,
                securityContextFacade,
                permissionGuard,
                runtimeSecurityPolicy,
                null
        );
        CurrentUser currentUser = currentUser("plugin:sms:view");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);

        assertThatThrownBy(strictController::currentPermissions)
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user resolver is unavailable");
    }

    @Test
    void currentPermissionsShouldRejectTrustedUserWhenLiveUsernameIsUnavailable() {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        PluginV2Controller strictController = new PluginV2Controller(
                pluginManagementAppService,
                securityContextFacade,
                permissionGuard,
                runtimeSecurityPolicy,
                systemInternalApi
        );
        CurrentUser currentUser = currentUser("plugin:sms:view");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(systemInternalApi.findUserIdentityById(100L)).thenReturn(
                new SystemUserSnapshotDTO(100L, "user-uuid-100", " ", null, "ENABLED", null, null, null, null, null, null, null, null, null, null, null)
        );

        assertThatThrownBy(strictController::currentPermissions)
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user username is unavailable");
        verify(systemInternalApi).findUserIdentityById(100L);
        verify(systemInternalApi, org.mockito.Mockito.never()).permissionSnapshot(100L, "user-uuid-100");
    }

    private CurrentUser currentUser(String... permissions) {
        CurrentUser currentUser = new CurrentUser(100L, "alice", "session-1", 3, true, Set.of(permissions));
        currentUser.setUserUuid("user-uuid-100");
        currentUser.setPermissionsVersion("v11:data-scope-cache-v4");
        return currentUser;
    }
}
