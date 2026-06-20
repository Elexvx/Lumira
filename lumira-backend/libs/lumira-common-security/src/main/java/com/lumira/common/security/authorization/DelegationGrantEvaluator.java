package com.lumira.common.security.authorization;

public interface DelegationGrantEvaluator {
    DelegationGrantDecision evaluate(AuthorizationRequest request);
}
