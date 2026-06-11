package com.lumira.saas.modules.system.verification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "saas.auth.wechat")
public class WechatLoginProperties {

    private boolean enabled = false;
    private String appId = "";
    private String appSecret = "";
    private String redirectUri = "";
    private int stateExpireMinutes = 10;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public int getStateExpireMinutes() {
        return stateExpireMinutes;
    }

    public void setStateExpireMinutes(int stateExpireMinutes) {
        this.stateExpireMinutes = stateExpireMinutes;
    }
}
