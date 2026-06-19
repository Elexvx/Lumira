package com.lumira.saas.modules.system.export;

import java.time.LocalDateTime;

public final class ExportVO {
    private ExportVO() {
    }

    public static class ExportStartVO {
        private String mode;
        private Long taskId;
        private String fileName;
        private String contentType;
        private String contentBase64;
        private Long totalCount;

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public Long getTaskId() { return taskId; }
        public void setTaskId(Long taskId) { this.taskId = taskId; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getContentType() { return contentType; }
        public void setContentType(String contentType) { this.contentType = contentType; }
        public String getContentBase64() { return contentBase64; }
        public void setContentBase64(String contentBase64) { this.contentBase64 = contentBase64; }
        public Long getTotalCount() { return totalCount; }
        public void setTotalCount(Long totalCount) { this.totalCount = totalCount; }
    }

    public static class ExportTaskVO {
        private Long id;
        private String moduleKey;
        private String status;
        private Long totalCount;
        private Long fileId;
        private String fileName;
        private String downloadUrl;
        private String errorMessage;
        private LocalDateTime createdAt;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getModuleKey() { return moduleKey; }
        public void setModuleKey(String moduleKey) { this.moduleKey = moduleKey; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Long getTotalCount() { return totalCount; }
        public void setTotalCount(Long totalCount) { this.totalCount = totalCount; }
        public Long getFileId() { return fileId; }
        public void setFileId(Long fileId) { this.fileId = fileId; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getDownloadUrl() { return downloadUrl; }
        public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getStartedAt() { return startedAt; }
        public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
        public LocalDateTime getFinishedAt() { return finishedAt; }
        public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    }
}
