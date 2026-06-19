package com.lumira.saas.modules.system.notification.vo;

public class WechatOfficialAccountSettingsVO {

    private Boolean enabled;
    private String appId;
    private String appSecret;
    private String templateId;
    private String detailUrl;
    private Boolean configured;
    private Boolean appSecretConfigured;

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getAppSecret() { return appSecret; }
    public void setAppSecret(String appSecret) { this.appSecret = appSecret; }
    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }
    public String getDetailUrl() { return detailUrl; }
    public void setDetailUrl(String detailUrl) { this.detailUrl = detailUrl; }
    public Boolean getConfigured() { return configured; }
    public void setConfigured(Boolean configured) { this.configured = configured; }
    public Boolean getAppSecretConfigured() { return appSecretConfigured; }
    public void setAppSecretConfigured(Boolean appSecretConfigured) { this.appSecretConfigured = appSecretConfigured; }
}
