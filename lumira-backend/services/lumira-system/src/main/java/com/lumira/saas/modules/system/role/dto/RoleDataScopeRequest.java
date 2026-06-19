package com.lumira.saas.modules.system.role.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public class RoleDataScopeRequest {

    @NotBlank
    private String resourceCode;
    @NotBlank
    private String scopeType;
    private List<Long> customDeptIds;
    private List<Long> customUserIds;

    public String getResourceCode() {
        return resourceCode;
    }

    public void setResourceCode(String resourceCode) {
        this.resourceCode = resourceCode == null ? null : resourceCode.trim();
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType == null ? null : scopeType.trim();
    }

    public List<Long> getCustomDeptIds() {
        return customDeptIds;
    }

    public void setCustomDeptIds(List<Long> customDeptIds) {
        this.customDeptIds = customDeptIds;
    }

    public List<Long> getCustomUserIds() {
        return customUserIds;
    }

    public void setCustomUserIds(List<Long> customUserIds) {
        this.customUserIds = customUserIds;
    }
}
