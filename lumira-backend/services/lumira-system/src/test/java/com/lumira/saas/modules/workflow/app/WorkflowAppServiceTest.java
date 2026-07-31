package com.lumira.saas.modules.workflow.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.event.PlatformEventPublisher;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.SqlRow;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.workflow.dto.WorkflowDTO;
import com.lumira.saas.modules.workflow.vo.WorkflowVO;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WorkflowAppServiceTest {

    @Test
    void getDefinitionShouldRequireViewPermissionBeforeDatabaseAccess() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        WorkflowAppService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.getDefinition(user(1001L, Set.of(), Set.of()), "EXPERT_APPLICATION"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void saveDraftShouldRequireConfigPermissionBeforeDatabaseAccess() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        WorkflowAppService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.saveDraft(user(1001L, Set.of(), Set.of()), "EXPERT_APPLICATION", new WorkflowDTO.DefinitionSaveRequest()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void saveDraftShouldRejectMissingSessionIdBeforeDatabaseAccess() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        WorkflowAppService service = service(jdbcTemplate);
        CurrentUser currentUser = user(1001L, Set.of(), Set.of("workflow:config"));
        currentUser.setSessionId(null);

        assertThatThrownBy(() -> service.saveDraft(currentUser, "EXPERT_APPLICATION", new WorkflowDTO.DefinitionSaveRequest()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void saveDraftShouldRejectMissingUserUuidBeforeDatabaseAccess() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        WorkflowAppService service = service(jdbcTemplate);
        CurrentUser currentUser = user(1001L, Set.of(), Set.of("workflow:config"));
        currentUser.setUserUuid(null);

        assertThatThrownBy(() -> service.saveDraft(currentUser, "EXPERT_APPLICATION", validDefinition()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void approveTaskShouldRejectMissingPermissionsVersionBeforeDatabaseAccess() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        WorkflowAppService service = service(jdbcTemplate);
        CurrentUser currentUser = user(1001L, Set.of(), Set.of("workflow:approve"));
        currentUser.setPermissionsVersion(null);

        assertThatThrownBy(() -> service.approveTask(currentUser, 9001L, "ok"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void approveTaskShouldRejectWhenLiveSnapshotRevokesApprovePermissionBeforeDatabaseAccess() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("workflow:view")));
        WorkflowAppService service = service(jdbcTemplate, permissionSnapshotService);

        assertThatThrownBy(() -> service.approveTask(user(1001L, Set.of(), Set.of("workflow:approve")), 9001L, "ok"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void approveTaskShouldRejectRevokedSessionTicketBeforeDatabaseAccess() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        when(sessionAuthenticationService.authenticateSessionTicket("session-1", 1001L, "user-uuid-1001", null, 1, "permissions-1"))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Session expired"));
        WorkflowAppService service = service(jdbcTemplate, null, sessionAuthenticationService);

        assertThatThrownBy(() -> service.approveTask(user(1001L, Set.of(), Set.of("workflow:approve")), 9001L, "ok"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void approveTaskShouldRejectDisabledTrustedUserIdentityBeforeDatabaseAccess() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", "alice-live", "DISABLED"));
        WorkflowAppService service = service(jdbcTemplate, permissionSnapshotService, systemInternalApi, null, null);

        assertThatThrownBy(() -> service.approveTask(user(1001L, Set.of(), Set.of("workflow:approve")), 9001L, "ok"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(jdbcTemplate);
        verify(permissionSnapshotService, never()).isTrustedActiveUser(any(), anyString());
    }

    @Test
    void approveTaskShouldRejectTrustedUserIdentityWhenLiveUsernameIsUnavailableBeforeDatabaseAccess() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", " ", "ENABLED"));
        WorkflowAppService service = service(jdbcTemplate, permissionSnapshotService, systemInternalApi, null, null);

        assertThatThrownBy(() -> service.approveTask(user(1001L, Set.of(), Set.of("workflow:approve")), 9001L, "ok"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(jdbcTemplate);
        verify(permissionSnapshotService, never()).isTrustedActiveUser(any(), anyString());
    }

    @Test
    void refreshTrustedCurrentUserShouldNormalizeInvalidSimulatedRoleIdBeforeSnapshotLoad() throws Exception {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", "alice-live", "ENABLED"));
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("workflow:view")));
        WorkflowAppService service = service(jdbcTemplate, permissionSnapshotService, systemInternalApi, null, null);
        CurrentUser currentUser = user(1001L, Set.of(), Set.of("workflow:view"));
        currentUser.setSimulatedRoleId(0L);
        Method method = WorkflowAppService.class.getDeclaredMethod("refreshTrustedCurrentUser", CurrentUser.class);
        method.setAccessible(true);

        method.invoke(service, currentUser);

        assertThat(currentUser.getSimulatedRoleId()).isNull();
        verify(permissionSnapshotService).loadSnapshot(1001L, "user-uuid-1001");
        verify(permissionSnapshotService, never()).loadGrantedRoleSnapshot(any(), anyString(), any());
    }

    @Test
    void publishShouldRequireConfigPermissionBeforeDatabaseAccess() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        WorkflowAppService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.publish(user(1001L, Set.of(), Set.of()), "EXPERT_APPLICATION"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void saveDraftShouldRejectLegacyDefinitionConfigPermissionBeforeDatabaseAccess() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        WorkflowAppService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.saveDraft(user(1001L, Set.of(), Set.of("workflow:definition:config")), "EXPERT_APPLICATION", validDefinition()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void publishShouldRejectLegacyDefinitionConfigPermissionBeforeDatabaseAccess() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        WorkflowAppService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.publish(user(1001L, Set.of(), Set.of("workflow:definition:config")), "EXPERT_APPLICATION"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void saveDraftShouldRejectNullDefinitionRequestBeforeDatabaseAccess() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        WorkflowAppService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.saveDraft(user(1001L, Set.of(), Set.of("workflow:config")), "EXPERT_APPLICATION", null))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void saveDraftShouldRejectUntrustedDefinitionShapeBeforeDatabaseAccess() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        WorkflowAppService service = service(jdbcTemplate);
        CurrentUser currentUser = user(1001L, Set.of(), Set.of("workflow:config"));
        WorkflowDTO.DefinitionSaveRequest duplicateNode = validDefinition();
        duplicateNode.getNodes().get(1).setNodeKey("start");
        WorkflowDTO.DefinitionSaveRequest unknownEdgeTarget = validDefinition();
        unknownEdgeTarget.getEdges().get(0).setTargetNodeKey("missing");
        WorkflowDTO.DefinitionSaveRequest invalidApprover = validDefinition();
        invalidApprover.getNodes().get(1).setApproverUserIds(List.of(0L));

        assertThatThrownBy(() -> service.saveDraft(currentUser, "EXPERT_APPLICATION", duplicateNode))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.saveDraft(currentUser, "EXPERT_APPLICATION", unknownEdgeTarget))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.saveDraft(currentUser, "EXPERT_APPLICATION", invalidApprover))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void saveDraftShouldRejectApprovalNodeWithoutApproversBeforeDatabaseAccess() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        WorkflowAppService service = service(jdbcTemplate);
        WorkflowDTO.DefinitionSaveRequest invalidDefinition = validDefinition();
        invalidDefinition.getNodes().get(1).setApproverUserIds(List.of());
        invalidDefinition.getNodes().get(1).setApproverRoleIds(List.of());

        assertThatThrownBy(() -> service.saveDraft(user(1001L, Set.of(), Set.of("workflow:config")), "EXPERT_APPLICATION", invalidDefinition))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void listMyTasksShouldRejectBlankUsernameBeforeDatabaseAccess() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        WorkflowAppService service = service(jdbcTemplate);
        CurrentUser currentUser = user(1001L, Set.of(), Set.of("workflow:approve"));
        currentUser.setUsername(" ");

        assertThatThrownBy(() -> service.listMyTasks(currentUser, "PENDING", 1, 20))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void approveTaskShouldRejectOversizedCommentBeforeDatabaseAccess() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        WorkflowAppService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.approveTask(user(1001L, Set.of(), Set.of("workflow:approve")), 9001L, "x".repeat(501)))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void listMyTasksShouldRequireApprovePermissionBeforeDatabaseAccess() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        WorkflowAppService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.listMyTasks(user(1001L, Set.of(), Set.of("workflow:view")), "PENDING", 1, 20))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void listMyTasksShouldTreatNullRoleIdsAsEmptyInsteadOfTrustingPrincipalShape() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(), any(), any())).thenReturn(0L);
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<WorkflowVO.Task>>any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        WorkflowAppService service = service(jdbcTemplate);
        CurrentUser currentUser = user(1001L, null, Set.of("workflow:approve"));

        assertThat(service.listMyTasks(currentUser, "PENDING", 1, 20).getRecords()).isEmpty();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), eq(Long.class), eq("PENDING"), eq(1001L), eq("user-uuid-1001"));
        assertThat(sql.getValue())
                .contains("t.approver_user_id = ? and t.approver_user_uuid = ?")
                .doesNotContain("approver_role_id in")
                .doesNotContain("approver_user_id is null")
                .doesNotContain("approver_role_id is null");
    }

    @Test
    void listLogsShouldRejectInvalidInstanceIdBeforeDatabaseAccess() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        WorkflowAppService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.listLogs(user(1001L, Set.of(), Set.of("workflow:view")), 0L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void approveTaskShouldRejectInvalidTaskIdBeforeDatabaseAccess() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        WorkflowAppService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.approveTask(user(1001L, Set.of(), Set.of("workflow:view")), -1L, "ok"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void approveTaskShouldRequireApprovePermissionBeforeTaskLookup() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        WorkflowAppService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.approveTask(user(1001L, Set.of(), Set.of("workflow:view")), 9001L, "ok"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void approveTaskShouldConstrainTaskUpdateByApproverUuid() throws Exception {
        List<String> updateSql = new ArrayList<>();
        MyBatisQueryOperations jdbcTemplate = new MyBatisQueryOperations() {
            @Override
            public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
                assertThat(sql).contains("t.approver_user_id = ? and t.approver_user_uuid = ?");
                SqlRow row = new SqlRow(Map.ofEntries(
                        Map.entry("id", 9001L),
                        Map.entry("instanceId", 7001L),
                        Map.entry("businessType", "EXPERT_APPLICATION"),
                        Map.entry("businessId", 5001L),
                        Map.entry("nodeKey", "review"),
                        Map.entry("nodeName", "Review"),
                        Map.entry("approvalMode", "ALL"),
                        Map.entry("status", "PENDING"),
                        Map.entry("approverUserId", 1001L),
                        Map.entry("approverUserUuid", "user-uuid-1001")
                ));
                try {
                    return List.of(rowMapper.mapRow(row, 0));
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }

            @Override
            public int update(String sql, Object... args) {
                updateSql.add(sql);
                return 1;
            }

            @Override
            public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
                return requiredType.cast(1L);
            }
        };
        WorkflowAppService service = service(jdbcTemplate);

        assertThatCode(() -> service.approveTask(user(1001L, Set.of(), Set.of("workflow:approve")), 9001L, "ok"))
                .doesNotThrowAnyException();

        assertThat(updateSql).anySatisfy(statement -> assertThat(statement)
                .contains("update workflow_task")
                .contains("where id = ? and deleted = 0 and status = 'PENDING'")
                .contains("approver_user_id = ? and approver_user_uuid = ?"));
    }

    @Test
    void workflowInstanceTransitionsShouldBindOriginalStatusAndCurrentNode() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/workflow/app/WorkflowAppService.java"));

        assertThat(source).contains(
                "where id = ? and status = ? and current_node_key = ? and deleted = 0",
                "where id = ? and status = ? and current_node_key <=> ? and deleted = 0",
                "instance.status()",
                "status, current_node_key as currentNodeKey",
                "and code = ?",
                "and approval_instance_id = ?",
                "and approval_status = 'PENDING'"
        ).doesNotContain("where id = ? and status = 'PENDING' and current_node_key = ? and deleted = 0");
    }

    @Test
    void workflowInstanceTransitionsShouldRequireSingleCurrentSnapshotUpdate() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/workflow/app/WorkflowAppService.java"));

        assertThat(source).contains(
                "requireSingleWorkflowUpdate(rejectedInstance, \"Workflow instance changed, please retry\")",
                "requireSingleWorkflowUpdate(moved, \"Workflow instance changed, please retry\")",
                "requireSingleWorkflowUpdate(approvedInstance, \"Workflow instance changed, please retry\")",
                "requireSingleWorkflowUpdate(expertUpdated, \"Workflow business state changed, please retry\")",
                "approveInstance(loadInstance(instanceId), currentUser)",
                "if (updated != 1)"
        );
    }

    @Test
    void expertApprovalEventPayloadShouldCarrySimulatedRoleIdWhenPresent() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/workflow/app/WorkflowAppService.java"));

        assertThat(source).contains(
                "Long simulatedRoleId = normalizeSimulatedRoleId(currentUser.getSimulatedRoleId());",
                "payload.put(\"userUuid\", userUuid);",
                "payload.put(\"simulatedRoleId\", simulatedRoleId);"
        );
    }

    @Test
    void workflowDefinitionWritesShouldBindBusinessTypeStatusAndVersion() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/workflow/app/WorkflowAppService.java"));

        assertThat(source).contains(
                "private DefinitionBoundary findDefinitionBoundary(String businessType)",
                "select id, business_type, status, version_no",
                "and business_type = ?",
                "and status = ?",
                "and version_no = ?",
                "existingDefinition.versionNo()",
                "definition.versionNo()"
        );
        assertThat(source).doesNotContain("where id = ? and deleted = 0\",\n                userId,\n                userUuid,\n                LocalDateTime.now(),\n                definitionId");
    }

    @Test
    void workflowDefinitionUpdateShouldRejectWhenSnapshotWriteMisses() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        org.mockito.Mockito.doAnswer(invocation -> List.of(invocation.<RowMapper<?>>getArgument(1).mapRow(definitionBoundaryRow(), 0)))
                .when(jdbcTemplate)
                .query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<?>>any(), eq("EXPERT_APPLICATION"));
        when(jdbcTemplate.update(org.mockito.ArgumentMatchers.contains("update workflow_definition"), any()))
                .thenReturn(0);
        WorkflowAppService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.saveDraft(user(1001L, Set.of(), Set.of("workflow:config")), "EXPERT_APPLICATION", validDefinition()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR));
    }

    @Test
    void publishShouldLogRefreshedLiveUsernameInsteadOfStalePrincipalValue() throws Exception {
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("workflow:config")));
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", "alice-live", "ENABLED"));
        OperationAuditService operationAuditService = mock(OperationAuditService.class);
        MyBatisQueryOperations jdbcTemplate = publishJdbcTemplate();
        WorkflowAppService service = service(jdbcTemplate, permissionSnapshotService, systemInternalApi, operationAuditService, null);
        CurrentUser currentUser = user(1001L, Set.of(), Set.of("workflow:config"));
        currentUser.setUsername("stale-name");

        WorkflowVO.Definition definition = service.publish(currentUser, "EXPERT_APPLICATION");

        assertThat(definition.getId()).isEqualTo(11L);
        assertThat(currentUser.getUsername()).isEqualTo("alice-live");
        verify(operationAuditService).log(
                eq(1001L),
                eq("user-uuid-1001"),
                eq("alice-live"),
                eq("workflow"),
                eq("publish"),
                eq("UPDATE"),
                eq("SUCCESS"),
                eq("Publish workflow: EXPERT_APPLICATION")
        );
    }

    @Test
    void startWorkflowShouldRejectWhenInstanceInsertMisses() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<WorkflowVO.Definition>>any(), eq("EXPERT_APPLICATION")))
                .thenReturn(List.of(activeDefinition()));
        when(jdbcTemplate.update(org.mockito.ArgumentMatchers.contains("insert into workflow_instance"), any()))
                .thenReturn(0);
        WorkflowAppService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.startWorkflow(user(1001L, Set.of(), Set.of("workflow:view")), "EXPERT_APPLICATION", 501L, "exp-501", "Ada", Map.of()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR));
    }

    @Test
    void startWorkflowShouldRejectWhenActionLogInsertMisses() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<WorkflowVO.Definition>>any(), eq("EXPERT_APPLICATION")))
                .thenReturn(List.of(activeDefinition()));
        when(jdbcTemplate.update(org.mockito.ArgumentMatchers.contains("insert into workflow_instance"), any()))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(eq("select last_insert_id()"), eq(Long.class))).thenReturn(7001L);
        when(jdbcTemplate.update(org.mockito.ArgumentMatchers.contains("insert into workflow_action_log"), any()))
                .thenReturn(0);
        WorkflowAppService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.startWorkflow(user(1001L, Set.of(), Set.of("workflow:view")), "EXPERT_APPLICATION", 501L, "exp-501", "Ada", Map.of()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR));
    }

    @Test
    void publishShouldRejectStoredDefinitionWithApprovalNodeMissingApproversBeforeUpdate() {
        MyBatisQueryOperations jdbcTemplate = runtimeDefinitionWithUnassignedApprovalJdbcTemplate();
        WorkflowAppService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.publish(user(1001L, Set.of(), Set.of("workflow:config")), "EXPERT_APPLICATION"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void startWorkflowShouldRejectStoredDefinitionWithApprovalNodeMissingApproversBeforeInsert() {
        MyBatisQueryOperations jdbcTemplate = runtimeDefinitionWithUnassignedApprovalJdbcTemplate();
        WorkflowAppService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.startWorkflow(user(1001L, Set.of(), Set.of("workflow:view")), "EXPERT_APPLICATION", 501L, "exp-001", "Ada", Map.of()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void listLogsShouldRejectUserWithoutInstanceRelationship() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(), any(), any(), any(), any())).thenReturn(0L);
        WorkflowAppService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.listLogs(user(1001L, Set.of(), Set.of("workflow:view")), 9001L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));

        verify(jdbcTemplate, never()).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<WorkflowVO.ActionLog>>any(), any());
    }

    @Test
    void listLogsShouldCheckApplicantAndApproverBindingsBeforeReadingLogs() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(), any(), any(), any(), any(), any())).thenReturn(1L);
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<WorkflowVO.ActionLog>>any(), any())).thenReturn(List.of());
        WorkflowAppService service = service(jdbcTemplate);

        assertThatCode(() -> service.listLogs(user(1001L, Set.of(3001L), Set.of("workflow:view")), 9001L))
                .doesNotThrowAnyException();

        verify(jdbcTemplate).queryForObject(
                org.mockito.ArgumentMatchers.contains("i.applicant_user_id = ? and i.applicant_user_uuid = ?"),
                eq(Long.class),
                eq(9001L),
                eq(1001L),
                eq("user-uuid-1001"),
                eq(1001L),
                eq("user-uuid-1001"),
                eq(3001L)
        );
    }

    @Test
    void listLogsShouldAllowGlobalPermissionWithoutRelationshipProbe() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<WorkflowVO.ActionLog>>any(), any())).thenReturn(List.of());
        WorkflowAppService service = service(jdbcTemplate);

        assertThatCode(() -> service.listLogs(user(1001L, Set.of(), Set.of("*")), 9001L))
                .doesNotThrowAnyException();

        verify(jdbcTemplate, never()).queryForObject(anyString(), eq(Long.class), any());
    }

    @Test
    void getDefinitionShouldRejectTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        WorkflowAppService service = new WorkflowAppService(
                jdbcTemplate,
                new ObjectMapper(),
                mock(PlatformEventPublisher.class),
                mock(OperationAuditService.class),
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.getDefinition(user(1001L, Set.of(), Set.of("workflow:view")), "EXPERT_APPLICATION"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(jdbcTemplate, never()).queryForObject(anyString(), eq(Long.class), any());
    }

    @Test
    void getDefinitionShouldRejectWhenTrustedPermissionSnapshotIsUnavailable() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001")).thenReturn(null);
        WorkflowAppService service = new WorkflowAppService(
                jdbcTemplate,
                new ObjectMapper(),
                mock(PlatformEventPublisher.class),
                mock(OperationAuditService.class),
                permissionSnapshotService,
                null,
                null
        );

        assertThatThrownBy(() -> service.getDefinition(user(1001L, Set.of(), Set.of("workflow:view")), "EXPERT_APPLICATION"))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(exception.getMessage()).contains("Trusted user permission snapshot is unavailable");
                });

        verify(jdbcTemplate, never()).queryForObject(anyString(), eq(Long.class), any());
    }

    private WorkflowAppService service(MyBatisQueryOperations jdbcTemplate) {
        return service(jdbcTemplate, null);
    }

    private WorkflowAppService service(MyBatisQueryOperations jdbcTemplate, PermissionSnapshotService permissionSnapshotService) {
        return service(jdbcTemplate, permissionSnapshotService, null, null, null);
    }

    private WorkflowAppService service(
            MyBatisQueryOperations jdbcTemplate,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        return service(jdbcTemplate, permissionSnapshotService, null, null, sessionAuthenticationService);
    }

    private WorkflowAppService service(
            MyBatisQueryOperations jdbcTemplate,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            OperationAuditService operationAuditService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        if (sessionAuthenticationService != null && systemInternalApi == null) {
            return new WorkflowAppService(
                    jdbcTemplate,
                    new ObjectMapper(),
                    mock(PlatformEventPublisher.class),
                    operationAuditService == null ? mock(OperationAuditService.class) : operationAuditService,
                    permissionSnapshotService,
                    sessionAuthenticationService
            );
        }
        if (systemInternalApi != null && sessionAuthenticationService == null) {
            return new WorkflowAppService(
                    jdbcTemplate,
                    new ObjectMapper(),
                    mock(PlatformEventPublisher.class),
                    operationAuditService == null ? mock(OperationAuditService.class) : operationAuditService,
                    permissionSnapshotService,
                    systemInternalApi
            );
        }
        if (systemInternalApi == null && sessionAuthenticationService == null) {
            if (permissionSnapshotService == null) {
                return new WorkflowAppService(
                        jdbcTemplate,
                        new ObjectMapper(),
                        mock(PlatformEventPublisher.class),
                        operationAuditService == null ? mock(OperationAuditService.class) : operationAuditService
                );
            }
            return new WorkflowAppService(
                    jdbcTemplate,
                    new ObjectMapper(),
                    mock(PlatformEventPublisher.class),
                    operationAuditService == null ? mock(OperationAuditService.class) : operationAuditService,
                    permissionSnapshotService
            );
        }
        return new WorkflowAppService(
                jdbcTemplate,
                new ObjectMapper(),
                mock(PlatformEventPublisher.class),
                operationAuditService == null ? mock(OperationAuditService.class) : operationAuditService,
                permissionSnapshotService,
                systemInternalApi,
                sessionAuthenticationService
        );
    }

    private CurrentUser user(Long userId, Set<Long> roleIds, Set<String> permissions) {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(userId);
        currentUser.setUsername("alice");
        currentUser.setSessionId("session-1");
        currentUser.setSessionVersion(1);
        currentUser.setAuthenticated(true);
        currentUser.setUserUuid("user-uuid-" + userId);
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setRoleIds(roleIds);
        currentUser.setPermissions(permissions);
        return currentUser;
    }

    private WorkflowDTO.DefinitionSaveRequest validDefinition() {
        WorkflowDTO.DefinitionSaveRequest request = new WorkflowDTO.DefinitionSaveRequest();
        request.setName("Expert approval");
        WorkflowDTO.NodeRequest start = node("start", "START");
        WorkflowDTO.NodeRequest approval = node("approval", "APPROVAL");
        approval.setApproverUserIds(List.of(1001L));
        WorkflowDTO.NodeRequest end = node("end", "END");
        WorkflowDTO.EdgeRequest first = edge("edge-1", "start", "approval");
        WorkflowDTO.EdgeRequest second = edge("edge-2", "approval", "end");
        request.setNodes(List.of(start, approval, end));
        request.setEdges(List.of(first, second));
        return request;
    }

    private SqlRow definitionBoundaryRow() {
        return new SqlRow(Map.of(
                "id", 11L,
                "business_type", "EXPERT_APPLICATION",
                "status", "ACTIVE",
                "version_no", 1
        ));
    }

    private WorkflowVO.Definition activeDefinition() {
        WorkflowVO.Definition definition = new WorkflowVO.Definition();
        definition.setId(11L);
        definition.setBusinessType("EXPERT_APPLICATION");
        definition.setName("Expert approval");
        definition.setStatus("ACTIVE");
        definition.setVersionNo(1);
        WorkflowVO.Node start = new WorkflowVO.Node();
        start.setNodeKey("start");
        start.setNodeType("START");
        start.setName("Start");
        WorkflowVO.Node end = new WorkflowVO.Node();
        end.setNodeKey("end");
        end.setNodeType("END");
        end.setName("End");
        WorkflowVO.Edge edge = new WorkflowVO.Edge();
        edge.setEdgeKey("start-end");
        edge.setSourceNodeKey("start");
        edge.setTargetNodeKey("end");
        edge.setSortOrder(1);
        definition.setNodes(List.of(start, end));
        definition.setEdges(List.of(edge));
        return definition;
    }

    private WorkflowDTO.NodeRequest node(String key, String type) {
        WorkflowDTO.NodeRequest node = new WorkflowDTO.NodeRequest();
        node.setNodeKey(key);
        node.setNodeType(type);
        node.setName(key);
        return node;
    }

    private WorkflowDTO.EdgeRequest edge(String key, String source, String target) {
        WorkflowDTO.EdgeRequest edge = new WorkflowDTO.EdgeRequest();
        edge.setEdgeKey(key);
        edge.setSourceNodeKey(source);
        edge.setTargetNodeKey(target);
        return edge;
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

    private MyBatisQueryOperations publishJdbcTemplate() {
        return new MyBatisQueryOperations() {
            @Override
            public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
                try {
                    if (sql.contains("from workflow_definition")) {
                        if (sql.contains("where id = ?")) {
                            return List.of(rowMapper.mapRow(activeDefinitionRow(), 0));
                        }
                        return List.of(rowMapper.mapRow(definitionBoundaryRow(), 0));
                    }
                    if (sql.contains("from workflow_node")) {
                        return List.of(rowMapper.mapRow(activeStartNodeRow(), 0), rowMapper.mapRow(activeEndNodeRow(), 1));
                    }
                    if (sql.contains("from workflow_edge")) {
                        return List.of(rowMapper.mapRow(activeEdgeRow(), 0));
                    }
                    throw new IllegalStateException("Unexpected query: " + sql);
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }

            @Override
            public int update(String sql, Object... args) {
                if (sql.contains("update workflow_definition")) {
                    return 1;
                }
                throw new IllegalStateException("Unexpected update: " + sql);
            }

            @Override
            public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
                throw new IllegalStateException("Unexpected queryForObject: " + sql);
            }

            @Override
            public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
                try {
                    if (sql.contains("from workflow_definition")) {
                        return rowMapper.mapRow(activeDefinitionRow(), 0);
                    }
                    throw new IllegalStateException("Unexpected mapped queryForObject: " + sql);
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }

            @Override
            public List<Map<String, Object>> queryForList(String sql, Object... args) {
                if (sql.contains("from workflow_node")) {
                    return List.of(activeStartNodeMap(), activeEndNodeMap());
                }
                if (sql.contains("from workflow_edge")) {
                    return List.of(activeEdgeMap());
                }
                throw new IllegalStateException("Unexpected queryForList: " + sql);
            }
        };
    }

    private SqlRow activeDefinitionRow() {
        return new SqlRow(Map.of(
                "id", 11L,
                "business_type", "EXPERT_APPLICATION",
                "name", "Expert approval",
                "status", "ACTIVE",
                "version_no", 1
        ));
    }

    private SqlRow activeStartNodeRow() {
        return new SqlRow(Map.of(
                "nodeKey", "start",
                "nodeType", "START",
                "name", "Start",
                "approverUserIdsJson", "[]",
                "approverRoleIdsJson", "[]",
                "conditionExpression", null,
                "approvalMode", null,
                "sortOrder", 1
        ));
    }

    private SqlRow activeEndNodeRow() {
        return new SqlRow(Map.of(
                "nodeKey", "end",
                "nodeType", "END",
                "name", "End",
                "approverUserIdsJson", "[]",
                "approverRoleIdsJson", "[]",
                "conditionExpression", null,
                "approvalMode", null,
                "sortOrder", 2
        ));
    }

    private SqlRow activeEdgeRow() {
        return new SqlRow(Map.of(
                "edgeKey", "start-end",
                "sourceNodeKey", "start",
                "targetNodeKey", "end",
                "conditionExpression", null,
                "sortOrder", 1
        ));
    }

    private Map<String, Object> activeStartNodeMap() {
        return Map.of(
                "id", 101L,
                "node_key", "start",
                "node_type", "START",
                "name", "Start"
        );
    }

    private Map<String, Object> activeEndNodeMap() {
        return Map.of(
                "id", 102L,
                "node_key", "end",
                "node_type", "END",
                "name", "End"
        );
    }

    private Map<String, Object> activeEdgeMap() {
        return Map.of(
                "id", 201L,
                "edge_key", "start-end",
                "source_node_key", "start",
                "target_node_key", "end",
                "sort_order", 1
        );
    }

    private MyBatisQueryOperations runtimeDefinitionWithUnassignedApprovalJdbcTemplate() {
        return new MyBatisQueryOperations() {
            @Override
            public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
                try {
                    if (sql.contains("from workflow_definition")) {
                        return List.of(rowMapper.mapRow(activeDefinitionRow(), 0));
                    }
                    throw new IllegalStateException("Unexpected query: " + sql);
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }

            @Override
            public int update(String sql, Object... args) {
                throw new IllegalStateException("Unexpected update: " + sql);
            }

            @Override
            public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
                throw new IllegalStateException("Unexpected queryForObject: " + sql);
            }

            @Override
            public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
                throw new IllegalStateException("Unexpected mapped queryForObject: " + sql);
            }

            @Override
            public List<Map<String, Object>> queryForList(String sql, Object... args) {
                if (sql.contains("from workflow_node")) {
                    return List.of(
                            activeStartNodeMap(),
                            Map.of(
                                    "id", 102L,
                                    "node_key", "approval",
                                    "node_type", "APPROVAL",
                                    "name", "Review",
                                    "approver_user_ids_json", "[]",
                                    "approver_role_ids_json", "[]",
                                    "approval_mode", "ALL"
                            ),
                            activeEndNodeMap()
                    );
                }
                if (sql.contains("from workflow_edge")) {
                    return List.of(
                            Map.of(
                                    "id", 201L,
                                    "edge_key", "start-approval",
                                    "source_node_key", "start",
                                    "target_node_key", "approval",
                                    "sort_order", 1
                            ),
                            Map.of(
                                    "id", 202L,
                                    "edge_key", "approval-end",
                                    "source_node_key", "approval",
                                    "target_node_key", "end",
                                    "sort_order", 2
                            )
                    );
                }
                throw new IllegalStateException("Unexpected queryForList: " + sql);
            }
        };
    }
}
