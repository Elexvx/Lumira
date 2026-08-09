package com.lumira.common.security;

/**
 * Resolves a fresh trusted user snapshot for bounded asynchronous work.
 * The owner validates identity status, role simulation and permissions.
 */
public interface TrustedUserSnapshotResolver {

    CurrentUser resolve(
            Long userId,
            String userUuid,
            Long simulatedRoleId,
            String ephemeralSessionId,
            String requiredPermission
    );
}
