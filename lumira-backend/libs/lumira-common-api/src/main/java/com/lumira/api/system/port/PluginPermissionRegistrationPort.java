package com.lumira.api.system.port;

import com.lumira.api.system.PluginPermissionRegistrationRequestDTO;

public interface PluginPermissionRegistrationPort {
    Boolean registerPluginPermissions(PluginPermissionRegistrationRequestDTO request);
}
