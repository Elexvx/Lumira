package com.lumira.saas.modules.system.verification.vo;

import java.util.List;

public class VerificationChallengeVO {

    private String factorCode;
    private String factorName;
    private String challengeId;
    private String maskedContact;
    private String promptMessage;
    private String setupUri;
    private String setupSecret;
    private List<String> recoveryCodes;
    private String debugCode;

    public String getFactorCode() { return factorCode; }
    public void setFactorCode(String factorCode) { this.factorCode = factorCode; }
    public String getFactorName() { return factorName; }
    public void setFactorName(String factorName) { this.factorName = factorName; }
    public String getChallengeId() { return challengeId; }
    public void setChallengeId(String challengeId) { this.challengeId = challengeId; }
    public String getMaskedContact() { return maskedContact; }
    public void setMaskedContact(String maskedContact) { this.maskedContact = maskedContact; }
    public String getPromptMessage() { return promptMessage; }
    public void setPromptMessage(String promptMessage) { this.promptMessage = promptMessage; }
    public String getSetupUri() { return setupUri; }
    public void setSetupUri(String setupUri) { this.setupUri = setupUri; }
    public String getSetupSecret() { return setupSecret; }
    public void setSetupSecret(String setupSecret) { this.setupSecret = setupSecret; }
    public List<String> getRecoveryCodes() { return recoveryCodes; }
    public void setRecoveryCodes(List<String> recoveryCodes) { this.recoveryCodes = recoveryCodes; }
    public String getDebugCode() { return debugCode; }
    public void setDebugCode(String debugCode) { this.debugCode = debugCode; }
}
