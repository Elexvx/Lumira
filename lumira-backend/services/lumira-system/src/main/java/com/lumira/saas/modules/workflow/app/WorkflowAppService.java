package com.lumira.saas.modules.workflow.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.infrastructure.event.PlatformEventPublisher;
import com.lumira.saas.infrastructure.event.PlatformEventTypes;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.workflow.dto.WorkflowDTO;
import com.lumira.saas.modules.workflow.vo.WorkflowVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class WorkflowAppService {
    public static final String BUSINESS_EXPERT_APPLICATION = "EXPERT_APPLICATION";
    public static final String EVENT_EXPERT_APPROVED = "EXPERT_APPROVED";
    private static final String STATUS_ENABLED = "ENABLED";

    private static final Set<String> NODE_TYPES = Set.of("START", "APPROVAL", "CONDITION", "END");
    private static final Set<String> APPROVAL_MODES = Set.of("ALL", "ANY");
    private static final long MAX_PAGE_SIZE = 100L;
    private static final int MAX_COMMENT_LENGTH = 500;

    private final MyBatisQueryOperations jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PlatformEventPublisher platformEventPublisher;
    private final OperationAuditService operationAuditService;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;
    private final boolean enforceTrustedUserResolution;

    @Autowired
    public WorkflowAppService(
            MyBatisQueryOperations jdbcTemplate,
            ObjectMapper objectMapper,
            PlatformEventPublisher platformEventPublisher,
            OperationAuditService operationAuditService,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(jdbcTemplate, objectMapper, platformEventPublisher, operationAuditService, permissionSnapshotService, systemInternalApi, sessionAuthenticationService, true);
    }

    private WorkflowAppService(
            MyBatisQueryOperations jdbcTemplate,
            ObjectMapper objectMapper,
            PlatformEventPublisher platformEventPublisher,
            OperationAuditService operationAuditService,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService,
            boolean enforceTrustedUserResolution
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.platformEventPublisher = platformEventPublisher;
        this.operationAuditService = operationAuditService;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    public WorkflowAppService(
            MyBatisQueryOperations jdbcTemplate,
            ObjectMapper objectMapper,
            PlatformEventPublisher platformEventPublisher,
            OperationAuditService operationAuditService,
            PermissionSnapshotService permissionSnapshotService
    ) {
        this(jdbcTemplate, objectMapper, platformEventPublisher, operationAuditService, permissionSnapshotService, null, null, false);
    }

    public WorkflowAppService(
            MyBatisQueryOperations jdbcTemplate,
            ObjectMapper objectMapper,
            PlatformEventPublisher platformEventPublisher,
            OperationAuditService operationAuditService,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi
    ) {
        this(jdbcTemplate, objectMapper, platformEventPublisher, operationAuditService, permissionSnapshotService, systemInternalApi, null, false);
    }

    public WorkflowAppService(
            MyBatisQueryOperations jdbcTemplate,
            ObjectMapper objectMapper,
            PlatformEventPublisher platformEventPublisher,
            OperationAuditService operationAuditService,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(jdbcTemplate, objectMapper, platformEventPublisher, operationAuditService, permissionSnapshotService, null, sessionAuthenticationService, false);
    }

    public WorkflowAppService(
            MyBatisQueryOperations jdbcTemplate,
            ObjectMapper objectMapper,
            PlatformEventPublisher platformEventPublisher,
            OperationAuditService operationAuditService
    ) {
        this(jdbcTemplate, objectMapper, platformEventPublisher, operationAuditService, null, null, null, false);
    }

    public WorkflowVO.Definition getDefinition(CurrentUser currentUser, String businessType) {
        requireAuthenticated(currentUser);
        requirePermission(currentUser, "workflow:view");
        return loadDefinition(normalizeBusinessType(businessType), false);
    }

    @Transactional
    public WorkflowVO.Definition saveDraft(CurrentUser currentUser, String businessType, WorkflowDTO.DefinitionSaveRequest request) {
        Long userId = requireUserId(currentUser);
        String userUuid = requireUserUuid(currentUser);
        requirePermission(currentUser, "workflow:config");
        String normalizedBusinessType = normalizeBusinessType(businessType);
        NormalizedDefinition normalized = normalizeDefinition(request);
        DefinitionBoundary existingDefinition = findDefinitionBoundary(normalizedBusinessType);
        Long definitionId = existingDefinition == null ? null : existingDefinition.id();
        LocalDateTime now = LocalDateTime.now();
        if (definitionId == null) {
            int inserted = jdbcTemplate.update(
                    """
                            insert into workflow_definition (business_type, name, status, version_no, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted)
                            values (?, ?, 'DRAFT', 1, ?, ?, ?, ?, 0)
                            """,
                    normalizedBusinessType,
                    normalized.name(),
                    userId,
                    userUuid,
                    userId,
                    userUuid
            );
            requireSingleWorkflowUpdate(inserted, "Workflow definition changed, please retry");
            definitionId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        } else {
            int updated = jdbcTemplate.update(
                    """
                            update workflow_definition
                            set name = ?, status = 'DRAFT', version_no = version_no + 1, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                            where id = ?
                              and business_type = ?
                              and status = ?
                              and version_no = ?
                              and deleted = 0
                            """,
                    normalized.name(),
                    userId,
                    userUuid,
                    now,
                    definitionId,
                    existingDefinition.businessType(),
                    existingDefinition.status(),
                    existingDefinition.versionNo()
            );
            requireSingleWorkflowUpdate(updated, "Workflow definition changed, please retry");
            jdbcTemplate.update(
                    """
                            update workflow_node
                            set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                            where definition_id = ?
                              and deleted = 0
                              and exists (
                                  select 1 from workflow_definition d
                                  where d.id = workflow_node.definition_id
                                    and d.business_type = ?
                                    and d.version_no = ?
                                    and d.deleted = 0
                              )
                            """,
                    userId,
                    userUuid,
                    now,
                    definitionId,
                    existingDefinition.businessType(),
                    existingDefinition.versionNo() + 1
            );
            jdbcTemplate.update(
                    """
                            update workflow_edge
                            set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                            where definition_id = ?
                              and deleted = 0
                              and exists (
                                  select 1 from workflow_definition d
                                  where d.id = workflow_edge.definition_id
                                    and d.business_type = ?
                                    and d.version_no = ?
                                    and d.deleted = 0
                              )
                            """,
                    userId,
                    userUuid,
                    now,
                    definitionId,
                    existingDefinition.businessType(),
                    existingDefinition.versionNo() + 1
            );
        }
        saveNodesAndEdges(definitionId, normalized, userId, userUuid);
        operationAuditService.log(userId, userUuid, currentUser.getUsername(), "workflow", "save-draft", "UPDATE", "SUCCESS", "Save workflow draft: " + normalizedBusinessType);
        return loadDefinitionById(definitionId);
    }

    @Transactional
    public WorkflowVO.Definition publish(CurrentUser currentUser, String businessType) {
        Long userId = requireUserId(currentUser);
        String userUuid = requireUserUuid(currentUser);
        requirePermission(currentUser, "workflow:config");
        String normalizedBusinessType = normalizeBusinessType(businessType);
        DefinitionBoundary definition = findDefinitionBoundary(normalizedBusinessType);
        if (definition == null) {
            throw biz(ErrorCode.NOT_FOUND, "Workflow definition not found");
        }
        validateDefinitionAssignments(loadDefinition(normalizedBusinessType, false));
        int updated = jdbcTemplate.update(
                """
                        update workflow_definition
                        set status = 'ACTIVE', updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ?
                          and business_type = ?
                          and status = ?
                          and version_no = ?
                          and deleted = 0
                        """,
                userId,
                userUuid,
                LocalDateTime.now(),
                definition.id(),
                definition.businessType(),
                definition.status(),
                definition.versionNo()
        );
        if (updated == 0) {
            throw biz(ErrorCode.NOT_FOUND, "Workflow definition changed, please retry");
        }
        operationAuditService.log(userId, userUuid, currentUser.getUsername(), "workflow", "publish", "UPDATE", "SUCCESS", "Publish workflow: " + normalizedBusinessType);
        return loadDefinitionById(definition.id());
    }

    @Transactional
    public Long startWorkflow(CurrentUser currentUser, String businessType, Long businessId, String businessUuid, String businessTitle, Map<String, Object> variables) {
        Long userId = requireUserId(currentUser);
        String userUuid = requireUserUuid(currentUser);
        WorkflowVO.Definition definition = loadDefinition(normalizeBusinessType(businessType), true);
        validateDefinitionAssignments(definition);
        String snapshot = toJson(Map.of(
                "definitionId", definition.getId(),
                "versionNo", definition.getVersionNo(),
                "nodes", definition.getNodes(),
                "edges", definition.getEdges()
        ));
        int inserted = jdbcTemplate.update(
                """
                        insert into workflow_instance (
                            definition_id, definition_version_no, business_type, business_id, business_uuid, business_title,
                            status, current_node_key, snapshot_json, variables_json, applicant_user_id, applicant_user_uuid,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, 'RUNNING', null, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                definition.getId(),
                definition.getVersionNo(),
                definition.getBusinessType(),
                businessId,
                businessUuid,
                businessTitle,
                snapshot,
                toJson(variables == null ? Map.of() : variables),
                userId,
                userUuid,
                userId,
                userUuid,
                userId,
                userUuid
        );
        requireSingleWorkflowUpdate(inserted, "Workflow instance changed, please retry");
        Long instanceId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        logAction(instanceId, null, "SUBMIT", null, null, currentUser, "Submitted");
        advanceToNext(instanceId, null, currentUser);
        operationAuditService.log(userId, userUuid, currentUser.getUsername(), "workflow", "submit", "CREATE", "SUCCESS", "Submit workflow: " + businessType + "/" + businessId);
        return instanceId;
    }

    public PageResponse<WorkflowVO.Task> listMyTasks(CurrentUser currentUser, String status, long pageNo, long pageSize) {
        Long userId = requireUserId(currentUser);
        String userUuid = requireUserUuid(currentUser);
        requirePermission(currentUser, "workflow:approve");
        long normalizedPageNo = Math.max(1L, pageNo);
        long normalizedPageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        String normalizedStatus = StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : "PENDING";
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(
                """
                         from workflow_task t
                         join workflow_instance i on i.id = t.instance_id and i.deleted = 0
                         where t.deleted = 0 and t.status = ?
                           and (t.approver_user_id = ? and t.approver_user_uuid = ?"""
        );
        params.add(normalizedStatus);
        params.add(userId);
        params.add(userUuid);
        Set<Long> roleIds = currentUser.getRoleIds() == null ? Set.of() : currentUser.getRoleIds();
        if (!roleIds.isEmpty()) {
            where.append(" or t.approver_role_id in (");
            where.append("?,".repeat(roleIds.size()));
            where.setLength(where.length() - 1);
            where.append(")");
            params.addAll(roleIds);
        }
        where.append(")");

        Long total = jdbcTemplate.queryForObject("select count(1)" + where, Long.class, params.toArray());
        List<Object> selectParams = new ArrayList<>(params);
        selectParams.add((normalizedPageNo - 1) * normalizedPageSize);
        selectParams.add(normalizedPageSize);
        List<WorkflowVO.Task> records = jdbcTemplate.query(
                """
                        select t.id, t.instance_id as instanceId, i.business_type as businessType, i.business_id as businessId,
                               i.business_uuid as businessUuid, i.business_title as businessTitle, t.node_key as nodeKey,
                               t.node_name as nodeName, t.status, t.approver_user_id as approverUserId,
                               t.approver_role_id as approverRoleId, t.created_at as createdAt, t.completed_at as completedAt
                        """ + where + " order by t.created_at desc, t.id desc limit ?, ?",
                new BeanPropertyRowMapper<>(WorkflowVO.Task.class),
                selectParams.toArray()
        );
        PageResponse<WorkflowVO.Task> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(total == null ? 0L : total);
        response.setPageNo(normalizedPageNo);
        response.setPageSize(normalizedPageSize);
        response.setHasMore(normalizedPageNo * normalizedPageSize < response.getTotal());
        return response;
    }

    @Transactional
    public boolean approveTask(CurrentUser currentUser, Long taskId, String comment) {
        return completeTask(currentUser, taskId, "APPROVED", "APPROVE", comment);
    }

    @Transactional
    public boolean rejectTask(CurrentUser currentUser, Long taskId, String comment) {
        return completeTask(currentUser, taskId, "REJECTED", "REJECT", comment);
    }

    public List<WorkflowVO.ActionLog> listLogs(CurrentUser currentUser, Long instanceId) {
        requireAuthenticated(currentUser);
        requirePermission(currentUser, "workflow:view");
        requirePositiveId(instanceId, "Workflow instance id is invalid");
        if (!hasPermission(currentUser, "*")) {
            requireWorkflowParticipant(currentUser, instanceId);
        }
        return jdbcTemplate.query(
                """
                        select id, instance_id as instanceId, task_id as taskId, action_type as actionType,
                               node_key as nodeKey, node_name as nodeName, operator_user_id as operatorUserId,
                               operator_user_uuid as operatorUserUuid, operator_username as operatorUsername,
                               comment, created_at as createdAt
                        from workflow_action_log
                        where instance_id = ? and deleted = 0
                        order by created_at asc, id asc
                        """,
                new BeanPropertyRowMapper<>(WorkflowVO.ActionLog.class),
                instanceId
        );
    }

    private boolean completeTask(CurrentUser currentUser, Long taskId, String taskStatus, String action, String comment) {
        Long userId = requireUserId(currentUser);
        String userUuid = requireUserUuid(currentUser);
        requirePositiveId(taskId, "Workflow task id is invalid");
        if (comment != null && comment.length() > MAX_COMMENT_LENGTH) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Workflow comment is too long");
        }
        requirePermission(currentUser, "workflow:approve");
        TaskRow task = loadTask(currentUser, taskId);
        if (task == null || !"PENDING".equals(task.status())) {
            throw biz(ErrorCode.NOT_FOUND, "Workflow task not found");
        }
        if (!canHandle(currentUser, task)) {
            throw biz(ErrorCode.FORBIDDEN, "No permission to approve this task");
        }
        LocalDateTime now = LocalDateTime.now();
        List<Object> updateParams = new ArrayList<>();
        updateParams.add(taskStatus);
        updateParams.add(userId);
        updateParams.add(userUuid);
        updateParams.add(currentUser.getUsername());
        updateParams.add(now);
        updateParams.add(trimToNull(comment));
        updateParams.add(userId);
        updateParams.add(userUuid);
        updateParams.add(now);
        updateParams.add(taskId);
        String assignmentPredicate = appendTaskAssignmentPredicate(currentUser, updateParams, "");
        int updated = jdbcTemplate.update(
                """
                        update workflow_task
                        set status = ?, completed_by = ?, completed_by_uuid = ?, completed_by_name = ?, completed_at = ?,
                            comment = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and deleted = 0 and status = 'PENDING'
                        """ + assignmentPredicate,
                updateParams.toArray()
        );
        if (updated == 0) {
            throw biz(ErrorCode.NOT_FOUND, "Workflow task not found");
        }
        logAction(task.instanceId(), taskId, action, task.nodeKey(), task.nodeName(), currentUser, comment);
        if ("REJECTED".equals(taskStatus)) {
            rejectInstance(task.instanceId(), currentUser, task, comment);
            return true;
        }
        if (shouldAdvanceAfterApproval(task)) {
            completeSiblingAnyTasks(task, currentUser);
            advanceToNext(task.instanceId(), task.nodeKey(), currentUser);
        }
        operationAuditService.log(userId, userUuid, currentUser.getUsername(), "workflow", action.toLowerCase(Locale.ROOT), "UPDATE", "SUCCESS", action + " workflow task: " + taskId);
        return true;
    }

    private void rejectInstance(Long instanceId, CurrentUser currentUser, TaskRow task, String comment) {
        Long userId = requireUserId(currentUser);
        String userUuid = requireUserUuid(currentUser);
        LocalDateTime now = LocalDateTime.now();
        InstanceRow instance = loadInstance(instanceId);
        jdbcTemplate.update(
                "update workflow_task set status = 'CANCELLED', updated_by = ?, updated_by_uuid = ?, updated_at = ? where instance_id = ? and status = 'PENDING' and deleted = 0",
                userId,
                userUuid,
                now,
                instanceId
        );
        int rejectedInstance = jdbcTemplate.update(
                "update workflow_instance set status = 'REJECTED', completed_at = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and status = ? and current_node_key = ? and deleted = 0",
                now,
                userId,
                userUuid,
                now,
                instanceId,
                instance.status(),
                task.nodeKey()
        );
        requireSingleWorkflowUpdate(rejectedInstance, "Workflow instance changed, please retry");
        if (BUSINESS_EXPERT_APPLICATION.equals(task.businessType())) {
            int expertUpdated = jdbcTemplate.update(
                    """
                            update aiadc_expert
                            set approval_status = 'REJECTED', approval_instance_id = ?, approved_by = ?, approved_at = ?,
                                status = 'inactive', updated_by = ?, updated_by_uuid = ?, updated_at = ?
                            where id = ?
                              and code = ?
                              and approval_instance_id = ?
                              and approval_status = 'PENDING'
                              and deleted = 0
                    """,
                    instanceId,
                    userId,
                    now,
                    userId,
                    userUuid,
                    now,
                    task.businessId(),
                    instance.businessUuid(),
                    instanceId
            );
            requireSingleWorkflowUpdate(expertUpdated, "Workflow business state changed, please retry");
        }
        logAction(instanceId, task.id(), "INSTANCE_REJECTED", task.nodeKey(), task.nodeName(), currentUser, comment);
    }

    private void advanceToNext(Long instanceId, String currentNodeKey, CurrentUser currentUser) {
        InstanceRow instance = loadInstance(instanceId);
        WorkflowSnapshot snapshot = parseSnapshot(instance.snapshotJson());
        String nextNodeKey = currentNodeKey == null ? firstStartTarget(snapshot) : resolveNextTarget(snapshot, currentNodeKey, parseMap(instance.variablesJson()));
        if (nextNodeKey == null) {
            approveInstance(instance, currentUser);
            return;
        }
        WorkflowVO.Node node = findNode(snapshot.nodes(), nextNodeKey);
        if (node == null) {
            approveInstance(instance, currentUser);
            return;
        }
        Long userId = requireUserId(currentUser);
        String userUuid = requireUserUuid(currentUser);
        int moved = jdbcTemplate.update(
                "update workflow_instance set current_node_key = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and status = ? and current_node_key <=> ? and deleted = 0",
                node.getNodeKey(),
                userId,
                userUuid,
                LocalDateTime.now(),
                instanceId,
                instance.status(),
                instance.currentNodeKey()
        );
        requireSingleWorkflowUpdate(moved, "Workflow instance changed, please retry");
        if ("END".equals(node.getNodeType())) {
            // The optimistic move above changed current_node_key. Reload the
            // instance so the terminal transition binds the new node instead
            // of the stale node from before the move.
            approveInstance(loadInstance(instanceId), currentUser);
            return;
        }
        if ("CONDITION".equals(node.getNodeType()) || "START".equals(node.getNodeType())) {
            advanceToNext(instanceId, node.getNodeKey(), currentUser);
            return;
        }
        createApprovalTasks(instanceId, node, currentUser);
    }

    private void approveInstance(InstanceRow instance, CurrentUser currentUser) {
        Long userId = requireUserId(currentUser);
        String userUuid = requireUserUuid(currentUser);
        LocalDateTime now = LocalDateTime.now();
        int approvedInstance = jdbcTemplate.update(
                "update workflow_instance set status = 'APPROVED', current_node_key = null, completed_at = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and status = ? and current_node_key <=> ? and deleted = 0",
                now,
                userId,
                userUuid,
                now,
                instance.id(),
                instance.status(),
                instance.currentNodeKey()
        );
        requireSingleWorkflowUpdate(approvedInstance, "Workflow instance changed, please retry");
        jdbcTemplate.update(
                "update workflow_task set status = 'CANCELLED', updated_by = ?, updated_by_uuid = ?, updated_at = ? where instance_id = ? and status = 'PENDING' and deleted = 0",
                userId,
                userUuid,
                now,
                instance.id()
        );
        if (BUSINESS_EXPERT_APPLICATION.equals(instance.businessType())) {
            int expertUpdated = jdbcTemplate.update(
                    """
                            update aiadc_expert
                            set approval_status = 'APPROVED', approval_instance_id = ?, approved_by = ?, approved_at = ?,
                                status = 'active', updated_by = ?, updated_by_uuid = ?, updated_at = ?
                            where id = ?
                              and code = ?
                              and approval_instance_id = ?
                              and approval_status = 'PENDING'
                              and deleted = 0
                    """,
                    instance.id(),
                    userId,
                    now,
                    userId,
                    userUuid,
                    now,
                    instance.businessId(),
                    instance.businessUuid(),
                    instance.id()
            );
            requireSingleWorkflowUpdate(expertUpdated, "Workflow business state changed, please retry");
            Long simulatedRoleId = normalizeSimulatedRoleId(currentUser.getSimulatedRoleId());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("businessType", instance.businessType());
            payload.put("businessUuid", instance.businessUuid() == null ? "" : instance.businessUuid());
            payload.put("workflowInstanceId", instance.id());
            payload.put("userUuid", userUuid);
            if (simulatedRoleId != null) {
                payload.put("simulatedRoleId", simulatedRoleId);
            }
            platformEventPublisher.publishAfterCommit(
                    PlatformEventTypes.SOURCE_SYSTEM,
                    EVENT_EXPERT_APPROVED,
                    userId,
                    "aiadc_expert",
                    instance.businessId(),
                    payload
            );
        }
        logAction(instance.id(), null, "INSTANCE_APPROVED", null, null, currentUser, "Approved");
    }

    private void requireSingleWorkflowUpdate(int updated, String message) {
        if (updated != 1) {
            throw biz(ErrorCode.BIZ_ERROR, message);
        }
    }

    private Long normalizeSimulatedRoleId(Long simulatedRoleId) {
        return simulatedRoleId == null || simulatedRoleId <= 0 ? null : simulatedRoleId;
    }

    private void createApprovalTasks(Long instanceId, WorkflowVO.Node node, CurrentUser currentUser) {
        List<Long> userIds = node.getApproverUserIds() == null ? List.of() : node.getApproverUserIds();
        List<Long> roleIds = node.getApproverRoleIds() == null ? List.of() : node.getApproverRoleIds();
        if (userIds.isEmpty() && roleIds.isEmpty()) {
            insertTask(instanceId, node, null, null, currentUser);
            return;
        }
        for (Long userId : userIds) {
            insertTask(instanceId, node, userId, null, currentUser);
        }
        for (Long roleId : roleIds) {
            insertTask(instanceId, node, null, roleId, currentUser);
        }
    }

    private void insertTask(Long instanceId, WorkflowVO.Node node, Long approverUserId, Long approverRoleId, CurrentUser currentUser) {
        Long userId = requireUserId(currentUser);
        String userUuid = requireUserUuid(currentUser);
        int inserted = jdbcTemplate.update(
                """
                        insert into workflow_task (
                            instance_id, node_key, node_name, approval_mode, status, approver_user_id, approver_user_uuid, approver_role_id,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, 'PENDING', ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                instanceId,
                node.getNodeKey(),
                node.getName(),
                normalizeApprovalMode(node.getApprovalMode()),
                approverUserId,
                approverUserId == null ? null : resolveTrustedUserUuid(approverUserId),
                approverRoleId,
                userId,
                userUuid,
                userId,
                userUuid
        );
        requireSingleWorkflowUpdate(inserted, "Workflow task changed, please retry");
    }

    private boolean shouldAdvanceAfterApproval(TaskRow task) {
        if ("ANY".equals(task.approvalMode())) {
            return true;
        }
        Long pending = jdbcTemplate.queryForObject(
                """
                        select count(1) from workflow_task
                        where instance_id = ? and node_key = ? and status = 'PENDING' and deleted = 0
                        """,
                Long.class,
                task.instanceId(),
                task.nodeKey()
        );
        return pending == null || pending == 0L;
    }

    private void completeSiblingAnyTasks(TaskRow task, CurrentUser currentUser) {
        if (!"ANY".equals(task.approvalMode())) {
            return;
        }
        jdbcTemplate.update(
                """
                        update workflow_task
                        set status = 'CANCELLED', updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where instance_id = ? and node_key = ? and status = 'PENDING' and deleted = 0
                        """,
                requireUserId(currentUser),
                requireUserUuid(currentUser),
                LocalDateTime.now(),
                task.instanceId(),
                task.nodeKey()
        );
    }

    private boolean canHandle(CurrentUser currentUser, TaskRow task) {
        String userUuid = requireUserUuid(currentUser);
        Set<Long> roleIds = currentUser.getRoleIds() == null ? Set.of() : currentUser.getRoleIds();
        return (Objects.equals(task.approverUserId(), currentUser.getUserId())
                    && StringUtils.hasText(task.approverUserUuid())
                    && task.approverUserUuid().trim().equals(userUuid))
                || (task.approverRoleId() != null && roleIds.contains(task.approverRoleId()))
                || (task.approverUserId() == null && task.approverRoleId() == null);
    }

    private void saveNodesAndEdges(Long definitionId, NormalizedDefinition definition, Long userId, String userUuid) {
        for (WorkflowDTO.NodeRequest node : definition.nodes()) {
            int inserted = jdbcTemplate.update(
                    """
                            insert into workflow_node (
                                definition_id, node_key, node_type, name, position_x, position_y, assignment_type,
                                approver_user_ids_json, approver_role_ids_json, approval_mode, config_json,
                                created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                            """,
                    definitionId,
                    node.getNodeKey(),
                    node.getNodeType(),
                    node.getName(),
                    node.getX(),
                    node.getY(),
                    trimToNull(node.getAssignmentType()),
                    toJson(node.getApproverUserIds() == null ? List.of() : node.getApproverUserIds()),
                    toJson(node.getApproverRoleIds() == null ? List.of() : node.getApproverRoleIds()),
                    normalizeApprovalMode(node.getApprovalMode()),
                    toJson(node.getConfig() == null ? Map.of() : node.getConfig()),
                    userId,
                    userUuid,
                    userId,
                    userUuid
            );
            requireSingleWorkflowUpdate(inserted, "Workflow node changed, please retry");
        }
        for (WorkflowDTO.EdgeRequest edge : definition.edges()) {
            int inserted = jdbcTemplate.update(
                    """
                            insert into workflow_edge (
                                definition_id, edge_key, source_node_key, target_node_key, condition_expression, sort_order,
                                config_json, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                            """,
                    definitionId,
                    edge.getEdgeKey(),
                    edge.getSourceNodeKey(),
                    edge.getTargetNodeKey(),
                    trimToNull(edge.getConditionExpression()),
                    edge.getSortOrder() == null ? 100 : edge.getSortOrder(),
                    toJson(edge.getConfig() == null ? Map.of() : edge.getConfig()),
                    userId,
                    userUuid,
                    userId,
                    userUuid
            );
            requireSingleWorkflowUpdate(inserted, "Workflow edge changed, please retry");
        }
    }

    private WorkflowVO.Definition loadDefinition(String businessType, boolean activeOnly) {
        String statusClause = activeOnly ? " and status = 'ACTIVE'" : "";
        List<WorkflowVO.Definition> rows = jdbcTemplate.query(
                """
                        select id, business_type as businessType, name, status, version_no as versionNo,
                               created_at as createdAt, updated_at as updatedAt
                        from workflow_definition
                        where business_type = ? and deleted = 0
                        """ + statusClause + " order by id desc limit 1",
                new BeanPropertyRowMapper<>(WorkflowVO.Definition.class),
                businessType
        );
        if (rows.isEmpty()) {
            throw biz(ErrorCode.NOT_FOUND, "Workflow definition not found");
        }
        return attachDefinitionChildren(rows.get(0));
    }

    private WorkflowVO.Definition loadDefinitionById(Long definitionId) {
        WorkflowVO.Definition definition = jdbcTemplate.queryForObject(
                """
                        select id, business_type as businessType, name, status, version_no as versionNo,
                               created_at as createdAt, updated_at as updatedAt
                        from workflow_definition
                        where id = ? and deleted = 0
                        """,
                new BeanPropertyRowMapper<>(WorkflowVO.Definition.class),
                definitionId
        );
        return attachDefinitionChildren(definition);
    }

    private WorkflowVO.Definition attachDefinitionChildren(WorkflowVO.Definition definition) {
        definition.setNodes(loadNodes(definition.getId()));
        definition.setEdges(loadEdges(definition.getId()));
        return definition;
    }

    private List<WorkflowVO.Node> loadNodes(Long definitionId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                        select id, node_key, node_type, name, position_x, position_y, assignment_type,
                               approver_user_ids_json, approver_role_ids_json, approval_mode, config_json
                        from workflow_node
                        where definition_id = ? and deleted = 0
                        order by id asc
                        """,
                definitionId
        );
        return rows.stream().map(this::mapNode).toList();
    }

    private List<WorkflowVO.Edge> loadEdges(Long definitionId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                        select id, edge_key, source_node_key, target_node_key, condition_expression, sort_order, config_json
                        from workflow_edge
                        where definition_id = ? and deleted = 0
                        order by sort_order asc, id asc
                        """,
                definitionId
        );
        return rows.stream().map(this::mapEdge).toList();
    }

    private WorkflowVO.Node mapNode(Map<String, Object> row) {
        WorkflowVO.Node node = new WorkflowVO.Node();
        node.setId(longValue(row.get("id")));
        node.setNodeKey(stringValue(row.get("node_key")));
        node.setNodeType(stringValue(row.get("node_type")));
        node.setName(stringValue(row.get("name")));
        node.setX(intValue(row.get("position_x")));
        node.setY(intValue(row.get("position_y")));
        node.setAssignmentType(stringValue(row.get("assignment_type")));
        node.setApproverUserIds(parseLongList(stringValue(row.get("approver_user_ids_json"))));
        node.setApproverRoleIds(parseLongList(stringValue(row.get("approver_role_ids_json"))));
        node.setApprovalMode(stringValue(row.get("approval_mode")));
        node.setConfig(parseMap(stringValue(row.get("config_json"))));
        return node;
    }

    private WorkflowVO.Edge mapEdge(Map<String, Object> row) {
        WorkflowVO.Edge edge = new WorkflowVO.Edge();
        edge.setId(longValue(row.get("id")));
        edge.setEdgeKey(stringValue(row.get("edge_key")));
        edge.setSourceNodeKey(stringValue(row.get("source_node_key")));
        edge.setTargetNodeKey(stringValue(row.get("target_node_key")));
        edge.setConditionExpression(stringValue(row.get("condition_expression")));
        edge.setSortOrder(intValue(row.get("sort_order")));
        edge.setConfig(parseMap(stringValue(row.get("config_json"))));
        return edge;
    }

    private TaskRow loadTask(CurrentUser currentUser, Long taskId) {
        List<Object> params = new ArrayList<>();
        params.add(taskId);
        String assignmentPredicate = appendTaskAssignmentPredicate(currentUser, params, "t.");
        List<TaskRow> rows = jdbcTemplate.query(
                """
                        select t.id, t.instance_id as instanceId, i.business_type as businessType, i.business_id as businessId,
                               t.node_key as nodeKey, t.node_name as nodeName, t.approval_mode as approvalMode,
                               t.status, t.approver_user_id as approverUserId, t.approver_user_uuid as approverUserUuid,
                               t.approver_role_id as approverRoleId
                        from workflow_task t
                        join workflow_instance i on i.id = t.instance_id and i.deleted = 0
                        where t.id = ? and t.deleted = 0
                        """ + assignmentPredicate + """
                        limit 1
                        """,
                (rs, rowNum) -> new TaskRow(
                        rs.getLong("id"),
                        rs.getLong("instanceId"),
                        rs.getString("businessType"),
                        rs.getLong("businessId"),
                        rs.getString("nodeKey"),
                        rs.getString("nodeName"),
                        rs.getString("approvalMode"),
                        rs.getString("status"),
                        rs.getObject("approverUserId", Long.class),
                        rs.getString("approverUserUuid"),
                        rs.getObject("approverRoleId", Long.class)
                ),
                params.toArray()
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String appendTaskAssignmentPredicate(CurrentUser currentUser, List<Object> params, String columnPrefix) {
        params.add(currentUser.getUserId());
        params.add(requireUserUuid(currentUser));
        StringBuilder predicate = new StringBuilder(
                " and ((" + columnPrefix + "approver_user_id = ? and " + columnPrefix + "approver_user_uuid = ?)"
        );
        Set<Long> roleIds = currentUser.getRoleIds() == null ? Set.of() : currentUser.getRoleIds();
        if (!roleIds.isEmpty()) {
            predicate.append(" or ").append(columnPrefix).append("approver_role_id in (");
            predicate.append("?,".repeat(roleIds.size()));
            predicate.setLength(predicate.length() - 1);
            predicate.append(")");
            params.addAll(roleIds);
        }
        predicate.append(")");
        return predicate.toString();
    }

    private InstanceRow loadInstance(Long instanceId) {
        List<InstanceRow> rows = jdbcTemplate.query(
                """
                        select id, business_type as businessType, business_id as businessId, business_uuid as businessUuid,
                               status, current_node_key as currentNodeKey,
                               snapshot_json as snapshotJson, variables_json as variablesJson
                        from workflow_instance
                        where id = ? and deleted = 0
                        limit 1
                        """,
                (rs, rowNum) -> new InstanceRow(
                        rs.getLong("id"),
                        rs.getString("businessType"),
                        rs.getLong("businessId"),
                        rs.getString("businessUuid"),
                        rs.getString("status"),
                        rs.getString("currentNodeKey"),
                        rs.getString("snapshotJson"),
                        rs.getString("variablesJson")
                ),
                instanceId
        );
        if (rows.isEmpty()) {
            throw biz(ErrorCode.NOT_FOUND, "Workflow instance not found");
        }
        return rows.get(0);
    }

    private String firstStartTarget(WorkflowSnapshot snapshot) {
        return snapshot.nodes().stream()
                .filter(node -> "START".equals(node.getNodeType()))
                .findFirst()
                .flatMap(node -> snapshot.edges().stream().filter(edge -> node.getNodeKey().equals(edge.getSourceNodeKey())).findFirst())
                .map(WorkflowVO.Edge::getTargetNodeKey)
                .orElse(null);
    }

    private String resolveNextTarget(WorkflowSnapshot snapshot, String nodeKey, Map<String, Object> variables) {
        return snapshot.edges().stream()
                .filter(edge -> nodeKey.equals(edge.getSourceNodeKey()))
                .sorted(Comparator.comparing(edge -> edge.getSortOrder() == null ? 100 : edge.getSortOrder()))
                .filter(edge -> conditionMatches(edge, variables))
                .map(WorkflowVO.Edge::getTargetNodeKey)
                .findFirst()
                .orElse(null);
    }

    private boolean conditionMatches(WorkflowVO.Edge edge, Map<String, Object> variables) {
        if (!StringUtils.hasText(edge.getConditionExpression())) {
            return true;
        }
        Map<String, Object> config = edge.getConfig() == null ? Map.of() : edge.getConfig();
        Object field = config.get("field");
        Object equals = config.get("equals");
        if (field == null || equals == null) {
            return true;
        }
        return Objects.equals(String.valueOf(variables.get(String.valueOf(field))), String.valueOf(equals));
    }

    private WorkflowVO.Node findNode(List<WorkflowVO.Node> nodes, String nodeKey) {
        return nodes.stream().filter(node -> nodeKey.equals(node.getNodeKey())).findFirst().orElse(null);
    }

    private NormalizedDefinition normalizeDefinition(WorkflowDTO.DefinitionSaveRequest request) {
        if (request == null) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Workflow definition request is required");
        }
        String name = trimRequired(request.getName(), "Workflow name is required");
        List<WorkflowDTO.NodeRequest> nodes = request.getNodes() == null ? List.of() : request.getNodes();
        List<WorkflowDTO.EdgeRequest> edges = request.getEdges() == null ? List.of() : request.getEdges();
        if (nodes.stream().noneMatch(node -> "START".equals(normalizeNodeType(node.getNodeType())))) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Workflow must contain a START node");
        }
        if (nodes.stream().noneMatch(node -> "END".equals(normalizeNodeType(node.getNodeType())))) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Workflow must contain an END node");
        }
        Set<String> nodeKeys = new java.util.HashSet<>();
        for (WorkflowDTO.NodeRequest node : nodes) {
            node.setNodeKey(trimRequired(node.getNodeKey(), "Node key is required"));
            if (!nodeKeys.add(node.getNodeKey())) {
                throw biz(ErrorCode.VALIDATION_ERROR, "Duplicate workflow node key");
            }
            node.setNodeType(normalizeNodeType(node.getNodeType()));
            node.setName(trimRequired(node.getName(), "Node name is required"));
            node.setApprovalMode(normalizeApprovalMode(node.getApprovalMode()));
            if (node.getApproverUserIds() != null && node.getApproverUserIds().stream().anyMatch(id -> id == null || id <= 0)) {
                throw biz(ErrorCode.VALIDATION_ERROR, "Invalid approver user");
            }
            if (node.getApproverRoleIds() != null && node.getApproverRoleIds().stream().anyMatch(id -> id == null || id <= 0)) {
                throw biz(ErrorCode.VALIDATION_ERROR, "Invalid approver role");
            }
            validateApprovalAssignment(node.getNodeType(), node.getApproverUserIds(), node.getApproverRoleIds());
        }
        for (WorkflowDTO.EdgeRequest edge : edges) {
            edge.setEdgeKey(trimRequired(edge.getEdgeKey(), "Edge key is required"));
            edge.setSourceNodeKey(trimRequired(edge.getSourceNodeKey(), "Source node is required"));
            edge.setTargetNodeKey(trimRequired(edge.getTargetNodeKey(), "Target node is required"));
            if (!nodeKeys.contains(edge.getSourceNodeKey()) || !nodeKeys.contains(edge.getTargetNodeKey())) {
                throw biz(ErrorCode.VALIDATION_ERROR, "Workflow edge references unknown node");
            }
        }
        return new NormalizedDefinition(name, nodes, edges);
    }

    private void validateDefinitionAssignments(WorkflowVO.Definition definition) {
        if (definition == null || definition.getNodes() == null) {
            return;
        }
        for (WorkflowVO.Node node : definition.getNodes()) {
            validateApprovalAssignment(node.getNodeType(), node.getApproverUserIds(), node.getApproverRoleIds());
        }
    }

    private void validateApprovalAssignment(String nodeType, List<Long> approverUserIds, List<Long> approverRoleIds) {
        if (!"APPROVAL".equals(normalizeNodeType(nodeType))) {
            return;
        }
        boolean hasApproverUsers = approverUserIds != null && !approverUserIds.isEmpty();
        boolean hasApproverRoles = approverRoleIds != null && !approverRoleIds.isEmpty();
        if (!hasApproverUsers && !hasApproverRoles) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Approval node must declare at least one approver");
        }
    }

    private String normalizeNodeType(String value) {
        String normalized = trimRequired(value, "Node type is required").toUpperCase(Locale.ROOT);
        if (!NODE_TYPES.contains(normalized)) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Invalid node type");
        }
        return normalized;
    }

    private String normalizeApprovalMode(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "ALL";
        if (!APPROVAL_MODES.contains(normalized)) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Invalid approval mode");
        }
        return normalized;
    }

    private DefinitionBoundary findDefinitionBoundary(String businessType) {
        List<DefinitionBoundary> rows = jdbcTemplate.query(
                """
                        select id, business_type, status, version_no
                        from workflow_definition
                        where business_type = ? and deleted = 0
                        order by id desc
                        limit 1
                        """,
                (rs, rowNum) -> new DefinitionBoundary(
                        rs.getLong("id"),
                        rs.getString("business_type"),
                        rs.getString("status"),
                        rs.getInt("version_no")
                ),
                businessType
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void logAction(Long instanceId, Long taskId, String action, String nodeKey, String nodeName, CurrentUser currentUser, String comment) {
        boolean trustedActor = AuthenticationTrustSupport.isTrustedCurrentUser(currentUser);
        Long actorUserId = trustedActor ? currentUser.getUserId() : null;
        String actorUserUuid = trustedActor ? currentUser.getUserUuid().trim() : null;
        int inserted = jdbcTemplate.update(
                """
                        insert into workflow_action_log (
                            instance_id, task_id, action_type, node_key, node_name, operator_user_id, operator_user_uuid,
                            operator_username, comment, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                instanceId,
                taskId,
                action,
                nodeKey,
                nodeName,
                actorUserId,
                actorUserUuid,
                trustedActor ? currentUser.getUsername() : null,
                trimToNull(comment),
                actorUserId,
                actorUserUuid,
                actorUserId,
                actorUserUuid
        );
        requireSingleWorkflowUpdate(inserted, "Workflow action log changed, please retry");
    }

    private WorkflowSnapshot parseSnapshot(String snapshotJson) {
        Map<String, Object> snapshot = parseMap(snapshotJson);
        List<WorkflowVO.Node> nodes = objectMapper.convertValue(snapshot.get("nodes"), new TypeReference<>() {});
        List<WorkflowVO.Edge> edges = objectMapper.convertValue(snapshot.get("edges"), new TypeReference<>() {});
        return new WorkflowSnapshot(nodes == null ? List.of() : nodes, edges == null ? List.of() : edges);
    }

    private Map<String, Object> parseMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private List<Long> parseLongList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize workflow JSON", exception);
        }
    }

    private String normalizeBusinessType(String value) {
        return trimRequired(value, "Business type is required").toUpperCase(Locale.ROOT);
    }

    private void requireAuthenticated(CurrentUser currentUser) {
        refreshTrustedCurrentUser(currentUser);
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw biz(ErrorCode.UNAUTHORIZED, "Login required");
        }
    }

    private Long requireUserId(CurrentUser currentUser) {
        requireAuthenticated(currentUser);
        return currentUser.getUserId();
    }

    private String requireUserUuid(CurrentUser currentUser) {
        requireAuthenticated(currentUser);
        return currentUser.getUserUuid().trim();
    }

    private String resolveTrustedUserUuid(Long userId) {
        if (userId == null || userId <= 0) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Invalid approver user");
        }
        List<String> rows = jdbcTemplate.query(
                """
                        select uuid
                        from sys_user
                        where id = ? and deleted = 0 and status = 'ENABLED'
                          and uuid is not null and uuid <> ''
                        limit 1
                        """,
                (rs, rowNum) -> rs.getString("uuid"),
                userId
        );
        if (rows.isEmpty() || !StringUtils.hasText(rows.get(0))) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Invalid approver user");
        }
        return rows.get(0).trim();
    }

    private void requireWorkflowParticipant(CurrentUser currentUser, Long instanceId) {
        Long userId = requireUserId(currentUser);
        String userUuid = requireUserUuid(currentUser);
        Set<Long> roleIds = currentUser.getRoleIds() == null ? Set.of() : currentUser.getRoleIds();
        StringBuilder sql = new StringBuilder(
                """
                        select count(1)
                        from workflow_instance i
                        left join workflow_task t
                          on t.instance_id = i.id and t.deleted = 0
                        where i.id = ? and i.deleted = 0
                          and (
                                (i.applicant_user_id = ? and i.applicant_user_uuid = ?)
                             or (t.approver_user_id = ? and t.approver_user_uuid = ?)
                        """
        );
        List<Object> params = new ArrayList<>();
        params.add(instanceId);
        params.add(userId);
        params.add(userUuid);
        params.add(userId);
        params.add(userUuid);
        if (!roleIds.isEmpty()) {
            sql.append(" or t.approver_role_id in (");
            sql.append("?,".repeat(roleIds.size()));
            sql.setLength(sql.length() - 1);
            sql.append(")");
            params.addAll(roleIds);
        }
        sql.append(")");
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        if (count == null || count <= 0) {
            throw biz(ErrorCode.NOT_FOUND, "Workflow instance not found");
        }
    }

    private void requirePositiveId(Long id, String message) {
        if (id == null || id <= 0) {
            throw biz(ErrorCode.VALIDATION_ERROR, message);
        }
    }

    private void requirePermission(CurrentUser currentUser, String permission) {
        if (!hasPermission(currentUser, permission)) {
            throw biz(ErrorCode.FORBIDDEN, "Permission denied");
        }
    }

    private void requireAnyPermission(CurrentUser currentUser, Set<String> permissions) {
        if (permissions.stream().noneMatch(permission -> hasPermission(currentUser, permission))) {
            throw biz(ErrorCode.FORBIDDEN, "Permission denied");
        }
    }

    private boolean hasPermission(CurrentUser currentUser, String permission) {
        refreshTrustedCurrentUser(currentUser);
        Set<String> permissions = currentUser == null || currentUser.getPermissions() == null
                ? Set.of()
                : currentUser.getPermissions();
        return permissions.contains("*") || permissions.contains(permission);
    }

    private void refreshTrustedCurrentUser(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            return;
        }
        if (sessionAuthenticationService != null) {
            CurrentUser refreshed = requireTrustedAuthenticatedCurrentUser(
                    sessionAuthenticationService.authenticateSessionTicket(
                            currentUser.getSessionId(),
                            currentUser.getUserId(),
                            currentUser.getUserUuid(),
                            currentUser.getSimulatedRoleId(),
                            currentUser.getSessionVersion(),
                            currentUser.getPermissionsVersion()
                    ),
                    "Trusted user identity is required"
            );
            copyTrustedCurrentUser(currentUser, refreshed);
            return;
        }
        if (permissionSnapshotService == null) {
            if (enforceTrustedUserResolution) {
                throw biz(ErrorCode.UNAUTHORIZED, "Trusted user resolver is unavailable");
            }
            return;
        }
        Long userId = currentUser.getUserId();
        String normalizedUserUuid = StringUtils.hasText(currentUser.getUserUuid()) ? currentUser.getUserUuid().trim() : null;
        if (userId == null || userId <= 0 || !StringUtils.hasText(normalizedUserUuid)) {
            throw biz(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
        }
        if (systemInternalApi != null) {
            SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
            if (userSnapshot == null || userSnapshot.userId() == null || !userId.equals(userSnapshot.userId())) {
                throw biz(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
            }
            if (!StringUtils.hasText(userSnapshot.userUuid())
                    || !normalizedUserUuid.equals(userSnapshot.userUuid().trim())) {
                throw biz(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
            }
            if (!STATUS_ENABLED.equalsIgnoreCase(userSnapshot.status())) {
                throw biz(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
            }
            if (!StringUtils.hasText(userSnapshot.username())) {
                throw biz(ErrorCode.UNAUTHORIZED, "Trusted user username is unavailable");
            }
            currentUser.setUserId(userSnapshot.userId());
            currentUser.setUserUuid(userSnapshot.userUuid().trim());
            currentUser.setUsername(userSnapshot.username().trim());
            normalizedUserUuid = userSnapshot.userUuid().trim();
        }
        if (!permissionSnapshotService.isTrustedActiveUser(userId, normalizedUserUuid)) {
            throw biz(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
        }
        Long simulatedRoleId = normalizeSimulatedRoleId(currentUser.getSimulatedRoleId());
        PermissionSnapshotService.PermissionSnapshot snapshot = simulatedRoleId != null
                ? permissionSnapshotService.loadGrantedRoleSnapshot(
                userId,
                normalizedUserUuid,
                simulatedRoleId
        )
                : permissionSnapshotService.loadSnapshot(userId, normalizedUserUuid);
        if (snapshot == null) {
            if (enforceTrustedUserResolution) {
                throw biz(ErrorCode.UNAUTHORIZED, "Trusted user permission snapshot is unavailable");
            }
            return;
        }
        currentUser.setUserUuid(normalizedUserUuid);
        currentUser.setSimulatedRoleId(simulatedRoleId);
        currentUser.setPermissions(snapshot.getPermissions() == null ? Set.of() : Set.copyOf(snapshot.getPermissions()));
        currentUser.setRoleIds(snapshot.getRoleIds() == null ? Set.of() : Set.copyOf(snapshot.getRoleIds()));
        currentUser.setPrimaryDeptId(snapshot.getPrimaryDeptId());
        currentUser.setDeptIds(snapshot.getDeptIds() == null ? Set.of() : Set.copyOf(snapshot.getDeptIds()));
        currentUser.setDescendantDeptIds(snapshot.getDescendantDeptIds() == null ? Set.of() : Set.copyOf(snapshot.getDescendantDeptIds()));
        currentUser.setDataScopes(snapshot.getDataScopes() == null ? List.of() : List.copyOf(snapshot.getDataScopes()));
        currentUser.setPermissionsVersion(snapshot.getVersion());
        currentUser.setDefaultHomePath(snapshot.getDefaultHomePath());
    }

    private CurrentUser requireTrustedAuthenticatedCurrentUser(
            SessionAuthenticationService.AuthenticatedAccess authenticatedAccess,
            String message
    ) {
        if (authenticatedAccess == null || !AuthenticationTrustSupport.isTrustedCurrentUser(authenticatedAccess.currentUser())) {
            throw biz(ErrorCode.UNAUTHORIZED, message);
        }
        return authenticatedAccess.currentUser();
    }

    private void copyTrustedCurrentUser(CurrentUser target, CurrentUser source) {
        target.setUserId(source.getUserId());
        target.setUserUuid(source.getUserUuid());
        target.setUsername(source.getUsername());
        target.setSessionId(source.getSessionId());
        target.setSessionVersion(source.getSessionVersion());
        target.setAuthenticated(source.isAuthenticated());
        target.setPermissions(source.getPermissions());
        target.setRoleIds(source.getRoleIds());
        target.setPrimaryDeptId(source.getPrimaryDeptId());
        target.setDeptIds(source.getDeptIds());
        target.setDescendantDeptIds(source.getDescendantDeptIds());
        target.setDataScopes(source.getDataScopes());
        target.setPermissionsVersion(source.getPermissionsVersion());
        target.setRequiresPasswordChange(source.getRequiresPasswordChange());
        target.setDefaultHomePath(source.getDefaultHomePath());
        target.setSimulatedRoleId(normalizeSimulatedRoleId(source.getSimulatedRoleId()));
        target.setLoginType(source.getLoginType());
    }

    private String trimRequired(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw biz(ErrorCode.VALIDATION_ERROR, message);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : value == null ? null : Long.parseLong(String.valueOf(value));
    }

    private Integer intValue(Object value) {
        return value instanceof Number number ? number.intValue() : value == null ? null : Integer.parseInt(String.valueOf(value));
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static BizException biz(ErrorCode code, String message) {
        return new BizException(code, message, message);
    }

    private record NormalizedDefinition(String name, List<WorkflowDTO.NodeRequest> nodes, List<WorkflowDTO.EdgeRequest> edges) {}

    private record WorkflowSnapshot(List<WorkflowVO.Node> nodes, List<WorkflowVO.Edge> edges) {}

    private record DefinitionBoundary(Long id, String businessType, String status, int versionNo) {}

    private record InstanceRow(Long id, String businessType, Long businessId, String businessUuid, String status, String currentNodeKey, String snapshotJson, String variablesJson) {}

    private record TaskRow(
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
}
