package com.legendary.invention.saas.modules.system.user.vo;

import java.util.List;

public class UserDetailVO extends UserVO {

    private List<Long> roleIds;
    private List<Long> tenantIds;

    public List<Long> getRoleIds() { return roleIds; }
    public void setRoleIds(List<Long> roleIds) { this.roleIds = roleIds; }
    public List<Long> getTenantIds() { return tenantIds; }
    public void setTenantIds(List<Long> tenantIds) { this.tenantIds = tenantIds; }
}
