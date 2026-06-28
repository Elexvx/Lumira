package com.lumira.saas.modules.workflow.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.infrastructure.event.PlatformEventPublisher;
import com.lumira.saas.infrastructure.event.PlatformEventTypes;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.workflow.dto.WorkflowDTO;
import com.lumira.saas.modules.workflow.vo.WorkflowVO;
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

    private static final Set<String> NODE_TYPES = Set.of("START", "APPROVAL", "CONDITION", "END");
    private static final Set<String> APPROVAL_MODES = Set.of("ALL", "ANY");
    private static final long MAX_PAGE_SIZE = 100L;

    private final MyBatisQueryOperations jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PlatformEventPublisher platformEventPublisher;
    private final OperationAuditService operationAuditService;

    public WorkflowAppService(
            MyBatisQueryOperations jdbcTemplate,
            ObjectMapper objectMapper,
            PlatformEventPublisher platformEventPublisher,
            OperationAuditService operationAuditService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.platformEventPublisher = platformEventPublisher;
        this.operationAuditService = operationAuditService;
    }

    public WorkflowVO.Definition getDefinition(CurrentUser currentUser, String businessType) {
        requireAuthenticated(currentUser);
        return loadDefinition(normalizeBusinessType(businessType), false);
    }

    @Transactional
    public WorkflowVO.Definition saveDraft(CurrentUser currentUser, String businessType, WorkflowDTO.DefinitionSaveRequest request) {
        Long userId = requireUserId(currentUser);
        String normalizedBusinessType = normalizeBusinessType(businessType);
        NormalizedDefinition normalized = normalizeDefinition(request);
        Long definitionId = findDefinitionId(normalizedBusinessType);
        LocalDateTime now = LocalDateTime.now();
        if (definitionId == null) {
            jdbcTemplate.update(
                    """
                            insert into workflow_definition (business_type, name, status, version_no, created_by, updated_by, deleted)
                            values (?, ?, 'DRAFT', 1, ?, ?, 0)
                            """,
                    normalizedBusinessType,
                    normalized.name(),
                    userId,
                    userId
            );
            definitionId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        } else {
            jdbcTemplate.update(
                    """
                            update workflow_definition
                            set name = ?, status = 'DRAFT', version_no = version_no + 1, updated_by = ?, updated_at = ?
                            where id = ? and deleted = 0
                            """,
                    normalized.name(),
                    userId,
                    now,
                    definitionId
            );
            jdbcTemplate.update("update workflow_node set deleted = 1, updated_by = ?, updated_at = ? where definition_id = ? and deleted = 0", userId, now, definitionId);
            jdbcTemplate.update("update workflow_edge set deleted = 1, updated_by = ?, updated_at = ? where definition_id = ? and deleted = 0", userId, now, definitionId);
        }
        saveNodesAndEdges(definitionId, normalized, userId);
        operationAuditService.log(userId, currentUser.getUsername(), "workflow", "save-draft", "UPDATE", "SUCCESS", "Save workflow draft: " + normalizedBusinessType);
        return loadDefinitionById(definitionId);
    }

    @Transactional
    public WorkflowVO.Definition publish(CurrentUser currentUser, String businessType) {
        Long userId = requireUserId(currentUser);
        String normalizedBusinessType = normalizeBusinessType(businessType);
        Long definitionId = findDefinitionId(normalizedBusinessType);
        if (definitionId == null) {
            throw biz(ErrorCode.NOT_FOUND, "Workflow definition not found");
        }
        jdbcTemplate.update(
                "update workflow_definition set status = 'ACTIVE', updated_by = ?, updated_at = ? where id = ? and deleted = 0",
                userId,
                LocalDateTime.now(),
                definitionId
        );
        operationAuditService.log(userId, currentUser.getUsername(), "workflow", "publish", "UPDATE", "SUCCESS", "Publish workflow: " + normalizedBusinessType);
        return loadDefinitionById(definitionId);
    }

    @Transactional
    public Long startWorkflow(CurrentUser currentUser, String businessType, Long businessId, String businessUuid, String businessTitle, Map<String, Object> variables) {
        Long userId = requireUserId(currentUser);
        WorkflowVO.Definition definition = loadDefinition(normalizeBusinessType(businessType), true);
        String snapshot = toJson(Map.of(
                "definitionId", definition.getId(),
                "versionNo", definition.getVersionNo(),
                "nodes", definition.getNodes(),
                "edges", definition.getEdges()
        ));
        jdbcTemplate.update(
                """
                        insert into workflow_instance (
                            definition_id, definition_version_no, business_type, business_id, business_uuid, business_title,
                            status, current_node_key, snapshot_json, variables_json, applicant_user_id, applicant_user_uuid,
                            created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, 'RUNNING', null, ?, ?, ?, ?, ?, ?, 0)
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
                currentUser.getUserUuid(),
                userId,
                userId
        );
        Long instanceId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        logAction(instanceId, null, "SUBMIT", null, null, currentUser, "Submitted");
        advanceToNext(instanceId, null, currentUser);
        operationAuditService.log(userId, currentUser.getUsername(), "workflow", "submit", "CREATE", "SUCCESS", "Submit workflow: " + businessType + "/" + businessId);
        return instanceId;
    }

    public PageResponse<WorkflowVO.Task> listMyTasks(CurrentUser currentUser, String status, long pageNo, long pageSize) {
        Long userId = requireUserId(currentUser);
        long normalizedPageNo = Math.max(1L, pageNo);
        long normalizedPageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        String normalizedStatus = StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : "PENDING";
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(
                """
                         from workflow_task t
                         join workflow_instance i on i.id = t.instance_id and i.deleted = 0
                         where t.deleted = 0 and t.status = ?
                           and (t.approver_user_id = ?"""
        );
        params.add(normalizedStatus);
        params.add(userId);
        if (!currentUser.getRoleIds().isEmpty()) {
            where.append(" or t.approver_role_id in (");
            where.append("?,".repeat(currentUser.getRoleIds().size()));
            where.setLength(where.length() - 1);
            where.append(")");
            params.addAll(currentUser.getRoleIds());
        }
        where.append(" or (t.approver_user_id is null and t.approver_role_id is null))");

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
        TaskRow task = loadTask(taskId);
        if (task == null || !"PENDING".equals(task.status())) {
            throw biz(ErrorCode.NOT_FOUND, "Workflow task not found");
        }
        if (!canHandle(currentUser, task)) {
            throw biz(ErrorCode.FORBIDDEN, "No permission to approve this task");
        }
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                        update workflow_task
                        set status = ?, completed_by = ?, completed_by_uuid = ?, completed_by_name = ?, completed_at = ?,
                            comment = ?, updated_by = ?, updated_at = ?
                        where id = ? and deleted = 0 and status = 'PENDING'
                        """,
                taskStatus,
                userId,
                currentUser.getUserUuid(),
                currentUser.getUsername(),
                now,
                trimToNull(comment),
                userId,
                now,
                taskId
        );
        logAction(task.instanceId(), taskId, action, task.nodeKey(), task.nodeName(), currentUser, comment);
        if ("REJECTED".equals(taskStatus)) {
            rejectInstance(task.instanceId(), currentUser, task, comment);
            return true;
        }
        if (shouldAdvanceAfterApproval(task)) {
            completeSiblingAnyTasks(task, currentUser);
            advanceToNext(task.instanceId(), task.nodeKey(), currentUser);
        }
        operationAuditService.log(userId, currentUser.getUsername(), "workflow", action.toLowerCase(Locale.ROOT), "UPDATE", "SUCCESS", action + " workflow task: " + taskId);
        return true;
    }

    private void rejectInstance(Long instanceId, CurrentUser currentUser, TaskRow task, String comment) {
        jdbcTemplate.update(
                "update workflow_task set status = 'CANCELLED', updated_by = ?, updated_at = ? where instance_id = ? and status = 'PENDING' and deleted = 0",
                requireUserId(currentUser),
                LocalDateTime.now(),
                instanceId
        );
        jdbcTemplate.update(
                "update workflow_instance set status = 'REJECTED', completed_at = ?, updated_by = ?, updated_at = ? where id = ? and deleted = 0",
                LocalDateTime.now(),
                requireUserId(currentUser),
                LocalDateTime.now(),
                instanceId
        );
        if (BUSINESS_EXPERT_APPLICATION.equals(task.businessType())) {
            jdbcTemplate.update(
                    """
                            update aiadc_expert
                            set approval_status = 'REJECTED', approval_instance_id = ?, approved_by = ?, approved_at = ?,
                                status = 'inactive', updated_by = ?, updated_at = ?
                            where id = ? and deleted = 0
                            """,
                    instanceId,
                    requireUserId(currentUser),
                    LocalDateTime.now(),
                    requireUserId(currentUser),
                    LocalDateTime.now(),
                    task.businessId()
            );
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
        jdbcTemplate.update(
                "update workflow_instance set current_node_key = ?, updated_by = ?, updated_at = ? where id = ? and deleted = 0",
                node.getNodeKey(),
                requireUserId(currentUser),
                LocalDateTime.now(),
                instanceId
        );
        if ("END".equals(node.getNodeType())) {
            approveInstance(instance, currentUser);
            return;
        }
        if ("CONDITION".equals(node.getNodeType()) || "START".equals(node.getNodeType())) {
            advanceToNext(instanceId, node.getNodeKey(), currentUser);
            return;
        }
        createApprovalTasks(instanceId, node, currentUser);
    }

    private void approveInstance(InstanceRow instance, CurrentUser currentUser) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "update workflow_instance set status = 'APPROVED', current_node_key = null, completed_at = ?, updated_by = ?, updated_at = ? where id = ? and deleted = 0",
                now,
                requireUserId(currentUser),
                now,
                instance.id()
        );
        jdbcTemplate.update(
                "update workflow_task set status = 'CANCELLED', updated_by = ?, updated_at = ? where instance_id = ? and status = 'PENDING' and deleted = 0",
                requireUserId(currentUser),
                now,
                instance.id()
        );
        if (BUSINESS_EXPERT_APPLICATION.equals(instance.businessType())) {
            jdbcTemplate.update(
                    """
                            update aiadc_expert
                            set approval_status = 'APPROVED', approval_instance_id = ?, approved_by = ?, approved_at = ?,
                                status = 'active', updated_by = ?, updated_at = ?
                            where id = ? and deleted = 0
                            """,
                    instance.id(),
                    requireUserId(currentUser),
                    now,
                    requireUserId(currentUser),
                    now,
                    instance.businessId()
            );
            platformEventPublisher.publishAfterCommit(
                    PlatformEventTypes.SOURCE_SYSTEM,
                    EVENT_EXPERT_APPROVED,
                    requireUserId(currentUser),
                    "aiadc_expert",
                    instance.businessId(),
                    Map.of(
                            "businessType", instance.businessType(),
                            "businessUuid", instance.businessUuid() == null ? "" : instance.businessUuid(),
                            "workflowInstanceId", instance.id()
                    )
            );
        }
        logAction(instance.id(), null, "INSTANCE_APPROVED", null, null, currentUser, "Approved");
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
        jdbcTemplate.update(
                """
                        insert into workflow_task (
                            instance_id, node_key, node_name, approval_mode, status, approver_user_id, approver_role_id,
                            created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, 'PENDING', ?, ?, ?, ?, 0)
                        """,
                instanceId,
                node.getNodeKey(),
                node.getName(),
                normalizeApprovalMode(node.getApprovalMode()),
                approverUserId,
                approverRoleId,
                requireUserId(currentUser),
                requireUserId(currentUser)
        );
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
                        set status = 'CANCELLED', updated_by = ?, updated_at = ?
                        where instance_id = ? and node_key = ? and status = 'PENDING' and deleted = 0
                        """,
                requireUserId(currentUser),
                LocalDateTime.now(),
                task.instanceId(),
                task.nodeKey()
        );
    }

    private boolean canHandle(CurrentUser currentUser, TaskRow task) {
        return Objects.equals(task.approverUserId(), currentUser.getUserId())
                || (task.approverRoleId() != null && currentUser.getRoleIds().contains(task.approverRoleId()))
                || (task.approverUserId() == null && task.approverRoleId() == null);
    }

    private void saveNodesAndEdges(Long definitionId, NormalizedDefinition definition, Long userId) {
        for (WorkflowDTO.NodeRequest node : definition.nodes()) {
            jdbcTemplate.update(
                    """
                            insert into workflow_node (
                                definition_id, node_key, node_type, name, position_x, position_y, assignment_type,
                                approver_user_ids_json, approver_role_ids_json, approval_mode, config_json,
                                created_by, updated_by, deleted
                            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
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
                    userId
            );
        }
        for (WorkflowDTO.EdgeRequest edge : definition.edges()) {
            jdbcTemplate.update(
                    """
                            insert into workflow_edge (
                                definition_id, edge_key, source_node_key, target_node_key, condition_expression, sort_order,
                                config_json, created_by, updated_by, deleted
                            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                            """,
                    definitionId,
                    edge.getEdgeKey(),
                    edge.getSourceNodeKey(),
                    edge.getTargetNodeKey(),
                    trimToNull(edge.getConditionExpression()),
                    edge.getSortOrder() == null ? 100 : edge.getSortOrder(),
                    toJson(edge.getConfig() == null ? Map.of() : edge.getConfig()),
                    userId,
                    userId
            );
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

    private TaskRow loadTask(Long taskId) {
        List<TaskRow> rows = jdbcTemplate.query(
                """
                        select t.id, t.instance_id as instanceId, i.business_type as businessType, i.business_id as businessId,
                               t.node_key as nodeKey, t.node_name as nodeName, t.approval_mode as approvalMode,
                               t.status, t.approver_user_id as approverUserId, t.approver_role_id as approverRoleId
                        from workflow_task t
                        join workflow_instance i on i.id = t.instance_id and i.deleted = 0
                        where t.id = ? and t.deleted = 0
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
                        rs.getObject("approverRoleId", Long.class)
                ),
                taskId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private InstanceRow loadInstance(Long instanceId) {
        List<InstanceRow> rows = jdbcTemplate.query(
                """
                        select id, business_type as businessType, business_id as businessId, business_uuid as businessUuid,
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
        String name = trimRequired(request.getName(), "Workflow name is required");
        List<WorkflowDTO.NodeRequest> nodes = request.getNodes() == null ? List.of() : request.getNodes();
        List<WorkflowDTO.EdgeRequest> edges = request.getEdges() == null ? List.of() : request.getEdges();
        if (nodes.stream().noneMatch(node -> "START".equals(normalizeNodeType(node.getNodeType())))) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Workflow must contain a START node");
        }
        if (nodes.stream().noneMatch(node -> "END".equals(normalizeNodeType(node.getNodeType())))) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Workflow must contain an END node");
        }
        for (WorkflowDTO.NodeRequest node : nodes) {
            node.setNodeKey(trimRequired(node.getNodeKey(), "Node key is required"));
            node.setNodeType(normalizeNodeType(node.getNodeType()));
            node.setName(trimRequired(node.getName(), "Node name is required"));
            node.setApprovalMode(normalizeApprovalMode(node.getApprovalMode()));
        }
        for (WorkflowDTO.EdgeRequest edge : edges) {
            edge.setEdgeKey(trimRequired(edge.getEdgeKey(), "Edge key is required"));
            edge.setSourceNodeKey(trimRequired(edge.getSourceNodeKey(), "Source node is required"));
            edge.setTargetNodeKey(trimRequired(edge.getTargetNodeKey(), "Target node is required"));
        }
        return new NormalizedDefinition(name, nodes, edges);
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

    private Long findDefinitionId(String businessType) {
        List<Long> rows = jdbcTemplate.query(
                "select id from workflow_definition where business_type = ? and deleted = 0 order by id desc limit 1",
                (rs, rowNum) -> rs.getLong("id"),
                businessType
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void logAction(Long instanceId, Long taskId, String action, String nodeKey, String nodeName, CurrentUser currentUser, String comment) {
        jdbcTemplate.update(
                """
                        insert into workflow_action_log (
                            instance_id, task_id, action_type, node_key, node_name, operator_user_id, operator_user_uuid,
                            operator_username, comment, created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                instanceId,
                taskId,
                action,
                nodeKey,
                nodeName,
                currentUser == null ? 0L : currentUser.getUserId(),
                currentUser == null ? null : currentUser.getUserUuid(),
                currentUser == null ? null : currentUser.getUsername(),
                trimToNull(comment),
                currentUser == null ? 0L : currentUser.getUserId(),
                currentUser == null ? 0L : currentUser.getUserId()
        );
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
        if (currentUser == null) {
            throw biz(ErrorCode.UNAUTHORIZED, "Login required");
        }
    }

    private Long requireUserId(CurrentUser currentUser) {
        if (currentUser == null || currentUser.getUserId() == null || currentUser.getUserId() <= 0) {
            throw biz(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return currentUser.getUserId();
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

    private record InstanceRow(Long id, String businessType, Long businessId, String businessUuid, String snapshotJson, String variablesJson) {}

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
            Long approverRoleId
    ) {}
}
