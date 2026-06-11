package com.lumira.saas.modules.plugin.runtime;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class PluginRuntimeSecurityPolicy {

    private static final Set<String> BLOCKED_HEADERS = Set.of(
            "authorization",
            "cookie",
            "set-cookie",
            "x-internal-token",
            "x-forwarded-authorization"
    );
    private static final Set<String> ALLOWED_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");

    private final PluginProperties pluginProperties;

    public PluginRuntimeSecurityPolicy(PluginProperties pluginProperties) {
        this.pluginProperties = pluginProperties;
    }

    public void validateMethod(String method) {
        String normalized = method == null ? "" : method.toUpperCase(Locale.ROOT);
        if (!ALLOWED_METHODS.contains(normalized)) {
            throw new BizException(ErrorCode.FORBIDDEN, "插件网关不允许该请求方法");
        }
    }

    public String normalizePluginPath(String path) {
        String normalized = StringUtils.hasText(path) ? path.trim() : "/";
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (normalized.contains("..") || normalized.contains("\\") || normalized.contains("%2e") || normalized.contains("%2E")) {
            throw new BizException(ErrorCode.FORBIDDEN, "插件路径不合法");
        }
        return normalized;
    }

    public void validateBodySize(long contentLength) {
        long maxBytes = Math.max(pluginProperties.getMaxGatewayBodyBytes(), 0L);
        if (maxBytes > 0 && contentLength > maxBytes) {
            throw new BizException(ErrorCode.BAD_REQUEST, "插件请求体超过限制");
        }
    }

    public Map<String, String> filterHeaders(Map<String, String> headers) {
        Map<String, String> result = new LinkedHashMap<>();
        if (headers == null || headers.isEmpty()) {
            return result;
        }
        headers.forEach((name, value) -> {
            if (!StringUtils.hasText(name)) {
                return;
            }
            String normalizedName = name.toLowerCase(Locale.ROOT);
            if (BLOCKED_HEADERS.contains(normalizedName)) {
                return;
            }
            result.put(name, value);
        });
        return result;
    }

    public void validateRequiredPermission(String permissionKey) {
        if (pluginProperties.isRequireHttpPermission() && !StringUtils.hasText(permissionKey)) {
            throw new BizException(ErrorCode.FORBIDDEN, "插件 HTTP 处理器必须声明权限");
        }
    }

    public RuntimePolicySnapshot snapshot() {
        return new RuntimePolicySnapshot(
                Math.max(pluginProperties.getMaxGatewayBodyBytes(), 0L),
                pluginProperties.isRequireHttpPermission(),
                ALLOWED_METHODS,
                BLOCKED_HEADERS
        );
    }

    public record RuntimePolicySnapshot(
            long maxGatewayBodyBytes,
            boolean requireHttpPermission,
            Set<String> allowedMethods,
            Set<String> blockedHeaders
    ) {
    }
}
