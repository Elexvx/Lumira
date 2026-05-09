package com.legendary.invention.saas.modules.approval.vo;

import java.time.LocalDateTime;
import java.util.List;

public final class ApprovalVO {
    private ApprovalVO() {
    }

    public static class TemplateVO {
        private Long id;
        private String templateName;
        private String businessType;
        private String description;
        private Boolean enabled;
        private LocalDateTime createTime;
        private List<NodeVO> nodes;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getTemplateName() { return templateName; }
        public void setTemplateName(String templateName) { this.templateName = templateName; }
        public String getBusinessType() { return businessType; }
        public void setBusinessType(String businessType) { this.businessType = businessType; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public LocalDateTime getCreateTime() { return createTime; }
        public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
        public List<NodeVO> getNodes() { return nodes; }
        public void setNodes(List<NodeVO> nodes) { this.nodes = nodes; }
    }

    public static class NodeVO {
        private Long id;
        private String nodeName;
        private Integer sortOrder;
        private String approvalPolicy;
        private String approverType;
        private Long approverId;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getNodeName() { return nodeName; }
        public void setNodeName(String nodeName) { this.nodeName = nodeName; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
        public String getApprovalPolicy() { return approvalPolicy; }
        public void setApprovalPolicy(String approvalPolicy) { this.approvalPolicy = approvalPolicy; }
        public String getApproverType() { return approverType; }
        public void setApproverType(String approverType) { this.approverType = approverType; }
        public Long getApproverId() { return approverId; }
        public void setApproverId(Long approverId) { this.approverId = approverId; }
    }

    public static class InstanceVO {
        private Long id;
        private Long templateId;
        private String businessType;
        private Long businessId;
        private String businessTitle;
        private String summary;
        private String payloadJson;
        private Long applicantId;
        private String applicantName;
        private String status;
        private Long currentNodeId;
        private LocalDateTime createTime;
        private List<TaskVO> tasks;
        private List<RecordVO> records;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getTemplateId() { return templateId; }
        public void setTemplateId(Long templateId) { this.templateId = templateId; }
        public String getBusinessType() { return businessType; }
        public void setBusinessType(String businessType) { this.businessType = businessType; }
        public Long getBusinessId() { return businessId; }
        public void setBusinessId(Long businessId) { this.businessId = businessId; }
        public String getBusinessTitle() { return businessTitle; }
        public void setBusinessTitle(String businessTitle) { this.businessTitle = businessTitle; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        public String getPayloadJson() { return payloadJson; }
        public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
        public Long getApplicantId() { return applicantId; }
        public void setApplicantId(Long applicantId) { this.applicantId = applicantId; }
        public String getApplicantName() { return applicantName; }
        public void setApplicantName(String applicantName) { this.applicantName = applicantName; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Long getCurrentNodeId() { return currentNodeId; }
        public void setCurrentNodeId(Long currentNodeId) { this.currentNodeId = currentNodeId; }
        public LocalDateTime getCreateTime() { return createTime; }
        public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
        public List<TaskVO> getTasks() { return tasks; }
        public void setTasks(List<TaskVO> tasks) { this.tasks = tasks; }
        public List<RecordVO> getRecords() { return records; }
        public void setRecords(List<RecordVO> records) { this.records = records; }
    }

    public static class TaskVO {
        private Long id;
        private Long instanceId;
        private Long nodeId;
        private Long assigneeUserId;
        private Long assigneeRoleId;
        private Long assigneeDeptId;
        private String status;
        private Long handledBy;
        private String handledComment;
        private LocalDateTime handledAt;
        private LocalDateTime createTime;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getInstanceId() { return instanceId; }
        public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }
        public Long getNodeId() { return nodeId; }
        public void setNodeId(Long nodeId) { this.nodeId = nodeId; }
        public Long getAssigneeUserId() { return assigneeUserId; }
        public void setAssigneeUserId(Long assigneeUserId) { this.assigneeUserId = assigneeUserId; }
        public Long getAssigneeRoleId() { return assigneeRoleId; }
        public void setAssigneeRoleId(Long assigneeRoleId) { this.assigneeRoleId = assigneeRoleId; }
        public Long getAssigneeDeptId() { return assigneeDeptId; }
        public void setAssigneeDeptId(Long assigneeDeptId) { this.assigneeDeptId = assigneeDeptId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Long getHandledBy() { return handledBy; }
        public void setHandledBy(Long handledBy) { this.handledBy = handledBy; }
        public String getHandledComment() { return handledComment; }
        public void setHandledComment(String handledComment) { this.handledComment = handledComment; }
        public LocalDateTime getHandledAt() { return handledAt; }
        public void setHandledAt(LocalDateTime handledAt) { this.handledAt = handledAt; }
        public LocalDateTime getCreateTime() { return createTime; }
        public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    }

    public static class RecordVO {
        private Long id;
        private Long instanceId;
        private Long taskId;
        private String action;
        private Long operatorId;
        private String operatorName;
        private String comment;
        private LocalDateTime createTime;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getInstanceId() { return instanceId; }
        public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }
        public Long getTaskId() { return taskId; }
        public void setTaskId(Long taskId) { this.taskId = taskId; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public Long getOperatorId() { return operatorId; }
        public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
        public String getOperatorName() { return operatorName; }
        public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
        public LocalDateTime getCreateTime() { return createTime; }
        public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    }
}
