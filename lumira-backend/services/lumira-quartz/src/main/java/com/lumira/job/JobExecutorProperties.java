package com.lumira.job;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "saas.job")
public class JobExecutorProperties {

    private String backendBaseUrl;
    private String messageServiceBaseUrl;
    private String fileServiceBaseUrl;
    private String paymentServiceBaseUrl;
    private String pluginServiceBaseUrl;
    private String internalToken;
    private AdaptiveRelay adaptiveRelay = new AdaptiveRelay();

    public String getBackendBaseUrl() {
        return backendBaseUrl;
    }

    public void setBackendBaseUrl(String backendBaseUrl) {
        this.backendBaseUrl = backendBaseUrl;
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

    public String getInternalToken() {
        return internalToken;
    }

    public void setInternalToken(String internalToken) {
        this.internalToken = internalToken;
    }

    public AdaptiveRelay getAdaptiveRelay() {
        return adaptiveRelay;
    }

    public void setAdaptiveRelay(AdaptiveRelay adaptiveRelay) {
        this.adaptiveRelay = adaptiveRelay;
    }

    public static class AdaptiveRelay {

        private boolean enabled;
        private long initialDelayMs = 5_000L;
        private long minDelayMs = 1_000L;
        private long maxDelayMs = 30_000L;
        private long failureDelayMs = 10_000L;
        private boolean platformEnabled = true;
        private boolean messageEnabled = true;
        private boolean fileEnabled = true;
        private boolean paymentEnabled = true;
        private boolean pluginEnabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getInitialDelayMs() {
            return initialDelayMs;
        }

        public void setInitialDelayMs(long initialDelayMs) {
            this.initialDelayMs = initialDelayMs;
        }

        public long getMinDelayMs() {
            return minDelayMs;
        }

        public void setMinDelayMs(long minDelayMs) {
            this.minDelayMs = minDelayMs;
        }

        public long getMaxDelayMs() {
            return maxDelayMs;
        }

        public void setMaxDelayMs(long maxDelayMs) {
            this.maxDelayMs = maxDelayMs;
        }

        public long getFailureDelayMs() {
            return failureDelayMs;
        }

        public void setFailureDelayMs(long failureDelayMs) {
            this.failureDelayMs = failureDelayMs;
        }

        public boolean isPlatformEnabled() {
            return platformEnabled;
        }

        public void setPlatformEnabled(boolean platformEnabled) {
            this.platformEnabled = platformEnabled;
        }

        public boolean isMessageEnabled() {
            return messageEnabled;
        }

        public void setMessageEnabled(boolean messageEnabled) {
            this.messageEnabled = messageEnabled;
        }

        public boolean isFileEnabled() {
            return fileEnabled;
        }

        public void setFileEnabled(boolean fileEnabled) {
            this.fileEnabled = fileEnabled;
        }

        public boolean isPaymentEnabled() {
            return paymentEnabled;
        }

        public void setPaymentEnabled(boolean paymentEnabled) {
            this.paymentEnabled = paymentEnabled;
        }

        public boolean isPluginEnabled() {
            return pluginEnabled;
        }

        public void setPluginEnabled(boolean pluginEnabled) {
            this.pluginEnabled = pluginEnabled;
        }
    }
}
