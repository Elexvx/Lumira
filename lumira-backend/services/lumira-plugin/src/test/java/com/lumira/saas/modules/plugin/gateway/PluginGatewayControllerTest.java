package com.lumira.saas.modules.plugin.gateway;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.web.security.SensitiveErrorMessageSanitizer;
import com.lumira.common.web.security.audit.SecurityAuditEventService;
import com.lumira.saas.modules.plugin.app.PluginManagementAppService;
import com.lumira.saas.modules.plugin.registry.PluginRuntimeDescriptor;
import com.lumira.saas.modules.plugin.runtime.PluginProperties;
import com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeContext;
import com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginDeclaredPermission;
import com.lumira.saas.modules.plugin.runtime.spi.PluginHttpHandler;
import com.lumira.saas.modules.plugin.runtime.PluginRuntimeSecurityPolicy;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PluginGatewayControllerTest {

    @Test
    void dispatchShouldRejectUnauthenticatedUserBeforeLoadingRuntime() {
        PluginManagementAppService appService = mock(PluginManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PluginGatewayController controller = new PluginGatewayController(
                appService,
                securityContextFacade,
                mock(PermissionGuard.class),
                mock(PluginRuntimeSecurityPolicy.class),
                mock(SensitiveErrorMessageSanitizer.class),
                mock(SecurityAuditEventService.class)
        );
        CurrentUser currentUser = new CurrentUser(100L, "alice", null, "session-1", 1, false, Set.of("plugin:test:view"));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(request.getRequestURI()).thenReturn("/api/p/test/hello");

        assertThatThrownBy(() -> controller.dispatch(request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(appService, never()).requireRuntime("test");
    }

    @Test
    void dispatchShouldRejectMissingSessionVersionBeforeLoadingRuntime() {
        PluginManagementAppService appService = mock(PluginManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PluginGatewayController controller = new PluginGatewayController(
                appService,
                securityContextFacade,
                mock(PermissionGuard.class),
                mock(PluginRuntimeSecurityPolicy.class),
                mock(SensitiveErrorMessageSanitizer.class),
                mock(SecurityAuditEventService.class)
        );
        CurrentUser currentUser = new CurrentUser(100L, "alice", null, "session-1", null, true, Set.of("plugin:test:view"));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(request.getRequestURI()).thenReturn("/api/p/test/hello");

        assertThatThrownBy(() -> controller.dispatch(request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(appService, never()).requireRuntime("test");
    }

    @Test
    void dispatchShouldRejectInvalidPluginCodeBeforeLoadingRuntime() {
        PluginManagementAppService appService = mock(PluginManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PluginRuntimeSecurityPolicy policy = new PluginRuntimeSecurityPolicy(new PluginProperties());
        PluginGatewayController controller = new PluginGatewayController(
                appService,
                securityContextFacade,
                mock(PermissionGuard.class),
                policy,
                mock(SensitiveErrorMessageSanitizer.class),
                mock(SecurityAuditEventService.class)
        );
        CurrentUser currentUser = new CurrentUser(100L, "alice", null, "session-1", 1, true, Set.of("plugin:test:view"));
        currentUser.setUserUuid("user-uuid-100");
        currentUser.setPermissionsVersion("permissions-1");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/p/../admin");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);

        assertThatThrownBy(() -> controller.dispatch(request))
                .isInstanceOf(BizException.class);

        verify(appService, never()).requireRuntime("../admin");
    }

    @Test
    void dispatchShouldRejectUnknownLengthBodyThatExceedsLimit() {
        PluginProperties properties = new PluginProperties();
        properties.setMaxGatewayBodyBytes(4);
        PluginRuntimeSecurityPolicy policy = new PluginRuntimeSecurityPolicy(properties);
        PluginManagementAppService appService = mock(PluginManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        PluginGatewayController controller = new PluginGatewayController(
                appService,
                securityContextFacade,
                permissionGuard,
                policy,
                mock(SensitiveErrorMessageSanitizer.class),
                mock(SecurityAuditEventService.class)
        );
        CurrentUser currentUser = new CurrentUser(100L, "alice", null, "session-1", 1, true, Set.of("plugin:test:view"));
        currentUser.setUserUuid("user-uuid-100");
        currentUser.setPermissionsVersion("permissions-1");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/p/test/echo") {
            @Override
            public long getContentLengthLong() {
                return -1L;
            }
        };
        request.setContent("12345".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        PluginHttpHandler handler = new PluginHttpHandler() {
            @Override
            public com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginHttpResponse handle(
                    com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginHttpRequest request,
                    PluginRuntimeContext context
            ) {
                return com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginHttpResponse.json(200, Map.of());
            }

            @Override
            public String requiredPermission(com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginHttpRequest request) {
                return "plugin:test:view";
            }
        };
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(appService.requireRuntime("test")).thenReturn(new PluginRuntimeDescriptor(
                "test",
                "1.0.0",
                null,
                new PluginRuntimeContext("test", "1.0.0", "0.1.0", Path.of("."), null, new com.fasterxml.jackson.databind.ObjectMapper()),
                null,
                handler,
                null,
                null,
                List.of(new PluginDeclaredPermission("plugin:test:view", "View test plugin", "test")),
                List.of(),
                List.of()
        ));

        assertThatThrownBy(() -> controller.dispatch(request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        verify(permissionGuard, never()).requirePermission(currentUser, "plugin:test:view");
    }

    @Test
    void dispatchShouldRejectPluginRedirectResponse() throws Exception {
        PluginRuntimeSecurityPolicy policy = new PluginRuntimeSecurityPolicy(new PluginProperties());
        PluginManagementAppService appService = mock(PluginManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        PluginGatewayController controller = new PluginGatewayController(
                appService,
                securityContextFacade,
                permissionGuard,
                policy,
                mock(SensitiveErrorMessageSanitizer.class),
                mock(SecurityAuditEventService.class)
        );
        CurrentUser currentUser = new CurrentUser(100L, "alice", null, "session-1", 1, true, Set.of("plugin:test:view"));
        currentUser.setUserUuid("user-uuid-100");
        currentUser.setPermissionsVersion("permissions-1");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/p/test/redirect");
        PluginHttpHandler handler = handlerReturning(
                new com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginHttpResponse(
                        302,
                        "redirect",
                        "text/plain"
                )
        );
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(appService.requireRuntime("test")).thenReturn(runtime("test", handler));

        assertThatThrownBy(() -> controller.dispatch(request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN))
                .hasMessageContaining("Plugin response status is not allowed");

        verify(permissionGuard).requirePermission(currentUser, "plugin:test:view");
    }

    @Test
    void dispatchShouldPassTrustedIdentitySnapshotToPluginRuntime() throws Exception {
        PluginRuntimeSecurityPolicy policy = new PluginRuntimeSecurityPolicy(new PluginProperties());
        PluginManagementAppService appService = mock(PluginManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        PluginGatewayController controller = new PluginGatewayController(
                appService,
                securityContextFacade,
                permissionGuard,
                policy,
                mock(SensitiveErrorMessageSanitizer.class),
                mock(SecurityAuditEventService.class)
        );
        CurrentUser currentUser = new CurrentUser(100L, "alice", null, "session-1", 7, true, Set.of("plugin:test:view"));
        currentUser.setUserUuid("user-uuid-100");
        currentUser.setPermissionsVersion("permissions-7");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/p/test/profile");
        PluginHttpHandler handler = new PluginHttpHandler() {
            @Override
            public com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginHttpResponse handle(
                    com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginHttpRequest request,
                    PluginRuntimeContext context
            ) {
                assertThat(request.userId()).isEqualTo(100L);
                assertThat(request.userUuid()).isEqualTo("user-uuid-100");
                assertThat(request.username()).isEqualTo("alice");
                assertThat(request.sessionId()).isEqualTo("session-1");
                assertThat(request.sessionVersion()).isEqualTo(7);
                assertThat(request.permissionsVersion()).isEqualTo("permissions-7");
                return com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginHttpResponse.json(200, Map.of("ok", true));
            }

            @Override
            public String requiredPermission(com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginHttpRequest request) {
                return "plugin:test:view";
            }
        };
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(appService.requireRuntime("test")).thenReturn(runtime("test", handler));

        assertThat(controller.dispatch(request).getStatusCode().value()).isEqualTo(200);

        verify(permissionGuard).requirePermission(currentUser, "plugin:test:view");
    }

    @Test
    void dispatchShouldRejectPermissionOutsidePluginNamespaceBeforePermissionGuard() {
        PluginRuntimeSecurityPolicy policy = new PluginRuntimeSecurityPolicy(new PluginProperties());
        PluginManagementAppService appService = mock(PluginManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        PluginGatewayController controller = new PluginGatewayController(
                appService,
                securityContextFacade,
                permissionGuard,
                policy,
                mock(SensitiveErrorMessageSanitizer.class),
                mock(SecurityAuditEventService.class)
        );
        CurrentUser currentUser = new CurrentUser(100L, "alice", null, "session-1", 1, true, Set.of("system:user:delete"));
        currentUser.setUserUuid("user-uuid-100");
        currentUser.setPermissionsVersion("permissions-1");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/p/test/delete-user");
        PluginHttpHandler handler = new PluginHttpHandler() {
            @Override
            public com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginHttpResponse handle(
                    com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginHttpRequest request,
                    PluginRuntimeContext context
            ) {
                return com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginHttpResponse.json(200, Map.of());
            }

            @Override
            public String requiredPermission(com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginHttpRequest request) {
                return "system:user:delete";
            }
        };
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(appService.requireRuntime("test")).thenReturn(runtime("test", handler));

        assertThatThrownBy(() -> controller.dispatch(request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN))
                .hasMessageContaining("outside plugin namespace");

        verify(permissionGuard, never()).requirePermission(currentUser, "system:user:delete");
    }

    @Test
    void dispatchShouldRejectPluginHtmlResponse() throws Exception {
        PluginRuntimeSecurityPolicy policy = new PluginRuntimeSecurityPolicy(new PluginProperties());
        PluginManagementAppService appService = mock(PluginManagementAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        PluginGatewayController controller = new PluginGatewayController(
                appService,
                securityContextFacade,
                permissionGuard,
                policy,
                mock(SensitiveErrorMessageSanitizer.class),
                mock(SecurityAuditEventService.class)
        );
        CurrentUser currentUser = new CurrentUser(100L, "alice", null, "session-1", 1, true, Set.of("plugin:test:view"));
        currentUser.setUserUuid("user-uuid-100");
        currentUser.setPermissionsVersion("permissions-1");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/p/test/page");
        PluginHttpHandler handler = handlerReturning(
                new com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginHttpResponse(
                        200,
                        "<script>alert(1)</script>",
                        "text/html"
                )
        );
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(appService.requireRuntime("test")).thenReturn(runtime("test", handler));

        assertThatThrownBy(() -> controller.dispatch(request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN))
                .hasMessageContaining("Plugin response content type is not allowed");

        verify(permissionGuard).requirePermission(currentUser, "plugin:test:view");
    }

    private PluginHttpHandler handlerReturning(
            com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginHttpResponse response
    ) {
        return new PluginHttpHandler() {
            @Override
            public com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginHttpResponse handle(
                    com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginHttpRequest request,
                    PluginRuntimeContext context
            ) {
                return response;
            }

            @Override
            public String requiredPermission(com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginHttpRequest request) {
                return "plugin:test:view";
            }
        };
    }

    private PluginRuntimeDescriptor runtime(String pluginCode, PluginHttpHandler handler) {
        return new PluginRuntimeDescriptor(
                pluginCode,
                "1.0.0",
                null,
                new PluginRuntimeContext(pluginCode, "1.0.0", "0.1.0", Path.of("."), null, new com.fasterxml.jackson.databind.ObjectMapper()),
                null,
                handler,
                null,
                null,
                List.of(new PluginDeclaredPermission("plugin:" + pluginCode + ":view", "View plugin", pluginCode)),
                List.of(),
                List.of()
        );
    }
}
