package com.legendary.invention.saas.modules.auth.vo;

import com.legendary.invention.saas.modules.tenant.vo.MyTenantVO;
import com.legendary.invention.saas.modules.tenant.vo.TenantSummaryVO;

import java.util.List;

public class LoginResponseVO {

    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long expiresIn;
    private AuthUserVO user;
    private List<MyTenantVO> tenants;
    private TenantSummaryVO currentTenant;
    private Boolean requiresSecondFactor;
    private String secondFactorPluginCode;
    private String secondFactorPluginName;
    private String secondFactorChallengeId;
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

    public AuthUserVO getUser() {
        return user;
    }

    public void setUser(AuthUserVO user) {
        this.user = user;
    }

    public List<MyTenantVO> getTenants() {
        return tenants;
    }

    public void setTenants(List<MyTenantVO> tenants) {
        this.tenants = tenants;
    }

    public TenantSummaryVO getCurrentTenant() {
        return currentTenant;
    }

    public void setCurrentTenant(TenantSummaryVO currentTenant) {
        this.currentTenant = currentTenant;
    }

    public Boolean getRequiresSecondFactor() {
        return requiresSecondFactor;
    }

    public void setRequiresSecondFactor(Boolean requiresSecondFactor) {
        this.requiresSecondFactor = requiresSecondFactor;
    }

    public String getSecondFactorPluginCode() {
        return secondFactorPluginCode;
    }

    public void setSecondFactorPluginCode(String secondFactorPluginCode) {
        this.secondFactorPluginCode = secondFactorPluginCode;
    }

    public String getSecondFactorPluginName() {
        return secondFactorPluginName;
    }

    public void setSecondFactorPluginName(String secondFactorPluginName) {
        this.secondFactorPluginName = secondFactorPluginName;
    }

    public String getSecondFactorChallengeId() {
        return secondFactorChallengeId;
    }

    public void setSecondFactorChallengeId(String secondFactorChallengeId) {
        this.secondFactorChallengeId = secondFactorChallengeId;
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
        private String pluginCode;
        private String pluginName;
        private String factorCode;
        private String factorName;
        private String challengeId;
        private String maskedContact;
        private String promptMessage;

        public String getPluginCode() {
            return pluginCode;
        }

        public void setPluginCode(String pluginCode) {
            this.pluginCode = pluginCode;
        }

        public String getPluginName() {
            return pluginName;
        }

        public void setPluginName(String pluginName) {
            this.pluginName = pluginName;
        }

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
