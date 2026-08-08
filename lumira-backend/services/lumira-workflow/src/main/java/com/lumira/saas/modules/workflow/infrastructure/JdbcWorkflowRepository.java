package com.lumira.saas.modules.workflow.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.saas.modules.workflow.infrastructure.persistence.WorkflowBeanPropertyRowMapper;
import com.lumira.saas.modules.workflow.infrastructure.persistence.WorkflowSqlOperations;
import com.lumira.saas.modules.workflow.dto.WorkflowDTO;
import com.lumira.saas.modules.workflow.repository.WorkflowRepository;
import com.lumira.saas.modules.workflow.vo.WorkflowVO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** MyBatis/JDBC persistence adapter for the workflow aggregate. */
@Repository
public class JdbcWorkflowRepository implements WorkflowRepository {
    private final WorkflowSqlOperations database;
    private final ObjectMapper objectMapper;

    public JdbcWorkflowRepository(WorkflowSqlOperations database, ObjectMapper objectMapper) {
        this.database = database;
        this.objectMapper = objectMapper;
    }

    @Override
    public WorkflowVO.Definition findDefinition(String businessType, boolean activeOnly) {
        String statusClause = activeOnly ? " and status = 'ACTIVE'" : "";
        List<WorkflowVO.Definition> rows = database.query(
                """
                        select id, business_type as businessType, name, status, version_no as versionNo,
                               created_at as createdAt, updated_at as updatedAt
                        from workflow_definition
                        where business_type = ? and deleted = 0
                        """ + statusClause + " order by id desc limit 1",
                new WorkflowBeanPropertyRowMapper<>(WorkflowVO.Definition.class),
                businessType
        );
        return rows.isEmpty() ? null : attachDefinitionChildren(rows.getFirst());
    }

    @Override
    public WorkflowVO.Definition findDefinitionById(Long definitionId) {
        WorkflowVO.Definition definition = database.queryForObject(
                """
                        select id, business_type as businessType, name, status, version_no as versionNo,
                               created_at as createdAt, updated_at as updatedAt
                        from workflow_definition
                        where id = ? and deleted = 0
                        """,
                new WorkflowBeanPropertyRowMapper<>(WorkflowVO.Definition.class),
                definitionId
        );
        return definition == null ? null : attachDefinitionChildren(definition);
    }

