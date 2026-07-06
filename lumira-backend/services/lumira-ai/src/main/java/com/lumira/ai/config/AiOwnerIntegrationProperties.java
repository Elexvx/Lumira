package com.lumira.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import com.lumira.common.web.TrustedServiceBaseUrlValidator;

@ConfigurationProperties(prefix = "lumira.ai.owner-integrations")
public class AiOwnerIntegrationProperties {

    private String systemToken;
    private String authSystemToken;
    private String fileToken;
    private OwnerEndpoint iam = new OwnerEndpoint();
    private OwnerEndpoint platform = new OwnerEndpoint();
    private OwnerEndpoint file = new OwnerEndpoint();

    public String getSystemToken() {
        return systemToken;
    }

    public void setSystemToken(String systemToken) {
        this.systemToken = systemToken;
    }

    public String getAuthSystemToken() {
        return authSystemToken;
    }

    public void setAuthSystemToken(String authSystemToken) {
        this.authSystemToken = authSystemToken;
    }

    public String getFileToken() {
        return fileToken;
    }

    public void setFileToken(String fileToken) {
        this.fileToken = fileToken;
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
            return enabled && validBaseUrl(baseUrl);
        }

        private boolean validBaseUrl(String candidate) {
            try {
                TrustedServiceBaseUrlValidator.requireHttpBaseUrl(candidate, "lumira.ai.owner-integrations.base-url");
                return true;
            } catch (IllegalStateException exception) {
                return false;
            }
        }
    }
}
