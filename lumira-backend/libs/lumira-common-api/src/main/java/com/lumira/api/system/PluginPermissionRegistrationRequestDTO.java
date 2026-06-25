package com.lumira.api.system;

import java.util.List;

public record PluginPermissionRegistrationRequestDTO(
        String pluginCode,
        List<Permission> permissions
) {

    public record Permission(
            String permissionKey,
            String permissionName,
            String permissionGroup
    ) {
    }
}
