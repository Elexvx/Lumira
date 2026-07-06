package com.lumira.saas.modules.ai.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.client.FileInternalApi;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.file.FileObjectDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.auth.vo.CurrentUserVO;
import com.lumira.saas.modules.ai.dto.AiDTO;
import com.lumira.saas.modules.ai.vo.AiVO;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.authorization.AuthorizationDecision;
import com.lumira.common.security.authorization.AuthorizationService;
import com.lumira.saas.modules.system.app.SystemManagementAppService;
import com.lumira.saas.modules.system.vo.SystemVO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiNativeToolRuntimeServiceTest {

    @Test
    void executeShouldRejectUnauthenticatedUserBeforeEmployeeOrToolChecks() {
        StubQueryOperations jdbcTemplate = new StubQueryOperations();
        AiSkillPermissionChecker permissionChecker = mock(AiSkillPermissionChecker.class);
        DefaultAiNativeToolRuntimeService service = newService(jdbcTemplate, permissionChecker);

        assertThatThrownBy(() -> service.execute(unauthenticatedUser(), request("system.permission.snapshot", Map.of())))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(jdbcTemplate.employeeExistsChecked).isFalse();
        assertThat(jdbcTemplate.employeeCountQueried).isFalse();
        assertThat(jdbcTemplate.lastUpdateSql).isNull();
    }

    @Test
    void executeShouldRejectBlankUsernameBeforeEmployeeOrToolChecks() {
        StubQueryOperations jdbcTemplate = new StubQueryOperations();
        AiSkillPermissionChecker permissionChecker = mock(AiSkillPermissionChecker.class);
        DefaultAiNativeToolRuntimeService service = newService(jdbcTemplate, permissionChecker);

        assertThatThrownBy(() -> service.execute(blankUsernameUser(), request("system.permission.snapshot", Map.of())))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(jdbcTemplate.employeeExistsChecked).isFalse();
        assertThat(jdbcTemplate.employeeCountQueried).isFalse();
        assertThat(jdbcTemplate.lastUpdateSql).isNull();
    }

    @Test
    void executeShouldRejectMissingSessionIdBeforeEmployeeOrToolChecks() {
        StubQueryOperations jdbcTemplate = new StubQueryOperations();
        AiSkillPermissionChecker permissionChecker = mock(AiSkillPermissionChecker.class);
        DefaultAiNativeToolRuntimeService service = newService(jdbcTemplate, permissionChecker);

        assertThatThrownBy(() -> service.execute(missingSessionIdUser(), request("system.permission.snapshot", Map.of())))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(jdbcTemplate.employeeExistsChecked).isFalse();
        assertThat(jdbcTemplate.employeeCountQueried).isFalse();
        assertThat(jdbcTemplate.lastUpdateSql).isNull();
    }

    @Test
    void executeShouldRejectMissingUserUuidBeforeEmployeeOrToolChecks() {
        StubQueryOperations jdbcTemplate = new StubQueryOperations();
        AiSkillPermissionChecker permissionChecker = mock(AiSkillPermissionChecker.class);
        DefaultAiNativeToolRuntimeService service = newService(jdbcTemplate, permissionChecker);
        CurrentUser currentUser = currentUser();
        currentUser.setUserUuid(" ");

        assertThatThrownBy(() -> service.execute(currentUser, request("system.permission.snapshot", Map.of())))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(jdbcTemplate.employeeExistsChecked).isFalse();
        assertThat(jdbcTemplate.employeeCountQueried).isFalse();
        assertThat(jdbcTemplate.lastUpdateSql).isNull();
    }

    @Test
    void executeShouldRejectMissingPermissionsVersionBeforeEmployeeOrToolChecks() {
        StubQueryOperations jdbcTemplate = new StubQueryOperations();
        AiSkillPermissionChecker permissionChecker = mock(AiSkillPermissionChecker.class);
        DefaultAiNativeToolRuntimeService service = newService(jdbcTemplate, permissionChecker);
        CurrentUser currentUser = currentUser();
        currentUser.setPermissionsVersion(" ");

        assertThatThrownBy(() -> service.execute(currentUser, request("system.permission.snapshot", Map.of())))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(jdbcTemplate.employeeExistsChecked).isFalse();
        assertThat(jdbcTemplate.employeeCountQueried).isFalse();
        assertThat(jdbcTemplate.lastUpdateSql).isNull();
    }

    @Test
    void executeShouldRejectRevokedSessionTicketBeforeEmployeeOrToolChecks() {
        StubQueryOperations jdbcTemplate = new StubQueryOperations();
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        when(sessionAuthenticationService.authenticateSessionTicket(anyString(), anyLong(), anyString(), any(), any(), any()))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Session expired"));
        DefaultAiNativeToolRuntimeService service = newService(
                jdbcTemplate,
                mock(AiSkillPermissionChecker.class),
                new StubFileInternalApi(),
                authorization(request -> AuthorizationDecision.allow("TEST_ALLOW", "allow")),
                null,
                null,
                sessionAuthenticationService,
                false
        );

        assertThatThrownBy(() -> service.execute(currentUser(), request("system.permission.snapshot", Map.of())))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        assertThat(jdbcTemplate.employeeExistsChecked).isFalse();
        assertThat(jdbcTemplate.employeeCountQueried).isFalse();
        assertThat(jdbcTemplate.lastUpdateSql).isNull();
    }

    @Test
    void executeShouldRejectDisabledTrustedIdentityBeforeEmployeeOrToolChecks() {
        StubQueryOperations jdbcTemplate = new StubQueryOperations();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(100L))
                .thenReturn(userSnapshot(100L, "user-uuid-100", "admin-live", "DISABLED"));
        DefaultAiNativeToolRuntimeService service = newService(
                jdbcTemplate,
                mock(AiSkillPermissionChecker.class),
                new StubFileInternalApi(),
                authorization(request -> AuthorizationDecision.allow("TEST_ALLOW", "allow")),
                permissionSnapshotService,
                systemInternalApi,
                null,
                null,
                false
        );

        assertThatThrownBy(() -> service.execute(currentUser(), request("system.permission.snapshot", Map.of())))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        assertThat(jdbcTemplate.employeeExistsChecked).isFalse();
        assertThat(jdbcTemplate.employeeCountQueried).isFalse();
        assertThat(jdbcTemplate.lastUpdateSql).isNull();
        verify(permissionSnapshotService, org.mockito.Mockito.never()).isTrustedActiveUser(100L, "user-uuid-100");
    }

    @Test
    void executesPermissionSnapshotAndRecordsAuditLog() {
        StubQueryOperations jdbcTemplate = new StubQueryOperations();
        AiSkillPermissionChecker permissionChecker = mock(AiSkillPermissionChecker.class);
        DefaultAiNativeToolRuntimeService service = newService(jdbcTemplate, permissionChecker);

        AiVO.ToolExecuteResultVO result = service.execute(currentUser(), request("system.permission.snapshot", Map.of()));

        assertThat(result.getResultStatus()).isEqualTo("SUCCESS");
        assertThat(result.getData()).containsEntry("username", "admin");
        assertThat(result.getData()).containsEntry("userUuid", "user-uuid-100");
        assertThat(result.getData().get("permissions").toString()).contains("ai:tool:execute");
        assertThat(jdbcTemplate.lastUpdateSql).contains("insert into ai_tool_audit_log");
        assertThat(jdbcTemplate.lastUpdateSql).contains("owner_user_id", "owner_user_uuid");
        assertThat(jdbcTemplate.lastUpdateArgs[2]).isEqualTo(100L);
        assertThat(jdbcTemplate.lastUpdateArgs[3]).isEqualTo("user-uuid-100");
        assertThat(jdbcTemplate.lastUpdateArgs[4]).isEqualTo("system.permission.snapshot");
        assertThat(jdbcTemplate.lastUpdateArgs[6]).isEqualTo("allow");
        assertThat(jdbcTemplate.lastUpdateArgs[9]).isEqualTo("SUCCESS");
        verify(permissionChecker).verifyToolAllowed(anyLong(), anyString(), any(), anyString(), org.mockito.ArgumentMatchers.eq(true), anyBoolean());
    }

    @Test
    void permissionSnapshotShouldRefreshTrustedPermissionsFromLiveSnapshot() {
        StubQueryOperations jdbcTemplate = new StubQueryOperations();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(100L, "user-uuid-100")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(100L, "user-uuid-100")).thenReturn(
                new PermissionSnapshotService.PermissionSnapshot(
                        "perm-v2",
                        Set.of("system:menu:view"),
                        Set.of(9L),
                        77L,
                        Set.of(77L),
                        Set.of(77L, 88L),
                        List.of(),
                        "/ops"
                )
        );
        DefaultAiNativeToolRuntimeService service = newService(
                jdbcTemplate,
                mock(AiSkillPermissionChecker.class),
                new StubFileInternalApi(),
                authorization(request -> AuthorizationDecision.allow("TEST_ALLOW", "allow")),
                permissionSnapshotService,
                null,
                false
        );

        AiVO.ToolExecuteResultVO result = service.execute(currentUser(), request("system.permission.snapshot", Map.of()));

        assertThat(result.getData()).containsEntry("userUuid", "user-uuid-100");
        assertThat(result.getData()).containsEntry("permissions", List.of("system:menu:view"));
        assertThat(result.getData()).containsEntry("roleIds", List.of(9L));
        assertThat(result.getData()).containsEntry("primaryDeptId", 77L);
        assertThat(result.getData()).containsEntry("deptIds", List.of(77L));
        assertThat(result.getData()).containsEntry("descendantDeptIds", List.of(77L, 88L));
    }

    @Test
    void permissionSnapshotShouldRefreshLiveUsernameBeforeExecution() {
        StubQueryOperations jdbcTemplate = new StubQueryOperations();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(100L))
                .thenReturn(userSnapshot(100L, "user-uuid-100", "admin-live", "ENABLED"));
        when(permissionSnapshotService.isTrustedActiveUser(100L, "user-uuid-100")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(100L, "user-uuid-100")).thenReturn(
                new PermissionSnapshotService.PermissionSnapshot("perm-v2", Set.of("ai:tool:execute"))
        );
        DefaultAiNativeToolRuntimeService service = newService(
                jdbcTemplate,
                mock(AiSkillPermissionChecker.class),
                new StubFileInternalApi(),
                authorization(request -> AuthorizationDecision.allow("TEST_ALLOW", "allow")),
                permissionSnapshotService,
                systemInternalApi,
                null,
                null,
                false
        );
        CurrentUser currentUser = currentUser();
        currentUser.setUsername("admin-stale");

        AiVO.ToolExecuteResultVO result = service.execute(currentUser, request("system.permission.snapshot", Map.of()));

        assertThat(result.getData()).containsEntry("username", "admin-live");
        assertThat(currentUser.getUsername()).isEqualTo("admin-live");
        assertThat(currentUser.getPermissionsVersion()).isEqualTo("perm-v2");
    }

    @Test
    void executeShouldRejectWhenLiveSnapshotRevokesRequiredPermission() {
        StubQueryOperations jdbcTemplate = new StubQueryOperations();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(100L, "user-uuid-100")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(100L, "user-uuid-100")).thenReturn(
                new PermissionSnapshotService.PermissionSnapshot("perm-v2", Set.of("ai:tool:execute"))
        );
        DefaultAiNativeToolRuntimeService service = newService(
                jdbcTemplate,
                mock(AiSkillPermissionChecker.class),
                new StubFileInternalApi(),
                authorization(request -> AuthorizationDecision.allow("TEST_ALLOW", "allow")),
                permissionSnapshotService,
                null,
                false
        );

        assertThatThrownBy(() -> service.execute(currentUser(), request("system.config.read", Map.of("configKey", "branding.website-name"))))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    void listToolsShouldHidePermissionedEntriesWhenLiveSnapshotRevokesViewPermission() {
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(100L, "user-uuid-100")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(100L, "user-uuid-100")).thenReturn(
                new PermissionSnapshotService.PermissionSnapshot("perm-v2", Set.of("ai:tool:execute"))
        );
        DefaultAiNativeToolRuntimeService service = newService(
                new StubQueryOperations(),
                mock(AiSkillPermissionChecker.class),
                new StubFileInternalApi(),
                authorization(request -> AuthorizationDecision.allow("TEST_ALLOW", "allow")),
                permissionSnapshotService,
                null,
                false
        );

        List<String> toolCodes = service.listTools(currentUser()).stream()
                .map(AiVO.ToolVO::getToolCode)
                .toList();

        assertThat(toolCodes)
                .contains("system.permission.snapshot")
                .doesNotContain("system.menu.list", "system.config.read", "system.user.search", "file.object.search");
    }

    @Test
    @SuppressWarnings("unchecked")
    void permissionSnapshotShouldNotMarkUntrustedUserAsAuthenticated() throws Exception {
        DefaultAiNativeToolRuntimeService service = newService(new StubQueryOperations(), mock(AiSkillPermissionChecker.class));
        Class<?> contextClass = Class.forName(DefaultAiNativeToolRuntimeService.class.getName() + "$ToolExecutionContext");
        Constructor<?> constructor = contextClass.getDeclaredConstructor(CurrentUser.class, Map.class);
        constructor.setAccessible(true);
        Method permissionSnapshot = DefaultAiNativeToolRuntimeService.class.getDeclaredMethod("permissionSnapshot", contextClass);
        permissionSnapshot.setAccessible(true);

        Map<String, Object> data = (Map<String, Object>) permissionSnapshot.invoke(
                service,
                constructor.newInstance(missingSessionIdUser(), Map.of())
        );

        assertThat(data)
                .containsEntry("userId", null)
                .containsEntry("userUuid", null)
                .containsEntry("username", null)
                .containsEntry("authenticated", false)
                .containsEntry("permissions", List.of())
                .containsEntry("roleIds", List.of())
                .containsEntry("primaryDeptId", null)
                .containsEntry("deptIds", List.of())
                .containsEntry("descendantDeptIds", List.of());
    }

    @Test
    void blocksSensitiveConfigAccessAndAuditsFailure() {
        StubQueryOperations jdbcTemplate = new StubQueryOperations();
        DefaultAiNativeToolRuntimeService service = newService(jdbcTemplate, mock(AiSkillPermissionChecker.class));

        assertThatThrownBy(() -> service.execute(currentUser(), request("system.config.read", Map.of("configKey", "jwt.secret"))))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN)
                .hasMessageContaining("jwt.secret");
        assertThat(jdbcTemplate.lastUpdateSql).contains("owner_user_id", "owner_user_uuid");
        assertThat(jdbcTemplate.lastUpdateArgs[2]).isEqualTo(100L);
        assertThat(jdbcTemplate.lastUpdateArgs[3]).isEqualTo("user-uuid-100");
        assertThat(jdbcTemplate.lastUpdateArgs[4]).isEqualTo("system.config.read");
        assertThat(jdbcTemplate.lastUpdateArgs[6]).isEqualTo("deny");
        assertThat(jdbcTemplate.lastUpdateArgs[9]).isEqualTo("FAIL");
    }

    @Test
    void blocksUnsupportedConfigScopeAndAuditsFailure() {
        StubQueryOperations jdbcTemplate = new StubQueryOperations();
        DefaultAiNativeToolRuntimeService service = newService(jdbcTemplate, mock(AiSkillPermissionChecker.class));

        assertThatThrownBy(() -> service.execute(currentUser(), request("system.config.read", Map.of("configKey", "auth.default-registration-role-code"))))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN)
                .hasMessageContaining("auth.default-registration-role-code");

        assertThat(jdbcTemplate.lastUpdateSql).contains("insert into ai_tool_audit_log");
        assertThat(jdbcTemplate.lastUpdateArgs[4]).isEqualTo("system.config.read");
        assertThat(jdbcTemplate.lastUpdateArgs[6]).isEqualTo("deny");
        assertThat(jdbcTemplate.lastUpdateArgs[9]).isEqualTo("FAIL");
    }

    @Test
    void configUpdateRejectsUnsupportedExistingConfigBeforeMutation() {
        StubQueryOperations jdbcTemplate = new StubQueryOperations();
        SystemManagementAppService systemManagementAppService = mock(SystemManagementAppService.class);
        SystemVO.ConfigVO existing = new SystemVO.ConfigVO();
        existing.setId(9L);
        existing.setConfigKey("auth.default-registration-role-code");
        existing.setConfigValue("commonuser");
        when(systemManagementAppService.getConfig(any(CurrentUser.class), eq(9L))).thenReturn(existing);
        DefaultAiNativeToolRuntimeService service = newService(
                jdbcTemplate,
                mock(AiSkillPermissionChecker.class),
                new StubFileInternalApi(),
                authorization(request -> AuthorizationDecision.allow("TEST_ALLOW", "allow")),
                systemManagementAppService,
                true
        );
        CurrentUser currentUser = trusted(new CurrentUser(
                100L,
                "admin",
                1001L,
                "session-1",
                1,
                true,
                Set.of("ai:tool:execute", "system:config:update")
        ));

        assertThatThrownBy(() -> service.execute(currentUser, request("system.config.update", Map.of(
                "configId", 9L,
                "configValue", "manager"
        )))).isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN)
                .hasMessageContaining("auth.default-registration-role-code");

        verify(systemManagementAppService).getConfig(any(CurrentUser.class), eq(9L));
        verify(systemManagementAppService, org.mockito.Mockito.never()).updateConfig(any(), any(), any());
    }

    @Test
    void searchesUsersWithMaskedContactFields() {
        StubQueryOperations jdbcTemplate = new StubQueryOperations();
        DefaultAiNativeToolRuntimeService service = newService(jdbcTemplate, mock(AiSkillPermissionChecker.class));

        AiVO.ToolExecuteResultVO result = service.execute(currentUser(), request("system.user.search", Map.of("keyword", "admin")));

        assertThat(result.getResultStatus()).isEqualTo("SUCCESS");
        assertThat(result.getData()).containsEntry("total", 1L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> users = (List<Map<String, Object>>) result.getData().get("items");
        assertThat(users).hasSize(1);
        assertThat(users.get(0)).containsEntry("mobile", "138****8000");
        assertThat(users.get(0)).containsEntry("email", "a***@example.com");
        assertThat(jdbcTemplate.lastUpdateArgs[4]).isEqualTo("system.user.search");
    }

    @Test
    @SuppressWarnings("unchecked")
    void menuListShouldFilterUnauthorizedPermissionScopedRows() {
        StubQueryOperations jdbcTemplate = new StubQueryOperations();
        AiPlatformQueryFacade platformQueryFacade = new AiPlatformQueryFacade() {
            @Override
            public List<Map<String, Object>> listMenus(String status, int limit) {
                return List.of(
                        menuRow("root", null),
                        menuRow("dashboard", "dashboard:view"),
                        menuRow("billing", "billing:view")
                );
            }

            @Override
            public Map<String, Object> readConfig(String configKey) {
                return Map.of();
            }
        };
        DefaultAiNativeToolRuntimeService service = newService(jdbcTemplate, mock(AiSkillPermissionChecker.class), platformQueryFacade);

        AiVO.ToolExecuteResultVO result = service.execute(menuViewerUser(), request("system.menu.list", Map.of()));

        assertThat(result.getResultStatus()).isEqualTo("SUCCESS");
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getData().get("items");
        assertThat(items)
                .extracting(item -> item.get("menuCode"))
                .containsExactly("root", "dashboard");
    }

    @Test
    void searchesFilesThroughFileOwnerContract() {
        StubQueryOperations jdbcTemplate = new StubQueryOperations();
        StubFileInternalApi fileInternalApi = new StubFileInternalApi();
        DefaultAiNativeToolRuntimeService service = newService(jdbcTemplate, mock(AiSkillPermissionChecker.class), fileInternalApi);

        AiVO.ToolExecuteResultVO result = service.execute(fileManagerUser(), request("file.object.search", Map.of("keyword", "avatar")));

        assertThat(result.getResultStatus()).isEqualTo("SUCCESS");
        assertThat(fileInternalApi.searchCalled).isTrue();
        assertThat(fileInternalApi.lastSearchSharedScope).isFalse();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> files = (List<Map<String, Object>>) result.getData().get("items");
        assertThat(files).hasSize(1);
        assertThat(files.get(0)).containsEntry("originalFileName", "avatar.png");
    }

    @Test
    void avatarUpdateUsesCurrentUserFileScopeOnly() {
        StubQueryOperations jdbcTemplate = new StubQueryOperations();
        StubFileInternalApi fileInternalApi = new StubFileInternalApi();
        SystemManagementAppService systemManagementAppService = mock(SystemManagementAppService.class);
        when(systemManagementAppService.updateCurrentUserAvatar(any(CurrentUser.class), anyString())).thenReturn(new CurrentUserVO());
        DefaultAiNativeToolRuntimeService service = newService(
                jdbcTemplate,
                mock(AiSkillPermissionChecker.class),
                fileInternalApi,
                authorization(request -> AuthorizationDecision.allow("TEST_ALLOW", "allow")),
                systemManagementAppService,
                true
        );

        AiVO.ToolExecuteResultVO result = service.execute(fileManagerUser(), request("profile.avatar.update", Map.of("fileId", 200L)));

        assertThat(result.getResultStatus()).isEqualTo("SUCCESS");
        assertThat(fileInternalApi.lastGetSharedScope).isFalse();
        assertThat(fileInternalApi.lastGetDownloadCenterScope).isFalse();
    }

    @Test
    void rejectsDisabledEmployeeWithoutCountingRows() {
        StubQueryOperations jdbcTemplate = new StubQueryOperations();
        jdbcTemplate.employeeExists = false;
        DefaultAiNativeToolRuntimeService service = newService(jdbcTemplate, mock(AiSkillPermissionChecker.class));

        assertThatThrownBy(() -> service.execute(currentUser(), request("system.permission.snapshot", Map.of())))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND);
        assertThat(jdbcTemplate.employeeExistsChecked).isTrue();
        assertThat(jdbcTemplate.employeeCountQueried).isFalse();
    }

    @Test
    void rejectsMissingEmployeeBeforePermissionGrantCheck() {
        StubQueryOperations jdbcTemplate = new StubQueryOperations();
        AiSkillPermissionChecker permissionChecker = mock(AiSkillPermissionChecker.class);
        DefaultAiNativeToolRuntimeService service = newService(jdbcTemplate, permissionChecker);
        AiDTO.ToolExecuteRequest request = request("system.permission.snapshot", Map.of());
        request.setEmployeeId(null);

        assertThatThrownBy(() -> service.execute(currentUser(), request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("valid digital employee");
        assertThat(jdbcTemplate.employeeExistsChecked).isFalse();
    }

    @Test
    void listToolsHidesPermissionedToolsFromUnauthorizedUser() {
        DefaultAiNativeToolRuntimeService service = newService(new StubQueryOperations(), mock(AiSkillPermissionChecker.class));

        List<AiVO.ToolVO> tools = service.listTools(trusted(new CurrentUser(100L, "admin", 1001L, "session-1", 1, true, Set.of())));

        assertThat(tools).extracting(AiVO.ToolVO::getToolCode)
                .contains("system.permission.snapshot")
                .doesNotContain("system.user.search", "system.user.create");
    }

    @Test
    void listToolsHidesPermissionedToolsFromUntrustedWildcardUser() {
        DefaultAiNativeToolRuntimeService service = newService(new StubQueryOperations(), mock(AiSkillPermissionChecker.class));

        List<AiVO.ToolVO> tools = service.listTools(new CurrentUser(100L, "admin", 1001L, "session-1", 1, false, Set.of("*")));

        assertThat(tools).extracting(AiVO.ToolVO::getToolCode)
                .contains("system.permission.snapshot")
                .doesNotContain("system.user.search", "system.config.read", "file.object.search");
    }

    @Test
    void listToolsFiltersEmployeeToolsThroughAuthorizationService() {
        DefaultAiNativeToolRuntimeService service = newService(
                new StubQueryOperations(),
                mock(AiSkillPermissionChecker.class),
                new StubFileInternalApi(),
                authorization(request -> "system.user.create".equals(request.toolCode())
                        ? AuthorizationDecision.requireConfirm("TEST_CONFIRM", "confirm", List.of("TEST_CONFIRM"))
                        : AuthorizationDecision.deny("TEST_DENY", "deny")),
                true
        );

        List<AiVO.ToolVO> tools = service.listTools(currentUser(), 1L);

        assertThat(tools).extracting(AiVO.ToolVO::getToolCode)
                .contains("system.user.create")
                .doesNotContain("system.permission.snapshot", "system.user.delete");
    }

    @Test
    void writeToolsAreDisabledByDefault() {
        DefaultAiNativeToolRuntimeService service = newService(new StubQueryOperations(), mock(AiSkillPermissionChecker.class));

        assertThat(service.listTools(currentUser())).extracting(AiVO.ToolVO::getToolCode)
                .doesNotContain("system.user.create", "system.config.create", "platform.floating_window.update");
        assertThatThrownBy(() -> service.execute(currentUser(), request("system.user.create", Map.of())))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND);
    }

    @Test
    void directExecuteShouldNotTrustApprovalGrantedArgument() {
        DefaultAiNativeToolRuntimeService service = newService(
                new StubQueryOperations(),
                mock(AiSkillPermissionChecker.class),
                new StubFileInternalApi(),
                authorization(request -> request.approvalGranted()
                        ? AuthorizationDecision.allow("TEST_APPROVED", "approved")
                        : AuthorizationDecision.deny("TEST_DENY", "approval required")),
                true
        );

        assertThatThrownBy(() -> service.execute(
                currentUser(),
                request("system.user.create", Map.of("_authorizationApprovalGranted", true))
        )).isInstanceOf(BizException.class)
                .hasMessageContaining("approval required");
    }

    @Test
    void userWriteToolsShouldRejectTargetUserUuidMismatchBeforeMutation() {
        SystemManagementAppService systemManagementAppService = mock(SystemManagementAppService.class);
        com.lumira.saas.modules.system.vo.SystemVO.UserDetailVO existing = new com.lumira.saas.modules.system.vo.SystemVO.UserDetailVO();
        existing.setId(200L);
        existing.setUserUuid("target-user-uuid");
        existing.setUsername("target");
        existing.setStatus("ENABLED");
        when(systemManagementAppService.getUser(any(CurrentUser.class), eq(200L))).thenReturn(existing);
        DefaultAiNativeToolRuntimeService service = newService(
                new StubQueryOperations(),
                mock(AiSkillPermissionChecker.class),
                new StubFileInternalApi(),
                authorization(request -> AuthorizationDecision.allow("TEST_ALLOW", "allow")),
                systemManagementAppService,
                true
        );
        CurrentUser currentUser = trusted(new CurrentUser(
                100L,
                "admin",
                1001L,
                "session-1",
                1,
                true,
                Set.of("ai:tool:execute", "system:user:update", "system:user:status", "system:user:delete")
        ));

        assertThatThrownBy(() -> service.execute(currentUser, request("system.user.status", Map.of(
                "userId", 200L,
                "userUuid", "other-user-uuid",
                "status", "DISABLED"
        )))).isInstanceOf(BizException.class)
                .hasMessageContaining("Target user identity mismatch");

        verify(systemManagementAppService).getUser(any(CurrentUser.class), eq(200L));
        verify(systemManagementAppService, org.mockito.Mockito.never()).updateUserStatus(any(), any(), any());
        verify(systemManagementAppService, org.mockito.Mockito.never()).updateUser(any(), any(), any());
        verify(systemManagementAppService, org.mockito.Mockito.never()).deleteUser(any(), any());
    }

    private DefaultAiNativeToolRuntimeService newService(
            MyBatisQueryOperations jdbcTemplate,
            AiSkillPermissionChecker permissionChecker
    ) {
        return newService(jdbcTemplate, permissionChecker, new StubFileInternalApi());
    }

    private DefaultAiNativeToolRuntimeService newService(
            MyBatisQueryOperations jdbcTemplate,
            AiSkillPermissionChecker permissionChecker,
            FileInternalApi fileInternalApi
    ) {
        return newService(jdbcTemplate, permissionChecker, fileInternalApi, new StubPlatformQueryFacade());
    }

    private DefaultAiNativeToolRuntimeService newService(
            MyBatisQueryOperations jdbcTemplate,
            AiSkillPermissionChecker permissionChecker,
            AiPlatformQueryFacade platformQueryFacade
    ) {
        return new DefaultAiNativeToolRuntimeService(
                jdbcTemplate,
                new PermissionGuard(),
                authorization(request -> AuthorizationDecision.allow("TEST_ALLOW", "allow")),
                permissionChecker,
                new ObjectMapper(),
                platformQueryFacade,
                new StubIamQueryFacade(),
                null,
                null,
                new StubFileInternalApi(),
                false
        );
    }

    private DefaultAiNativeToolRuntimeService newService(
            MyBatisQueryOperations jdbcTemplate,
            AiSkillPermissionChecker permissionChecker,
            FileInternalApi fileInternalApi,
            AiPlatformQueryFacade platformQueryFacade
    ) {
        return new DefaultAiNativeToolRuntimeService(
                jdbcTemplate,
                new PermissionGuard(),
                authorization(request -> AuthorizationDecision.allow("TEST_ALLOW", "allow")),
                permissionChecker,
                new ObjectMapper(),
                platformQueryFacade,
                new StubIamQueryFacade(),
                null,
                null,
                fileInternalApi,
                false
        );
    }

    private DefaultAiNativeToolRuntimeService newService(
            MyBatisQueryOperations jdbcTemplate,
            AiSkillPermissionChecker permissionChecker,
            FileInternalApi fileInternalApi,
            AuthorizationService authorizationService
    ) {
        return newService(jdbcTemplate, permissionChecker, fileInternalApi, authorizationService, false);
    }

    private DefaultAiNativeToolRuntimeService newService(
            MyBatisQueryOperations jdbcTemplate,
            AiSkillPermissionChecker permissionChecker,
            FileInternalApi fileInternalApi,
            AuthorizationService authorizationService,
            boolean writeToolsEnabled
    ) {
        return newService(jdbcTemplate, permissionChecker, fileInternalApi, authorizationService, null, null, null, null, writeToolsEnabled);
    }

    private DefaultAiNativeToolRuntimeService newService(
            MyBatisQueryOperations jdbcTemplate,
            AiSkillPermissionChecker permissionChecker,
            FileInternalApi fileInternalApi,
            AuthorizationService authorizationService,
            PermissionSnapshotService permissionSnapshotService,
            SystemManagementAppService systemManagementAppService,
            boolean writeToolsEnabled
    ) {
        return newService(
                jdbcTemplate,
                permissionChecker,
                fileInternalApi,
                authorizationService,
                permissionSnapshotService,
                null,
                systemManagementAppService,
                writeToolsEnabled
        );
    }

    private DefaultAiNativeToolRuntimeService newService(
            MyBatisQueryOperations jdbcTemplate,
            AiSkillPermissionChecker permissionChecker,
            FileInternalApi fileInternalApi,
            AuthorizationService authorizationService,
            PermissionSnapshotService permissionSnapshotService,
            SystemManagementAppService systemManagementAppService,
            SessionAuthenticationService sessionAuthenticationService,
            boolean writeToolsEnabled
    ) {
        return newService(
                jdbcTemplate,
                permissionChecker,
                fileInternalApi,
                authorizationService,
                permissionSnapshotService,
                null,
                systemManagementAppService,
                sessionAuthenticationService,
                writeToolsEnabled
        );
    }

    private DefaultAiNativeToolRuntimeService newService(
            MyBatisQueryOperations jdbcTemplate,
            AiSkillPermissionChecker permissionChecker,
            FileInternalApi fileInternalApi,
            AuthorizationService authorizationService,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SystemManagementAppService systemManagementAppService,
            boolean writeToolsEnabled
    ) {
        return newService(
                jdbcTemplate,
                permissionChecker,
                fileInternalApi,
                authorizationService,
                permissionSnapshotService,
                systemInternalApi,
                systemManagementAppService,
                null,
                writeToolsEnabled
        );
    }

    private DefaultAiNativeToolRuntimeService newService(
            MyBatisQueryOperations jdbcTemplate,
            AiSkillPermissionChecker permissionChecker,
            FileInternalApi fileInternalApi,
            AuthorizationService authorizationService,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SystemManagementAppService systemManagementAppService,
            SessionAuthenticationService sessionAuthenticationService,
            boolean writeToolsEnabled
    ) {
        return new DefaultAiNativeToolRuntimeService(
                jdbcTemplate,
                new PermissionGuard(),
                authorizationService,
                permissionChecker,
                new ObjectMapper(),
                new StubPlatformQueryFacade(),
                new StubIamQueryFacade(),
                permissionSnapshotService,
                systemInternalApi,
                sessionAuthenticationService,
                systemManagementAppService,
                fileInternalApi,
                writeToolsEnabled
        );
    }

    private DefaultAiNativeToolRuntimeService newService(
            MyBatisQueryOperations jdbcTemplate,
            AiSkillPermissionChecker permissionChecker,
            FileInternalApi fileInternalApi,
            AuthorizationService authorizationService,
            SystemManagementAppService systemManagementAppService,
            boolean writeToolsEnabled
    ) {
        return newService(jdbcTemplate, permissionChecker, fileInternalApi, authorizationService, null, null, systemManagementAppService, null, writeToolsEnabled);
    }

    private AuthorizationService authorization(java.util.function.Function<com.lumira.common.security.authorization.AuthorizationRequest, AuthorizationDecision> evaluator) {
        return new AuthorizationService() {
            @Override
            public AuthorizationDecision evaluate(com.lumira.common.security.authorization.AuthorizationRequest request) {
                return evaluator.apply(request);
            }

            @Override
            public void require(com.lumira.common.security.authorization.AuthorizationRequest request) {
                AuthorizationDecision decision = evaluate(request);
                if (!decision.allowed()) {
                    throw new BizException(com.lumira.common.enums.ErrorCode.FORBIDDEN, decision.message());
                }
            }
        };
    }

    private CurrentUser currentUser() {
        CurrentUser currentUser = new CurrentUser(
                100L,
                "admin",
                1001L,
                "session-1",
                1,
                true,
                Set.of("ai:tool:execute", "system:config:view", "system:menu:view", "system:user:view", "system:file:view", "audit:view")
        );
        return trusted(currentUser);
    }

    private static SystemUserSnapshotDTO userSnapshot(Long userId, String userUuid, String username, String status) {
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

    private CurrentUser fileManagerUser() {
        CurrentUser currentUser = new CurrentUser(
                100L,
                "admin",
                1001L,
                "session-1",
                1,
                true,
                Set.of("ai:tool:execute", "profile:view", "system:file:view", "system:file:manage")
        );
        return trusted(currentUser);
    }

    private CurrentUser menuViewerUser() {
        CurrentUser currentUser = new CurrentUser(
                100L,
                "admin",
                1001L,
                "session-1",
                1,
                true,
                Set.of("ai:tool:execute", "system:menu:view", "dashboard:view")
        );
        return trusted(currentUser);
    }

    private Map<String, Object> menuRow(String menuCode, String permissionKey) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("menuCode", menuCode);
        row.put("permissionKey", permissionKey);
        row.put("status", "ENABLED");
        return row;
    }

    private CurrentUser unauthenticatedUser() {
        return new CurrentUser(
                100L,
                "admin",
                1001L,
                "session-1",
                1,
                false,
                Set.of("*", "ai:tool:execute", "system:user:view")
        );
    }

    private CurrentUser blankUsernameUser() {
        return new CurrentUser(
                100L,
                " ",
                1001L,
                "session-1",
                1,
                true,
                Set.of("*", "ai:tool:execute", "system:user:view")
        );
    }

    private CurrentUser missingSessionIdUser() {
        return new CurrentUser(
                100L,
                "admin",
                1001L,
                null,
                1,
                true,
                Set.of("*", "ai:tool:execute", "system:user:view")
        );
    }

    private CurrentUser trusted(CurrentUser currentUser) {
        currentUser.setUserUuid("user-uuid-" + currentUser.getUserId());
        currentUser.setPermissionsVersion("permissions-1");
        return currentUser;
    }

    private AiDTO.ToolExecuteRequest request(String toolCode, Map<String, Object> arguments) {
        AiDTO.ToolExecuteRequest request = new AiDTO.ToolExecuteRequest();
        request.setEmployeeId(1L);
        request.setToolCode(toolCode);
        request.setArguments(arguments);
        return request;
    }

    private static class StubQueryOperations extends MyBatisQueryOperations {
        private String lastUpdateSql;
        private Object[] lastUpdateArgs;
        private boolean employeeExistsChecked;
        private boolean employeeCountQueried;
        private boolean employeeExists = true;

        @Override
        public boolean exists(String sql, Object... args) {
            if (sql.contains("from ai_employee")) {
                employeeExistsChecked = true;
                return employeeExists;
            }
            return super.exists(sql, args);
        }

        @Override
        public int update(String sql, Object... args) {
            this.lastUpdateSql = sql;
            this.lastUpdateArgs = args;
            return 1;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("from ai_employee")) {
                employeeCountQueried = true;
                return requiredType.cast(1L);
            }
            if (sql.contains("from sys_user u") && sql.contains("count(1)")) {
                return requiredType.cast(1L);
            }
            return null;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (sql.contains("from sys_config")) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("configKey", args[0]);
                row.put("configName", "Site Name");
                row.put("configValue", "SaaS Foundation");
                return List.of(row);
            }
            if (sql.contains("from sys_user u")) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", 100L);
                row.put("username", "admin");
                row.put("mobile", "13800008000");
                row.put("email", "admin@example.com");
                row.put("status", "ENABLED");
                return List.of(row);
            }
            return List.of();
        }
    }

    private static class StubPlatformQueryFacade implements AiPlatformQueryFacade {

        @Override
        public List<Map<String, Object>> listMenus(String status, int limit) {
            return List.of();
        }

        @Override
        public Map<String, Object> readConfig(String configKey) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("configKey", configKey);
            row.put("configName", "Site Name");
            row.put("configValue", "SaaS Foundation");
            return row;
        }
    }

    private static class StubIamQueryFacade implements AiIamQueryFacade {
        @Override
        public UserSearchResult searchUsers(String keyword, String status, int limit) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", 100L);
            row.put("username", "admin");
            row.put("mobile", "138****8000");
            row.put("email", "a***@example.com");
            row.put("status", "ENABLED");
            return new UserSearchResult(List.of(row), 1L);
        }
    }

    private static class StubFileInternalApi implements FileInternalApi {
        private boolean searchCalled;
        private boolean lastSearchSharedScope;
        private boolean lastGetSharedScope;
        private boolean lastGetDownloadCenterScope;
        private String lastUserUuid;

        @Override
        public FileObjectDTO uploadImage(org.springframework.web.multipart.MultipartFile file, String category, String remark, String bucket) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FileObjectDTO uploadDocument(
                org.springframework.web.multipart.MultipartFile file,
                String category,
                String tags,
                String remark,
                String bucket
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FileObjectDTO getFileForUser(
                Long fileId,
                Long userId,
                String userUuid,
                String username,
                boolean sharedScope,
                boolean downloadCenterScope
        ) {
            lastUserUuid = userUuid;
            lastGetSharedScope = sharedScope;
            lastGetDownloadCenterScope = downloadCenterScope;
            return new FileObjectDTO(
                    fileId,
                    userId,
                    userUuid,
                    username,
                    "avatar.png",
                    "avatar.png",
                    "LOCAL",
                    "default",
                    "png",
                    "image/png",
                    10L,
                    "10 B",
                    "/tmp/avatar.png",
                    "/uploads/avatar.png",
                    "/uploads/avatar.png",
                    "/uploads/avatar.png",
                    "IMAGE",
                    true,
                    "avatar",
                    "",
                    "",
                    "ENABLED",
                    null,
                    null
            );
        }

        @Override
        public List<FileObjectDTO> searchFilesForUser(
                Long userId,
                String userUuid,
                String username,
                String keyword,
                String contentType,
                String status,
                boolean sharedScope,
                int limit
        ) {
            searchCalled = true;
            lastUserUuid = userUuid;
            lastSearchSharedScope = sharedScope;
            return List.of(getFileForUser(200L, userId, userUuid, username, sharedScope, false));
        }
    }
}
