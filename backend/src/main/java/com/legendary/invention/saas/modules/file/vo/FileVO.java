package com.legendary.invention.saas.modules.file.vo;

import java.time.LocalDateTime;

public final class FileVO {

    private FileVO() {
    }

    public static class FileObjectVO {
        private Long id;
        private Long tenantId;
        private Long uploadedBy;
        private String uploadedByName;
        private String originalFileName;
        private String storedFileName;
        private String fileExtension;
        private String mimeType;
        private Long fileSizeBytes;
        private String fileSizeLabel;
        private String storagePath;
        private String publicUrl;
        private String previewUrl;
        private String downloadUrl;
        private String previewMode;
        private Boolean previewable;
        private String category;
        private String tags;
        private String remark;
        private String status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getTenantId() {
            return tenantId;
        }

        public void setTenantId(Long tenantId) {
            this.tenantId = tenantId;
        }

        public Long getUploadedBy() {
            return uploadedBy;
        }

        public void setUploadedBy(Long uploadedBy) {
            this.uploadedBy = uploadedBy;
        }

        public String getUploadedByName() {
            return uploadedByName;
        }

        public void setUploadedByName(String uploadedByName) {
            this.uploadedByName = uploadedByName;
        }

        public String getOriginalFileName() {
            return originalFileName;
        }

        public void setOriginalFileName(String originalFileName) {
            this.originalFileName = originalFileName;
        }

        public String getStoredFileName() {
            return storedFileName;
        }

        public void setStoredFileName(String storedFileName) {
            this.storedFileName = storedFileName;
        }

        public String getFileExtension() {
            return fileExtension;
        }

        public void setFileExtension(String fileExtension) {
            this.fileExtension = fileExtension;
        }

        public String getMimeType() {
            return mimeType;
        }

        public void setMimeType(String mimeType) {
            this.mimeType = mimeType;
        }

        public Long getFileSizeBytes() {
            return fileSizeBytes;
        }

        public void setFileSizeBytes(Long fileSizeBytes) {
            this.fileSizeBytes = fileSizeBytes;
        }

        public String getFileSizeLabel() {
            return fileSizeLabel;
        }

        public void setFileSizeLabel(String fileSizeLabel) {
            this.fileSizeLabel = fileSizeLabel;
        }

        public String getStoragePath() {
            return storagePath;
        }

        public void setStoragePath(String storagePath) {
            this.storagePath = storagePath;
        }

        public String getPublicUrl() {
            return publicUrl;
        }

        public void setPublicUrl(String publicUrl) {
            this.publicUrl = publicUrl;
        }

        public String getPreviewUrl() {
            return previewUrl;
        }

        public void setPreviewUrl(String previewUrl) {
            this.previewUrl = previewUrl;
        }

        public String getDownloadUrl() {
            return downloadUrl;
        }

        public void setDownloadUrl(String downloadUrl) {
            this.downloadUrl = downloadUrl;
        }

        public String getPreviewMode() {
            return previewMode;
        }

        public void setPreviewMode(String previewMode) {
            this.previewMode = previewMode;
        }

        public Boolean getPreviewable() {
            return previewable;
        }

        public void setPreviewable(Boolean previewable) {
            this.previewable = previewable;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getTags() {
            return tags;
        }

        public void setTags(String tags) {
            this.tags = tags;
        }

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }
    }
}
