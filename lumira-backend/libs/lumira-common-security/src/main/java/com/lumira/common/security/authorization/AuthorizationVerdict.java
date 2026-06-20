package com.lumira.common.security.authorization;

public enum AuthorizationVerdict {
    ALLOW,
    DENY,
    REQUIRE_CONFIRM,
    REQUIRE_APPROVAL,
    MASKED,
    READ_ONLY
}
