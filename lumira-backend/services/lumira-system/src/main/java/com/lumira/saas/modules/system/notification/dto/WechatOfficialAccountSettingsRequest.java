package com.lumira.saas.modules.system.notification.dto;

public class WechatOfficialAccountSettingsRequest {

    private Boolean enabled;
    private String appId;
    private String appSecret;
    private String templateId;
    private String detailUrl;

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
}
