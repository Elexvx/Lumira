package com.lumira.saas.modules.auth.dto;

import jakarta.validation.constraints.Size;

public class SecondFactorBindRequest {

    @Size(max = 256)
    private String currentPassword;

    @Size(max = 16)
    private String currentFactorCode;

    @Size(max = 64)
    private String currentChallengeId;

    @Size(max = 64)
    private String currentVerificationCode;

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getCurrentFactorCode() {
        return currentFactorCode;
    }

    public void setCurrentFactorCode(String currentFactorCode) {
        this.currentFactorCode = currentFactorCode;
    }

    public String getCurrentChallengeId() {
        return currentChallengeId;
    }

    public void setCurrentChallengeId(String currentChallengeId) {
        this.currentChallengeId = currentChallengeId;
    }

    public String getCurrentVerificationCode() {
        return currentVerificationCode;
    }

    public void setCurrentVerificationCode(String currentVerificationCode) {
        this.currentVerificationCode = currentVerificationCode;
    }
}
