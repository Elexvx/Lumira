package com.lumira.api.audit;

import java.util.Map;

/** Sanitized security-audit data that can cross a service boundary. */
public record SecurityAuditRecord(
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
) { }
