package com.lumira.saas.modules.workflow.repository;

import com.lumira.saas.modules.workflow.dto.WorkflowDTO;
import com.lumira.saas.modules.workflow.vo.WorkflowVO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Persistence boundary for the workflow aggregate.
 *
 * <p>The application service owns authorization, transaction boundaries and
 * workflow state transitions. SQL, row mapping and optimistic-write predicates
 * belong exclusively to the infrastructure adapter.</p>
 */
public interface WorkflowRepository {
    WorkflowVO.Definition findDefinition(String businessType, boolean activeOnly);

    WorkflowVO.Definition findDefinitionById(Long definitionId);

    DefinitionBoundary findDefinitionBoundary(String businessType);

    DraftSaveResult saveDraft(
            DefinitionBoundary existingDefinition,
            String businessType,
            WorkflowDTO.DefinitionSaveRequest definition,
            Actor actor,
            LocalDateTime updatedAt
    );

    int publishDefinition(DefinitionBoundary definition, Actor actor, LocalDateTime updatedAt);

    InstanceCreateResult createInstance(InstanceCreate command);

    TaskPage findTasks(TaskAssignment assignment, String status, long offset, long limit);

    List<WorkflowVO.ActionLog> findActionLogs(Long instanceId);

    TaskRow findTask(Long taskId, TaskAssignment assignment);

    int completeTask(Long taskId, TaskAssignment assignment, TaskCompletion completion);

    InstanceRow findInstance(Long instanceId);

    int cancelPendingTasks(Long instanceId, Actor actor, LocalDateTime updatedAt);

    int rejectInstance(InstanceRow instance, TaskRow task, Actor actor, LocalDateTime completedAt);

    int moveInstance(InstanceRow instance, String nodeKey, Actor actor, LocalDateTime updatedAt);

    int approveInstance(InstanceRow instance, Actor actor, LocalDateTime completedAt);

    int createTask(TaskCreate command);

    long countPendingTasks(Long instanceId, String nodeKey);

    int cancelSiblingTasks(Long instanceId, String nodeKey, Actor actor, LocalDateTime updatedAt);

    int insertActionLog(ActionLogCreate command);

    boolean hasParticipant(Long instanceId, TaskAssignment assignment);

    record Actor(Long userId, String userUuid) {}

    record DefinitionBoundary(Long id, String businessType, String status, int versionNo) {}

    record DraftSaveResult(Long definitionId, int definitionWriteCount, List<Integer> nodeWriteCounts, List<Integer> edgeWriteCounts) {}

    record InstanceCreate(
            Long definitionId,
            Integer definitionVersionNo,
            String businessType,
            Long businessId,
            String businessUuid,
            String businessTitle,
            String snapshotJson,
            String variablesJson,
            Actor actor
    ) {}

    record InstanceCreateResult(Long instanceId, int writeCount) {}

    record TaskAssignment(Long userId, String userUuid, Set<Long> roleIds) {}

    record TaskPage(List<WorkflowVO.Task> records, long total) {}

    record TaskRow(
            Long id,
            Long instanceId,
            String businessType,
            Long businessId,
            String nodeKey,
            String nodeName,
            String approvalMode,
            String status,
            Long approverUserId,
            String approverUserUuid,
            Long approverRoleId
    ) {}

    record TaskCompletion(
            String status,
            Actor actor,
            String actorUsername,
            LocalDateTime completedAt,
            String comment
    ) {}

    record InstanceRow(
            Long id,
            String businessType,
            Long businessId,
            String businessUuid,
            String status,
            String currentNodeKey,
            String snapshotJson,
            String variablesJson
    ) {}

    record TaskCreate(
            Long instanceId,
            String nodeKey,
            String nodeName,
            String approvalMode,
            Long approverUserId,
            String approverUserUuid,
            Long approverRoleId,
            Actor actor
    ) {}

    record ActionLogCreate(
            Long instanceId,
            Long taskId,
            String actionType,
            String nodeKey,
            String nodeName,
            Long operatorUserId,
            String operatorUserUuid,
            String operatorUsername,
            String comment
    ) {}
}
