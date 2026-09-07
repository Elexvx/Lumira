package com.lumira.api.system.port;

/** Plugin-facing system capabilities, limited to permission/menu/version integration. */
public interface SystemPluginManagementPort extends
        UserIdentityQueryPort,
        PermissionSnapshotPort,
        AuthorizationVersionPort,
        MenuCatalogPort,
        PluginPermissionRegistrationPort,
        ReadModelVersionPort {
}
