package com.yourcompany.saas.modules.tenant.vo;

public class TenantSummaryVO {

    private Long tenantId;
    private String tenantCode;
    private String tenantName;
    private String tenantShortName;
    private String status;

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public String getTenantShortName() {
        return tenantShortName;
    }

    public void setTenantShortName(String tenantShortName) {
        this.tenantShortName = tenantShortName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
