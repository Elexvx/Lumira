package com.lumira.saas.modules.system.verification.vo;

public class SmsVerificationSettingsVO {

    private Boolean enabled;
    private String provider;
    private String signName;
    private String templateCode;
    private String accessKeyId;
    private String accessKeySecret;
    private String endpoint;
    private String region;
    private Boolean configured;
    private Boolean accessKeySecretConfigured;

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getSignName() { return signName; }
    public void setSignName(String signName) { this.signName = signName; }
    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }
    public String getAccessKeyId() { return accessKeyId; }
    public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }
    public String getAccessKeySecret() { return accessKeySecret; }
    public void setAccessKeySecret(String accessKeySecret) { this.accessKeySecret = accessKeySecret; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public Boolean getConfigured() { return configured; }
    public void setConfigured(Boolean configured) { this.configured = configured; }
    public Boolean getAccessKeySecretConfigured() { return accessKeySecretConfigured; }
    public void setAccessKeySecretConfigured(Boolean accessKeySecretConfigured) { this.accessKeySecretConfigured = accessKeySecretConfigured; }
}
