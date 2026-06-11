package com.lumira.saas.modules.system.online;

import java.time.Instant;

public class OnlineSessionEvent {

    public static final String ACTION_UPSERT = "UPSERT";
    public static final String ACTION_REMOVED = "REMOVED";
    public static final String ACTION_HEARTBEAT = "HEARTBEAT";

    private String action;
    private Long tenantId;
    private Long userId;
    private String sessionId;
    private String operatorUsername;
    private Instant occurredAt;

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getOperatorUsername() {
        return operatorUsername;
    }

    public void setOperatorUsername(String operatorUsername) {
        this.operatorUsername = operatorUsername;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}
