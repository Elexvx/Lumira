package com.lumira.saas.modules.audit.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.common.web.TraceContext;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.security.PermissionGuard;
import com.lumira.saas.modules.system.app.SystemManagementAppService;
import com.lumira.saas.modules.system.vo.SystemVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.lumira.common.security.AuthenticationTrustSupport.isTrustedCurrentUser;

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
        CurrentUser currentUser = require("audit:view");
        PageResponse<SystemVO.AuditLogVO> login = systemManagementAppService.listLoginLogs(currentUser, null, 1, 1);
        PageResponse<SystemVO.AuditLogVO> operation = systemManagementAppService.listOperationLogs(currentUser, null, 1, 1);
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
            @RequestParam(name = "loginType", required = false) String loginType,
            @RequestParam(name = "startTime", required = false) String startTime,
            @RequestParam(name = "endTime", required = false) String endTime,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        CurrentUser currentUser = require("audit:login:view");
        return ApiResponse.success(
                systemManagementAppService.listLoginLogs(currentUser, username, loginType, startTime, endTime, pageNo, pageSize),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/operation-logs")
    public ApiResponse<PageResponse<SystemVO.AuditLogVO>> operationLogs(
            @RequestParam(name = "username", required = false) String username,
            @RequestParam(name = "startTime", required = false) String startTime,
            @RequestParam(name = "endTime", required = false) String endTime,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        CurrentUser currentUser = require("audit:operation:view");
        return ApiResponse.success(
                systemManagementAppService.listOperationLogs(currentUser, username, startTime, endTime, pageNo, pageSize),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/verification-logs")
    public ApiResponse<PageResponse<SystemVO.AuditLogVO>> verificationLogs(
            @RequestParam(name = "channel", required = false) String channel,
            @RequestParam(name = "scene", required = false) String scene,
            @RequestParam(name = "resultStatus", required = false) String resultStatus,
            @RequestParam(name = "startTime", required = false) String startTime,
            @RequestParam(name = "endTime", required = false) String endTime,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        CurrentUser currentUser = require("audit:operation:view");
        return ApiResponse.success(
                systemManagementAppService.listVerificationLogs(currentUser, channel, scene, resultStatus, startTime, endTime, pageNo, pageSize),
                TraceContext.getRequestId()
        );
    }

    private CurrentUser require(String permissionKey) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        permissionGuard.requirePermission(currentUser, permissionKey);
        return requireTrustedUser(currentUser);
    }

    private CurrentUser requireTrustedUser(CurrentUser currentUser) {
        if (!isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return currentUser;
    }
}
