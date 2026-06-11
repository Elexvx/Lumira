package com.lumira.saas.modules.plugin.runtime;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "saas.plugin")
public class PluginProperties {

    private String platformVersion = "0.1.0";
    private String apiVersion = "1.0.0";
    private String storageRoot = "storage/plugins";
    private String stagingRoot = "storage/plugin-staging";
    private String signatureSecret = "";
    private String sharedDepsVersion = "1";
    private long maxGatewayBodyBytes = 1024 * 1024;
    private boolean requireHttpPermission = true;

    public String getPlatformVersion() {
        return platformVersion;
    }

    public void setPlatformVersion(String platformVersion) {
        this.platformVersion = platformVersion;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getStorageRoot() {
        return storageRoot;
    }

    public void setStorageRoot(String storageRoot) {
        this.storageRoot = storageRoot;
    }

    public String getStagingRoot() {
        return stagingRoot;
    }

    public void setStagingRoot(String stagingRoot) {
        this.stagingRoot = stagingRoot;
    }

    public String getSignatureSecret() {
        return signatureSecret;
    }

    public void setSignatureSecret(String signatureSecret) {
        this.signatureSecret = signatureSecret;
    }

    public String getSharedDepsVersion() {
        return sharedDepsVersion;
    }

    public void setSharedDepsVersion(String sharedDepsVersion) {
        this.sharedDepsVersion = sharedDepsVersion;
    }

    public long getMaxGatewayBodyBytes() {
        return maxGatewayBodyBytes;
    }

    public void setMaxGatewayBodyBytes(long maxGatewayBodyBytes) {
        this.maxGatewayBodyBytes = maxGatewayBodyBytes;
    }

    public boolean isRequireHttpPermission() {
        return requireHttpPermission;
    }

    public void setRequireHttpPermission(boolean requireHttpPermission) {
        this.requireHttpPermission = requireHttpPermission;
    }
}
