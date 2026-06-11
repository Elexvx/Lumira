package com.lumira.api.system;

public class VerificationProviderDTO {
    private String factorCode;
    private String factorName;
    private boolean enabled;
    private boolean bound;
    private String status;
    private String promptMessage;
    private String maskedContact;
    private Long expiresInSeconds;
    private String challengeId;
    private String debugCode;

    public String getFactorCode() {
        return factorCode;
    }

    public void setFactorCode(String factorCode) {
        this.factorCode = factorCode;
    }

    public String getFactorName() {
        return factorName;
    }

    public void setFactorName(String factorName) {
        this.factorName = factorName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isBound() {
        return bound;
    }

    public void setBound(boolean bound) {
        this.bound = bound;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPromptMessage() {
        return promptMessage;
    }

    public void setPromptMessage(String promptMessage) {
        this.promptMessage = promptMessage;
    }

    public String getMaskedContact() {
        return maskedContact;
    }

    public void setMaskedContact(String maskedContact) {
        this.maskedContact = maskedContact;
    }

    public Long getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public void setExpiresInSeconds(Long expiresInSeconds) {
        this.expiresInSeconds = expiresInSeconds;
    }

    public String getChallengeId() {
        return challengeId;
    }

    public void setChallengeId(String challengeId) {
        this.challengeId = challengeId;
    }

    public String getDebugCode() {
        return debugCode;
    }

    public void setDebugCode(String debugCode) {
        this.debugCode = debugCode;
    }
}
