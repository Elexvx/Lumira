package com.legendary.invention.saas.modules.tenant.domain;

import com.legendary.invention.saas.modules.tenant.entity.TenantInfoEntity;

public class UserTenantAccess {

    private Long tenantId;
    private boolean isDefault;
    private TenantInfoEntity tenant;

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    public TenantInfoEntity getTenant() {
        return tenant;
    }

    public void setTenant(TenantInfoEntity tenant) {
        this.tenant = tenant;
    }
}
