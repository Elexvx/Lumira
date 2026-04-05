package com.yourcompany.saas.infrastructure.upload;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "saas.upload")
public class UploadProperties {

    private String storageRoot = "storage/uploads";
    private String publicPath = "/api/uploads";
    private long maxImageSizeBytes = 5L * 1024 * 1024;

    public String getStorageRoot() {
        return storageRoot;
    }

    public void setStorageRoot(String storageRoot) {
        this.storageRoot = storageRoot;
    }

    public String getPublicPath() {
        return publicPath;
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
}
