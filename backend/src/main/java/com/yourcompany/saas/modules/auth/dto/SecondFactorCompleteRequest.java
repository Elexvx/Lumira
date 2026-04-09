package com.yourcompany.saas.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class SecondFactorCompleteRequest {

    @NotBlank
    private String pluginCode;

    @NotBlank
    private String challengeId;

    @NotBlank
    private String verificationCode;

    public String getPluginCode() {
        return pluginCode;
    }

    public void setPluginCode(String pluginCode) {
        this.pluginCode = pluginCode;
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
