package com.lumira.api.system;

import java.util.List;

public record PasskeySettingsDTO(
        Boolean enabled,
        Boolean passwordlessEnabled,
        Boolean selfBindingEnabled,
        String rpId,
        String rpName,
        List<String> allowedOrigins,
        Integer challengeTtlSeconds
) {
}
