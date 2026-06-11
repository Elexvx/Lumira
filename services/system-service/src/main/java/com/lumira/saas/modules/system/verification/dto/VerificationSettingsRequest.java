package com.lumira.saas.modules.system.verification.dto;

import java.util.List;

public class VerificationSettingsRequest {

    private Boolean enabled;
    private Boolean emailLoginEnabled;
    private Boolean passwordLoginEnabled;
    private List<String> loginModeOrder;

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getEmailLoginEnabled() {
        return emailLoginEnabled;
    }

    public void setEmailLoginEnabled(Boolean emailLoginEnabled) {
        this.emailLoginEnabled = emailLoginEnabled;
    }

    public Boolean getPasswordLoginEnabled() {
        return passwordLoginEnabled;
    }

    public void setPasswordLoginEnabled(Boolean passwordLoginEnabled) {
        this.passwordLoginEnabled = passwordLoginEnabled;
    }

    public List<String> getLoginModeOrder() {
        return loginModeOrder;
    }

    public void setLoginModeOrder(List<String> loginModeOrder) {
        this.loginModeOrder = loginModeOrder;
    }
}
