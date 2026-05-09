package com.legendary.invention.saas.modules.approval.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public final class ApprovalDTO {
    private ApprovalDTO() {
    }

    public static class TemplateRequest {
        @NotBlank
        private String templateName;
        @NotBlank
        private String businessType;
        private String description;
        @Valid
        @NotEmpty
        private List<NodeRequest> nodes;

        public String getTemplateName() { return templateName; }
        public void setTemplateName(String templateName) { this.templateName = templateName; }
        public String getBusinessType() { return businessType; }
        public void setBusinessType(String businessType) { this.businessType = businessType; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public List<NodeRequest> getNodes() { return nodes; }
        public void setNodes(List<NodeRequest> nodes) { this.nodes = nodes; }
    }

    public static class NodeRequest {
        @NotBlank
        private String nodeName;
        private Integer sortOrder;
        private String approvalPolicy = "ANY_ONE";
        @NotBlank
        private String approverType;
        @NotNull
        private Long approverId;

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

    public static class EnabledRequest {
        private boolean enabled;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public static class InstanceCreateRequest {
        @NotBlank
        private String businessType;
        private Long businessId;
        @NotBlank
        private String businessTitle;
        private String summary;
        private String payloadJson;

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
    }

    public static class HandleTaskRequest {
        private String comment;
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
    }
}
