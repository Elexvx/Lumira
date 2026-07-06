package com.lumira.saas.modules.plugin.gateway;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.web.TraceContext;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.web.security.SensitiveErrorMessageSanitizer;
import com.lumira.common.web.security.audit.SecurityAuditEvent;
import com.lumira.common.web.security.audit.SecurityAuditEventService;
import com.lumira.saas.modules.plugin.app.PluginManagementAppService;
import com.lumira.saas.modules.plugin.registry.PluginRuntimeDescriptor;
import com.lumira.saas.modules.plugin.runtime.PluginRuntimeSecurityPolicy;
import com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginHttpRequest;
import com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginHttpResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
public class PluginGatewayController {
    private static final String STATUS_ENABLED = "ENABLED";

    private final PluginManagementAppService pluginManagementAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;
    private final PluginRuntimeSecurityPolicy runtimeSecurityPolicy;
    private final SensitiveErrorMessageSanitizer sensitiveErrorMessageSanitizer;
    private final SecurityAuditEventService securityAuditEventService;
    private final SystemInternalApi systemInternalApi;

    public PluginGatewayController(
            PluginManagementAppService pluginManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            PluginRuntimeSecurityPolicy runtimeSecurityPolicy,
            SensitiveErrorMessageSanitizer sensitiveErrorMessageSanitizer,
            SecurityAuditEventService securityAuditEventService
    ) {
        this(
                pluginManagementAppService,
                securityContextFacade,
                permissionGuard,
                runtimeSecurityPolicy,
                sensitiveErrorMessageSanitizer,
                securityAuditEventService,
                null
        );
    }

    @Autowired
    public PluginGatewayController(
            PluginManagementAppService pluginManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            PluginRuntimeSecurityPolicy runtimeSecurityPolicy,
            SensitiveErrorMessageSanitizer sensitiveErrorMessageSanitizer,
            SecurityAuditEventService securityAuditEventService,
            SystemInternalApi systemInternalApi
    ) {
        this.pluginManagementAppService = pluginManagementAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
        this.runtimeSecurityPolicy = runtimeSecurityPolicy;
        this.sensitiveErrorMessageSanitizer = sensitiveErrorMessageSanitizer;
        this.securityAuditEventService = securityAuditEventService;
        this.systemInternalApi = systemInternalApi;
    }

    @RequestMapping("/api/p/{pluginCode}/**")
    public ResponseEntity<Object> dispatch(HttpServletRequest request) throws Exception {
        CurrentUser currentUser = requireAuthenticatedUser();
        Long actorUserId = currentUser.getUserId();
        String actorUsername = currentUser.getUsername();
        runtimeSecurityPolicy.validateMethod(request.getMethod());
        runtimeSecurityPolicy.validateBodySize(request.getContentLengthLong());
        String pluginCode = resolvePluginCode(request.getRequestURI());
        PluginRuntimeDescriptor runtimeDescriptor = pluginManagementAppService.requireRuntime(pluginCode);
        PluginHttpRequest pluginRequest = new PluginHttpRequest(
                request.getMethod(),
                resolvePluginPath(pluginCode, request.getRequestURI()),
                resolveQueryParameters(request),
                runtimeSecurityPolicy.filterHeaders(resolveHeaders(request)),
                resolveBody(request),
                actorUserId,
                currentUser.getUserUuid(),
                actorUsername,
                currentUser.getSessionId(),
                currentUser.getSessionVersion(),
                currentUser.getPermissionsVersion(),
                TraceContext.getRequestId(),
                TraceContext.getTraceId()
        );
        String permissionKey = runtimeDescriptor.getHttpHandler().requiredPermission(pluginRequest);
        permissionKey = runtimeSecurityPolicy.validateRequiredPermission(
                runtimeDescriptor.getPluginCode(),
                permissionKey,
                runtimeDescriptor.getPermissions()
        );
        permissionGuard.requirePermission(currentUser, permissionKey);
        try {
            PluginHttpResponse response = runtimeDescriptor.getHttpHandler().handle(pluginRequest, runtimeDescriptor.getRuntimeContext());
            int responseStatus = runtimeSecurityPolicy.validateResponseStatus(response.status());
            MediaType responseContentType = runtimeSecurityPolicy.validateResponseContentType(response.contentType());
            return ResponseEntity.status(responseStatus)
                    .contentType(responseContentType)
                    .body(response.body());
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            String sanitizedReason = sensitiveErrorMessageSanitizer.sanitize(exception.getMessage());
            org.slf4j.LoggerFactory.getLogger(PluginGatewayController.class).warn(
                    "Plugin request failed requestId={} traceId={} pluginCode={} reason={}",
                    TraceContext.getRequestId(),
                    TraceContext.getTraceId(),
                    pluginCode,
                    sanitizedReason,
                    exception
            );
            securityAuditEventService.record(request, SecurityAuditEvent.builder("PLUGIN_EXCEPTION_SANITIZED", "WARN", "DENIED")
                    .userId(actorUserId)
                    .resourceCode("plugin_gateway")
                    .actionCode(request.getMethod())
                    .targetId(pluginCode)
                    .reasonCode(exception.getClass().getSimpleName())
                    .message(sanitizedReason)
                    .metadata(Map.of("path", pluginRequest.path(), "pluginCode", pluginCode)));
            throw new BizException(
                    ErrorCode.PLUGIN_RUNTIME_ERROR,
                    "Plugin request failed",
                    "Plugin request failed, please contact an administrator"
            );
        }
    }

