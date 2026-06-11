package com.lumira.saas.modules.system.verification.vo;

public class WechatLoginSettingsVO {

    private Boolean enabled;
    private String appId;
    private String appSecret;
    private String redirectUri;
    private Integer stateExpireMinutes;
    private Boolean configured;
    private Boolean appSecretConfigured;

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getAppSecret() { return appSecret; }
    public void setAppSecret(String appSecret) { this.appSecret = appSecret; }
    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }
    public Integer getStateExpireMinutes() { return stateExpireMinutes; }
    public void setStateExpireMinutes(Integer stateExpireMinutes) { this.stateExpireMinutes = stateExpireMinutes; }
    public Boolean getConfigured() { return configured; }
    public void setConfigured(Boolean configured) { this.configured = configured; }
    public Boolean getAppSecretConfigured() { return appSecretConfigured; }
    public void setAppSecretConfigured(Boolean appSecretConfigured) { this.appSecretConfigured = appSecretConfigured; }
}
