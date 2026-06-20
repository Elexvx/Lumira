package com.lumira.common.security.authorization;

public interface AgentToolGrantEvaluator {
    AgentToolGrantDecision evaluate(AuthorizationRequest request);
}
