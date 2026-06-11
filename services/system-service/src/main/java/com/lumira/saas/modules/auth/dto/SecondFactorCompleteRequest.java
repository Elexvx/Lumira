package com.lumira.saas.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class SecondFactorCompleteRequest {

    @NotBlank
    private String factorCode;

    @NotBlank
    private String challengeId;

    @NotBlank
    private String verificationCode;

    public String getFactorCode() {
        return factorCode;
    }

    public void setFactorCode(String factorCode) {
        this.factorCode = factorCode;
    }

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
