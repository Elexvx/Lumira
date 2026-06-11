package com.lumira.saas.modules.plugin.runtime.runtime;

import java.util.List;
import java.util.Map;

public final class PluginRuntimeModels {

    private PluginRuntimeModels() {
    }

    public record PluginHttpRequest(
            String method,
            String path,
            Map<String, List<String>> queryParameters,
            Map<String, String> headers,
            String body,
            Long tenantId,
            Long userId,
            String username,
            String requestId,
            String traceId
    ) {
    }

    public record PluginHttpResponse(
            int status,
            Object body,
            String contentType
    ) {
        public static PluginHttpResponse json(int status, Object body) {
            return new PluginHttpResponse(status, body, "application/json");
        }
    }

    public record PluginDeclaredPermission(
            String permissionKey,
            String permissionName,
            String permissionGroup
    ) {
    }

    public record PluginDeclaredMenu(
            String menuCode,
            String parentMenuCode,
            String menuName,
            String routePath,
            String icon,
            String permissionKey,
            Integer sortNo
    ) {
    }

    public record PluginHealthReport(
            boolean healthy,
            String message,
            Map<String, Object> details
    ) {
        public static PluginHealthReport healthy(String message) {
            return new PluginHealthReport(true, message, Map.of());
        }

        public static PluginHealthReport unhealthy(String message, Map<String, Object> details) {
            return new PluginHealthReport(false, message, details == null ? Map.of() : details);
        }
    }

    public record PluginScheduledTask(
            String taskCode,
            long initialDelaySeconds,
            long fixedDelaySeconds,
            Runnable task
    ) {
    }

    public record PluginSecondFactorProfile(
            String pluginCode,
            String pluginName,
            String factorCode,
            String factorName,
            boolean enabled,
            boolean bound,
            boolean emailRequired,
            String maskedContact,
            String statusMessage
    ) {
    }

    public record PluginSecondFactorChallenge(
            String pluginCode,
            String pluginName,
            String factorCode,
            String factorName,
            String challengeId,
            String maskedContact,
            String promptMessage,
            String setupUri,
            String setupSecret,
            List<String> recoveryCodes
    ) {
    }

    public record PluginSecondFactorVerification(
            boolean verified,
            Long tenantId,
            Long userId,
            String username,
            String message
    ) {
        public static PluginSecondFactorVerification success(Long tenantId, Long userId, String username, String message) {
            return new PluginSecondFactorVerification(true, tenantId, userId, username, message);
        }

        public static PluginSecondFactorVerification failure(String message) {
            return new PluginSecondFactorVerification(false, null, null, null, message);
        }
    }
}
