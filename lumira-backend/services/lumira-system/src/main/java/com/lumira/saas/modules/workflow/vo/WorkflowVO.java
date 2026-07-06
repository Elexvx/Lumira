package com.lumira.saas.modules.workflow.vo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class WorkflowVO {
    private WorkflowVO() {
    }

    public static class Definition {
        private Long id;
        private String businessType;
        private String name;
        private String status;
        private Integer versionNo;
        private List<Node> nodes;
        private List<Edge> edges;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getBusinessType() { return businessType; }
        public void setBusinessType(String businessType) { this.businessType = businessType; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Integer getVersionNo() { return versionNo; }
        public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
        public List<Node> getNodes() { return nodes; }
        public void setNodes(List<Node> nodes) { this.nodes = nodes; }
        public List<Edge> getEdges() { return edges; }
        public void setEdges(List<Edge> edges) { this.edges = edges; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    public static class Node {
        private Long id;
        private String nodeKey;
        private String nodeType;
        private String name;
        private Integer x;
        private Integer y;
        private String assignmentType;
        private List<Long> approverUserIds;
        private List<Long> approverRoleIds;
        private String approvalMode;
        private Map<String, Object> config;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getNodeKey() { return nodeKey; }
        public void setNodeKey(String nodeKey) { this.nodeKey = nodeKey; }
        public String getNodeType() { return nodeType; }
        public void setNodeType(String nodeType) { this.nodeType = nodeType; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getX() { return x; }
        public void setX(Integer x) { this.x = x; }
        public Integer getY() { return y; }
        public void setY(Integer y) { this.y = y; }
        public String getAssignmentType() { return assignmentType; }
        public void setAssignmentType(String assignmentType) { this.assignmentType = assignmentType; }
        public List<Long> getApproverUserIds() { return approverUserIds; }
        public void setApproverUserIds(List<Long> approverUserIds) { this.approverUserIds = approverUserIds; }
        public List<Long> getApproverRoleIds() { return approverRoleIds; }
        public void setApproverRoleIds(List<Long> approverRoleIds) { this.approverRoleIds = approverRoleIds; }
        public String getApprovalMode() { return approvalMode; }
        public void setApprovalMode(String approvalMode) { this.approvalMode = approvalMode; }
        public Map<String, Object> getConfig() { return config; }
        public void setConfig(Map<String, Object> config) { this.config = config; }
    }

    public static class Edge {
        private Long id;
        private String edgeKey;
        private String sourceNodeKey;
        private String targetNodeKey;
        private String conditionExpression;
        private Integer sortOrder;
        private Map<String, Object> config;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getEdgeKey() { return edgeKey; }
        public void setEdgeKey(String edgeKey) { this.edgeKey = edgeKey; }
        public String getSourceNodeKey() { return sourceNodeKey; }
        public void setSourceNodeKey(String sourceNodeKey) { this.sourceNodeKey = sourceNodeKey; }
        public String getTargetNodeKey() { return targetNodeKey; }
        public void setTargetNodeKey(String targetNodeKey) { this.targetNodeKey = targetNodeKey; }
        public String getConditionExpression() { return conditionExpression; }
        public void setConditionExpression(String conditionExpression) { this.conditionExpression = conditionExpression; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
        public Map<String, Object> getConfig() { return config; }
        public void setConfig(Map<String, Object> config) { this.config = config; }
    }

    public static class Task {
        private Long id;
        private Long instanceId;
        private String businessType;
        private Long businessId;
        private String businessUuid;
        private String businessTitle;
        private String nodeKey;
        private String nodeName;
        private String status;
        private Long approverUserId;
        private String approverUserUuid;
        private Long approverRoleId;
        private LocalDateTime createdAt;
        private LocalDateTime completedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getInstanceId() { return instanceId; }
        public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }
        public String getBusinessType() { return businessType; }
        public void setBusinessType(String businessType) { this.businessType = businessType; }
        public Long getBusinessId() { return businessId; }
        public void setBusinessId(Long businessId) { this.businessId = businessId; }
        public String getBusinessUuid() { return businessUuid; }
        public void setBusinessUuid(String businessUuid) { this.businessUuid = businessUuid; }
        public String getBusinessTitle() { return businessTitle; }
        public void setBusinessTitle(String businessTitle) { this.businessTitle = businessTitle; }
        public String getNodeKey() { return nodeKey; }
        public void setNodeKey(String nodeKey) { this.nodeKey = nodeKey; }
        public String getNodeName() { return nodeName; }
        public void setNodeName(String nodeName) { this.nodeName = nodeName; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Long getApproverUserId() { return approverUserId; }
        public void setApproverUserId(Long approverUserId) { this.approverUserId = approverUserId; }
        public String getApproverUserUuid() { return approverUserUuid; }
        public void setApproverUserUuid(String approverUserUuid) { this.approverUserUuid = approverUserUuid; }
        public Long getApproverRoleId() { return approverRoleId; }
        public void setApproverRoleId(Long approverRoleId) { this.approverRoleId = approverRoleId; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getCompletedAt() { return completedAt; }
        public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    }

    public static class ActionLog {
        private Long id;
        private Long instanceId;
        private Long taskId;
        private String actionType;
        private String nodeKey;
        private String nodeName;
        private Long operatorUserId;
        private String operatorUserUuid;
        private String operatorUsername;
        private String comment;
        private LocalDateTime createdAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getInstanceId() { return instanceId; }
        public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }
        public Long getTaskId() { return taskId; }
        public void setTaskId(Long taskId) { this.taskId = taskId; }
        public String getActionType() { return actionType; }
        public void setActionType(String actionType) { this.actionType = actionType; }
        public String getNodeKey() { return nodeKey; }
        public void setNodeKey(String nodeKey) { this.nodeKey = nodeKey; }
        public String getNodeName() { return nodeName; }
        public void setNodeName(String nodeName) { this.nodeName = nodeName; }
        public Long getOperatorUserId() { return operatorUserId; }
        public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }
        public String getOperatorUserUuid() { return operatorUserUuid; }
        public void setOperatorUserUuid(String operatorUserUuid) { this.operatorUserUuid = operatorUserUuid; }
        public String getOperatorUsername() { return operatorUsername; }
        public void setOperatorUsername(String operatorUsername) { this.operatorUsername = operatorUsername; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }
}
