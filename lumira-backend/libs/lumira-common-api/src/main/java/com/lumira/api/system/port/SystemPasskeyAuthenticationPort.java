package com.lumira.api.system.port;

/** Passkey authentication capability set; excludes unrelated system administration APIs. */
public interface SystemPasskeyAuthenticationPort extends
        UserIdentityQueryPort,
        PermissionSnapshotPort,
        VerificationPort,
        PasskeyPort {
}
