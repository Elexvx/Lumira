package com.lumira.saas.infrastructure.security.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.lumira.common.security.data.DataPermissionRule;

import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthSession {

    private String sessionId;
    private Long userId;
    private String username;
    private Long currentTenantId;
    private Long simulatedRoleId;
    private Instant loginTime;
    private Instant lastActivityAt;
    private Instant expireTime;
    private Integer sessionVersion;
    private String clientType;
    private String loginIp;
    private String userAgent;
    private String refreshTokenId;
    private Boolean requiresPasswordChange;
    private String permissionsVersion;
    private List<String> permissions;
    private List<Long> roleIds;
    private Long primaryDeptId;
    private List<Long> deptIds;
    private List<Long> descendantDeptIds;
    private List<DataPermissionRule> dataScopes;
    private String defaultHomePath;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
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

    public Instant getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(Instant loginTime) {
        this.loginTime = loginTime;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public void setLastActivityAt(Instant lastActivityAt) {
        this.lastActivityAt = lastActivityAt;
    }

    public Instant getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(Instant expireTime) {
        this.expireTime = expireTime;
    }

    public Integer getSessionVersion() {
        return sessionVersion;
    }

    public void setSessionVersion(Integer sessionVersion) {
        this.sessionVersion = sessionVersion;
    }

    public String getClientType() {
        return clientType;
    }

    public void setClientType(String clientType) {
        this.clientType = clientType;
    }

    public String getLoginIp() {
        return loginIp;
    }

    public void setLoginIp(String loginIp) {
        this.loginIp = loginIp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getRefreshTokenId() {
        return refreshTokenId;
    }

    public void setRefreshTokenId(String refreshTokenId) {
        this.refreshTokenId = refreshTokenId;
    }

    public Boolean getRequiresPasswordChange() {
        return requiresPasswordChange;
    }

    public void setRequiresPasswordChange(Boolean requiresPasswordChange) {
        this.requiresPasswordChange = requiresPasswordChange;
    }

    public String getPermissionsVersion() {
        return permissionsVersion;
    }

    public void setPermissionsVersion(String permissionsVersion) {
        this.permissionsVersion = permissionsVersion;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }

    public List<Long> getRoleIds() {
        return roleIds;
    }

    public void setRoleIds(List<Long> roleIds) {
        this.roleIds = roleIds;
    }

    public Long getPrimaryDeptId() {
        return primaryDeptId;
    }

    public void setPrimaryDeptId(Long primaryDeptId) {
        this.primaryDeptId = primaryDeptId;
    }

    public List<Long> getDeptIds() {
        return deptIds;
    }

    public void setDeptIds(List<Long> deptIds) {
        this.deptIds = deptIds;
    }

    public List<Long> getDescendantDeptIds() {
        return descendantDeptIds;
    }

    public void setDescendantDeptIds(List<Long> descendantDeptIds) {
        this.descendantDeptIds = descendantDeptIds;
    }

    public List<DataPermissionRule> getDataScopes() {
        return dataScopes;
    }

    public void setDataScopes(List<DataPermissionRule> dataScopes) {
        this.dataScopes = dataScopes;
    }

    public String getDefaultHomePath() {
        return defaultHomePath;
    }

    public void setDefaultHomePath(String defaultHomePath) {
        this.defaultHomePath = defaultHomePath;
    }
}
