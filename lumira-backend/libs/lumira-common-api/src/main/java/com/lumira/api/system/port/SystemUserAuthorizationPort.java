package com.lumira.api.system.port;

/**
 * Narrow capability set for business modules that need a user snapshot and
 * an authorization snapshot for the same request.
 */
public interface SystemUserAuthorizationPort extends UserIdentityQueryPort, PermissionSnapshotPort {
}
