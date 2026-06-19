package com.lumira.saas.modules.system.role.vo;

import java.time.LocalDateTime;

public class RoleVO {

    private Long id;
    private Long tenantId;
    private String roleCode;
    private String roleName;
    private String roleType;
    private String defaultHomePath;
    private Integer permissionCount;
    private Integer userCount;
    private Boolean defaultRegistrationRole;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }
    public String getRoleType() { return roleType; }
    public void setRoleType(String roleType) { this.roleType = roleType; }
    public String getDefaultHomePath() { return defaultHomePath; }
    public void setDefaultHomePath(String defaultHomePath) { this.defaultHomePath = defaultHomePath; }
    public Integer getPermissionCount() { return permissionCount; }
    public void setPermissionCount(Integer permissionCount) { this.permissionCount = permissionCount; }
    public Integer getUserCount() { return userCount; }
    public void setUserCount(Integer userCount) { this.userCount = userCount; }
    public Boolean getDefaultRegistrationRole() { return defaultRegistrationRole; }
    public void setDefaultRegistrationRole(Boolean defaultRegistrationRole) { this.defaultRegistrationRole = defaultRegistrationRole; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
