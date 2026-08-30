package com.lumira.saas.infrastructure.security.service;

import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.TrustedCurrentUserResolver;

/**
 * System-owned implementation of the cross-context trusted-user port.
 *
 * <p>It deliberately delegates to the existing session-ticket validation
 * path, so extracted modules retain revoked-session checks and refreshed IAM
 * snapshots without linking to system-service implementation types.</p>
 */
public class SessionTrustedCurrentUserResolver implements TrustedCurrentUserResolver {

    private final SessionAuthenticationService sessionAuthenticationService;

    public SessionTrustedCurrentUserResolver(SessionAuthenticationService sessionAuthenticationService) {
        this.sessionAuthenticationService = sessionAuthenticationService;
    }

    @Override
    public CurrentUser resolve(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            return currentUser;
        }
        return resolveSessionTicket(
                currentUser.getSessionId(),
                currentUser.getUserId(),
                currentUser.getUserUuid(),
                currentUser.getSimulatedRoleId(),
                currentUser.getSessionVersion(),
                currentUser.getPermissionsVersion()
        );
    }

    @Override
    public CurrentUser resolveSessionTicket(
            String sessionId,
            Long userId,
            String userUuid,
            Long simulatedRoleId,
            Integer sessionVersion,
            String permissionsVersion
    ) {
        SessionAuthenticationService.AuthenticatedAccess authenticatedAccess =
                sessionAuthenticationService.authenticateSessionTicket(
                        sessionId,
                        userId,
                        userUuid,
                        simulatedRoleId,
                        sessionVersion,
                        permissionsVersion
                );
        return authenticatedAccess == null ? null : authenticatedAccess.currentUser();
    }
}
