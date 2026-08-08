package com.lumira.saas.modules.workflow.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.workflow.WorkflowAuditPort;
import com.lumira.api.workflow.WorkflowEventPort;
import com.lumira.api.workflow.WorkflowEventTypes;
import com.lumira.api.workflow.WorkflowExpertApplicationPort;
import com.lumira.api.workflow.WorkflowStartPort;
import com.lumira.api.workflow.WorkflowUserAccessPort;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.modules.workflow.dto.WorkflowDTO;
import com.lumira.saas.modules.workflow.repository.WorkflowRepository;
import com.lumira.saas.modules.workflow.vo.WorkflowVO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkflowAppServiceTest {

    @Test
    void workflowIsTheSharedStartPortImplementationWithoutSystemImplementationImports() throws Exception {
        assertThat(WorkflowStartPort.class).isAssignableFrom(WorkflowAppService.class);
        String source = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/workflow/app/WorkflowAppService.java"));

        assertThat(source)
                .contains("WorkflowUserAccessPort", "WorkflowAuditPort", "WorkflowEventPort", "WorkflowExpertApplicationPort")
                .doesNotContain("SystemInternalApi", "PermissionSnapshotService", "SessionAuthenticationService")
                .doesNotContain("PlatformEventPublisher", "OperationAuditService", "MyBatisQueryOperations");
    }

    @Test
    void getDefinitionRequiresRefreshedViewPermissionBeforeRepositoryAccess() {
        WorkflowRepository repository = mock(WorkflowRepository.class);
        WorkflowAppService service = service(repository, accessReturning(user(1001L, Set.of(), Set.of())));

        assertThatThrownBy(() -> service.getDefinition(user(1001L, Set.of(), Set.of()), "EXPERT_APPLICATION"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(repository);
    }

    @Test
    void liveUserAccessPortCanRevokeAStaleApprovePermissionBeforeRepositoryAccess() {
        WorkflowRepository repository = mock(WorkflowRepository.class);
        WorkflowAppService service = service(repository, accessReturning(user(1001L, Set.of(), Set.of("workflow:view"))));

        assertThatThrownBy(() -> service.approveTask(user(1001L, Set.of(), Set.of("workflow:approve")), 77L, "ok"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(repository);
    }

    @Test
    void unavailableOrUntrustedUserAccessResolutionRejectsBeforeRepositoryAccess() {
        WorkflowRepository repository = mock(WorkflowRepository.class);
        WorkflowUserAccessPort userAccessPort = mock(WorkflowUserAccessPort.class);
        when(userAccessPort.refreshTrustedUser(any(CurrentUser.class))).thenReturn(new CurrentUser());
        WorkflowAppService service = service(repository, userAccessPort);

        assertThatThrownBy(() -> service.getDefinition(user(1001L, Set.of(), Set.of("workflow:view")), "EXPERT_APPLICATION"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(repository);
    }

    @Test
    void saveDraftUsesRepositoryOptimisticBoundaryAndAuditsTheRefreshedActor() {
        WorkflowRepository repository = mock(WorkflowRepository.class);
        WorkflowAuditPort auditPort = mock(WorkflowAuditPort.class);
        WorkflowVO.Definition stored = definition("DRAFT", startNode(), endNode());
        when(repository.findDefinitionBoundary("EXPERT_APPLICATION")).thenReturn(null);
        when(repository.saveDraft(any(), eq("EXPERT_APPLICATION"), any(), any(), any()))
                .thenReturn(new WorkflowRepository.DraftSaveResult(11L, 1, List.of(1, 1), List.of(1)));
        when(repository.findDefinitionById(11L)).thenReturn(stored);
        CurrentUser refreshed = user(1001L, Set.of(), Set.of("workflow:config"));
        refreshed.setUsername("live-admin");
        WorkflowAppService service = service(repository, accessReturning(refreshed), mock(WorkflowEventPort.class), auditPort,
                mock(WorkflowExpertApplicationPort.class));

        WorkflowVO.Definition result = service.saveDraft(user(1001L, Set.of(), Set.of("workflow:config")), "EXPERT_APPLICATION", validDefinition());

        assertThat(result).isSameAs(stored);
        verify(auditPort).log(1001L, "user-uuid-1001", "live-admin", "workflow", "save-draft", "UPDATE", "SUCCESS", "Save workflow draft: EXPERT_APPLICATION");
    }

    @Test
    void saveDraftRejectsAnOptimisticDefinitionMiss() {
        WorkflowRepository repository = mock(WorkflowRepository.class);
        when(repository.findDefinitionBoundary("EXPERT_APPLICATION"))
                .thenReturn(new WorkflowRepository.DefinitionBoundary(11L, "EXPERT_APPLICATION", "DRAFT", 4));
        when(repository.saveDraft(any(), any(), any(), any(), any()))
                .thenReturn(new WorkflowRepository.DraftSaveResult(11L, 0, List.of(), List.of()));
        WorkflowAppService service = service(repository);

        assertThatThrownBy(() -> service.saveDraft(user(1001L, Set.of(), Set.of("workflow:config")), "EXPERT_APPLICATION", validDefinition()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR));
    }

    @Test
    void saveDraftRejectsApprovalNodesWithoutAnApproverBeforeRepositoryAccess() {
        WorkflowRepository repository = mock(WorkflowRepository.class);
        WorkflowDTO.DefinitionSaveRequest invalid = validDefinition();
        invalid.getNodes().get(1).setApproverUserIds(List.of());
        WorkflowAppService service = service(repository);

        assertThatThrownBy(() -> service.saveDraft(user(1001L, Set.of(), Set.of("workflow:config")), "EXPERT_APPLICATION", invalid))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        verifyNoInteractions(repository);
    }

    @Test
    void terminalExpertApprovalUpdatesTheSystemAggregateAndPublishesAfterCommit() {
        WorkflowRepository repository = mock(WorkflowRepository.class);
        WorkflowEventPort eventPort = mock(WorkflowEventPort.class);
        WorkflowExpertApplicationPort expertPort = mock(WorkflowExpertApplicationPort.class);
        when(repository.findDefinition("EXPERT_APPLICATION", true)).thenReturn(definition("ACTIVE", startNode(), endNode()));
        when(repository.createInstance(any())).thenReturn(new WorkflowRepository.InstanceCreateResult(7001L, 1));
        when(repository.insertActionLog(any())).thenReturn(1);
        when(repository.findInstance(7001L)).thenReturn(new WorkflowRepository.InstanceRow(
                7001L, "EXPERT_APPLICATION", 501L, "expert-501", "RUNNING", null,
                "{\"nodes\":[{\"nodeKey\":\"start\",\"nodeType\":\"START\",\"name\":\"Start\"},{\"nodeKey\":\"end\",\"nodeType\":\"END\",\"name\":\"End\"}],\"edges\":[{\"sourceNodeKey\":\"start\",\"targetNodeKey\":\"end\",\"sortOrder\":1}]}",
                "{}"
        ));
        when(repository.moveInstance(any(), eq("end"), any(), any())).thenReturn(1);
        when(repository.approveInstance(any(), any(), any())).thenReturn(1);
        when(expertPort.updateStatus(any())).thenReturn(1);
        WorkflowAppService service = service(repository, defaultUserAccess(), eventPort, mock(WorkflowAuditPort.class), expertPort);

        Long instanceId = service.startWorkflow(user(1001L, Set.of(), Set.of("workflow:view")), "EXPERT_APPLICATION", 501L, "expert-501", "Ada", Map.of("name", "Ada"));

        assertThat(instanceId).isEqualTo(7001L);
        verify(expertPort).updateStatus(any(WorkflowExpertApplicationPort.ExpertApplicationDecision.class));
        verify(eventPort).record(
                eq(WorkflowEventTypes.EXPERT_APPROVED), eq(1001L), eq("aiadc_expert"), eq(501L), anyMap()
        );
    }

    @Test
    void approvalTaskUsesTheSystemOwnedUserDirectoryPortForAssignedUserUuid() {
        WorkflowRepository repository = mock(WorkflowRepository.class);
        WorkflowUserAccessPort userAccessPort = defaultUserAccess();
        when(userAccessPort.findEnabledUserUuid(2002L)).thenReturn("reviewer-uuid-2002");
        when(repository.findDefinition("EXPERT_APPLICATION", true)).thenReturn(definition("ACTIVE", startNode(), approvalNode(2002L)));
        when(repository.createInstance(any())).thenReturn(new WorkflowRepository.InstanceCreateResult(7002L, 1));
        when(repository.insertActionLog(any())).thenReturn(1);
        when(repository.findInstance(7002L)).thenReturn(new WorkflowRepository.InstanceRow(
                7002L, "EXPERT_APPLICATION", 502L, "expert-502", "RUNNING", null,
                "{\"nodes\":[{\"nodeKey\":\"start\",\"nodeType\":\"START\",\"name\":\"Start\"},{\"nodeKey\":\"approve\",\"nodeType\":\"APPROVAL\",\"name\":\"Approve\",\"approvalMode\":\"ALL\",\"approverUserIds\":[2002]}],\"edges\":[{\"sourceNodeKey\":\"start\",\"targetNodeKey\":\"approve\",\"sortOrder\":1}]}",
                "{}"
        ));
        when(repository.moveInstance(any(), eq("approve"), any(), any())).thenReturn(1);
        when(repository.createTask(any())).thenReturn(1);
        WorkflowAppService service = service(repository, userAccessPort, mock(WorkflowEventPort.class), mock(WorkflowAuditPort.class), mock(WorkflowExpertApplicationPort.class));

        service.startWorkflow(user(1001L, Set.of(), Set.of("workflow:view")), "EXPERT_APPLICATION", 502L, "expert-502", "Ada", Map.of());

        verify(userAccessPort).findEnabledUserUuid(2002L);
        verify(repository).createTask(any(WorkflowRepository.TaskCreate.class));
    }

    @Test
    void rejectedExpertTaskUpdatesTheSystemOwnedExpertAggregateUnderTheSameWorkflowTransaction() {
        WorkflowRepository repository = mock(WorkflowRepository.class);
        WorkflowExpertApplicationPort expertPort = mock(WorkflowExpertApplicationPort.class);
        WorkflowRepository.TaskRow task = new WorkflowRepository.TaskRow(
                91L, 7003L, "EXPERT_APPLICATION", 503L, "approve", "Approve", "ALL", "PENDING", 1001L, "user-uuid-1001", null
        );
        when(repository.findTask(eq(91L), any())).thenReturn(task);
        when(repository.completeTask(eq(91L), any(), any())).thenReturn(1);
        when(repository.findInstance(7003L)).thenReturn(new WorkflowRepository.InstanceRow(
                7003L, "EXPERT_APPLICATION", 503L, "expert-503", "RUNNING", "approve", "{}", "{}"
        ));
        when(repository.rejectInstance(any(), any(), any(), any())).thenReturn(1);
        when(repository.insertActionLog(any())).thenReturn(1);
        when(expertPort.updateStatus(any())).thenReturn(1);
        WorkflowAppService service = service(repository, defaultUserAccess(), mock(WorkflowEventPort.class), mock(WorkflowAuditPort.class), expertPort);

        assertThat(service.rejectTask(user(1001L, Set.of(), Set.of("workflow:approve")), 91L, "not suitable")).isTrue();

        verify(repository).cancelPendingTasks(eq(7003L), any(), any());
        verify(expertPort).updateStatus(any(WorkflowExpertApplicationPort.ExpertApplicationDecision.class));
    }

    @Test
    void logsRequireWorkflowParticipationUnlessCallerHasGlobalPermission() {
        WorkflowRepository repository = mock(WorkflowRepository.class);
        when(repository.hasParticipant(eq(8001L), any())).thenReturn(false);
        WorkflowAppService service = service(repository);

        assertThatThrownBy(() -> service.listLogs(user(1001L, Set.of(), Set.of("workflow:view")), 8001L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
        verify(repository, never()).findActionLogs(anyLong());
    }

    @Test
    void globalWorkflowViewerCanReadLogsWithoutParticipantProbe() {
        WorkflowRepository repository = mock(WorkflowRepository.class);
        when(repository.findActionLogs(8002L)).thenReturn(List.of());
        WorkflowAppService service = service(repository);

        assertThat(service.listLogs(user(1001L, Set.of(), Set.of("*")), 8002L)).isEmpty();
        verify(repository, never()).hasParticipant(anyLong(), any());
    }

    private WorkflowAppService service(WorkflowRepository repository) {
        return service(repository, defaultUserAccess(), mock(WorkflowEventPort.class), mock(WorkflowAuditPort.class), mock(WorkflowExpertApplicationPort.class));
    }

    private WorkflowAppService service(WorkflowRepository repository, WorkflowUserAccessPort userAccessPort) {
        return service(repository, userAccessPort, mock(WorkflowEventPort.class), mock(WorkflowAuditPort.class), mock(WorkflowExpertApplicationPort.class));
    }

    private WorkflowAppService service(
            WorkflowRepository repository,
            WorkflowUserAccessPort userAccessPort,
            WorkflowEventPort eventPort,
            WorkflowAuditPort auditPort,
            WorkflowExpertApplicationPort expertPort
    ) {
        return new WorkflowAppService(repository, new ObjectMapper(), eventPort, auditPort, userAccessPort, expertPort);
    }

    private WorkflowUserAccessPort defaultUserAccess() {
        WorkflowUserAccessPort userAccessPort = mock(WorkflowUserAccessPort.class);
        when(userAccessPort.refreshTrustedUser(any(CurrentUser.class))).thenAnswer(invocation -> invocation.getArgument(0, CurrentUser.class));
        return userAccessPort;
    }

    private WorkflowUserAccessPort accessReturning(CurrentUser refreshed) {
        WorkflowUserAccessPort userAccessPort = mock(WorkflowUserAccessPort.class);
        when(userAccessPort.refreshTrustedUser(any(CurrentUser.class))).thenReturn(refreshed);
        return userAccessPort;
    }

    private CurrentUser user(Long userId, Set<Long> roleIds, Set<String> permissions) {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(userId);
        currentUser.setUserUuid("user-uuid-" + userId);
        currentUser.setUsername("workflow-user-" + userId);
        currentUser.setSessionId("session-" + userId);
        currentUser.setSessionVersion(1);
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setAuthenticated(true);
        currentUser.setRoleIds(roleIds);
        currentUser.setPermissions(permissions);
        return currentUser;
    }

    private WorkflowDTO.DefinitionSaveRequest validDefinition() {
        WorkflowDTO.DefinitionSaveRequest request = new WorkflowDTO.DefinitionSaveRequest();
        request.setName("Expert approval");
        request.setNodes(List.of(nodeRequest("start", "START"), nodeRequest("approve", "APPROVAL"), nodeRequest("end", "END")));
        request.getNodes().get(1).setApproverUserIds(List.of(2002L));
        request.setEdges(List.of(edgeRequest("start-approve", "start", "approve"), edgeRequest("approve-end", "approve", "end")));
        return request;
    }

    private WorkflowDTO.NodeRequest nodeRequest(String key, String type) {
        WorkflowDTO.NodeRequest node = new WorkflowDTO.NodeRequest();
        node.setNodeKey(key);
        node.setNodeType(type);
        node.setName(key);
        node.setApprovalMode("ALL");
        return node;
    }

    private WorkflowDTO.EdgeRequest edgeRequest(String key, String source, String target) {
        WorkflowDTO.EdgeRequest edge = new WorkflowDTO.EdgeRequest();
        edge.setEdgeKey(key);
        edge.setSourceNodeKey(source);
        edge.setTargetNodeKey(target);
        edge.setSortOrder(1);
        return edge;
    }

    private WorkflowVO.Definition definition(String status, WorkflowVO.Node... nodes) {
        WorkflowVO.Definition definition = new WorkflowVO.Definition();
        definition.setId(11L);
        definition.setBusinessType("EXPERT_APPLICATION");
        definition.setName("Expert approval");
        definition.setStatus(status);
        definition.setVersionNo(1);
        definition.setNodes(List.of(nodes));
        WorkflowVO.Edge edge = new WorkflowVO.Edge();
        edge.setEdgeKey("next");
        edge.setSourceNodeKey(nodes[0].getNodeKey());
        edge.setTargetNodeKey(nodes[1].getNodeKey());
        edge.setSortOrder(1);
        definition.setEdges(List.of(edge));
        return definition;
    }

    private WorkflowVO.Node startNode() {
        return node("start", "START");
    }

    private WorkflowVO.Node endNode() {
        return node("end", "END");
    }

    private WorkflowVO.Node approvalNode(Long approverUserId) {
        WorkflowVO.Node node = node("approve", "APPROVAL");
        node.setApprovalMode("ALL");
        node.setApproverUserIds(List.of(approverUserId));
        return node;
    }

    private WorkflowVO.Node node(String key, String type) {
        WorkflowVO.Node node = new WorkflowVO.Node();
        node.setNodeKey(key);
        node.setNodeType(type);
        node.setName(key);
        return node;
    }
}
