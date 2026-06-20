package com.lumira.common.web.security.audit;

import java.util.Map;

public record SecurityAuditEvent(
        Long tenantId,
        Long userId,
        Long employeeId,
        String eventType,
        String severity,
        String sourceIp,
        String userAgent,
        String requestId,
        String traceId,
        String resourceCode,
        String actionCode,
        String targetId,
        String result,
        String reasonCode,
        String message,
        Map<String, ?> metadata
) {

    public static Builder builder(String eventType, String severity, String result) {
        return new Builder(eventType, severity, result);
    }

    public static final class Builder {
        private Long tenantId;
        private Long userId;
        private Long employeeId;
        private final String eventType;
        private final String severity;
        private String sourceIp;
        private String userAgent;
        private String requestId;
        private String traceId;
        private String resourceCode;
        private String actionCode;
        private String targetId;
        private final String result;
        private String reasonCode;
        private String message;
        private Map<String, ?> metadata;

        private Builder(String eventType, String severity, String result) {
            this.eventType = eventType;
            this.severity = severity;
            this.result = result;
        }

        public Builder tenantId(Long tenantId) { this.tenantId = tenantId; return this; }
        public Builder userId(Long userId) { this.userId = userId; return this; }
        public Builder employeeId(Long employeeId) { this.employeeId = employeeId; return this; }
        public Builder sourceIp(String sourceIp) { this.sourceIp = sourceIp; return this; }
        public Builder userAgent(String userAgent) { this.userAgent = userAgent; return this; }
        public Builder requestId(String requestId) { this.requestId = requestId; return this; }
        public Builder traceId(String traceId) { this.traceId = traceId; return this; }
        public Builder resourceCode(String resourceCode) { this.resourceCode = resourceCode; return this; }
        public Builder actionCode(String actionCode) { this.actionCode = actionCode; return this; }
        public Builder targetId(String targetId) { this.targetId = targetId; return this; }
        public Builder reasonCode(String reasonCode) { this.reasonCode = reasonCode; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public Builder metadata(Map<String, ?> metadata) { this.metadata = metadata; return this; }

        public SecurityAuditEvent build() {
            return new SecurityAuditEvent(
                    tenantId, userId, employeeId, eventType, severity, sourceIp, userAgent, requestId, traceId,
                    resourceCode, actionCode, targetId, result, reasonCode, message, metadata);
        }
    }
}
