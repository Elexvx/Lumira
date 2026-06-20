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

    public static AuthorizationRequest aiTool(CurrentUser currentUser, Long employeeId, String toolCode,
                                               String permissionKey, String riskLevel, boolean confirmed,
                                               boolean approvalGranted, Map<String, Object> arguments) {
        Long tenantId = currentUser == null ? null : currentUser.getCurrentTenantId();
        Long userId = currentUser == null ? null : currentUser.getUserId();
        return new AuthorizationRequest(tenantId, SubjectRef.humanUser(tenantId, userId),
                SubjectRef.digitalEmployee(tenantId, employeeId), userId, employeeId, "ai_tool", "execute",
                permissionKey, toolCode, riskLevel, null, arguments == null ? Map.of() : Map.copyOf(arguments),
                confirmed, approvalGranted, "AI_AGENT", null, null, currentUser);
    }

    public static AuthorizationRequest aiToolView(CurrentUser currentUser, Long employeeId, String toolCode,
                                                  String permissionKey, String riskLevel, Map<String, Object> arguments) {
        return aiToolAccess(currentUser, employeeId, toolCode, permissionKey, riskLevel, "view", arguments);
    }

    public static AuthorizationRequest aiToolAccess(CurrentUser currentUser, Long employeeId, String toolCode,
                                                    String permissionKey, String riskLevel, String actionCode,
                                                    Map<String, Object> arguments) {
        Long tenantId = currentUser == null ? null : currentUser.getCurrentTenantId();
        Long userId = currentUser == null ? null : currentUser.getUserId();
        return new AuthorizationRequest(tenantId, SubjectRef.humanUser(tenantId, userId),
                SubjectRef.digitalEmployee(tenantId, employeeId), userId, employeeId, "ai_tool", actionCode,
                permissionKey, toolCode, riskLevel, null, arguments == null ? Map.of() : Map.copyOf(arguments),
                false, false, "AI_AGENT", null, null, currentUser);
    }

    public static AuthorizationRequest plugin(CurrentUser currentUser, String permissionKey, String pluginCode) {
        Long tenantId = currentUser == null ? null : currentUser.getCurrentTenantId();
        Long userId = currentUser == null ? null : currentUser.getUserId();
        return new AuthorizationRequest(tenantId, SubjectRef.humanUser(tenantId, userId), null, userId, null,
                "plugin", "invoke", permissionKey, pluginCode, "LOW", null, Map.of(), false, false,
                "PLUGIN", null, null, currentUser);
    }

    public static AuthorizationRequest systemJob(Long tenantId, String resourceCode, String actionCode, String requestId) {
        return new AuthorizationRequest(tenantId, null, null, null, null, resourceCode, actionCode,
                null, null, "LOW", null, Map.of("systemPrincipal", Boolean.TRUE), false, true,
                "SYSTEM_JOB", requestId, null, null);
    }
}
