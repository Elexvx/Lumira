package com.lumira.saas.modules.workflow.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.workflow.WorkflowAuditPort;
import com.lumira.api.workflow.WorkflowEventPort;
import com.lumira.api.workflow.WorkflowEventTypes;
import com.lumira.api.workflow.WorkflowExpertApplicationPort;
import com.lumira.api.workflow.WorkflowStartPort;
import com.lumira.api.workflow.WorkflowUserAccessPort;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.vo.PageResponse;
import com.lumira.saas.modules.workflow.dto.WorkflowDTO;
import com.lumira.saas.modules.workflow.repository.WorkflowRepository;
import com.lumira.saas.modules.workflow.repository.WorkflowRepository.Actor;
import com.lumira.saas.modules.workflow.repository.WorkflowRepository.DefinitionBoundary;
import com.lumira.saas.modules.workflow.repository.WorkflowRepository.InstanceCreate;
import com.lumira.saas.modules.workflow.repository.WorkflowRepository.InstanceCreateResult;
import com.lumira.saas.modules.workflow.repository.WorkflowRepository.InstanceRow;
import com.lumira.saas.modules.workflow.repository.WorkflowRepository.TaskAssignment;
import com.lumira.saas.modules.workflow.repository.WorkflowRepository.TaskCompletion;
import com.lumira.saas.modules.workflow.repository.WorkflowRepository.TaskCreate;
import com.lumira.saas.modules.workflow.repository.WorkflowRepository.TaskRow;
import com.lumira.saas.modules.workflow.vo.WorkflowVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class WorkflowAppService implements WorkflowStartPort {
    public static final String BUSINESS_EXPERT_APPLICATION = WorkflowStartPort.BUSINESS_EXPERT_APPLICATION;
    public static final String EVENT_EXPERT_APPROVED = WorkflowEventTypes.EXPERT_APPROVED;

    private static final Set<String> NODE_TYPES = Set.of("START", "APPROVAL", "CONDITION", "END");
    private static final Set<String> APPROVAL_MODES = Set.of("ALL", "ANY");
    private static final long MAX_PAGE_SIZE = 100L;
    private static final int MAX_COMMENT_LENGTH = 500;

    private final WorkflowRepository workflowRepository;
    private final ObjectMapper objectMapper;
    private final WorkflowEventPort workflowEventPort;
    private final WorkflowAuditPort workflowAuditPort;
    private final WorkflowUserAccessPort workflowUserAccessPort;
    private final WorkflowExpertApplicationPort workflowExpertApplicationPort;

    public WorkflowAppService(
            WorkflowRepository workflowRepository,
            ObjectMapper objectMapper,
            WorkflowEventPort workflowEventPort,
            WorkflowAuditPort workflowAuditPort,
            WorkflowUserAccessPort workflowUserAccessPort,
            WorkflowExpertApplicationPort workflowExpertApplicationPort
    ) {
        this.workflowRepository = workflowRepository;
        this.objectMapper = objectMapper;
        this.workflowEventPort = workflowEventPort;
        this.workflowAuditPort = workflowAuditPort;
        this.workflowUserAccessPort = workflowUserAccessPort;
        this.workflowExpertApplicationPort = workflowExpertApplicationPort;
    }

    public WorkflowVO.Definition getDefinition(CurrentUser currentUser, String businessType) {
        requireAuthenticated(currentUser);
        requirePermission(currentUser, "workflow:view");
        return requireDefinition(workflowRepository.findDefinition(normalizeBusinessType(businessType), false));
    }

    @Transactional
    public WorkflowVO.Definition saveDraft(CurrentUser currentUser, String businessType, WorkflowDTO.DefinitionSaveRequest request) {
        Long userId = requireUserId(currentUser);
        String userUuid = requireUserUuid(currentUser);
        requirePermission(currentUser, "workflow:config");
        String normalizedBusinessType = normalizeBusinessType(businessType);
        normalizeDefinition(request);
        DefinitionBoundary existingDefinition = workflowRepository.findDefinitionBoundary(normalizedBusinessType);
        WorkflowRepository.DraftSaveResult saved = workflowRepository.saveDraft(
                existingDefinition,
                normalizedBusinessType,
                request,
                new Actor(userId, userUuid),
                LocalDateTime.now()
        );
        requireSingleWorkflowUpdate(saved.definitionWriteCount(), "Workflow definition changed, please retry");
        saved.nodeWriteCounts().forEach(updated -> requireSingleWorkflowUpdate(updated, "Workflow node changed, please retry"));
        saved.edgeWriteCounts().forEach(updated -> requireSingleWorkflowUpdate(updated, "Workflow edge changed, please retry"));
        Long definitionId = saved.definitionId();
        workflowAuditPort.log(userId, userUuid, currentUser.getUsername(), "workflow", "save-draft", "UPDATE", "SUCCESS", "Save workflow draft: " + normalizedBusinessType);
        return requireDefinition(workflowRepository.findDefinitionById(definitionId));
    }

    @Transactional
    public WorkflowVO.Definition publish(CurrentUser currentUser, String businessType) {
        Long userId = requireUserId(currentUser);
        String userUuid = requireUserUuid(currentUser);
        requirePermission(currentUser, "workflow:config");
        String normalizedBusinessType = normalizeBusinessType(businessType);
        DefinitionBoundary definition = workflowRepository.findDefinitionBoundary(normalizedBusinessType);
        if (definition == null) {
            throw biz(ErrorCode.NOT_FOUND, "Workflow definition not found");
        }
        validateDefinitionAssignments(requireDefinition(workflowRepository.findDefinition(normalizedBusinessType, false)));
        int updated = workflowRepository.publishDefinition(definition, new Actor(userId, userUuid), LocalDateTime.now());
        if (updated == 0) {
            throw biz(ErrorCode.NOT_FOUND, "Workflow definition changed, please retry");
        }
        workflowAuditPort.log(userId, userUuid, currentUser.getUsername(), "workflow", "publish", "UPDATE", "SUCCESS", "Publish workflow: " + normalizedBusinessType);
        return requireDefinition(workflowRepository.findDefinitionById(definition.id()));
    }

    @Override
    @Transactional
    public Long startWorkflow(CurrentUser currentUser, String businessType, Long businessId, String businessUuid, String businessTitle, Map<String, Object> variables) {
        Long userId = requireUserId(currentUser);
        String userUuid = requireUserUuid(currentUser);
        WorkflowVO.Definition definition = requireDefinition(workflowRepository.findDefinition(normalizeBusinessType(businessType), true));
        validateDefinitionAssignments(definition);
        String snapshot = toJson(Map.of(
                "definitionId", definition.getId(),
                "versionNo", definition.getVersionNo(),
                "nodes", definition.getNodes(),
                "edges", definition.getEdges()
        ));
        InstanceCreateResult created = workflowRepository.createInstance(new InstanceCreate(
                definition.getId(), definition.getVersionNo(), definition.getBusinessType(), businessId, businessUuid, businessTitle,
                snapshot, toJson(variables == null ? Map.of() : variables), new Actor(userId, userUuid)
        ));
        requireSingleWorkflowUpdate(created.writeCount(), "Workflow instance changed, please retry");
        Long instanceId = created.instanceId();
        logAction(instanceId, null, "SUBMIT", null, null, currentUser, "Submitted");
        advanceToNext(instanceId, null, currentUser);
        workflowAuditPort.log(userId, userUuid, currentUser.getUsername(), "workflow", "submit", "CREATE", "SUCCESS", "Submit workflow: " + businessType + "/" + businessId);
        return instanceId;
    }

    public PageResponse<WorkflowVO.Task> listMyTasks(CurrentUser currentUser, String status, long pageNo, long pageSize) {
        Long userId = requireUserId(currentUser);
        String userUuid = requireUserUuid(currentUser);
        requirePermission(currentUser, "workflow:approve");
        long normalizedPageNo = Math.max(1L, pageNo);
        long normalizedPageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        String normalizedStatus = StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : "PENDING";
        Set<Long> roleIds = currentUser.getRoleIds() == null ? Set.of() : currentUser.getRoleIds();
        WorkflowRepository.TaskPage tasks = workflowRepository.findTasks(
                new TaskAssignment(userId, userUuid, roleIds), normalizedStatus,
                (normalizedPageNo - 1) * normalizedPageSize, normalizedPageSize
        );
        PageResponse<WorkflowVO.Task> response = new PageResponse<>();
        response.setRecords(tasks.records());
        response.setTotal(tasks.total());
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
        return workflowRepository.findActionLogs(instanceId);
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
        int updated = workflowRepository.completeTask(
                taskId,
                taskAssignment(currentUser),
                new TaskCompletion(taskStatus, new Actor(userId, userUuid), currentUser.getUsername(), now, trimToNull(comment))
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
        workflowAuditPort.log(userId, userUuid, currentUser.getUsername(), "workflow", action.toLowerCase(Locale.ROOT), "UPDATE", "SUCCESS", action + " workflow task: " + taskId);
        return true;
    }

    private void rejectInstance(Long instanceId, CurrentUser currentUser, TaskRow task, String comment) {
        Long userId = requireUserId(currentUser);
        String userUuid = requireUserUuid(currentUser);
        LocalDateTime now = LocalDateTime.now();
        InstanceRow instance = requireInstance(workflowRepository.findInstance(instanceId));
        workflowRepository.cancelPendingTasks(instanceId, new Actor(userId, userUuid), now);
        int rejectedInstance = workflowRepository.rejectInstance(instance, task, new Actor(userId, userUuid), now);
        requireSingleWorkflowUpdate(rejectedInstance, "Workflow instance changed, please retry");
        if (BUSINESS_EXPERT_APPLICATION.equals(task.businessType())) {
            int expertUpdated = workflowExpertApplicationPort.updateStatus(new WorkflowExpertApplicationPort.ExpertApplicationDecision(
                    "REJECTED", "inactive", instanceId, userId, now, userUuid, task.businessId(), instance.businessUuid()
            ));
            requireSingleWorkflowUpdate(expertUpdated, "Workflow business state changed, please retry");
        }
        logAction(instanceId, task.id(), "INSTANCE_REJECTED", task.nodeKey(), task.nodeName(), currentUser, comment);
    }

    private void advanceToNext(Long instanceId, String currentNodeKey, CurrentUser currentUser) {
        InstanceRow instance = requireInstance(workflowRepository.findInstance(instanceId));
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
        int moved = workflowRepository.moveInstance(instance, node.getNodeKey(), new Actor(userId, userUuid), LocalDateTime.now());
        requireSingleWorkflowUpdate(moved, "Workflow instance changed, please retry");
        if ("END".equals(node.getNodeType())) {
            // The optimistic move above changed current_node_key. Reload the
            // instance so the terminal transition binds the new node instead
            // of the stale node from before the move.
            approveInstance(requireInstance(workflowRepository.findInstance(instanceId)), currentUser);
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
        int approvedInstance = workflowRepository.approveInstance(instance, new Actor(userId, userUuid), now);
        requireSingleWorkflowUpdate(approvedInstance, "Workflow instance changed, please retry");
        workflowRepository.cancelPendingTasks(instance.id(), new Actor(userId, userUuid), now);
        if (BUSINESS_EXPERT_APPLICATION.equals(instance.businessType())) {
            int expertUpdated = workflowExpertApplicationPort.updateStatus(new WorkflowExpertApplicationPort.ExpertApplicationDecision(
                    "APPROVED", "active", instance.id(), userId, now, userUuid, instance.businessId(), instance.businessUuid()
            ));
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
            workflowEventPort.record(
                    WorkflowEventTypes.EXPERT_APPROVED,
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
        int inserted = workflowRepository.createTask(new TaskCreate(
                instanceId, node.getNodeKey(), node.getName(), normalizeApprovalMode(node.getApprovalMode()), approverUserId,
                approverUserId == null ? null : resolveTrustedUserUuid(approverUserId), approverRoleId, new Actor(userId, userUuid)
        ));
        requireSingleWorkflowUpdate(inserted, "Workflow task changed, please retry");
    }

    private boolean shouldAdvanceAfterApproval(TaskRow task) {
        if ("ANY".equals(task.approvalMode())) {
            return true;
        }
        return workflowRepository.countPendingTasks(task.instanceId(), task.nodeKey()) == 0L;
    }

    private void completeSiblingAnyTasks(TaskRow task, CurrentUser currentUser) {
        if (!"ANY".equals(task.approvalMode())) {
            return;
        }
        workflowRepository.cancelSiblingTasks(
                task.instanceId(), task.nodeKey(), new Actor(requireUserId(currentUser), requireUserUuid(currentUser)), LocalDateTime.now()
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

    private TaskRow loadTask(CurrentUser currentUser, Long taskId) {
        return workflowRepository.findTask(taskId, taskAssignment(currentUser));
    }

    private TaskAssignment taskAssignment(CurrentUser currentUser) {
        Set<Long> roleIds = currentUser.getRoleIds() == null ? Set.of() : currentUser.getRoleIds();
        return new TaskAssignment(currentUser.getUserId(), requireUserUuid(currentUser), roleIds);
    }

    private WorkflowVO.Definition requireDefinition(WorkflowVO.Definition definition) {
        if (definition == null) {
            throw biz(ErrorCode.NOT_FOUND, "Workflow definition not found");
        }
        return definition;
    }

    private InstanceRow requireInstance(InstanceRow instance) {
        if (instance == null) {
            throw biz(ErrorCode.NOT_FOUND, "Workflow instance not found");
        }
        return instance;
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

    private void normalizeDefinition(WorkflowDTO.DefinitionSaveRequest request) {
        if (request == null) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Workflow definition request is required");
        }
        trimRequired(request.getName(), "Workflow name is required");
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

    private void logAction(Long instanceId, Long taskId, String action, String nodeKey, String nodeName, CurrentUser currentUser, String comment) {
        boolean trustedActor = AuthenticationTrustSupport.isTrustedCurrentUser(currentUser);
        Long actorUserId = trustedActor ? currentUser.getUserId() : null;
        String actorUserUuid = trustedActor ? currentUser.getUserUuid().trim() : null;
        int inserted = workflowRepository.insertActionLog(new WorkflowRepository.ActionLogCreate(
                instanceId, taskId, action, nodeKey, nodeName, actorUserId, actorUserUuid,
                trustedActor ? currentUser.getUsername() : null, trimToNull(comment)
        ));
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
        String userUuid = workflowUserAccessPort.findEnabledUserUuid(userId);
        if (!StringUtils.hasText(userUuid)) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Invalid approver user");
        }
        return userUuid.trim();
    }

    private void requireWorkflowParticipant(CurrentUser currentUser, Long instanceId) {
        if (!workflowRepository.hasParticipant(instanceId, taskAssignment(currentUser))) {
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
        CurrentUser refreshed = workflowUserAccessPort == null
                ? null
                : workflowUserAccessPort.refreshTrustedUser(currentUser);
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(refreshed)) {
            throw biz(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
        }
        copyTrustedCurrentUser(currentUser, refreshed);
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

    private static BizException biz(ErrorCode code, String message) {
        return new BizException(code, message, message);
    }

    private record WorkflowSnapshot(List<WorkflowVO.Node> nodes, List<WorkflowVO.Edge> edges) {}

}
