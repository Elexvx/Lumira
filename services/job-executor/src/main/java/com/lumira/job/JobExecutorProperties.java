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
}
