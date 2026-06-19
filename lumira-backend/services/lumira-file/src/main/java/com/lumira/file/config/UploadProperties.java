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
    private int zipMaxEntries = 1000;
    private long zipMaxUncompressedBytes = 100L * 1024 * 1024;
    private long zipMaxSingleEntryBytes = 20L * 1024 * 1024;
    private long zipMaxCompressionRatio = 100L;

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

    public int getZipMaxEntries() {
        return zipMaxEntries;
    }

    public void setZipMaxEntries(int zipMaxEntries) {
        this.zipMaxEntries = zipMaxEntries;
    }

    public long getZipMaxUncompressedBytes() {
        return zipMaxUncompressedBytes;
    }

    public void setZipMaxUncompressedBytes(long zipMaxUncompressedBytes) {
        this.zipMaxUncompressedBytes = zipMaxUncompressedBytes;
    }

    public long getZipMaxSingleEntryBytes() {
        return zipMaxSingleEntryBytes;
    }

    public void setZipMaxSingleEntryBytes(long zipMaxSingleEntryBytes) {
        this.zipMaxSingleEntryBytes = zipMaxSingleEntryBytes;
    }

    public long getZipMaxCompressionRatio() {
        return zipMaxCompressionRatio;
    }

    public void setZipMaxCompressionRatio(long zipMaxCompressionRatio) {
        this.zipMaxCompressionRatio = zipMaxCompressionRatio;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
