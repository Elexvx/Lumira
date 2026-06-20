package com.lumira.common.security.authorization;

import java.util.List;

public record AuthorizationDecision(
        AuthorizationVerdict verdict,
        String reasonCode,
        String message,
        boolean auditRequired,
        boolean approvalRequired,
        boolean confirmRequired,
        String policyId,
        List<String> matchedPolicies,
        String dataScopeSummary
) {
    public static AuthorizationDecision allow(String reasonCode, String message) {
        return new AuthorizationDecision(AuthorizationVerdict.ALLOW, reasonCode, message, false, false, false, null, List.of(reasonCode), "tenant");
    }

    public static AuthorizationDecision deny(String reasonCode, String message) {
        return new AuthorizationDecision(AuthorizationVerdict.DENY, reasonCode, message, true, false, false, null, List.of(reasonCode), "none");
    }

    public static AuthorizationDecision requireConfirm(String reasonCode, String message, List<String> matchedPolicies) {
        return new AuthorizationDecision(AuthorizationVerdict.REQUIRE_CONFIRM, reasonCode, message, true, false, true, null, matchedPolicies, "tenant");
    }

    public static AuthorizationDecision requireApproval(String reasonCode, String message, List<String> matchedPolicies) {
        return new AuthorizationDecision(AuthorizationVerdict.REQUIRE_APPROVAL, reasonCode, message, true, true, false, null, matchedPolicies, "tenant");
    }

    public boolean allowed() {
        return verdict == AuthorizationVerdict.ALLOW || verdict == AuthorizationVerdict.READ_ONLY;
    }
}
