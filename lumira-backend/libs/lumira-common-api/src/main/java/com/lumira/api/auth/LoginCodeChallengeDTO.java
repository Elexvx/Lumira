package com.lumira.api.auth;

import com.lumira.api.system.MockSmsDeliveryDTO;

public class LoginCodeChallengeDTO {
    private String loginType;
    private String factorName;
    private String challengeId;
    private String maskedContact;
    private String promptMessage;
    private Long expiresInSeconds;
    private Long cooldownSeconds;
    private MockSmsDeliveryDTO mockSmsDelivery;

    public String getLoginType() {
        return loginType;
    }

    public void setLoginType(String loginType) {
        this.loginType = loginType;
    }

    public String getFactorName() {
        return factorName;
    }

    public void setFactorName(String factorName) {
        this.factorName = factorName;
    }

    public String getChallengeId() {
        return challengeId;
    }

    public void setChallengeId(String challengeId) {
        this.challengeId = challengeId;
    }

    public String getMaskedContact() {
        return maskedContact;
    }

    public void setMaskedContact(String maskedContact) {
        this.maskedContact = maskedContact;
    }

    public String getPromptMessage() {
        return promptMessage;
    }

    public void setPromptMessage(String promptMessage) {
        this.promptMessage = promptMessage;
    }

    public Long getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public void setExpiresInSeconds(Long expiresInSeconds) {
        this.expiresInSeconds = expiresInSeconds;
    }

    public Long getCooldownSeconds() {
        return cooldownSeconds;
    }

    public void setCooldownSeconds(Long cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }

    public MockSmsDeliveryDTO getMockSmsDelivery() {
        return mockSmsDelivery;
    }

    public void setMockSmsDelivery(MockSmsDeliveryDTO mockSmsDelivery) {
        this.mockSmsDelivery = mockSmsDelivery;
    }
}
