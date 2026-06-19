package com.lumira.saas.modules.system.verification.dto;

public class WechatLoginSettingsRequest {

    private Boolean enabled;
    private String appId;
    private String appSecret;
    private String redirectUri;
    private Integer stateExpireMinutes;

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
}
