package com.lumira.common.security;

/**
 * Verifies that an authorization snapshot version carried by a trusted session
 * is still current at the authoritative IAM boundary.
 *
 * <p>An implementation must return {@code false} for a definitive mismatch
 * and fail closed by throwing when it cannot determine the authoritative
 * version.</p>
 */
public interface AuthorizationSnapshotVersionVerifier {

    boolean isCurrent(String authorizationSnapshotVersion);
}
