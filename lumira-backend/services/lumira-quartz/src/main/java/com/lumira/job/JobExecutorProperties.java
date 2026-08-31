package com.lumira.job;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "saas.job")
public class JobExecutorProperties {

    private String backendBaseUrl;
    private String systemServiceBaseUrl;
    private String messageServiceBaseUrl;
    private String fileServiceBaseUrl;
    private String paymentServiceBaseUrl;
    private String pluginServiceBaseUrl;
    private Internal internal = new Internal();
    private InternalHttp internalHttp = new InternalHttp();

    public String getBackendBaseUrl() {
        return backendBaseUrl;
    }

    public void setBackendBaseUrl(String backendBaseUrl) {
        this.backendBaseUrl = backendBaseUrl;
    }

    public String getSystemServiceBaseUrl() {
        return systemServiceBaseUrl;
    }

    public void setSystemServiceBaseUrl(String systemServiceBaseUrl) {
        this.systemServiceBaseUrl = systemServiceBaseUrl;
    }

    public String getMessageServiceBaseUrl() {
        return messageServiceBaseUrl;
    }

    public void setMessageServiceBaseUrl(String messageServiceBaseUrl) {
        this.messageServiceBaseUrl = messageServiceBaseUrl;
    }

    public String getFileServiceBaseUrl() {
        return fileServiceBaseUrl;
    }

    public void setFileServiceBaseUrl(String fileServiceBaseUrl) {
        this.fileServiceBaseUrl = fileServiceBaseUrl;
    }

    public String getPaymentServiceBaseUrl() {
        return paymentServiceBaseUrl;
    }

    public void setPaymentServiceBaseUrl(String paymentServiceBaseUrl) {
        this.paymentServiceBaseUrl = paymentServiceBaseUrl;
    }

    public String getPluginServiceBaseUrl() {
        return pluginServiceBaseUrl;
    }

    public void setPluginServiceBaseUrl(String pluginServiceBaseUrl) {
        this.pluginServiceBaseUrl = pluginServiceBaseUrl;
    }

    public Internal getInternal() {
        return internal;
    }

    public void setInternal(Internal internal) {
        this.internal = internal == null ? new Internal() : internal;
    }

    public InternalHttp getInternalHttp() { return internalHttp; }
    public void setInternalHttp(InternalHttp internalHttp) { this.internalHttp = internalHttp == null ? new InternalHttp() : internalHttp; }

    public static class InternalHttp {
        private java.time.Duration connectTimeout = java.time.Duration.ofSeconds(2);
        private java.time.Duration responseTimeout = java.time.Duration.ofSeconds(5);
        private java.time.Duration retryBackoff = java.time.Duration.ofMillis(100);
        private int maxResponseBytes = 1024 * 1024;
        private int maxAttempts = 2;
        private String releaseId = "unknown";
        private int schemaVersion = 1;

        public java.time.Duration getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(java.time.Duration connectTimeout) { this.connectTimeout = connectTimeout; }
        public java.time.Duration getResponseTimeout() { return responseTimeout; }
        public void setResponseTimeout(java.time.Duration responseTimeout) { this.responseTimeout = responseTimeout; }
        public java.time.Duration getRetryBackoff() { return retryBackoff; }
        public void setRetryBackoff(java.time.Duration retryBackoff) { this.retryBackoff = retryBackoff; }
        public int getMaxResponseBytes() { return maxResponseBytes; }
        public void setMaxResponseBytes(int maxResponseBytes) { this.maxResponseBytes = maxResponseBytes; }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public String getReleaseId() { return releaseId; }
        public void setReleaseId(String releaseId) { this.releaseId = releaseId; }
        public int getSchemaVersion() { return schemaVersion; }
        public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }
    }

    public static class Internal {
        private String fileToken;
        private String messageToken;
        private String paymentToken;
        private String pluginToken;
        private String jobToken;

        public String getFileToken() {
            return fileToken;
        }

        public void setFileToken(String fileToken) {
            this.fileToken = fileToken;
        }

        public String getMessageToken() {
            return messageToken;
        }

        public void setMessageToken(String messageToken) {
            this.messageToken = messageToken;
        }

        public String getPaymentToken() {
            return paymentToken;
        }

        public void setPaymentToken(String paymentToken) {
            this.paymentToken = paymentToken;
        }

        public String getPluginToken() {
            return pluginToken;
        }

        public void setPluginToken(String pluginToken) {
            this.pluginToken = pluginToken;
        }

        public String getJobToken() {
            return jobToken;
        }

        public void setJobToken(String jobToken) {
            this.jobToken = jobToken;
        }
    }
}
