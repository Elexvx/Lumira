package com.lumira.common.security;

public class JwtTokenClaims {
    private String sessionId;
    private Long userId;
    private String username;
    private Long currentTenantId;
    private Integer sessionVersion;
    private String tokenId;
    private JwtTokenType tokenType;

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

    public Integer getSessionVersion() {
        return sessionVersion;
    }

    public void setSessionVersion(Integer sessionVersion) {
        this.sessionVersion = sessionVersion;
    }

    public String getTokenId() {
        return tokenId;
    }

    public void setTokenId(String tokenId) {
        this.tokenId = tokenId;
    }

    public JwtTokenType getTokenType() {
        return tokenType;
    }

    public void setTokenType(JwtTokenType tokenType) {
        this.tokenType = tokenType;
    }
}
