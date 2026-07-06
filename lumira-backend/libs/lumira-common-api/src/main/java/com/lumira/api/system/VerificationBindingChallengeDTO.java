package com.lumira.api.system;

public class VerificationBindingChallengeDTO extends VerificationChallengeDTO {
    private String setupUri;
    private String setupSecret;

    public String getSetupUri() {
        return setupUri;
    }

    public void setSetupUri(String setupUri) {
        this.setupUri = setupUri;
    }

    public String getSetupSecret() {
        return setupSecret;
    }

    public void setSetupSecret(String setupSecret) {
        this.setupSecret = setupSecret;
    }
}
