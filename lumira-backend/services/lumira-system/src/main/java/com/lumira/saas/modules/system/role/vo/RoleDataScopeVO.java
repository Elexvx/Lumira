package com.lumira.saas.modules.system.role.vo;

import java.util.List;

public class RoleDataScopeVO {

    private String resourceCode;
    private String scopeType;
    private List<Long> customDeptIds;
    private List<Long> customUserIds;

    public String getResourceCode() {
        return resourceCode;
    }

    public void setResourceCode(String resourceCode) {
        this.resourceCode = resourceCode;
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
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
