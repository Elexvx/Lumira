package com.yourcompany.saas.common.constant;

public final class CacheKeyConstants {

    private CacheKeyConstants() {
    }

    public static final String PREFIX = "saas";
    public static final String SESSION = "session";
    public static final String TENANT_CONTEXT = "tenant_context";

    public static String tenantKey(String tenantId, String suffix) {
        return String.join(":", PREFIX, "tenant", tenantId, suffix);
    }

    public static String userKey(String tenantId, String userId, String suffix) {
        return String.join(":", PREFIX, "tenant", tenantId, "user", userId, suffix);
    }
}
