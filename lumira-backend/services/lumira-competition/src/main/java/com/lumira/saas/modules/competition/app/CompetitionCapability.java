package com.lumira.saas.modules.competition.app;

/**
 * Stable capabilities exposed by a competition workspace.
 *
 * <p>The capability names are part of the frontend contract. They deliberately
 * hide the platform RBAC permission keys while the server remains responsible
 * for re-authorizing every API request.</p>
 */
public enum CompetitionCapability {
    WORKSPACE_VIEW("workspace.view", false),
    REGISTRATION_READ("registration.read", false),
    REGISTRATION_MANAGE("registration.manage", true),
    REVIEW_READ("review.read", false),
    REVIEW_MANAGE("review.manage", true),
    PAYMENT_READ("payment.read", false),
    CERTIFICATE_READ("certificate.read", false),
    CERTIFICATE_MANAGE("certificate.manage", true),
    SETTINGS_MANAGE("settings.manage", true),
    AUDIT_READ("audit.read", false);

    private final String wireName;
    private final boolean write;

    CompetitionCapability(String wireName, boolean write) {
        this.wireName = wireName;
        this.write = write;
    }

    public String wireName() {
        return wireName;
    }

    public boolean isWrite() {
        return write;
    }
}
