package com.lumira.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("file_storage_space")
public class FileStorageSpaceEntity {

    @TableId(type = IdType.AUTO)
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
    private String accessKeySecret;
    private String renameStrategy;
    private Integer maxFileSizeMb;
    private String allowedMimeTypes;
    private Integer defaultFlag;
    private Integer retainFileOnRecordDelete;
    private Integer anonymousAccessAllowed;
    private String status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
    private Integer deleted;
    @TableField(exist = false)
    private Long fileCount;
    @TableField(exist = false)
    private Long totalSizeBytes;

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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getRootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public String getAccessKeySecret() {
        return accessKeySecret;
    }

    public void setAccessKeySecret(String accessKeySecret) {
        this.accessKeySecret = accessKeySecret;
    }

    public String getRenameStrategy() {
        return renameStrategy;
    }

    public void setRenameStrategy(String renameStrategy) {
        this.renameStrategy = renameStrategy;
    }

    public Integer getMaxFileSizeMb() {
        return maxFileSizeMb;
    }

    public void setMaxFileSizeMb(Integer maxFileSizeMb) {
        this.maxFileSizeMb = maxFileSizeMb;
    }

    public String getAllowedMimeTypes() {
        return allowedMimeTypes;
    }

    public void setAllowedMimeTypes(String allowedMimeTypes) {
        this.allowedMimeTypes = allowedMimeTypes;
    }

    public Integer getDefaultFlag() {
        return defaultFlag;
    }

    public void setDefaultFlag(Integer defaultFlag) {
        this.defaultFlag = defaultFlag;
    }

    public Integer getRetainFileOnRecordDelete() {
        return retainFileOnRecordDelete;
    }

    public void setRetainFileOnRecordDelete(Integer retainFileOnRecordDelete) {
        this.retainFileOnRecordDelete = retainFileOnRecordDelete;
    }

    public Integer getAnonymousAccessAllowed() {
        return anonymousAccessAllowed;
    }

    public void setAnonymousAccessAllowed(Integer anonymousAccessAllowed) {
        this.anonymousAccessAllowed = anonymousAccessAllowed;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public Long getFileCount() {
        return fileCount;
    }

    public void setFileCount(Long fileCount) {
        this.fileCount = fileCount;
    }

    public Long getTotalSizeBytes() {
        return totalSizeBytes;
    }

    public void setTotalSizeBytes(Long totalSizeBytes) {
        this.totalSizeBytes = totalSizeBytes;
    }
}