    @Override
    public DefinitionBoundary findDefinitionBoundary(String businessType) {
        List<DefinitionBoundary> rows = database.query(
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
        return rows.isEmpty() ? null : rows.getFirst();
    }

    @Override
    public DraftSaveResult saveDraft(
            DefinitionBoundary existingDefinition,
            String businessType,
            WorkflowDTO.DefinitionSaveRequest definition,
            Actor actor,
            LocalDateTime updatedAt
    ) {
        Long definitionId;
        int definitionWriteCount;
        if (existingDefinition == null) {
            definitionWriteCount = database.update(
                    """
                            insert into workflow_definition (business_type, name, status, version_no, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted)
                            values (?, ?, 'DRAFT', 1, ?, ?, ?, ?, 0)
                            """,
                    businessType,
                    definition.getName(),
                    actor.userId(),
                    actor.userUuid(),
                    actor.userId(),
                    actor.userUuid()
            );
            definitionId = definitionWriteCount == 1 ? database.queryForObject("select last_insert_id()", Long.class) : null;
        } else {
            definitionId = existingDefinition.id();
            definitionWriteCount = database.update(
                    """
                            update workflow_definition
                            set name = ?, status = 'DRAFT', version_no = version_no + 1, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                            where id = ?
                              and business_type = ?
                              and status = ?
                              and version_no = ?
                              and deleted = 0
                            """,
                    definition.getName(),
                    actor.userId(),
                    actor.userUuid(),
                    updatedAt,
                    definitionId,
                    existingDefinition.businessType(),
                    existingDefinition.status(),
                    existingDefinition.versionNo()
            );
            if (definitionWriteCount != 1) {
                return new DraftSaveResult(definitionId, definitionWriteCount, List.of(), List.of());
            }
            database.update(
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
                    actor.userId(), actor.userUuid(), updatedAt, definitionId,
                    existingDefinition.businessType(), existingDefinition.versionNo() + 1
            );
            database.update(
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
                    actor.userId(), actor.userUuid(), updatedAt, definitionId,
                    existingDefinition.businessType(), existingDefinition.versionNo() + 1
            );
        }
        if (definitionWriteCount != 1 || definitionId == null) {
            return new DraftSaveResult(definitionId, definitionWriteCount, List.of(), List.of());
        }
        List<Integer> nodeWriteCounts = new ArrayList<>();
        for (WorkflowDTO.NodeRequest node : definition.getNodes()) {
            int inserted = database.update(
                    """
                            insert into workflow_node (
                                definition_id, node_key, node_type, name, position_x, position_y, assignment_type,
                                approver_user_ids_json, approver_role_ids_json, approval_mode, config_json,
                                created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                            """,
                    definitionId, node.getNodeKey(), node.getNodeType(), node.getName(), node.getX(), node.getY(),
                    trimToNull(node.getAssignmentType()), toJson(node.getApproverUserIds() == null ? List.of() : node.getApproverUserIds()),
                    toJson(node.getApproverRoleIds() == null ? List.of() : node.getApproverRoleIds()), node.getApprovalMode(),
                    toJson(node.getConfig() == null ? Map.of() : node.getConfig()), actor.userId(), actor.userUuid(), actor.userId(), actor.userUuid()
            );
            nodeWriteCounts.add(inserted);
            if (inserted != 1) {
                return new DraftSaveResult(definitionId, definitionWriteCount, nodeWriteCounts, List.of());
            }
        }
        List<Integer> edgeWriteCounts = new ArrayList<>();
        for (WorkflowDTO.EdgeRequest edge : definition.getEdges()) {
            int inserted = database.update(
                    """
                            insert into workflow_edge (
                                definition_id, edge_key, source_node_key, target_node_key, condition_expression, sort_order,
                                config_json, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                            """,
                    definitionId, edge.getEdgeKey(), edge.getSourceNodeKey(), edge.getTargetNodeKey(),
                    trimToNull(edge.getConditionExpression()), edge.getSortOrder() == null ? 100 : edge.getSortOrder(),
                    toJson(edge.getConfig() == null ? Map.of() : edge.getConfig()), actor.userId(), actor.userUuid(), actor.userId(), actor.userUuid()
            );
            edgeWriteCounts.add(inserted);
            if (inserted != 1) {
                break;
            }
        }
        return new DraftSaveResult(definitionId, definitionWriteCount, nodeWriteCounts, edgeWriteCounts);
    }

    @Override
    public int publishDefinition(DefinitionBoundary definition, Actor actor, LocalDateTime updatedAt) {
        return database.update(
                """
                        update workflow_definition
                        set status = 'ACTIVE', updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ?
                          and business_type = ?
                          and status = ?
                          and version_no = ?
                          and deleted = 0
                        """,
                actor.userId(), actor.userUuid(), updatedAt, definition.id(), definition.businessType(), definition.status(), definition.versionNo()
        );
    }

    @Override
    public InstanceCreateResult createInstance(InstanceCreate command) {
        int inserted = database.update(
                """
                        insert into workflow_instance (
                            definition_id, definition_version_no, business_type, business_id, business_uuid, business_title,
                            status, current_node_key, snapshot_json, variables_json, applicant_user_id, applicant_user_uuid,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, 'RUNNING', null, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                command.definitionId(), command.definitionVersionNo(), command.businessType(), command.businessId(), command.businessUuid(),
                command.businessTitle(), command.snapshotJson(), command.variablesJson(), command.actor().userId(), command.actor().userUuid(),
                command.actor().userId(), command.actor().userUuid(), command.actor().userId(), command.actor().userUuid()
        );
        return new InstanceCreateResult(inserted == 1 ? database.queryForObject("select last_insert_id()", Long.class) : null, inserted);
    }

    @Override
    public TaskPage findTasks(TaskAssignment assignment, String status, long offset, long limit) {
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(
                """
                         from workflow_task t
                         join workflow_instance i on i.id = t.instance_id and i.deleted = 0
                         where t.deleted = 0 and t.status = ?
                           and (t.approver_user_id = ? and t.approver_user_uuid = ?"""
        );
        params.add(status);
        params.add(assignment.userId());
        params.add(assignment.userUuid());
        appendRoleAssignment(where, params, assignment.roleIds(), "t.");
        where.append(")");
        Long total = database.queryForObject("select count(1)" + where, Long.class, params.toArray());
        params.add(offset);
        params.add(limit);
        List<WorkflowVO.Task> records = database.query(
                """
                        select t.id, t.instance_id as instanceId, i.business_type as businessType, i.business_id as businessId,
                               i.business_uuid as businessUuid, i.business_title as businessTitle, t.node_key as nodeKey,
                               t.node_name as nodeName, t.status, t.approver_user_id as approverUserId,
                               t.approver_role_id as approverRoleId, t.created_at as createdAt, t.completed_at as completedAt
                        """ + where + " order by t.created_at desc, t.id desc limit ?, ?",
                new WorkflowBeanPropertyRowMapper<>(WorkflowVO.Task.class),
                params.toArray()
        );
        return new TaskPage(records, total == null ? 0L : total);
    }

    @Override
    public List<WorkflowVO.ActionLog> findActionLogs(Long instanceId) {
        return database.query(
                """
                        select id, instance_id as instanceId, task_id as taskId, action_type as actionType,
                               node_key as nodeKey, node_name as nodeName, operator_user_id as operatorUserId,
                               operator_user_uuid as operatorUserUuid, operator_username as operatorUsername,
                               comment, created_at as createdAt
                        from workflow_action_log
                        where instance_id = ? and deleted = 0
                        order by created_at asc, id asc
                        """,
                new WorkflowBeanPropertyRowMapper<>(WorkflowVO.ActionLog.class),
                instanceId
        );
    }

    @Override
    public TaskRow findTask(Long taskId, TaskAssignment assignment) {
        List<Object> params = new ArrayList<>();
        params.add(taskId);
        StringBuilder predicate = new StringBuilder(" and ((t.approver_user_id = ? and t.approver_user_uuid = ?)");
        params.add(assignment.userId());
        params.add(assignment.userUuid());
        appendRoleAssignment(predicate, params, assignment.roleIds(), "t.");
        predicate.append(")");
        List<TaskRow> rows = database.query(
                """
                        select t.id, t.instance_id as instanceId, i.business_type as businessType, i.business_id as businessId,
                               t.node_key as nodeKey, t.node_name as nodeName, t.approval_mode as approvalMode,
                               t.status, t.approver_user_id as approverUserId, t.approver_user_uuid as approverUserUuid,
                               t.approver_role_id as approverRoleId
                        from workflow_task t
                        join workflow_instance i on i.id = t.instance_id and i.deleted = 0
                        where t.id = ? and t.deleted = 0
                        """ + predicate + """
                        limit 1
                        """,
                (rs, rowNum) -> new TaskRow(
                        rs.getLong("id"), rs.getLong("instanceId"), rs.getString("businessType"), rs.getLong("businessId"),
                        rs.getString("nodeKey"), rs.getString("nodeName"), rs.getString("approvalMode"), rs.getString("status"),
                        rs.getObject("approverUserId", Long.class), rs.getString("approverUserUuid"), rs.getObject("approverRoleId", Long.class)
                ),
                params.toArray()
        );
        return rows.isEmpty() ? null : rows.getFirst();
    }

    @Override
    public int completeTask(Long taskId, TaskAssignment assignment, TaskCompletion completion) {
        List<Object> params = new ArrayList<>();
        params.add(completion.status());
        params.add(completion.actor().userId());
        params.add(completion.actor().userUuid());
        params.add(completion.actorUsername());
        params.add(completion.completedAt());
        params.add(completion.comment());
        params.add(completion.actor().userId());
        params.add(completion.actor().userUuid());
        params.add(completion.completedAt());
        params.add(taskId);
        StringBuilder predicate = new StringBuilder(" and ((approver_user_id = ? and approver_user_uuid = ?)");
        params.add(assignment.userId());
        params.add(assignment.userUuid());
        appendRoleAssignment(predicate, params, assignment.roleIds(), "");
        predicate.append(")");
        return database.update(
                """
                        update workflow_task
                        set status = ?, completed_by = ?, completed_by_uuid = ?, completed_by_name = ?, completed_at = ?,
                            comment = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and deleted = 0 and status = 'PENDING'
                        """ + predicate,
                params.toArray()
        );
    }

    @Override
    public InstanceRow findInstance(Long instanceId) {
        List<InstanceRow> rows = database.query(
                """
                        select id, business_type as businessType, business_id as businessId, business_uuid as businessUuid,
                               status, current_node_key as currentNodeKey,
                               snapshot_json as snapshotJson, variables_json as variablesJson
                        from workflow_instance
                        where id = ? and deleted = 0
                        limit 1
                        """,
                (rs, rowNum) -> new InstanceRow(
                        rs.getLong("id"), rs.getString("businessType"), rs.getLong("businessId"), rs.getString("businessUuid"),
                        rs.getString("status"), rs.getString("currentNodeKey"), rs.getString("snapshotJson"), rs.getString("variablesJson")
                ),
                instanceId
        );
        return rows.isEmpty() ? null : rows.getFirst();
    }

    @Override
    public int cancelPendingTasks(Long instanceId, Actor actor, LocalDateTime updatedAt) {
        return database.update(
                "update workflow_task set status = 'CANCELLED', updated_by = ?, updated_by_uuid = ?, updated_at = ? where instance_id = ? and status = 'PENDING' and deleted = 0",
                actor.userId(), actor.userUuid(), updatedAt, instanceId
        );
    }

    @Override
    public int rejectInstance(InstanceRow instance, TaskRow task, Actor actor, LocalDateTime completedAt) {
        return database.update(
                "update workflow_instance set status = 'REJECTED', completed_at = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and status = ? and current_node_key = ? and deleted = 0",
                completedAt, actor.userId(), actor.userUuid(), completedAt, instance.id(), instance.status(), task.nodeKey()
        );
    }

    @Override
    public int moveInstance(InstanceRow instance, String nodeKey, Actor actor, LocalDateTime updatedAt) {
        return database.update(
                "update workflow_instance set current_node_key = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and status = ? and current_node_key <=> ? and deleted = 0",
                nodeKey, actor.userId(), actor.userUuid(), updatedAt, instance.id(), instance.status(), instance.currentNodeKey()
        );
    }

    @Override
    public int approveInstance(InstanceRow instance, Actor actor, LocalDateTime completedAt) {
        return database.update(
                "update workflow_instance set status = 'APPROVED', current_node_key = null, completed_at = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and status = ? and current_node_key <=> ? and deleted = 0",
                completedAt, actor.userId(), actor.userUuid(), completedAt, instance.id(), instance.status(), instance.currentNodeKey()
        );
    }

    @Override
    public int createTask(TaskCreate command) {
        return database.update(
                """
                        insert into workflow_task (
                            instance_id, node_key, node_name, approval_mode, status, approver_user_id, approver_user_uuid, approver_role_id,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, 'PENDING', ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                command.instanceId(), command.nodeKey(), command.nodeName(), command.approvalMode(), command.approverUserId(),
                command.approverUserUuid(), command.approverRoleId(), command.actor().userId(), command.actor().userUuid(),
                command.actor().userId(), command.actor().userUuid()
        );
    }

    @Override
    public long countPendingTasks(Long instanceId, String nodeKey) {
        Long pending = database.queryForObject(
                """
                        select count(1) from workflow_task
                        where instance_id = ? and node_key = ? and status = 'PENDING' and deleted = 0
                        """,
                Long.class, instanceId, nodeKey
        );
        return pending == null ? 0L : pending;
    }

    @Override
    public int cancelSiblingTasks(Long instanceId, String nodeKey, Actor actor, LocalDateTime updatedAt) {
        return database.update(
                """
                        update workflow_task
                        set status = 'CANCELLED', updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where instance_id = ? and node_key = ? and status = 'PENDING' and deleted = 0
                        """,
                actor.userId(), actor.userUuid(), updatedAt, instanceId, nodeKey
        );
    }

    @Override
    public int insertActionLog(ActionLogCreate command) {
        return database.update(
                """
                        insert into workflow_action_log (
                            instance_id, task_id, action_type, node_key, node_name, operator_user_id, operator_user_uuid,
                            operator_username, comment, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                command.instanceId(), command.taskId(), command.actionType(), command.nodeKey(), command.nodeName(),
                command.operatorUserId(), command.operatorUserUuid(), command.operatorUsername(), command.comment(),
                command.operatorUserId(), command.operatorUserUuid(), command.operatorUserId(), command.operatorUserUuid()
        );
    }

    @Override
    public boolean hasParticipant(Long instanceId, TaskAssignment assignment) {
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
        params.add(assignment.userId());
        params.add(assignment.userUuid());
        params.add(assignment.userId());
        params.add(assignment.userUuid());
        appendRoleAssignment(sql, params, assignment.roleIds(), "t.");
        sql.append(")");
        Long count = database.queryForObject(sql.toString(), Long.class, params.toArray());
        return count != null && count > 0;
    }

    private WorkflowVO.Definition attachDefinitionChildren(WorkflowVO.Definition definition) {
        definition.setNodes(loadNodes(definition.getId()));
        definition.setEdges(loadEdges(definition.getId()));
        return definition;
    }

    private List<WorkflowVO.Node> loadNodes(Long definitionId) {
        List<Map<String, Object>> rows = database.queryForList(
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
        List<Map<String, Object>> rows = database.queryForList(
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

    private void appendRoleAssignment(StringBuilder sql, List<Object> params, java.util.Set<Long> roleIds, String columnPrefix) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        sql.append(" or ").append(columnPrefix).append("approver_role_id in (");
        sql.append("?,".repeat(roleIds.size()));
        sql.setLength(sql.length() - 1);
        sql.append(")");
        params.addAll(roleIds);
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

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize workflow JSON", exception);
        }
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : value == null ? null : Long.parseLong(String.valueOf(value));
    }

    private static Integer intValue(Object value) {
        return value instanceof Number number ? number.intValue() : value == null ? null : Integer.parseInt(String.valueOf(value));
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
