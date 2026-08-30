package com.lumira.common.security;

/**
 * Resolves a request-scoped user against the owning authentication runtime.
 *
 * <p>The returned user must reflect the current session, identity status, and
 * permission/data-scope snapshot. Domain modules depend on this port instead
 * of an owner module's concrete session or IAM services.</p>
 */
public interface TrustedCurrentUserResolver {

    /**
     * Resolves a trusted, current user for an already-authenticated request.
     * Implementations may reject revoked or unavailable credentials with a
     * business exception and may return {@code null} when trust cannot be
     * established.
     */
    CurrentUser resolve(CurrentUser currentUser);

    /**
     * Resolves an internal session ticket when the caller has the session
     * claims but does not carry a request {@link CurrentUser}. The default is
     * deliberately fail-closed so extracted modules cannot treat a raw ticket
     * as trusted unless its owning authentication runtime supports it.
     */
    default CurrentUser resolveSessionTicket(
            String sessionId,
            Long userId,
            String userUuid,
            Long simulatedRoleId,
            Integer sessionVersion,
            String permissionsVersion
    ) {
        return null;
    }
}
