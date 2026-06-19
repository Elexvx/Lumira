package com.lumira.saas.modules.system.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.saas.common.annotation.RepeatSubmit;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.common.web.TraceContext;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.security.PermissionGuard;
import com.lumira.saas.modules.system.app.OnlineSessionManagementAppService;
import com.lumira.saas.modules.system.vo.SystemVO;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/system/online-users")
public class OnlineSessionController {

    private final OnlineSessionManagementAppService onlineSessionManagementAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;

    public OnlineSessionController(
            OnlineSessionManagementAppService onlineSessionManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this.onlineSessionManagementAppService = onlineSessionManagementAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping
    public ApiResponse<PageResponse<SystemVO.OnlineSessionVO>> list(
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        require("system:online-user:view");
        return ApiResponse.success(
                onlineSessionManagementAppService.listOnlineSessions(securityContextFacade.getCurrentUser(), pageNo, pageSize),
                TraceContext.getRequestId()
        );
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events() {
        require("system:online-user:view");
        return onlineSessionManagementAppService.stream(securityContextFacade.getCurrentUser());
    }

    @DeleteMapping("/{sessionId}")
    @RepeatSubmit
    public ApiResponse<Boolean> kick(@PathVariable("sessionId") String sessionId) {
        require("system:online-user:kick");
        return ApiResponse.success(
                onlineSessionManagementAppService.kickSession(securityContextFacade.getCurrentUser(), sessionId),
                TraceContext.getRequestId()
        );
    }

    @PatchMapping("/{userId}/ban")
    @RepeatSubmit
    public ApiResponse<Boolean> ban(@PathVariable("userId") Long userId) {
        require("system:online-user:ban");
        return ApiResponse.success(
                onlineSessionManagementAppService.banUser(securityContextFacade.getCurrentUser(), userId),
                TraceContext.getRequestId()
        );
    }

    private void require(String permissionKey) {
        permissionGuard.requirePermission(securityContextFacade.getCurrentUser(), permissionKey);
    }
}
