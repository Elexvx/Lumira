package com.lumira.saas.modules.plugin.runtime;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginDeclaredPermission;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class PluginRuntimeSecurityPolicy {

    private static final Set<String> BLOCKED_HEADERS = Set.of(
            "authorization",
            "cookie",
            "set-cookie",
            "x-job-token",
            "x-internal-token",
            "x-forwarded-internal-token",
            "x-forwarded-authorization",
            "host",
            "forwarded",
            "x-forwarded-for",
            "x-forwarded-host",
            "x-forwarded-port",
            "x-forwarded-proto",
            "x-real-ip"
    );
    private static final Set<String> ALLOWED_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");
    private static final List<MediaType> ALLOWED_RESPONSE_MEDIA_TYPES = List.of(
            MediaType.APPLICATION_JSON,
            MediaType.TEXT_PLAIN,
            MediaType.APPLICATION_OCTET_STREAM
    );
    private static final Pattern PLUGIN_CODE_PATTERN = Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}");

    private final PluginProperties pluginProperties;

    public PluginRuntimeSecurityPolicy(PluginProperties pluginProperties) {
        this.pluginProperties = pluginProperties;
    }

    public void validateMethod(String method) {
        String normalized = method == null ? "" : method.toUpperCase(Locale.ROOT);
        if (!ALLOWED_METHODS.contains(normalized)) {
            throw new BizException(ErrorCode.FORBIDDEN, "鎻掍欢缃戝叧涓嶅厑璁歌璇锋眰鏂规硶");
        }
    }

    public String normalizePluginPath(String path) {
        String normalized = StringUtils.hasText(path) ? path.trim() : "/";
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (normalized.contains("..") || normalized.contains("\\") || normalized.contains("%2e") || normalized.contains("%2E")) {
            throw new BizException(ErrorCode.FORBIDDEN, "Plugin path is invalid");
        }
        return normalized;
    }

    public String validatePluginCode(String pluginCode) {
        String normalized = pluginCode == null ? "" : pluginCode.trim();
        if (!PLUGIN_CODE_PATTERN.matcher(normalized).matches()
                || normalized.contains("..")
                || normalized.startsWith(".")
                || normalized.endsWith(".")) {
            throw new BizException(ErrorCode.NOT_FOUND, "Plugin route does not exist");
        }
        return normalized;
    }

    public void validateBodySize(long contentLength) {
        long maxBytes = Math.max(pluginProperties.getMaxGatewayBodyBytes(), 0L);
        if (maxBytes > 0 && contentLength > maxBytes) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Plugin request body exceeds limit");
        }
    }

    public long maxGatewayBodyBytes() {
        return Math.max(pluginProperties.getMaxGatewayBodyBytes(), 0L);
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
        if (!StringUtils.hasText(permissionKey)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Plugin HTTP handler must declare a permission");
        }
    }

    public String validateRequiredPermission(String pluginCode, String permissionKey, List<PluginDeclaredPermission> declaredPermissions) {
        validateRequiredPermission(permissionKey);
        String normalizedPluginCode = validatePluginCode(pluginCode);
        String normalizedPermissionKey = permissionKey.trim();
        String requiredPrefix = "plugin:" + normalizedPluginCode + ":";
        if (!normalizedPermissionKey.startsWith(requiredPrefix)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Plugin permission is outside plugin namespace");
        }
        boolean declared = declaredPermissions != null && declaredPermissions.stream()
                .filter(Objects::nonNull)
                .map(PluginDeclaredPermission::permissionKey)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .anyMatch(normalizedPermissionKey::equals);
        if (!declared) {
            throw new BizException(ErrorCode.FORBIDDEN, "Plugin permission is not declared");
        }
        return normalizedPermissionKey;
    }

    public int validateResponseStatus(int status) {
        if (status < 200 || status > 599 || (status >= 300 && status < 400)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Plugin response status is not allowed");
        }
        return status;
    }

    public MediaType validateResponseContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Plugin response content type is required");
        }
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException exception) {
            throw new BizException(ErrorCode.FORBIDDEN, "Plugin response content type is invalid");
        }
        boolean allowed = ALLOWED_RESPONSE_MEDIA_TYPES.stream()
                .anyMatch(allowedType -> allowedType.isCompatibleWith(mediaType));
        if (!allowed) {
            throw new BizException(ErrorCode.FORBIDDEN, "Plugin response content type is not allowed");
        }
        return mediaType;
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
