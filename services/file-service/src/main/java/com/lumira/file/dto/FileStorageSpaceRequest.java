package com.lumira.file.dto;

public class FileStorageSpaceRequest {
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
    private Boolean defaultStorage;
    private Boolean retainFileOnRecordDelete;
    private Boolean anonymousAccessAllowed;
    private String status;

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
    public String getAccessKeySecret() { return accessKeySecret; }
    public void setAccessKeySecret(String accessKeySecret) { this.accessKeySecret = accessKeySecret; }
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
    public Boolean getAnonymousAccessAllowed() { return anonymousAccessAllowed; }
    public void setAnonymousAccessAllowed(Boolean anonymousAccessAllowed) { this.anonymousAccessAllowed = anonymousAccessAllowed; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public static class TestResult {
        private String provider;
        private String status;
        private String message;
        private Long responseTimeMs;

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public Long getResponseTimeMs() { return responseTimeMs; }
        public void setResponseTimeMs(Long responseTimeMs) { this.responseTimeMs = responseTimeMs; }
    }
}
