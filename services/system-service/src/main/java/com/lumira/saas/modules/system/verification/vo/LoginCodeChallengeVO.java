package com.lumira.saas.modules.system.verification.vo;

public class LoginCodeChallengeVO {

    private String loginType;
    private String factorName;
    private String challengeId;
    private String maskedContact;
    private String promptMessage;
    private Long expiresInSeconds;
    private Long cooldownSeconds;
    private String debugCode;

    public String getLoginType() { return loginType; }
    public void setLoginType(String loginType) { this.loginType = loginType; }
    public String getFactorName() { return factorName; }
    public void setFactorName(String factorName) { this.factorName = factorName; }
    public String getChallengeId() { return challengeId; }
    public void setChallengeId(String challengeId) { this.challengeId = challengeId; }
    public String getMaskedContact() { return maskedContact; }
    public void setMaskedContact(String maskedContact) { this.maskedContact = maskedContact; }
    public String getPromptMessage() { return promptMessage; }
    public void setPromptMessage(String promptMessage) { this.promptMessage = promptMessage; }
    public Long getExpiresInSeconds() { return expiresInSeconds; }
    public void setExpiresInSeconds(Long expiresInSeconds) { this.expiresInSeconds = expiresInSeconds; }
    public Long getCooldownSeconds() { return cooldownSeconds; }
    public void setCooldownSeconds(Long cooldownSeconds) { this.cooldownSeconds = cooldownSeconds; }
    public String getDebugCode() { return debugCode; }
    public void setDebugCode(String debugCode) { this.debugCode = debugCode; }
}
