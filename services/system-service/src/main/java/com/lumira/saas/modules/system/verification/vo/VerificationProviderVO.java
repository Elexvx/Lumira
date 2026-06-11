package com.lumira.saas.modules.system.verification.vo;

public class VerificationProviderVO {

    private String factorCode;
    private String factorName;
    private Boolean systemEnabled;
    private Boolean enabled;
    private Boolean bound;
    private Boolean emailRequired;
    private Boolean mobileRequired;
    private String maskedContact;
    private String statusMessage;

    public String getFactorCode() { return factorCode; }
    public void setFactorCode(String factorCode) { this.factorCode = factorCode; }
    public String getFactorName() { return factorName; }
    public void setFactorName(String factorName) { this.factorName = factorName; }
    public Boolean getSystemEnabled() { return systemEnabled; }
    public void setSystemEnabled(Boolean systemEnabled) { this.systemEnabled = systemEnabled; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Boolean getBound() { return bound; }
    public void setBound(Boolean bound) { this.bound = bound; }
    public Boolean getEmailRequired() { return emailRequired; }
    public void setEmailRequired(Boolean emailRequired) { this.emailRequired = emailRequired; }
    public Boolean getMobileRequired() { return mobileRequired; }
    public void setMobileRequired(Boolean mobileRequired) { this.mobileRequired = mobileRequired; }
    public String getMaskedContact() { return maskedContact; }
    public void setMaskedContact(String maskedContact) { this.maskedContact = maskedContact; }
    public String getStatusMessage() { return statusMessage; }
    public void setStatusMessage(String statusMessage) { this.statusMessage = statusMessage; }
}
