package com.lumira.saas.modules.ai.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.SqlRow;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.ai.dto.AiDTO;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static java.util.Map.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiToolPolicyServiceTest {

    @Test
    void listPoliciesShouldRequireViewPermissionBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        DefaultAiToolPolicyService service = new DefaultAiToolPolicyService(queryOperations);

        assertThatThrownBy(() -> service.listPolicies(userWithPermissions(Set.of()), 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(queryOperations.observedArgs).isEmpty();
        assertThat(queryOperations.queryCalled).isFalse();
    }

    @Test
    void listPoliciesShouldRejectBlankUsernameBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        DefaultAiToolPolicyService service = new DefaultAiToolPolicyService(queryOperations);

        assertThatThrownBy(() -> service.listPolicies(blankUsernameUser(), 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(queryOperations.observedArgs).isEmpty();
        assertThat(queryOperations.queryCalled).isFalse();
    }

    @Test
    void listPoliciesShouldRejectMissingSessionVersionBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        DefaultAiToolPolicyService service = new DefaultAiToolPolicyService(queryOperations);

        assertThatThrownBy(() -> service.listPolicies(missingSessionVersionUser(), 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(queryOperations.observedArgs).isEmpty();
        assertThat(queryOperations.queryCalled).isFalse();
    }

    @Test
    void listPoliciesShouldRejectMissingUserUuidBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        DefaultAiToolPolicyService service = new DefaultAiToolPolicyService(queryOperations);
        CurrentUser currentUser = currentUser();
        currentUser.setUserUuid(" ");

        assertThatThrownBy(() -> service.listPolicies(currentUser, 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(queryOperations.observedArgs).isEmpty();
        assertThat(queryOperations.queryCalled).isFalse();
    }

    @Test
    void listPoliciesShouldRejectMissingPermissionsVersionBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        DefaultAiToolPolicyService service = new DefaultAiToolPolicyService(queryOperations);
        CurrentUser currentUser = currentUser();
        currentUser.setPermissionsVersion(" ");

        assertThatThrownBy(() -> service.listPolicies(currentUser, 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(queryOperations.observedArgs).isEmpty();
        assertThat(queryOperations.queryCalled).isFalse();
    }

    @Test
    void createPolicyShouldRequireManagePermissionBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        DefaultAiToolPolicyService service = new DefaultAiToolPolicyService(queryOperations);

        assertThatThrownBy(() -> service.createPolicy(currentUser(), new AiDTO.ToolPolicyUpsertRequest()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(queryOperations.observedArgs).isEmpty();
        assertThat(queryOperations.updateCalled).isFalse();
    }

    @Test
    void listPoliciesShouldRejectWhenLiveSnapshotRevokesViewPermissionBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(100L, "user-uuid-100")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(100L, "user-uuid-100"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("ai:tool-policy:manage")));
        DefaultAiToolPolicyService service = new DefaultAiToolPolicyService(queryOperations, permissionSnapshotService);

        assertThatThrownBy(() -> service.listPolicies(currentUser(), 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(queryOperations.observedArgs).isEmpty();
        assertThat(queryOperations.queryCalled).isFalse();
    }

    @Test
    void listPoliciesShouldRejectRevokedSessionTicketBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        when(sessionAuthenticationService.authenticateSessionTicket(any(), any(), any(), any(), any(), any()))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Session expired"));
        DefaultAiToolPolicyService service = new DefaultAiToolPolicyService(queryOperations, null, sessionAuthenticationService);

        assertThatThrownBy(() -> service.listPolicies(currentUser(), 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        assertThat(queryOperations.observedArgs).isEmpty();
        assertThat(queryOperations.queryCalled).isFalse();
    }

    @Test
    void listPoliciesShouldRejectTrustedUserWhenNoTrustedResolverIsAvailableBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        DefaultAiToolPolicyService service = new DefaultAiToolPolicyService(queryOperations, null, (SessionAuthenticationService) null);

        assertThatThrownBy(() -> service.listPolicies(currentUser(), 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(queryOperations.observedArgs).isEmpty();
        assertThat(queryOperations.queryCalled).isFalse();
    }

    @Test
    void listPoliciesShouldRejectWhenTrustedPermissionSnapshotIsUnavailableBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(100L, "user-uuid-100")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(100L, "user-uuid-100")).thenReturn(null);
        DefaultAiToolPolicyService service = new DefaultAiToolPolicyService(queryOperations, permissionSnapshotService, null, null);

        assertThatThrownBy(() -> service.listPolicies(currentUser(), 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(queryOperations.observedArgs).isEmpty();
        assertThat(queryOperations.queryCalled).isFalse();
    }

    @Test
    void listPoliciesShouldRejectDisabledTrustedIdentityBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(100L))
                .thenReturn(userSnapshot(100L, "user-uuid-100", "admin-live", "DISABLED"));
        DefaultAiToolPolicyService service =
                new DefaultAiToolPolicyService(queryOperations, permissionSnapshotService, systemInternalApi, null);

        assertThatThrownBy(() -> service.listPolicies(currentUser(), 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(queryOperations.observedArgs).isEmpty();
        assertThat(queryOperations.queryCalled).isFalse();
        org.mockito.Mockito.verify(permissionSnapshotService, never()).isTrustedActiveUser(100L, "user-uuid-100");
    }

    @Test
    void listPoliciesShouldRejectTrustedIdentityWhenLiveUsernameIsUnavailableBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(100L))
                .thenReturn(userSnapshot(100L, "user-uuid-100", " ", "ENABLED"));
        DefaultAiToolPolicyService service =
                new DefaultAiToolPolicyService(queryOperations, permissionSnapshotService, systemInternalApi, null);

        assertThatThrownBy(() -> service.listPolicies(currentUser(), 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(queryOperations.observedArgs).isEmpty();
        assertThat(queryOperations.queryCalled).isFalse();
        org.mockito.Mockito.verify(permissionSnapshotService, never()).isTrustedActiveUser(100L, "user-uuid-100");
    }

    @Test
    void listPoliciesShouldRefreshLiveUsernameBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        CurrentUser currentUser = currentUser();
        currentUser.setUsername("admin-stale");
        when(systemInternalApi.findUserIdentityById(100L))
                .thenReturn(userSnapshot(100L, "user-uuid-100", "admin-live", "ENABLED"));
        when(permissionSnapshotService.isTrustedActiveUser(100L, "user-uuid-100")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(100L, "user-uuid-100"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("ai:tool-policy:view")));
        DefaultAiToolPolicyService service =
                new DefaultAiToolPolicyService(queryOperations, permissionSnapshotService, systemInternalApi, null);

        var response = service.listPolicies(currentUser, 1, 10);

        assertThat(response.getRecords()).hasSize(1);
        assertThat(currentUser.getUsername()).isEqualTo("admin-live");
        assertThat(currentUser.getPermissionsVersion()).isEqualTo("permissions-2");
    }

    @Test
    void refreshTrustedCurrentUserShouldNormalizeInvalidSimulatedRoleIdBeforeSnapshotLoad() throws Exception {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(100L, "user-uuid-100")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(100L, "user-uuid-100"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("ai:tool-policy:view")));
        DefaultAiToolPolicyService service = new DefaultAiToolPolicyService(queryOperations, permissionSnapshotService);
        CurrentUser currentUser = currentUser();
        currentUser.setSimulatedRoleId(0L);

        Method method = DefaultAiToolPolicyService.class.getDeclaredMethod("refreshTrustedCurrentUser", CurrentUser.class);
        method.setAccessible(true);
        method.invoke(service, currentUser);

        assertThat(currentUser.getSimulatedRoleId()).isNull();
        assertThat(currentUser.getPermissionsVersion()).isEqualTo("permissions-2");
        verify(permissionSnapshotService).loadSnapshot(100L, "user-uuid-100");
        verify(permissionSnapshotService, never()).loadGrantedRoleSnapshot(100L, "user-uuid-100", 0L);
    }

    @Test
    void listPoliciesShouldSkipCountForFirstShortPage() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        DefaultAiToolPolicyService service = new DefaultAiToolPolicyService(queryOperations);

        var response = service.listPolicies(currentUser(), 1, 10);

        assertThat(response.getRecords()).hasSize(1);
        assertThat(response.getTotal()).isEqualTo(1L);
        assertThat(queryOperations.countQueryCalled).isFalse();
        assertThat(queryOperations.observedArgs).containsExactly(10L, 0L);
    }

    @Test
    void createPolicyShouldRejectWhenInsertMissesBeforeGeneratedIdLookup() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.updateCount = 0;
        DefaultAiToolPolicyService service = new DefaultAiToolPolicyService(queryOperations);

        assertThatThrownBy(() -> service.createPolicy(manageUser(), policyRequest()))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("AI tool policy changed, please retry");
                });

        assertThat(queryOperations.lastInsertIdQueries).isZero();
    }

    @Test
    void policyStateWritesShouldBindOriginalPolicySnapshot() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/ai/app/AiToolPolicyService.java"));

        assertThat(source).contains("AiVO.ToolPolicyVO existing = requirePolicy(id)");
        assertThat(source).contains("where id = ? and policy_name = ? and tool_code = ? and enabled = ? and is_deleted = 0");
        assertThat(source).contains("existing.getPolicyName()");
        assertThat(source).contains("existing.getToolCode()");
        assertThat(source).contains("requirePolicyWrite(updated)");
        assertThat(source).doesNotContain("update ai_tool_policy set enabled = ?, update_time = ? where id = ? and is_deleted = 0");
        assertThat(source).doesNotContain("update ai_tool_policy set is_deleted = 1, update_time = ? where id = ? and is_deleted = 0");
    }

    @Test
    void updatePolicyShouldRejectWhenSnapshotWriteMisses() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.updateCount = 0;
        DefaultAiToolPolicyService service = new DefaultAiToolPolicyService(queryOperations);

        assertThatThrownBy(() -> service.updatePolicy(manageUser(), 1L, policyRequest()))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("AI tool policy changed, please retry");
                });
    }

    @Test
    void updatePolicyEnabledShouldRejectWhenSnapshotWriteMisses() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.updateCount = 0;
        DefaultAiToolPolicyService service = new DefaultAiToolPolicyService(queryOperations);

        assertThatThrownBy(() -> service.updatePolicyEnabled(manageUser(), 1L, false))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("AI tool policy changed, please retry");
                });
    }

    @Test
    void deletePolicyShouldRejectWhenSnapshotWriteMisses() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.updateCount = 0;
        DefaultAiToolPolicyService service = new DefaultAiToolPolicyService(queryOperations);

        assertThatThrownBy(() -> service.deletePolicy(manageUser(), 1L))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("AI tool policy changed, please retry");
                });
    }

    private static CurrentUser currentUser() {
        return userWithPermissions(Set.of("ai:tool-policy:view"));
    }

    private static CurrentUser manageUser() {
        return userWithPermissions(Set.of("ai:tool-policy:manage"));
    }

    private static AiDTO.ToolPolicyUpsertRequest policyRequest() {
        AiDTO.ToolPolicyUpsertRequest request = new AiDTO.ToolPolicyUpsertRequest();
        request.setPolicyName("Default deny");
        request.setToolCode("*");
        request.setRiskLevel("HIGH");
        request.setMatchType("KEYWORD");
        request.setMatchValue("delete");
        request.setVerdict("DENY");
        request.setEnabled(true);
        return request;
    }

    private static CurrentUser userWithPermissions(Set<String> permissions) {
        return trusted(new CurrentUser(100L, "admin", 2002L, "session-1", 1, true, permissions));
    }

    private static CurrentUser blankUsernameUser() {
        return new CurrentUser(100L, " ", 2002L, "session-1", 1, true, Set.of("*", "ai:tool-policy:view"));
    }

    private static CurrentUser missingSessionVersionUser() {
        return new CurrentUser(100L, "admin", 2002L, "session-1", null, true, Set.of("*", "ai:tool-policy:view"));
    }

    private static CurrentUser trusted(CurrentUser currentUser) {
        currentUser.setUserUuid("user-uuid-" + currentUser.getUserId());
        currentUser.setPermissionsVersion("permissions-1");
        return currentUser;
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

    private static final class RecordingQueryOperations extends MyBatisQueryOperations {
        private boolean countQueryCalled;
        private boolean queryCalled;
        private boolean updateCalled;
        private int updateCount = 1;
        private int lastInsertIdQueries;
        private final List<Object> observedArgs = new ArrayList<>();

        @Override
        public int update(String sql, Object... args) {
            updateCalled = true;
            recordArgs(args);
            return updateCount;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            recordArgs(args);
            if (sql.contains("last_insert_id")) {
                lastInsertIdQueries += 1;
            }
            if (sql.contains("count(1)")) {
                countQueryCalled = true;
            }
            return requiredType.cast(5L);
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            queryCalled = true;
            recordArgs(args);
            if (sql.contains("from ai_tool_policy")) {
                try {
                    return List.of(rowMapper.mapRow(new SqlRow(Map.ofEntries(
                            entry("id", 1L),                            entry("policyName", "默认拦截"),
                            entry("toolCode", "*"),
                            entry("actionType", "*"),
                            entry("riskLevel", "HIGH"),
                            entry("matchType", "KEYWORD"),
                            entry("matchValue", "delete"),
                            entry("verdict", "DENY"),
                            entry("message", "需要确认"),
                            entry("enabled", true),
                            entry("createTime", LocalDateTime.now()),
                            entry("updateTime", LocalDateTime.now())
                    )), 0));
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }
            return List.of();
        }

        private void recordArgs(Object... args) {
            if (args == null) {
                return;
            }
            Collections.addAll(observedArgs, args);
        }
    }
}

