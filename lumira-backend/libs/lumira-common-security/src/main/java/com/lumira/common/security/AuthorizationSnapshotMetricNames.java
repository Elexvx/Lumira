package com.lumira.common.security;

/** Stable security-event metric names shared by auth and IAM runtime paths. */
public final class AuthorizationSnapshotMetricNames {

    public static final String AUTHZ_VERSION_STALE = "AUTHZ_VERSION_STALE";
    public static final String AUTHZ_VERSION_UNAVAILABLE = "AUTHZ_VERSION_UNAVAILABLE";
    public static final String AUTHZ_VERSION_REHYDRATE = "AUTHZ_VERSION_REHYDRATE";
    public static final String AUTHZ_SESSION_REVOKED = "AUTHZ_SESSION_REVOKED";

    private AuthorizationSnapshotMetricNames() {
    }
}
