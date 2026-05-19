package com.legendary.invention.saas.modules.system.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public class RoleUpsertRequest {

    @NotBlank
    @Size(max = 64, message = "roleCode长度不能超过64个字符")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{0,63}$", message = "roleCode只能由字母、数字和下划线组成，且必须以字母开头")
    private String roleCode;
    @NotBlank
    private String roleName;
    @NotBlank
    private String roleType;
    private List<String> permissionKeys;
    private List<RoleDataScopeRequest> dataScopes;

    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode == null ? null : roleCode.trim(); }
    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }
    public String getRoleType() { return roleType; }
    public void setRoleType(String roleType) { this.roleType = roleType; }
    public List<String> getPermissionKeys() { return permissionKeys; }
    public void setPermissionKeys(List<String> permissionKeys) { this.permissionKeys = permissionKeys; }
    public List<RoleDataScopeRequest> getDataScopes() { return dataScopes; }
    public void setDataScopes(List<RoleDataScopeRequest> dataScopes) { this.dataScopes = dataScopes; }
}
