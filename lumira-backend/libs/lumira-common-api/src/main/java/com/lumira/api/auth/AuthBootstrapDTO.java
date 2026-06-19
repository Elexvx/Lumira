package com.lumira.api.auth;

import com.lumira.api.system.SecuritySettingsDTO;

public record AuthBootstrapDTO(
        CurrentUserDTO currentUser,
        SecuritySettingsDTO securitySettings
) {
}
