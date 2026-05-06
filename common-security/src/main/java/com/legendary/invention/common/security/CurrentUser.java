package com.legendary.invention.common.security;

import java.util.Set;

public class CurrentUser {
    private Long userId;
    private String username;
    private Long currentTenantId;
    private String sessionId;
    private Integer sessionVersion;
    private boolean authenticated;
    private Set<String> permissions;

    public CurrentUser() {
    }

    public CurrentUser(Long userId, String username, Long currentTenantId, String sessionId, Integer sessionVersion, boolean authenticated, Set<String> permissions) {
        this.userId = userId;
        this.username = username;
        this.currentTenantId = currentTenantId;
        this.sessionId = sessionId;
        this.sessionVersion = sessionVersion;
        this.authenticated = authenticated;
        this.permissions = permissions;
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

    public boolean isAuthenticated() {
        return authenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(Set<String> permissions) {
        this.permissions = permissions;
    }
}
