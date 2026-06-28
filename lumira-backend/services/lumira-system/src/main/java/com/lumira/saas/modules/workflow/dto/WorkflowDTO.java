package com.lumira.saas.modules.workflow.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public final class WorkflowDTO {
    private WorkflowDTO() {
    }

    public static class DefinitionSaveRequest {
        @NotBlank
        @Size(max = 128)
        private String name;
        @Valid
        @NotEmpty
        private List<NodeRequest> nodes;
        @Valid
        private List<EdgeRequest> edges;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public List<NodeRequest> getNodes() { return nodes; }
        public void setNodes(List<NodeRequest> nodes) { this.nodes = nodes; }
        public List<EdgeRequest> getEdges() { return edges; }
        public void setEdges(List<EdgeRequest> edges) { this.edges = edges; }
    }

    public static class NodeRequest {
        @NotBlank
        @Size(max = 64)
        private String nodeKey;
        @NotBlank
        @Size(max = 32)
        private String nodeType;
        @NotBlank
        @Size(max = 128)
        private String name;
        private Integer x;
        private Integer y;
        private String assignmentType;
        private List<Long> approverUserIds;
        private List<Long> approverRoleIds;
        private String approvalMode;
        private Map<String, Object> config;

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

    public static class EdgeRequest {
        @NotBlank
        @Size(max = 64)
        private String edgeKey;
        @NotBlank
        @Size(max = 64)
        private String sourceNodeKey;
        @NotBlank
        @Size(max = 64)
        private String targetNodeKey;
        private String conditionExpression;
        private Integer sortOrder;
        private Map<String, Object> config;

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

    public static class WorkflowActionRequest {
        @Size(max = 500)
        private String comment;

        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
    }
}
