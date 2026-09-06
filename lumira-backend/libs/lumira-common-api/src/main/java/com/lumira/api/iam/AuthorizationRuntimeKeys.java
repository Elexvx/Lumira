package com.lumira.api.iam;

/**
 * Runtime Redis key contract shared by the IAM owner and stateless consumers.
 *
 * <p>These keys are coordination and authorization-version state. They are
 * deliberately kept on the runtime Redis plane and must never be treated as
 * rebuildable cache entries.</p>
 */
public final class AuthorizationRuntimeKeys {

    public static final String PREFIX = "lumira:runtime:authz-version:";
    public static final String SUBJECT_SCOPE = "authorization:subject:";
    public static final String BINDING_SCOPE = "authorization:binding:";
    public static final String ROLE_SCOPE = "authorization:role:";
    public static final String DATA_POLICY_ROLE_SCOPE = "authorization:data-policy:role:";
    public static final String DATA_POLICY_GLOBAL_SCOPE = "authorization:data-policy:global";

    private AuthorizationRuntimeKeys() {
    }

    public static String subject(String subject) {
        return key(SUBJECT_SCOPE, requireText(subject, "subject"));
    }

    public static String binding(String subject) {
        return key(BINDING_SCOPE, requireText(subject, "subject"));
    }

    public static String role(long roleId) {
        return positiveKey(ROLE_SCOPE, roleId, "roleId");
    }

    public static String roleDataPolicy(long roleId) {
        return positiveKey(DATA_POLICY_ROLE_SCOPE, roleId, "roleId");
    }

    public static String globalDataPolicy() {
        return PREFIX + DATA_POLICY_GLOBAL_SCOPE;
    }

    private static String key(String scopePrefix, String value) {
        return PREFIX + scopePrefix + value;
    }

    private static String positiveKey(String scopePrefix, long value, String field) {
        if (value <= 0L) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return PREFIX + scopePrefix + value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
