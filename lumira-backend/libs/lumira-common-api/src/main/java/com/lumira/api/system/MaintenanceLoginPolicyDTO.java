package com.lumira.api.system;

import java.util.List;

public record MaintenanceLoginPolicyDTO(
        boolean enabled,
        List<Long> allowedRoleIds
) {
    public MaintenanceLoginPolicyDTO {
        allowedRoleIds = allowedRoleIds == null ? List.of() : List.copyOf(allowedRoleIds);
    }
}
