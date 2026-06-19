package com.lumira.saas.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class LoginCodeCompleteRequest {

    @NotBlank(message = "验证码会话不能为空")
    private String challengeId;

    @NotBlank(message = "验证码不能为空")
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
