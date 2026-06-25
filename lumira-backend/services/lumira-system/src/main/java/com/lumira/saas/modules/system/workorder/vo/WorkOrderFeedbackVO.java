package com.lumira.saas.modules.system.workorder.vo;

public class WorkOrderFeedbackVO {

    public static class WorkOrderRecord {
        private Long id;
        private String title;
        private String detailHtml;
        private String priority;
        private String status;
        private Long submitterId;
        private String submitterName;
        private String adminReply;
        private Long handledBy;
        private String handledAt;
        private String createdAt;
        private String updatedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDetailHtml() { return detailHtml; }
        public void setDetailHtml(String detailHtml) { this.detailHtml = detailHtml; }
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Long getSubmitterId() { return submitterId; }
        public void setSubmitterId(Long submitterId) { this.submitterId = submitterId; }
        public String getSubmitterName() { return submitterName; }
        public void setSubmitterName(String submitterName) { this.submitterName = submitterName; }
        public String getAdminReply() { return adminReply; }
        public void setAdminReply(String adminReply) { this.adminReply = adminReply; }
        public Long getHandledBy() { return handledBy; }
        public void setHandledBy(Long handledBy) { this.handledBy = handledBy; }
        public String getHandledAt() { return handledAt; }
        public void setHandledAt(String handledAt) { this.handledAt = handledAt; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public String getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    }
}
