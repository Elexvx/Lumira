package com.lumira.common.security.authorization;

import java.util.List;

public record DelegationGrantDecision(
        AuthorizationVerdict verdict,
        String reasonCode,
        String message,
        String scopeType,
        String maxRiskLevel,
        boolean requireConfirm,
        boolean requireApproval,
        List<String> matchedPolicies
) {
    public static DelegationGrantDecision allow(String scopeType, String maxRiskLevel, boolean requireConfirm,
                                                boolean requireApproval, List<String> matchedPolicies) {
        return new DelegationGrantDecision(AuthorizationVerdict.ALLOW, "DELEGATION_GRANT_MATCH",
                "Delegation grant matched", scopeType, maxRiskLevel, requireConfirm, requireApproval,
                matchedPolicies == null || matchedPolicies.isEmpty() ? List.of("DELEGATION_GRANT_MATCH") : List.copyOf(matchedPolicies));
    }

    public static DelegationGrantDecision notInScope() {
        return new DelegationGrantDecision(AuthorizationVerdict.ALLOW, "DELEGATION_NOT_IN_SCOPE",
                "No delegation required", "tenant", "LOW", false, false, List.of("DELEGATION_NOT_IN_SCOPE"));
    }

    public static DelegationGrantDecision deny(String reasonCode, String message) {
        return new DelegationGrantDecision(AuthorizationVerdict.DENY, reasonCode, message,
                "none", "LOW", false, false, List.of(reasonCode));
    }

    public static DelegationGrantDecision requireConfirm(String reasonCode, String message, List<String> matchedPolicies) {
        return new DelegationGrantDecision(AuthorizationVerdict.REQUIRE_CONFIRM, reasonCode, message,
                "tenant", "LOW", true, false, matchedPolicies == null || matchedPolicies.isEmpty() ? List.of(reasonCode) : List.copyOf(matchedPolicies));
    }

    public static DelegationGrantDecision requireApproval(String reasonCode, String message, List<String> matchedPolicies) {
        return new DelegationGrantDecision(AuthorizationVerdict.REQUIRE_APPROVAL, reasonCode, message,
                "tenant", "LOW", false, true, matchedPolicies == null || matchedPolicies.isEmpty() ? List.of(reasonCode) : List.copyOf(matchedPolicies));
    }

    public boolean allowed() {
        return verdict == AuthorizationVerdict.ALLOW || verdict == AuthorizationVerdict.READ_ONLY;
    }
}
