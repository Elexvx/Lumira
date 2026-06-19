package com.lumira.saas.modules.system.verification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "saas.verification")
public class SystemVerificationProperties {

    private String issuer = "lumira";
    private int totpDigits = 6;
    private int totpStepSeconds = 30;
    private int bindChallengeExpireMinutes = 10;
    private int loginChallengeExpireMinutes = 5;
    private int recoveryCodeCount = 8;
    private int recoveryCodeLength = 8;
    private boolean exposeDebugCode = false;
    private boolean emailLoginEnabled = false;

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public int getTotpDigits() {
        return totpDigits;
    }

    public void setTotpDigits(int totpDigits) {
        this.totpDigits = totpDigits;
    }

    public int getTotpStepSeconds() {
        return totpStepSeconds;
    }

    public void setTotpStepSeconds(int totpStepSeconds) {
        this.totpStepSeconds = totpStepSeconds;
    }

    public int getBindChallengeExpireMinutes() {
        return bindChallengeExpireMinutes;
    }

    public void setBindChallengeExpireMinutes(int bindChallengeExpireMinutes) {
        this.bindChallengeExpireMinutes = bindChallengeExpireMinutes;
    }

    public int getLoginChallengeExpireMinutes() {
        return loginChallengeExpireMinutes;
    }

    public void setLoginChallengeExpireMinutes(int loginChallengeExpireMinutes) {
        this.loginChallengeExpireMinutes = loginChallengeExpireMinutes;
    }

    public int getRecoveryCodeCount() {
        return recoveryCodeCount;
    }

    public void setRecoveryCodeCount(int recoveryCodeCount) {
        this.recoveryCodeCount = recoveryCodeCount;
    }

    public int getRecoveryCodeLength() {
        return recoveryCodeLength;
    }

    public void setRecoveryCodeLength(int recoveryCodeLength) {
        this.recoveryCodeLength = recoveryCodeLength;
    }

    public boolean isExposeDebugCode() {
        return exposeDebugCode;
    }

    public void setExposeDebugCode(boolean exposeDebugCode) {
        this.exposeDebugCode = exposeDebugCode;
    }

    public boolean isEmailLoginEnabled() {
        return emailLoginEnabled;
    }

    public void setEmailLoginEnabled(boolean emailLoginEnabled) {
        this.emailLoginEnabled = emailLoginEnabled;
    }
}
