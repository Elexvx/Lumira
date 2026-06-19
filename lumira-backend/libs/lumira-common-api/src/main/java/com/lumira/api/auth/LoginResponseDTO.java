package com.lumira.api.auth;

import java.util.List;

public class LoginResponseDTO {

    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long expiresIn;
    private AuthUserDTO user;
    private Boolean requiresSecondFactor;
    private List<SecondFactorOptionDTO> secondFactorOptions;
    private Boolean requiresCaptcha;
    private Boolean requiresPasswordChange;

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

    public AuthUserDTO getUser() {
        return user;
    }

    public void setUser(AuthUserDTO user) {
        this.user = user;
    }

    public Boolean getRequiresSecondFactor() {
        return requiresSecondFactor;
    }

    public void setRequiresSecondFactor(Boolean requiresSecondFactor) {
        this.requiresSecondFactor = requiresSecondFactor;
    }

    public List<SecondFactorOptionDTO> getSecondFactorOptions() {
        return secondFactorOptions;
    }

    public void setSecondFactorOptions(List<SecondFactorOptionDTO> secondFactorOptions) {
        this.secondFactorOptions = secondFactorOptions;
    }

    public Boolean getRequiresCaptcha() {
        return requiresCaptcha;
    }

    public void setRequiresCaptcha(Boolean requiresCaptcha) {
        this.requiresCaptcha = requiresCaptcha;
    }

    public Boolean getRequiresPasswordChange() {
        return requiresPasswordChange;
    }

    public void setRequiresPasswordChange(Boolean requiresPasswordChange) {
        this.requiresPasswordChange = requiresPasswordChange;
    }

    public static class SecondFactorOptionDTO {
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

    public static class SecondFactorOptionVO extends SecondFactorOptionDTO {
    }
}
