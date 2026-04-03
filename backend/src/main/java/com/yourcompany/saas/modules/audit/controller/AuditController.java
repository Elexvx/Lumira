package com.yourcompany.saas.modules.audit.controller;

import com.yourcompany.saas.common.api.ApiResponse;
import com.yourcompany.saas.common.vo.PageResponse;
import com.yourcompany.saas.infrastructure.observability.TraceContext;
import com.yourcompany.saas.infrastructure.security.SecurityContextFacade;
import com.yourcompany.saas.modules.iam.service.PermissionGuard;
import com.yourcompany.saas.modules.system.app.SystemManagementAppService;
import com.yourcompany.saas.modules.system.vo.SystemVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final SystemManagementAppService systemManagementAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;

    public AuditController(
            SystemManagementAppService systemManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this.systemManagementAppService = systemManagementAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping("/summary")
    public ApiResponse<java.util.Map<String, Integer>> summary() {
        require("audit:view");
        PageResponse<SystemVO.AuditLogVO> login = systemManagementAppService.listLoginLogs(securityContextFacade.getCurrentUser(), null, null, 1, 1);
        PageResponse<SystemVO.AuditLogVO> operation = systemManagementAppService.listOperationLogs(securityContextFacade.getCurrentUser(), null, null, 1, 1);
        return ApiResponse.success(
                java.util.Map.of(
                        "loginCount", (int) login.getTotal(),
                        "operationCount", (int) operation.getTotal()
                ),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/login-logs")
    public ApiResponse<PageResponse<SystemVO.AuditLogVO>> loginLogs(
            @RequestParam(name = "username", required = false) String username,
            @RequestParam(name = "tenantId", required = false) Long tenantId,
            @RequestParam(name = "loginType", required = false) String loginType,
            @RequestParam(name = "startTime", required = false) String startTime,
            @RequestParam(name = "endTime", required = false) String endTime,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        require("audit:login:view");
        return ApiResponse.success(
                systemManagementAppService.listLoginLogs(securityContextFacade.getCurrentUser(), username, tenantId, loginType, startTime, endTime, pageNo, pageSize),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/operation-logs")
    public ApiResponse<PageResponse<SystemVO.AuditLogVO>> operationLogs(
            @RequestParam(name = "username", required = false) String username,
            @RequestParam(name = "tenantId", required = false) Long tenantId,
            @RequestParam(name = "startTime", required = false) String startTime,
            @RequestParam(name = "endTime", required = false) String endTime,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        require("audit:operation:view");
        return ApiResponse.success(
                systemManagementAppService.listOperationLogs(securityContextFacade.getCurrentUser(), username, tenantId, startTime, endTime, pageNo, pageSize),
                TraceContext.getRequestId()
        );
    }

    private void require(String permissionKey) {
        permissionGuard.requirePermission(securityContextFacade.getCurrentUser(), permissionKey);
    }
}
