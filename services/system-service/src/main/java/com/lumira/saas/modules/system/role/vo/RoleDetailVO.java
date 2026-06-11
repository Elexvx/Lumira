package com.lumira.saas.modules.system.role.vo;

import java.util.List;

public class RoleDetailVO extends RoleVO {

    private List<String> permissionKeys;
    private List<RoleDataScopeVO> dataScopes;

    public List<String> getPermissionKeys() { return permissionKeys; }
    public void setPermissionKeys(List<String> permissionKeys) { this.permissionKeys = permissionKeys; }
    public List<RoleDataScopeVO> getDataScopes() { return dataScopes; }
    public void setDataScopes(List<RoleDataScopeVO> dataScopes) { this.dataScopes = dataScopes; }
}
