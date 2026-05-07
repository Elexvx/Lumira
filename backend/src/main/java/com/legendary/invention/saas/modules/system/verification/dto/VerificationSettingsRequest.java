package com.legendary.invention.saas.modules.system.verification.dto;

public class VerificationSettingsRequest {

    private Boolean enabled;
    private Boolean emailLoginEnabled;

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
}
