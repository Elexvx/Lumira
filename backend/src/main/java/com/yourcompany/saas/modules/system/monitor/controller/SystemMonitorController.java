package com.yourcompany.saas.modules.system.monitor.controller;

import com.yourcompany.saas.common.api.ApiResponse;
import com.yourcompany.saas.infrastructure.observability.TraceContext;
import com.yourcompany.saas.infrastructure.security.SecurityContextFacade;
import com.yourcompany.saas.modules.iam.service.PermissionGuard;
import com.yourcompany.saas.modules.system.monitor.app.SystemMonitorAppService;
import com.yourcompany.saas.modules.system.monitor.vo.SystemMonitorVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system/monitor")
public class SystemMonitorController {

    private final SystemMonitorAppService systemMonitorAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;

    public SystemMonitorController(
            SystemMonitorAppService systemMonitorAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this.systemMonitorAppService = systemMonitorAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
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

    private void require(String permissionKey) {
        permissionGuard.requirePermission(securityContextFacade.getCurrentUser(), permissionKey);
    }
}
