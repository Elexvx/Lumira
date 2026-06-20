package com.lumira.saas.modules.plugin.gateway;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.web.TraceContext;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class PluginGatewayController {

    private final PluginManagementAppService pluginManagementAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;
    private final PluginRuntimeSecurityPolicy runtimeSecurityPolicy;
    private final SensitiveErrorMessageSanitizer sensitiveErrorMessageSanitizer;
    private final SecurityAuditEventService securityAuditEventService;

    public PluginGatewayController(
            PluginManagementAppService pluginManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            PluginRuntimeSecurityPolicy runtimeSecurityPolicy,
            SensitiveErrorMessageSanitizer sensitiveErrorMessageSanitizer,
            SecurityAuditEventService securityAuditEventService
    ) {
        this.pluginManagementAppService = pluginManagementAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
        this.runtimeSecurityPolicy = runtimeSecurityPolicy;
        this.sensitiveErrorMessageSanitizer = sensitiveErrorMessageSanitizer;
        this.securityAuditEventService = securityAuditEventService;
    }

    @RequestMapping("/api/p/{pluginCode}/**")
    public ResponseEntity<Object> dispatch(HttpServletRequest request) throws Exception {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        runtimeSecurityPolicy.validateMethod(request.getMethod());
        runtimeSecurityPolicy.validateBodySize(request.getContentLengthLong());
        String pluginCode = resolvePluginCode(request.getRequestURI());
        Long tenantId = currentUser.getCurrentTenantId() == null
                ? com.lumira.common.constant.PlatformConstants.PLATFORM_TENANT_ID
                : currentUser.getCurrentTenantId();
        PluginRuntimeDescriptor runtimeDescriptor = pluginManagementAppService.requireTenantRuntime(tenantId, pluginCode);
        PluginHttpRequest pluginRequest = new PluginHttpRequest(
                request.getMethod(),
                resolvePluginPath(pluginCode, request.getRequestURI()),
                resolveQueryParameters(request),
                runtimeSecurityPolicy.filterHeaders(resolveHeaders(request)),
                resolveBody(request),
                tenantId,
                currentUser.getUserId(),
                currentUser.getUsername(),
                TraceContext.getRequestId(),
                TraceContext.getTraceId()
        );
        String permissionKey = runtimeDescriptor.getHttpHandler().requiredPermission(pluginRequest);
        runtimeSecurityPolicy.validateRequiredPermission(permissionKey);
        permissionGuard.requirePermission(currentUser, permissionKey);
        try {
            PluginHttpResponse response = runtimeDescriptor.getHttpHandler().handle(pluginRequest, runtimeDescriptor.getRuntimeContext());
            return ResponseEntity.status(response.status())
                    .contentType(MediaType.parseMediaType(response.contentType()))
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
                    .tenantId(tenantId)
                    .userId(currentUser.getUserId())
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
    private String resolvePluginCode(String requestUri) {
        String prefix = "/api/p/";
        int start = requestUri.indexOf(prefix);
        if (start < 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "插件路由不存在");
        }
        String remainder = requestUri.substring(start + prefix.length());
        int slashIndex = remainder.indexOf('/');
        return slashIndex < 0 ? remainder : remainder.substring(0, slashIndex);
    }

    private String resolvePluginPath(String pluginCode, String requestUri) {
        String prefix = "/api/p/" + pluginCode;
        String remainder = requestUri.substring(requestUri.indexOf(prefix) + prefix.length());
        return runtimeSecurityPolicy.normalizePluginPath(remainder.isBlank() ? "/" : remainder);
    }

    private String resolveBody(HttpServletRequest request) {
        try {
            return StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new BizException(ErrorCode.BAD_REQUEST, "插件请求体读取失败");
        }
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

