package com.lumira.api.system;

public record CurrentUserRoleOptionDTO(
        Long id,
        String roleCode,
        String roleName,
        String roleType,
        Integer permissionCount
) {
}
