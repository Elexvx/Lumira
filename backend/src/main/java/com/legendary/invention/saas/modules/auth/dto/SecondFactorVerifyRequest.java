package com.legendary.invention.saas.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class SecondFactorVerifyRequest {

    @NotBlank
    private String challengeId;

    @NotBlank
    private String verificationCode;

    public String getChallengeId() {
        return challengeId;
    }

    public void setChallengeId(String challengeId) {
        this.challengeId = challengeId;
    }

    public String getVerificationCode() {
        return verificationCode;
    }

    public void setVerificationCode(String verificationCode) {
        this.verificationCode = verificationCode;
    }
}
