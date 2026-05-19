package com.legendary.invention.saas.modules.system.role.vo;

import java.util.List;

public class RoleDetailVO extends RoleVO {

    private List<String> permissionKeys;

    public List<String> getPermissionKeys() { return permissionKeys; }
    public void setPermissionKeys(List<String> permissionKeys) { this.permissionKeys = permissionKeys; }
}
