package com.lumira.common.security.authorization;

public interface AuthorizationService {

    AuthorizationDecision evaluate(AuthorizationRequest request);

    void require(AuthorizationRequest request);
}
