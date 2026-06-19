package com.lumira.saas.modules.auth.vo;

import java.util.List;

public class LoginResponseVO {

    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long expiresIn;
    private Boolean requiresSecondFactor;
    private List<SecondFactorOptionVO> secondFactorOptions;
    private Boolean requiresCaptcha;

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public Long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(Long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public Boolean getRequiresSecondFactor() {
        return requiresSecondFactor;
    }

    public void setRequiresSecondFactor(Boolean requiresSecondFactor) {
        this.requiresSecondFactor = requiresSecondFactor;
    }

    public List<SecondFactorOptionVO> getSecondFactorOptions() {
        return secondFactorOptions;
    }

    public void setSecondFactorOptions(List<SecondFactorOptionVO> secondFactorOptions) {
        this.secondFactorOptions = secondFactorOptions;
    }

    public Boolean getRequiresCaptcha() {
        return requiresCaptcha;
    }

    public void setRequiresCaptcha(Boolean requiresCaptcha) {
        this.requiresCaptcha = requiresCaptcha;
    }

    public static class SecondFactorOptionVO {
        private String factorCode;
        private String factorName;
        private String challengeId;
        private String maskedContact;
        private String promptMessage;

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
    }
}
