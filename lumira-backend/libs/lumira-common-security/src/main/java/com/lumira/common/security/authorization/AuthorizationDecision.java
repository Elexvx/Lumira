package com.lumira.common.security.authorization;

import java.util.List;

public record AuthorizationDecision(
        AuthorizationVerdict verdict,
        String reasonCode,
        String message,
        boolean auditRequired,
        boolean approvalRequired,
        String policyId,
        List<String> matchedPolicies
) {
    public static AuthorizationDecision allow(String reasonCode, String message) {
        return new AuthorizationDecision(AuthorizationVerdict.ALLOW, reasonCode, message, false, false, null, List.of(reasonCode));
    }

    public static AuthorizationDecision deny(String reasonCode, String message) {
        return new AuthorizationDecision(AuthorizationVerdict.DENY, reasonCode, message, true, false, null, List.of(reasonCode));
    }

    public boolean allowed() {
        return verdict == AuthorizationVerdict.ALLOW || verdict == AuthorizationVerdict.READ_ONLY;
    }
}
