package com.lumira.common.security;

import com.lumira.common.constant.PlatformConstants;

public final class PlatformContext {
    private PlatformContext() {
    }

    public static Long compatibilityTenantId() {
        return PlatformConstants.PLATFORM_TENANT_ID;
    }

    public static Long effectiveCompatibilityTenantId(Long requestedTenantId) {
        return requestedTenantId == null ? compatibilityTenantId() : requestedTenantId;
    }
}
