package com.legendary.invention.saas.modules.tenant.vo;

public class SwitchTenantVO {

    private TenantSummaryVO currentTenant;
    private String accessToken;
    private String tokenType;
    private Long expiresIn;
    private Integer sessionVersion;
    private String permissionsVersion;

    public TenantSummaryVO getCurrentTenant() {
        return currentTenant;
    }

    public void setCurrentTenant(TenantSummaryVO currentTenant) {
        this.currentTenant = currentTenant;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
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

    public Integer getSessionVersion() {
        return sessionVersion;
    }

    public void setSessionVersion(Integer sessionVersion) {
        this.sessionVersion = sessionVersion;
    }

    public String getPermissionsVersion() {
        return permissionsVersion;
    }

    public void setPermissionsVersion(String permissionsVersion) {
        this.permissionsVersion = permissionsVersion;
    }
}
