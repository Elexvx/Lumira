package com.yourcompany.saas.modules.tenant.vo;

public class CurrentTenantVO {

    private Boolean hasCurrentTenant;
    private TenantSummaryVO currentTenant;

    public Boolean getHasCurrentTenant() {
        return hasCurrentTenant;
    }

    public void setHasCurrentTenant(Boolean hasCurrentTenant) {
        this.hasCurrentTenant = hasCurrentTenant;
    }

    public TenantSummaryVO getCurrentTenant() {
        return currentTenant;
    }

    public void setCurrentTenant(TenantSummaryVO currentTenant) {
        this.currentTenant = currentTenant;
    }
}
