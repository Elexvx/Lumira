package com.lumira.saas.modules.system.verification.vo;

import java.util.List;

public class PasskeySettingsVO {
    private Boolean enabled;
    private Boolean passwordlessEnabled;
    private Boolean selfBindingEnabled;
    private String rpId;
    private String rpName;
    private List<String> allowedOrigins;
    private Integer challengeTtlSeconds;

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Boolean getPasswordlessEnabled() { return passwordlessEnabled; }
    public void setPasswordlessEnabled(Boolean passwordlessEnabled) { this.passwordlessEnabled = passwordlessEnabled; }
    public Boolean getSelfBindingEnabled() { return selfBindingEnabled; }
    public void setSelfBindingEnabled(Boolean selfBindingEnabled) { this.selfBindingEnabled = selfBindingEnabled; }
    public String getRpId() { return rpId; }
    public void setRpId(String rpId) { this.rpId = rpId; }
    public String getRpName() { return rpName; }
    public void setRpName(String rpName) { this.rpName = rpName; }
    public List<String> getAllowedOrigins() { return allowedOrigins; }
    public void setAllowedOrigins(List<String> allowedOrigins) { this.allowedOrigins = allowedOrigins; }
    public Integer getChallengeTtlSeconds() { return challengeTtlSeconds; }
    public void setChallengeTtlSeconds(Integer challengeTtlSeconds) { this.challengeTtlSeconds = challengeTtlSeconds; }
}
