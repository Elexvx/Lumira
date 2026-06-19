package com.lumira.saas.modules.system.role.dto;

import java.util.List;

public class RolePermissionRequest {

    private List<String> permissionKeys;

    public List<String> getPermissionKeys() { return permissionKeys; }
    public void setPermissionKeys(List<String> permissionKeys) { this.permissionKeys = permissionKeys; }
}
