package com.lumira.common.security.authorization;

import java.util.List;

public record AgentToolGrantDecision(
        boolean allowed,
        String reasonCode,
        String permissionMode,
        String permissionKey,
        String maxRiskLevel,
        boolean requireConfirm,
        boolean requireApproval,
        List<String> matchedPolicies
) {
    public static AgentToolGrantDecision deny(String reasonCode) {
        return new AgentToolGrantDecision(false, reasonCode, "", "", "LOW", false, false, List.of(reasonCode));
    }

    public static AgentToolGrantDecision allow(String permissionMode, String permissionKey, String maxRiskLevel,
                                               boolean requireConfirm, boolean requireApproval, List<String> policies) {
        return new AgentToolGrantDecision(true, "AGENT_GRANT_ALLOW", permissionMode, permissionKey, maxRiskLevel,
                requireConfirm, requireApproval, policies == null || policies.isEmpty() ? List.of("AGENT_GRANT_ALLOW") : List.copyOf(policies));
    }
}
