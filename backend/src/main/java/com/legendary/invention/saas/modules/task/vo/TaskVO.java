package com.legendary.invention.saas.modules.task.vo;

import java.time.LocalDateTime;
import java.util.List;

public final class TaskVO {
    private TaskVO() {
    }

    public static class TaskItemVO {
        private Long id;
        private String taskType;
        private String businessType;
        private Long businessId;
        private String businessTitle;
        private String title;
        private String description;
        private String status;
        private String sourceModule;
        private Long sourceTaskId;
        private String redirectUrl;
        private LocalDateTime dueTime;
        private LocalDateTime completedAt;
        private LocalDateTime createTime;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getTaskType() { return taskType; }
        public void setTaskType(String taskType) { this.taskType = taskType; }
        public String getBusinessType() { return businessType; }
        public void setBusinessType(String businessType) { this.businessType = businessType; }
        public Long getBusinessId() { return businessId; }
        public void setBusinessId(Long businessId) { this.businessId = businessId; }
        public String getBusinessTitle() { return businessTitle; }
        public void setBusinessTitle(String businessTitle) { this.businessTitle = businessTitle; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getSourceModule() { return sourceModule; }
        public void setSourceModule(String sourceModule) { this.sourceModule = sourceModule; }
        public Long getSourceTaskId() { return sourceTaskId; }
        public void setSourceTaskId(Long sourceTaskId) { this.sourceTaskId = sourceTaskId; }
        public String getRedirectUrl() { return redirectUrl; }
        public void setRedirectUrl(String redirectUrl) { this.redirectUrl = redirectUrl; }
        public LocalDateTime getDueTime() { return dueTime; }
        public void setDueTime(LocalDateTime dueTime) { this.dueTime = dueTime; }
        public LocalDateTime getCompletedAt() { return completedAt; }
        public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
        public LocalDateTime getCreateTime() { return createTime; }
        public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    }

    public static class TaskSummaryVO {
        private long pendingCount;
        private long approvalCount;
        private long evaluationCount;
        private long reviewCount;
        private List<TaskItemVO> latestPending;

        public long getPendingCount() { return pendingCount; }
        public void setPendingCount(long pendingCount) { this.pendingCount = pendingCount; }
        public long getApprovalCount() { return approvalCount; }
        public void setApprovalCount(long approvalCount) { this.approvalCount = approvalCount; }
        public long getEvaluationCount() { return evaluationCount; }
        public void setEvaluationCount(long evaluationCount) { this.evaluationCount = evaluationCount; }
        public long getReviewCount() { return reviewCount; }
        public void setReviewCount(long reviewCount) { this.reviewCount = reviewCount; }
        public List<TaskItemVO> getLatestPending() { return latestPending; }
        public void setLatestPending(List<TaskItemVO> latestPending) { this.latestPending = latestPending; }
    }
}
