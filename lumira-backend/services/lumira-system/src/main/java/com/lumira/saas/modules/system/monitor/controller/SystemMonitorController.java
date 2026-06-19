package com.lumira.saas.modules.system.monitor.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.web.TraceContext;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.security.PermissionGuard;
import com.lumira.saas.modules.system.monitor.app.SystemMonitorAppService;
import com.lumira.saas.modules.system.monitor.vo.SystemMonitorVO;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Locale;
import org.springdoc.webmvc.api.OpenApiWebMvcResource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system/monitor")
public class SystemMonitorController {

    private static final String API_DOCS_PATH = "/api-docs";

    private final SystemMonitorAppService systemMonitorAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;
    private final ObjectProvider<OpenApiWebMvcResource> openApiWebMvcResourceProvider;

    public SystemMonitorController(
            SystemMonitorAppService systemMonitorAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            ObjectProvider<OpenApiWebMvcResource> openApiWebMvcResourceProvider
    ) {
        this.systemMonitorAppService = systemMonitorAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
        this.openApiWebMvcResourceProvider = openApiWebMvcResourceProvider;
    }

    @GetMapping("/service")
    public ApiResponse<SystemMonitorVO.ServiceMonitorVO> serviceMonitor() {
        require("system:monitor:service:view");
        return ApiResponse.success(systemMonitorAppService.getServiceMonitor(), TraceContext.getRequestId());
    }

    @GetMapping("/redis")
    public ApiResponse<SystemMonitorVO.RedisMonitorVO> redisMonitor() {
        require("system:monitor:redis:view");
        return ApiResponse.success(systemMonitorAppService.getRedisMonitor(), TraceContext.getRequestId());
    }

    @GetMapping("/api-docs")
    public ResponseEntity<byte[]> apiDocs(HttpServletRequest request, Locale locale) throws IOException {
        require("system:monitor:docs:view");

        OpenApiWebMvcResource openApiResource = openApiWebMvcResourceProvider.getIfAvailable();
        if (openApiResource == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(openApiResource.openapiJson(request, API_DOCS_PATH, locale));
    }

    private void require(String permissionKey) {
        permissionGuard.requirePermission(securityContextFacade.getCurrentUser(), permissionKey);
    }
}
