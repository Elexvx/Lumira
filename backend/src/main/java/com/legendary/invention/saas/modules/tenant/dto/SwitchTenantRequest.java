package com.legendary.invention.saas.modules.tenant.dto;

import jakarta.validation.constraints.NotNull;

public class SwitchTenantRequest {

    @NotNull(message = "tenantId不能为空")
    private Long tenantId;

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }
}
