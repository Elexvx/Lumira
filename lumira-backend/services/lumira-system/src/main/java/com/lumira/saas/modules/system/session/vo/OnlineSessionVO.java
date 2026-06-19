package com.lumira.saas.modules.system.session.vo;

import java.time.LocalDateTime;

public class OnlineSessionVO {
    private String sessionId;
    private Long userId;
    private String username;
    private String nickname;
    private String realName;
    private LocalDateTime loginTime;
    private LocalDateTime lastActivityAt;
    private LocalDateTime expireTime;
    private String clientType;
    private String loginIp;
    private String userAgent;

    public String getSessionId() { return sessionId; }

    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public Long getUserId() { return userId; }

    public void setUserId(Long userId) { this.userId = userId; }

    public String getUsername() { return username; }

    public void setUsername(String username) { this.username = username; }

    public String getNickname() { return nickname; }

    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getRealName() { return realName; }

    public void setRealName(String realName) { this.realName = realName; }

    public LocalDateTime getLoginTime() { return loginTime; }

    public void setLoginTime(LocalDateTime loginTime) { this.loginTime = loginTime; }

    public LocalDateTime getLastActivityAt() { return lastActivityAt; }

    public void setLastActivityAt(LocalDateTime lastActivityAt) { this.lastActivityAt = lastActivityAt; }

    public LocalDateTime getExpireTime() { return expireTime; }

    public void setExpireTime(LocalDateTime expireTime) { this.expireTime = expireTime; }

    public String getClientType() { return clientType; }

    public void setClientType(String clientType) { this.clientType = clientType; }

    public String getLoginIp() { return loginIp; }

    public void setLoginIp(String loginIp) { this.loginIp = loginIp; }

    public String getUserAgent() { return userAgent; }

    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
}
