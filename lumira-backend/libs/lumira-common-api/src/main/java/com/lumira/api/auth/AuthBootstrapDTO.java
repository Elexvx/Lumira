package com.lumira.api.auth;

import com.lumira.api.system.SecuritySettingsDTO;
import java.util.List;
import java.util.Map;

public record AuthBootstrapDTO(
        CurrentUserDTO currentUser,
        SecuritySettingsDTO securitySettings,
        List<Map<String, Object>> menuTree,
        List<Object> availablePlugins,
        Map<String, Object> runtimeAppearanceSettings
) {
    public AuthBootstrapDTO(CurrentUserDTO currentUser, SecuritySettingsDTO securitySettings) {
        this(currentUser, securitySettings, List.of(), List.of(), Map.of());
    }
}
