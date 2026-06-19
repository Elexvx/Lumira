package com.lumira.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "saas.upload")
public class UploadProperties {

    private static final String DEFAULT_STORAGE_ROOT = "storage/uploads";
    private static final String DEFAULT_PUBLIC_PATH = "/api/uploads";

    private String storageRoot = "storage/uploads";
    private String publicPath = "/api/uploads";
    private long maxImageSizeBytes = 5L * 1024 * 1024;
    private long maxDocumentSizeBytes = 50L * 1024 * 1024;

    public String getStorageRoot() {
        return hasText(storageRoot) ? storageRoot : DEFAULT_STORAGE_ROOT;
    }

    public void setStorageRoot(String storageRoot) {
        this.storageRoot = storageRoot;
    }

    public String getPublicPath() {
        return hasText(publicPath) ? publicPath : DEFAULT_PUBLIC_PATH;
    }

    public void setPublicPath(String publicPath) {
        this.publicPath = publicPath;
    }

    public long getMaxImageSizeBytes() {
        return maxImageSizeBytes;
    }

    public void setMaxImageSizeBytes(long maxImageSizeBytes) {
        this.maxImageSizeBytes = maxImageSizeBytes;
    }

    public long getMaxDocumentSizeBytes() {
        return maxDocumentSizeBytes;
    }

    public void setMaxDocumentSizeBytes(long maxDocumentSizeBytes) {
        this.maxDocumentSizeBytes = maxDocumentSizeBytes;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
