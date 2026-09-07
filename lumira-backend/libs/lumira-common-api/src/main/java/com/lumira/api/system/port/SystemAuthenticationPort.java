package com.lumira.api.system.port;

/** Authentication-facing capability set; intentionally excludes plugin and platform administration APIs. */
public interface SystemAuthenticationPort extends
        UserIdentityQueryPort,
        PermissionSnapshotPort,
        AuthorizationVersionPort,
        VerificationPort,
        AuditWritePort,
        ReadModelVersionPort,
        RuntimeConfigurationPort {
}
