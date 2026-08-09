package com.lumira.saas.modules.ai.integration;

import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.TrustedCurrentUserResolver;
import java.util.Set;
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
        CurrentUser candidate = new CurrentUser(
                userId,
                null,
                sessionId,
                sessionVersion,
                true,
                Set.of()
        );
        candidate.setUserUuid(userUuid);
        candidate.setSimulatedRoleId(simulatedRoleId);
        candidate.setPermissionsVersion(permissionsVersion);
        CurrentUser resolved = trustedCurrentUserResolver.resolve(candidate);
        return resolved == null ? null : new AuthenticatedAccess(resolved);
    }

    public record AuthenticatedAccess(CurrentUser currentUser) {
    }
}
