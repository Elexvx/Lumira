package com.lumira.ai.integration;

import com.lumira.ai.config.AiOwnerIntegrationProperties;
import com.lumira.ai.vo.AiToolVO;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.api.system.MenuNodeDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RemoteAiOwnerToolGatewayTest {

    @Test
    void permissionSnapshotFallsBackWhenIamOwnerIsNotConfigured() {
        RemoteAiOwnerToolGateway gateway = new RemoteAiOwnerToolGateway(
                new AiOwnerIntegrationProperties(),
                RestClient.builder()
        );

        var execution = gateway.execute(
                user(),
                new AiToolVO("system.permission.snapshot", "权限快照", "system", "读取权限快照", "MEDIUM", true, true, "system.permission.snapshot", Map.of()),
                Map.of()
        );

        assertThat(execution.remote()).isFalse();
        assertThat(execution.degraded()).isTrue();
        assertThat(execution.data()).containsEntry("userId", 7L);
        assertThat(execution.data()).containsEntry("userUuid", "user-uuid-7");
        assertThat(execution.data()).containsEntry("degradedReason", "iam-owner-not-configured");
        assertThat(gateway.degradedOwners()).containsExactly("iam", "platform", "file");
    }

    @Test
    void ownerEndpointRequiresTrustedHttpUrlWithoutUserInfoQueryOrFragment() {
        AiOwnerIntegrationProperties properties = new AiOwnerIntegrationProperties();
        properties.getIam().setEnabled(true);
        properties.getIam().setBaseUrl("ftp://iam-owner");
        properties.getPlatform().setEnabled(true);
        properties.getPlatform().setBaseUrl("http://token@platform-owner");
        properties.getFile().setEnabled(true);
        properties.getFile().setBaseUrl("http://file-owner?trace=1");
        RemoteAiOwnerToolGateway gateway = new RemoteAiOwnerToolGateway(properties, RestClient.builder());

        assertThat(gateway.configuredOwners()).isEmpty();
        assertThat(gateway.degradedOwners()).containsExactly("iam", "platform", "file");

        var execution = gateway.execute(
                user(),
                new AiToolVO("system.permission.snapshot", "snapshot", "system", "read snapshot", "LOW", true, true, null, Map.of()),
                Map.of()
        );

        assertThat(execution.remote()).isFalse();
        assertThat(execution.degraded()).isTrue();
        assertThat(execution.data()).containsEntry("degradedReason", "iam-owner-not-configured");
    }

    @Test
    void configReadFiltersSensitiveKeysBeforeRemoteCall() {
        AiOwnerIntegrationProperties properties = new AiOwnerIntegrationProperties();
        properties.getPlatform().setEnabled(true);
        properties.getPlatform().setBaseUrl("http://127.0.0.1:1");
        RemoteAiOwnerToolGateway gateway = new RemoteAiOwnerToolGateway(properties, RestClient.builder());

        var execution = gateway.execute(
                user(),
                new AiToolVO("system.config.read", "读取配置", "system", "读取非敏感配置", "MEDIUM", true, true, "system:config:view", Map.of()),
                Map.of("keys", java.util.List.of("verification.wechat-login.app-secret", "jwt.secret", "auth.default-registration-role-code"))
        );

        assertThat(execution.remote()).isFalse();
        assertThat(execution.degraded()).isFalse();
        assertThat(execution.data()).containsEntry("limitedBy", "empty-config-key-list");
    }

    @Test
    void executeShouldRejectWhenLivePermissionsLoseToolPermissionBeforeRemoteCall() {
        AiOwnerIntegrationProperties properties = new AiOwnerIntegrationProperties();
        properties.getPlatform().setEnabled(true);
        properties.getPlatform().setBaseUrl("http://platform-owner");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RemoteAiOwnerToolGateway gateway = new RemoteAiOwnerToolGateway(properties, builder, providerWithPermissions(Set.of("dashboard:view")));

        assertThatThrownBy(() -> gateway.execute(
                menuViewerUser(),
                new AiToolVO("system.config.read", "config", "system", "read config", "MEDIUM", true, true, "system:config:view", Map.of()),
                Map.of("keys", java.util.List.of("branding.website-name"))
        )).isInstanceOfSatisfying(BizException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        server.verify();
    }

    @Test
    void configReadForwardsOnlyAiManagedBrandingKeys() {
        AiOwnerIntegrationProperties properties = new AiOwnerIntegrationProperties();
        properties.setSystemToken("system-token");
        properties.getPlatform().setEnabled(true);
        properties.getPlatform().setBaseUrl("http://platform-owner");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(containsString("/internal/system/config/ai-platform-values")))
                .andExpect(requestTo(containsString("keys=branding.website-name")))
                .andExpect(header("X-Job-Token", "system-token"))
                .andRespond(withSuccess("""
                        {"branding.website-name":"Lumira"}
                        """, MediaType.APPLICATION_JSON));
        RemoteAiOwnerToolGateway gateway = new RemoteAiOwnerToolGateway(properties, builder);

        var execution = gateway.execute(
                user(),
                new AiToolVO("system.config.read", "config", "system", "read config", "MEDIUM", true, true, "system:config:view", Map.of()),
                Map.of("keys", java.util.List.of("branding.website-name", "auth.default-registration-role-code"))
        );

        assertThat(execution.remote()).isTrue();
        assertThat(execution.degraded()).isFalse();
        assertThat(execution.data()).containsEntry("keys", List.of("branding.website-name"));
        server.verify();
    }

    @Test
    void configReadRejectsTooManyKeysBeforeRemoteCall() {
        AiOwnerIntegrationProperties properties = new AiOwnerIntegrationProperties();
        properties.getPlatform().setEnabled(true);
        properties.getPlatform().setBaseUrl("http://platform-owner");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RemoteAiOwnerToolGateway gateway = new RemoteAiOwnerToolGateway(properties, builder);
        java.util.List<String> keys = java.util.stream.IntStream.range(0, 51)
                .mapToObj(index -> "config.key." + index)
                .toList();

        assertThatThrownBy(() -> gateway.execute(
                user(),
                new AiToolVO("system.config.read", "config", "system", "read config", "MEDIUM", true, true, "system:config:view", Map.of()),
                Map.of("keys", keys)
        )).isInstanceOfSatisfying(BizException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        server.verify();
    }

    @Test
    void configReadRejectsBlankKeyBeforeRemoteCall() {
        AiOwnerIntegrationProperties properties = new AiOwnerIntegrationProperties();
        properties.getPlatform().setEnabled(true);
        properties.getPlatform().setBaseUrl("http://platform-owner");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RemoteAiOwnerToolGateway gateway = new RemoteAiOwnerToolGateway(properties, builder);

        assertThatThrownBy(() -> gateway.execute(
                user(),
                new AiToolVO("system.config.read", "config", "system", "read config", "MEDIUM", true, true, "system:config:view", Map.of()),
                Map.of("keys", java.util.List.of("public.key", " "))
        )).isInstanceOfSatisfying(BizException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        server.verify();
    }

    @Test
    void menuListFiltersUnauthorizedMenuNodesFromRemoteResponse() {
        AiOwnerIntegrationProperties properties = new AiOwnerIntegrationProperties();
        properties.setAuthSystemToken("auth-system-token");
        properties.getPlatform().setEnabled(true);
        properties.getPlatform().setBaseUrl("http://platform-owner");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(containsString("/internal/system/menus/ai-visible")))
                .andExpect(requestTo(containsString("userId=7")))
                .andExpect(requestTo(containsString("userUuid=user-uuid-7")))
                .andExpect(header("X-Job-Token", "auth-system-token"))
                .andRespond(withSuccess("""
                        [
                          {
                            "id": 1,
                            "menuCode": "root",
                            "name": "Root",
                            "children": [
                              {"id": 2, "parentId": 1, "menuCode": "dashboard", "name": "Dashboard", "permissionKey": "dashboard:view", "children": []},
                              {"id": 3, "parentId": 1, "menuCode": "billing", "name": "Billing", "permissionKey": "billing:view", "children": []}
                            ]
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));
        RemoteAiOwnerToolGateway gateway = new RemoteAiOwnerToolGateway(properties, builder);

        var execution = gateway.execute(
                menuViewerUser(),
                new AiToolVO("system.menu.list", "menu", "system", "read menus", "LOW", true, true, "system:menu:view", Map.of()),
                Map.of()
        );

        assertThat(execution.remote()).isTrue();
        assertThat(execution.degraded()).isFalse();
        @SuppressWarnings("unchecked")
        List<MenuNodeDTO> menus = (List<MenuNodeDTO>) execution.data().get("menus");
        assertThat(menus).hasSize(1);
        assertThat(menus.get(0).getChildren())
                .extracting(MenuNodeDTO::getMenuCode)
                .containsExactly("dashboard");
        server.verify();
    }

    @Test
    void fileSearchIgnoresRequestedSharedScopeAndUsesCurrentUserScope() {
        AiOwnerIntegrationProperties properties = new AiOwnerIntegrationProperties();
        properties.setFileToken("file-token");
        properties.getFile().setEnabled(true);
        properties.getFile().setBaseUrl("http://file-owner");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(containsString("sharedScope=false")))
                .andExpect(requestTo(containsString("userUuid=user-uuid-7")))
                .andExpect(header("X-Job-Token", "file-token"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        RemoteAiOwnerToolGateway gateway = new RemoteAiOwnerToolGateway(properties, builder);

        var execution = gateway.execute(
                user(),
                new AiToolVO("file.object.search", "文件搜索", "file", "搜索文件", "LOW", true, true, "system:file:view", Map.of()),
                Map.of("keyword", "avatar", "sharedScope", true)
        );

        assertThat(execution.remote()).isTrue();
        assertThat(execution.degraded()).isFalse();
        server.verify();
    }

    @Test
    void fileSearchRejectsInvalidLimitBeforeRemoteCall() {
        AiOwnerIntegrationProperties properties = new AiOwnerIntegrationProperties();
        properties.setFileToken("file-token");
        properties.getFile().setEnabled(true);
        properties.getFile().setBaseUrl("http://file-owner");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RemoteAiOwnerToolGateway gateway = new RemoteAiOwnerToolGateway(properties, builder);

        assertThatThrownBy(() -> gateway.execute(
                user(),
                new AiToolVO("file.object.search", "file search", "file", "search files", "LOW", true, true, "system:file:view", Map.of()),
                Map.of("keyword", "avatar", "limit", 0)
        )).isInstanceOfSatisfying(BizException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
        assertThatThrownBy(() -> gateway.execute(
                user(),
                new AiToolVO("file.object.search", "file search", "file", "search files", "LOW", true, true, "system:file:view", Map.of()),
                Map.of("keyword", "avatar", "limit", "abc")
        )).isInstanceOfSatisfying(BizException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        server.verify();
    }

    @Test
    void permissionSnapshotUsesAuthSystemScopedToken() {
        AiOwnerIntegrationProperties properties = new AiOwnerIntegrationProperties();
        properties.setAuthSystemToken("auth-system-token");
        properties.getIam().setEnabled(true);
        properties.getIam().setBaseUrl("http://iam-owner");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(containsString("/internal/system/permissions/snapshot")))
                .andExpect(requestTo(containsString("userUuid=user-uuid-7")))
                .andExpect(header("X-Job-Token", "auth-system-token"))
                .andRespond(withSuccess("""
                        {"version":1,"permissions":["system:user:view"],"roleIds":[1],"defaultHomePath":"/dashboard"}
                        """, MediaType.APPLICATION_JSON));
        RemoteAiOwnerToolGateway gateway = new RemoteAiOwnerToolGateway(properties, builder);

        var execution = gateway.execute(
                user(),
                new AiToolVO("system.permission.snapshot", "snapshot", "system", "read snapshot", "LOW", true, true, null, Map.of()),
                Map.of()
        );

        assertThat(execution.remote()).isTrue();
        assertThat(execution.degraded()).isFalse();
        assertThat(execution.data()).containsEntry("userUuid", "user-uuid-7");
        server.verify();
    }

    @Test
    void permissionSnapshotDegradesWithoutScopedSystemToken() {
        AiOwnerIntegrationProperties properties = new AiOwnerIntegrationProperties();
        properties.getIam().setEnabled(true);
        properties.getIam().setBaseUrl("http://iam-owner");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RemoteAiOwnerToolGateway gateway = new RemoteAiOwnerToolGateway(properties, builder);

        var execution = gateway.execute(
                user(),
                new AiToolVO("system.permission.snapshot", "snapshot", "system", "read snapshot", "LOW", true, true, null, Map.of()),
                Map.of()
        );

        assertThat(execution.remote()).isFalse();
        assertThat(execution.degraded()).isTrue();
        assertThat(execution.data()).containsEntry("ownerOrTool", "system.permission.snapshot");
        assertThat(execution.data()).containsEntry("limitedBy", "standalone-ai-service");
        assertThat(execution.data()).containsEntry("degradedReason", "iam-owner-call-failed");
        assertThat(execution.data()).containsEntry("arguments", Map.of("userId", 7L, "userUuid", "user-uuid-7"));
        assertThat(execution.data()).doesNotContainKeys("permissions", "roleIds", "defaultHomePath");
        server.verify();
    }

    @Test
    void scopedTokenForOwnerPathUsesDedicatedAuthSystemTokenForAuthOwnedSystemPaths() {
        AiOwnerIntegrationProperties properties = new AiOwnerIntegrationProperties();
        properties.setSystemToken("system-token");
        properties.setAuthSystemToken("auth-system-token");
        RemoteAiOwnerToolGateway gateway = new RemoteAiOwnerToolGateway(properties, RestClient.builder());

        assertThat(gateway.scopedTokenForOwnerPath("/internal/system/permissions/snapshot")).isEqualTo("auth-system-token");
        assertThat(gateway.scopedTokenForOwnerPath("/internal/system/security/settings")).isEqualTo("auth-system-token");
        assertThat(gateway.scopedTokenForOwnerPath("/internal/system/users/login/alice")).isEqualTo("auth-system-token");
        assertThat(gateway.scopedTokenForOwnerPath("/internal/system/menus/ai-visible?userId=7&userUuid=user-uuid-7")).isEqualTo("auth-system-token");
    }

    @Test
    void executeRejectsUntrustedCurrentUserBeforeOwnerCall() {
        RemoteAiOwnerToolGateway gateway = new RemoteAiOwnerToolGateway(
                new AiOwnerIntegrationProperties(),
                RestClient.builder()
        );

        CurrentUser unauthenticated = new CurrentUser(7L, "ai-user", 2002L, "s1", 1, false, Set.of("*"));

        assertThatThrownBy(() -> gateway.execute(
                unauthenticated,
                new AiToolVO("system.permission.snapshot", "snapshot", "system", "read snapshot", "LOW", true, true, null, Map.of()),
                Map.of()
        )).isInstanceOfSatisfying(BizException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void executeRejectsMissingSessionVersionBeforeOwnerCall() {
        RemoteAiOwnerToolGateway gateway = new RemoteAiOwnerToolGateway(
                new AiOwnerIntegrationProperties(),
                RestClient.builder()
        );

        CurrentUser currentUser = new CurrentUser(7L, "ai-user", 2002L, "s1", null, true, Set.of("*"));

        assertThatThrownBy(() -> gateway.execute(
                currentUser,
                new AiToolVO("system.permission.snapshot", "snapshot", "system", "read snapshot", "LOW", true, true, null, Map.of()),
                Map.of()
        )).isInstanceOfSatisfying(BizException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void executeRejectsDisabledTrustedUserBeforeOwnerCall() {
        SystemInternalApi systemInternalApi = org.mockito.Mockito.mock(SystemInternalApi.class);
        org.mockito.Mockito.when(systemInternalApi.findUserIdentityById(7L)).thenReturn(userSnapshot(7L, "ai-user", "DISABLED"));
        RemoteAiOwnerToolGateway gateway = new RemoteAiOwnerToolGateway(
                new AiOwnerIntegrationProperties(),
                RestClient.builder(),
                provider(systemInternalApi)
        );

        assertThatThrownBy(() -> gateway.execute(
                user(),
                new AiToolVO("system.permission.snapshot", "snapshot", "system", "read snapshot", "LOW", true, true, null, Map.of()),
                Map.of()
        )).isInstanceOfSatisfying(BizException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void executeRejectsTrustedUserWhenLiveUsernameIsUnavailable() {
        SystemInternalApi systemInternalApi = org.mockito.Mockito.mock(SystemInternalApi.class);
        org.mockito.Mockito.when(systemInternalApi.findUserIdentityById(7L)).thenReturn(userSnapshot(7L, " ", "ENABLED"));
        RemoteAiOwnerToolGateway gateway = new RemoteAiOwnerToolGateway(
                new AiOwnerIntegrationProperties(),
                RestClient.builder(),
                provider(systemInternalApi)
        );

        assertThatThrownBy(() -> gateway.execute(
                user(),
                new AiToolVO("system.permission.snapshot", "snapshot", "system", "read snapshot", "LOW", true, true, null, Map.of()),
                Map.of()
        )).isInstanceOfSatisfying(BizException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void executeRejectsWhenTrustedResolverIsUnavailableInStrictGatewayMode() {
        RemoteAiOwnerToolGateway gateway = new RemoteAiOwnerToolGateway(
                new AiOwnerIntegrationProperties(),
                RestClient.builder(),
                null
        );

        assertThatThrownBy(() -> gateway.execute(
                user(),
                new AiToolVO("system.permission.snapshot", "snapshot", "system", "read snapshot", "LOW", true, true, null, Map.of()),
                Map.of()
        )).isInstanceOfSatisfying(BizException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void degradedToolsNormalizeNullArguments() {
        RemoteAiOwnerToolGateway gateway = new RemoteAiOwnerToolGateway(
                new AiOwnerIntegrationProperties(),
                RestClient.builder()
        );

        var execution = gateway.execute(
                user(),
                new AiToolVO("audit.ai_call.search", "audit", "system", "audit search", "LOW", true, true, null, Map.of()),
                null
        );

        assertThat(execution.degraded()).isTrue();
        assertThat(execution.data()).containsEntry("arguments", Map.of());
    }

    @Test
    void permissionSnapshotFallbackClearsStalePermissionsWhenRemoteSnapshotPermissionsAreUnavailable() {
        SystemInternalApi systemInternalApi = org.mockito.Mockito.mock(SystemInternalApi.class);
        org.mockito.Mockito.when(systemInternalApi.findUserIdentityById(7L)).thenReturn(userSnapshot(7L, "ai-user", "ENABLED"));
        org.mockito.Mockito.when(systemInternalApi.permissionSnapshot(7L, "user-uuid-7"))
                .thenReturn(new PermissionSnapshotDTO("perm-v7", null, List.of(1L), 2L, List.of(2L), List.of(2L, 3L), List.of(), "/dashboard"));
        RemoteAiOwnerToolGateway gateway = new RemoteAiOwnerToolGateway(
                new AiOwnerIntegrationProperties(),
                RestClient.builder(),
                providerWithExplicitSnapshot(systemInternalApi)
        );

        var execution = gateway.execute(
                user(),
                new AiToolVO("system.permission.snapshot", "snapshot", "system", "read snapshot", "LOW", true, true, null, Map.of()),
                Map.of()
        );

        assertThat(execution.remote()).isFalse();
        assertThat(execution.degraded()).isTrue();
        assertThat(execution.data()).containsEntry("permissions", Set.of());
    }

    @Test
    void permissionSnapshotFallbackUsesSimulatedRolePermissionSnapshot() {
        SystemInternalApi systemInternalApi = org.mockito.Mockito.mock(SystemInternalApi.class);
        org.mockito.Mockito.when(systemInternalApi.findUserIdentityById(7L)).thenReturn(userSnapshot(7L, "ai-user", "ENABLED"));
        org.mockito.Mockito.when(systemInternalApi.simulatedRolePermissionSnapshot(7L, "user-uuid-7", 9L))
                .thenReturn(new PermissionSnapshotDTO("perm-v7-role-9", List.of("dashboard:view"), List.of(9L), 2L, List.of(2L), List.of(2L, 3L), List.of(), "/dashboard"));
        RemoteAiOwnerToolGateway gateway = new RemoteAiOwnerToolGateway(
                new AiOwnerIntegrationProperties(),
                RestClient.builder(),
                providerWithExplicitSnapshot(systemInternalApi)
        );
        CurrentUser currentUser = user();
        currentUser.setSimulatedRoleId(9L);

        gateway.execute(
                currentUser,
                new AiToolVO("system.permission.snapshot", "snapshot", "system", "read snapshot", "LOW", true, true, null, Map.of()),
                Map.of()
        );

        org.mockito.Mockito.verify(systemInternalApi, org.mockito.Mockito.atLeastOnce()).simulatedRolePermissionSnapshot(7L, "user-uuid-7", 9L);
        org.mockito.Mockito.verify(systemInternalApi, org.mockito.Mockito.never()).permissionSnapshot(7L, "user-uuid-7");
    }

    private CurrentUser user() {
        CurrentUser currentUser = new CurrentUser(7L, "ai-user", 2002L, "s1", 1, true, Set.of("*"));
        currentUser.setUserUuid("user-uuid-7");
        currentUser.setPermissionsVersion("permissions-1");
        return currentUser;
    }

    private CurrentUser menuViewerUser() {
        CurrentUser currentUser = new CurrentUser(7L, "ai-user", 2002L, "s1", 1, true, Set.of("system:menu:view", "dashboard:view"));
        currentUser.setUserUuid("user-uuid-7");
        currentUser.setPermissionsVersion("permissions-1");
        return currentUser;
    }

    private ObjectProvider<SystemInternalApi> provider(SystemInternalApi systemInternalApi) {
        if (systemInternalApi != null) {
            org.mockito.Mockito.when(systemInternalApi.permissionSnapshot(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString()))
                    .thenAnswer(invocation -> permissionSnapshot(invocation.getArgument(0, Long.class)));
        }
        return providerWithExplicitSnapshot(systemInternalApi);
    }

    private ObjectProvider<SystemInternalApi> providerWithPermissions(Set<String> permissions) {
        SystemInternalApi systemInternalApi = org.mockito.Mockito.mock(SystemInternalApi.class);
        org.mockito.Mockito.when(systemInternalApi.findUserIdentityById(7L)).thenReturn(userSnapshot(7L, "ai-user", "ENABLED"));
        org.mockito.Mockito.when(systemInternalApi.permissionSnapshot(7L, "user-uuid-7"))
                .thenReturn(new PermissionSnapshotDTO("perm-v7", List.copyOf(permissions), List.of(1L), 2L, List.of(2L), List.of(2L, 3L), List.of(), "/dashboard"));
        return providerWithExplicitSnapshot(systemInternalApi);
    }

    private ObjectProvider<SystemInternalApi> providerWithExplicitSnapshot(SystemInternalApi systemInternalApi) {
        ObjectProvider<SystemInternalApi> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        org.mockito.Mockito.when(provider.getIfAvailable()).thenReturn(systemInternalApi);
        return provider;
    }

    private SystemUserSnapshotDTO userSnapshot(Long userId, String username, String status) {
        return new SystemUserSnapshotDTO(userId, "user-uuid-" + userId, username, null, status, null, null, null, null, null, null, null, null, null, null, null);
    }

    private PermissionSnapshotDTO permissionSnapshot(Long userId) {
        return new PermissionSnapshotDTO(
                "perm-v" + userId,
                List.of("*"),
                List.of(1L),
                2L,
                List.of(2L),
                List.of(2L, 3L),
                List.of(),
                "/dashboard"
        );
    }
}
