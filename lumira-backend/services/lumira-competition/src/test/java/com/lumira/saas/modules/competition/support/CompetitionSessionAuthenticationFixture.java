package com.lumira.saas.modules.competition.support;

import com.lumira.common.security.CurrentUser;

/** Test seam for a System-owned session ticket check. */
public class CompetitionSessionAuthenticationFixture {

    public AuthenticatedAccess authenticateSessionTicket(
            String sessionId,
            Long userId,
            String userUuid,
            Long simulatedRoleId,
            Integer sessionVersion,
            String permissionsVersion
    ) {
        return null;
    }

    public record AuthenticatedAccess(CurrentUser currentUser) {
    }
}
