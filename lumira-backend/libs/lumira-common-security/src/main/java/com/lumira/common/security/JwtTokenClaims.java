package com.lumira.common.security;

public class JwtTokenClaims {
    private String sessionId;
    private Long userId;
    private String userUuid;
    private String username;
    private Long simulatedRoleId;
    private Integer sessionVersion;
    private String permissionsVersion;
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

    public String getUserUuid() {
        return userUuid;
    }

    public void setUserUuid(String userUuid) {
        this.userUuid = userUuid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Long getSimulatedRoleId() {
        return simulatedRoleId;
    }

    public void setSimulatedRoleId(Long simulatedRoleId) {
        this.simulatedRoleId = simulatedRoleId;
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
