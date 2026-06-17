package com.lumira.saas.modules.plugin.controller;

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
    void enable_shouldRejectTenantMismatchBeforeApplicationService() {
        CurrentUser currentUser = currentUser("plugin:management:enable");
        PluginDTO.EnableRequest request = new PluginDTO.EnableRequest();
        request.setTenantId(2002L);
        request.setPluginCode("sms");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);

        assertThatThrownBy(() -> controller.enable(request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("只能管理当前租户的插件");

        verify(permissionGuard).requirePermission(currentUser, "plugin:management:enable");
    }

    @Test
    void enable_shouldDelegateToApplicationServiceWithCurrentUser() {
        CurrentUser currentUser = currentUser("plugin:management:enable");
        PluginDTO.EnableRequest request = new PluginDTO.EnableRequest();
        request.setTenantId(1001L);
        request.setPluginCode("sms");
        request.setVersion("1.0.0");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);

        var response = controller.enable(request);

        assertThat(response.getData()).isTrue();
        verify(pluginManagementAppService).enable(request, currentUser);
    }

    @Test
    void currentBootstrap_shouldUseCurrentTenantAndSortedPermissionSnapshot() {
        CurrentUser currentUser = currentUser("plugin:sms:view", "dashboard:view");
        Map<String, Object> bootstrap = Map.of("plugins", List.of());
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(pluginManagementAppService.currentBootstrap(1001L, List.of("dashboard:view", "plugin:sms:view")))
                .thenReturn(bootstrap);

        var response = controller.currentBootstrap();

        assertThat(response.getData()).isSameAs(bootstrap);
        verify(pluginManagementAppService).currentBootstrap(1001L, List.of("dashboard:view", "plugin:sms:view"));
    }

    @Test
    void currentMenus_shouldDelegateWithPermissionSnapshot() {
        CurrentUser currentUser = currentUser("plugin:sms:view");
        List<Map<String, Object>> menus = List.of(Map.of("menuCode", "plugin.sms"));
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(pluginManagementAppService.currentMenus(1001L, List.of("plugin:sms:view"))).thenReturn(menus);

        var response = controller.currentMenus();

        assertThat(response.getData()).isSameAs(menus);
        verify(pluginManagementAppService).currentMenus(1001L, List.of("plugin:sms:view"));
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
        doThrow(new BizException(com.lumira.common.enums.ErrorCode.FORBIDDEN, "缺少权限: plugin:management:view"))
                .when(permissionGuard).requirePermission(currentUser, "plugin:management:view");

        assertThatThrownBy(() -> controller.definitions())
                .isInstanceOf(BizException.class)
                .hasMessageContaining("缺少权限");
    }

    private CurrentUser currentUser(String... permissions) {
        return new CurrentUser(100L, "alice", 1001L, "session-1", 3, true, Set.of(permissions));
    }
}
