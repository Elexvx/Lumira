package com.yourcompany.saas.modules.auth.vo;

import com.yourcompany.saas.modules.tenant.vo.MyTenantVO;
import com.yourcompany.saas.modules.tenant.vo.TenantSummaryVO;

import java.util.List;

public class LoginResponseVO {

    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long expiresIn;
    private AuthUserVO user;
    private List<MyTenantVO> tenants;
    private TenantSummaryVO currentTenant;

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
}