    private CurrentUser requireAuthenticatedUser() {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        currentUser = refreshTrustedCurrentUser(currentUser);
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return currentUser;
    }

    private CurrentUser refreshTrustedCurrentUser(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser) || systemInternalApi == null) {
            return currentUser;
        }
        Long userId = currentUser.getUserId();
        String normalizedUserUuid = currentUser.getUserUuid() == null ? null : currentUser.getUserUuid().trim();
        if (userId == null || userId <= 0 || !StringUtils.hasText(normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
        if (userSnapshot == null || userSnapshot.userId() == null || !userId.equals(userSnapshot.userId())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
        }
        if (!StringUtils.hasText(userSnapshot.userUuid())
                || !normalizedUserUuid.equals(userSnapshot.userUuid().trim())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
        }
        if (!StringUtils.hasText(userSnapshot.status())
                || !STATUS_ENABLED.equalsIgnoreCase(userSnapshot.status().trim())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
        }
        PermissionSnapshotDTO permissionSnapshot = systemInternalApi.permissionSnapshot(
                userId,
                userSnapshot.userUuid().trim()
        );
        if (permissionSnapshot == null || !StringUtils.hasText(permissionSnapshot.version())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user permissions are unavailable");
        }
        currentUser.setUserId(userSnapshot.userId());
        currentUser.setUserUuid(userSnapshot.userUuid().trim());
        currentUser.setUsername(userSnapshot.username());
        currentUser.setPermissions(permissionSnapshot.permissions() == null ? Set.of() : Set.copyOf(permissionSnapshot.permissions()));
        currentUser.setRoleIds(permissionSnapshot.roleIds() == null ? Set.of() : Set.copyOf(permissionSnapshot.roleIds()));
        currentUser.setPrimaryDeptId(permissionSnapshot.primaryDeptId());
        currentUser.setDeptIds(permissionSnapshot.deptIds() == null ? Set.of() : Set.copyOf(permissionSnapshot.deptIds()));
        currentUser.setDescendantDeptIds(
                permissionSnapshot.descendantDeptIds() == null ? Set.of() : Set.copyOf(permissionSnapshot.descendantDeptIds())
        );
        currentUser.setDataScopes(permissionSnapshot.dataScopes() == null ? List.of() : List.copyOf(permissionSnapshot.dataScopes()));
        currentUser.setPermissionsVersion(permissionSnapshot.version().trim());
        currentUser.setDefaultHomePath(permissionSnapshot.defaultHomePath());
        return currentUser;
    }

    private String resolvePluginCode(String requestUri) {
        String prefix = "/api/p/";
        int start = requestUri.indexOf(prefix);
        if (start < 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "Plugin route does not exist");
        }
        String remainder = requestUri.substring(start + prefix.length());
        int slashIndex = remainder.indexOf('/');
        return runtimeSecurityPolicy.validatePluginCode(slashIndex < 0 ? remainder : remainder.substring(0, slashIndex));
    }

    private String resolvePluginPath(String pluginCode, String requestUri) {
        String prefix = "/api/p/" + pluginCode;
        String remainder = requestUri.substring(requestUri.indexOf(prefix) + prefix.length());
        return runtimeSecurityPolicy.normalizePluginPath(remainder.isBlank() ? "/" : remainder);
    }

    private String resolveBody(HttpServletRequest request) {
        try {
            long maxBytes = runtimeSecurityPolicy.maxGatewayBodyBytes();
            if (maxBytes <= 0) {
                return StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);
            }
            int readLimit = maxBytes >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) maxBytes + 1;
            byte[] bytes = readLimitedBytes(request.getInputStream(), readLimit);
            if (bytes.length > maxBytes) {
                throw new BizException(ErrorCode.BAD_REQUEST, "Plugin request body exceeds limit");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (BizException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Failed to read plugin request body");
        }
    }

    private byte[] readLimitedBytes(InputStream inputStream, int readLimit) throws IOException {
        if (readLimit <= 0) {
            return new byte[0];
        }
        return inputStream.readNBytes(readLimit);
    }

    private Map<String, List<String>> resolveQueryParameters(HttpServletRequest request) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, value) -> result.put(key, value == null ? List.of() : List.of(value)));
        return result;
    }

    private Map<String, String> resolveHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames == null) {
            return Collections.emptyMap();
        }
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headers.put(headerName, request.getHeader(headerName));
        }
        return headers;
    }
}

