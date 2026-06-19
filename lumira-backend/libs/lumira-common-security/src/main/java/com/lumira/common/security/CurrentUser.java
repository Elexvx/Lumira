package com.lumira.common.security;

import com.lumira.common.security.data.DataPermissionRule;

import java.util.List;
import java.util.Set;

public class CurrentUser {
    private Long userId;
    private String username;
    private Long currentTenantId;
    private Long simulatedRoleId;
    private String sessionId;
    private Integer sessionVersion;
    private String permissionsVersion;
    private Boolean requiresPasswordChange;
    private boolean authenticated;
    private Set<String> permissions;
    private Set<Long> roleIds;
    private Long primaryDeptId;
    private String defaultHomePath;
    private Set<Long> deptIds;
    private Set<Long> descendantDeptIds;
    private List<DataPermissionRule> dataScopes;

    public CurrentUser() {
    }

    public CurrentUser(Long userId, String username, Long currentTenantId, String sessionId, Integer sessionVersion, boolean authenticated, Set<String> permissions) {
        this(userId, username, currentTenantId, sessionId, sessionVersion, authenticated, permissions, Set.of(), null, Set.of(), Set.of(), List.of());
    }

    public CurrentUser(
            Long userId,
            String username,
            Long currentTenantId,
            String sessionId,
            Integer sessionVersion,
            boolean authenticated,
            Set<String> permissions,
            Set<Long> roleIds,
            Long primaryDeptId,
            Set<Long> deptIds,
            Set<Long> descendantDeptIds,
            List<DataPermissionRule> dataScopes
    ) {
        this.userId = userId;
        this.username = username;
        this.currentTenantId = currentTenantId;
        this.sessionId = sessionId;
        this.sessionVersion = sessionVersion;
        this.authenticated = authenticated;
        this.permissions = permissions;
        this.roleIds = roleIds;
        this.primaryDeptId = primaryDeptId;
        this.deptIds = deptIds;
        this.descendantDeptIds = descendantDeptIds;
        this.dataScopes = dataScopes;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Long getCurrentTenantId() {
        return currentTenantId;
    }

    public void setCurrentTenantId(Long currentTenantId) {
        this.currentTenantId = currentTenantId;
    }

    public Long getSimulatedRoleId() {
        return simulatedRoleId;
    }

    public void setSimulatedRoleId(Long simulatedRoleId) {
        this.simulatedRoleId = simulatedRoleId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Integer getSessionVersion() {
        return sessionVersion;
    }

    public void setSessionVersion(Integer sessionVersion) {
        this.sessionVersion = sessionVersion;
    }

    public String getPermissionsVersion() {
        return permissionsVersion;
    }

    public void setPermissionsVersion(String permissionsVersion) {
        this.permissionsVersion = permissionsVersion;
    }

    public Boolean getRequiresPasswordChange() {
        return requiresPasswordChange;
    }

    public void setRequiresPasswordChange(Boolean requiresPasswordChange) {
        this.requiresPasswordChange = requiresPasswordChange;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }

    public Set<String> getPermissions() {
        return permissions == null ? Set.of() : permissions;
    }

    public void setPermissions(Set<String> permissions) {
        this.permissions = permissions;
    }

    public Set<Long> getRoleIds() {
        return roleIds == null ? Set.of() : roleIds;
    }

    public void setRoleIds(Set<Long> roleIds) {
        this.roleIds = roleIds;
    }

    public Long getPrimaryDeptId() {
        return primaryDeptId;
    }

    public void setPrimaryDeptId(Long primaryDeptId) {
        this.primaryDeptId = primaryDeptId;
    }

    public String getDefaultHomePath() {
        return defaultHomePath;
    }

    public void setDefaultHomePath(String defaultHomePath) {
        this.defaultHomePath = defaultHomePath;
    }

    public Set<Long> getDeptIds() {
        return deptIds == null ? Set.of() : deptIds;
    }

    public void setDeptIds(Set<Long> deptIds) {
        this.deptIds = deptIds;
    }

    public Set<Long> getDescendantDeptIds() {
        return descendantDeptIds == null ? Set.of() : descendantDeptIds;
    }

    public void setDescendantDeptIds(Set<Long> descendantDeptIds) {
        this.descendantDeptIds = descendantDeptIds;
    }

    public List<DataPermissionRule> getDataScopes() {
        return dataScopes == null ? List.of() : dataScopes;
    }

    public void setDataScopes(List<DataPermissionRule> dataScopes) {
        this.dataScopes = dataScopes;
    }
}
