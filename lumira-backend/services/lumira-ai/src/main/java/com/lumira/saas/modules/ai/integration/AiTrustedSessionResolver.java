package com.lumira.saas.modules.ai.integration;

import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.TrustedCurrentUserResolver;
import org.springframework.stereotype.Component;

/** AI adapter over the System-owned trusted-session contract. */
@Component
public class AiTrustedSessionResolver {

    private final TrustedCurrentUserResolver trustedCurrentUserResolver;

    public AiTrustedSessionResolver(TrustedCurrentUserResolver trustedCurrentUserResolver) {
        this.trustedCurrentUserResolver = trustedCurrentUserResolver;
    }

    public AuthenticatedAccess authenticateSessionTicket(
            String sessionId,
            Long userId,
            String userUuid,
            Long simulatedRoleId,
            Integer sessionVersion,
            String permissionsVersion
    ) {
        CurrentUser resolved = trustedCurrentUserResolver.resolveSessionTicket(
                sessionId,
                userId,
                userUuid,
                simulatedRoleId,
                sessionVersion,
                permissionsVersion
        );
        return AuthenticationTrustSupport.isTrustedCurrentUser(resolved)
                ? new AuthenticatedAccess(resolved)
                : null;
    }

    public record AuthenticatedAccess(CurrentUser currentUser) {
    }
}
