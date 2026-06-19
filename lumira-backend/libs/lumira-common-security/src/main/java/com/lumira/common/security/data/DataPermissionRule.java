package com.lumira.common.security.data;

import java.util.List;

public record DataPermissionRule(
        String resourceCode,
        DataScopeType scopeType,
        List<Long> customDeptIds,
        List<Long> customUserIds
) {
    public DataPermissionRule {
        resourceCode = resourceCode == null || resourceCode.isBlank() ? "*" : resourceCode.trim();
        scopeType = scopeType == null ? DataScopeType.SELF : scopeType;
        customDeptIds = customDeptIds == null ? List.of() : List.copyOf(customDeptIds);
        customUserIds = customUserIds == null ? List.of() : List.copyOf(customUserIds);
    }

    public boolean matches(String requestedResourceCode) {
        if ("*".equals(resourceCode)) {
            return true;
        }
        if (requestedResourceCode == null || requestedResourceCode.isBlank()) {
            return false;
        }
        return resourceCode.equalsIgnoreCase(requestedResourceCode.trim());
    }
}
