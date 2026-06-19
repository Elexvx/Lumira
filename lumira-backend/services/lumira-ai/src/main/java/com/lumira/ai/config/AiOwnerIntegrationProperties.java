package com.lumira.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lumira.ai.owner-integrations")
public class AiOwnerIntegrationProperties {

    private String internalToken;
    private OwnerEndpoint iam = new OwnerEndpoint();
    private OwnerEndpoint platform = new OwnerEndpoint();
    private OwnerEndpoint file = new OwnerEndpoint();

    public String getInternalToken() {
        return internalToken;
    }

    public void setInternalToken(String internalToken) {
        this.internalToken = internalToken;
    }

    public OwnerEndpoint getIam() {
        return iam;
    }

    public void setIam(OwnerEndpoint iam) {
        this.iam = iam;
    }

    public OwnerEndpoint getPlatform() {
        return platform;
    }

    public void setPlatform(OwnerEndpoint platform) {
        this.platform = platform;
    }

    public OwnerEndpoint getFile() {
        return file;
    }

    public void setFile(OwnerEndpoint file) {
        this.file = file;
    }

    public static class OwnerEndpoint {
        private boolean enabled;
        private String baseUrl;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public boolean configured() {
            return enabled && baseUrl != null && !baseUrl.isBlank();
        }
    }
}
