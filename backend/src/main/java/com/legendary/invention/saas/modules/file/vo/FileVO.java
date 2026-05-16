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
        private String storageType;
        private String bucket;
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

        public String getStorageType() {
            return storageType;
        }

        public void setStorageType(String storageType) {
            this.storageType = storageType;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
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

    public static class StorageSpaceVO {
        private Long id;
        private Long tenantId;
        private String title;
        private String storageKey;
        private String provider;
        private String rootPath;
        private String bucketName;
        private String endpoint;
        private String region;
        private String accessKeyId;
        private Boolean secretConfigured;
        private String renameStrategy;
        private Integer maxFileSizeMb;
        private String allowedMimeTypes;
        private Boolean defaultStorage;
        private Boolean retainFileOnRecordDelete;
        private String status;
        private Long fileCount;
        private Long totalSizeBytes;
        private String totalSizeLabel;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getTenantId() { return tenantId; }
        public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getStorageKey() { return storageKey; }
        public void setStorageKey(String storageKey) { this.storageKey = storageKey; }
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getRootPath() { return rootPath; }
        public void setRootPath(String rootPath) { this.rootPath = rootPath; }
        public String getBucketName() { return bucketName; }
        public void setBucketName(String bucketName) { this.bucketName = bucketName; }
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
        public String getAccessKeyId() { return accessKeyId; }
        public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }
        public Boolean getSecretConfigured() { return secretConfigured; }
        public void setSecretConfigured(Boolean secretConfigured) { this.secretConfigured = secretConfigured; }
        public String getRenameStrategy() { return renameStrategy; }
        public void setRenameStrategy(String renameStrategy) { this.renameStrategy = renameStrategy; }
        public Integer getMaxFileSizeMb() { return maxFileSizeMb; }
        public void setMaxFileSizeMb(Integer maxFileSizeMb) { this.maxFileSizeMb = maxFileSizeMb; }
        public String getAllowedMimeTypes() { return allowedMimeTypes; }
        public void setAllowedMimeTypes(String allowedMimeTypes) { this.allowedMimeTypes = allowedMimeTypes; }
        public Boolean getDefaultStorage() { return defaultStorage; }
        public void setDefaultStorage(Boolean defaultStorage) { this.defaultStorage = defaultStorage; }
        public Boolean getRetainFileOnRecordDelete() { return retainFileOnRecordDelete; }
        public void setRetainFileOnRecordDelete(Boolean retainFileOnRecordDelete) { this.retainFileOnRecordDelete = retainFileOnRecordDelete; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Long getFileCount() { return fileCount; }
        public void setFileCount(Long fileCount) { this.fileCount = fileCount; }
        public Long getTotalSizeBytes() { return totalSizeBytes; }
        public void setTotalSizeBytes(Long totalSizeBytes) { this.totalSizeBytes = totalSizeBytes; }
        public String getTotalSizeLabel() { return totalSizeLabel; }
        public void setTotalSizeLabel(String totalSizeLabel) { this.totalSizeLabel = totalSizeLabel; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }
}
