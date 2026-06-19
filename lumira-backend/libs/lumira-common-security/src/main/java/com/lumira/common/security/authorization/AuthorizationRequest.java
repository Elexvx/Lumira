package com.lumira.common.security.authorization;

import com.lumira.common.security.CurrentUser;

import java.util.Map;

public record AuthorizationRequest(
        Long tenantId,
        SubjectRef humanSubject,
        SubjectRef agentSubject,
        Long humanUserId,
        Long employeeId,
        String resourceCode,
        String actionCode,
        String permissionKey,
        String toolCode,
        String riskLevel,
        Long resourceId,
        Map<String, Object> arguments,
        boolean confirmed,
        boolean approvalGranted,
        String channel,
        String requestId,
        String traceId,
        CurrentUser currentUser
) {
    public static AuthorizationRequest permission(CurrentUser currentUser, String permissionKey) {
        Long tenantId = currentUser == null ? null : currentUser.getCurrentTenantId();
        Long userId = currentUser == null ? null : currentUser.getUserId();
        return new AuthorizationRequest(
                tenantId,
                SubjectRef.humanUser(tenantId, userId),
                null,
                userId,
                null,
                null,
                null,
                permissionKey,
                null,
                "LOW",
                null,
                Map.of(),
                false,
                false,
                "WEB",
                null,
                null,
                currentUser
        );
    }
}
