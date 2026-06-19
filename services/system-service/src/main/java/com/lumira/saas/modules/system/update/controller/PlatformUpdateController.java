package com.lumira.saas.modules.system.update.controller;

import com.lumira.common.web.TraceContext;
import com.lumira.common.api.ApiResponse;
import com.lumira.saas.common.annotation.RepeatSubmit;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.security.PermissionGuard;
import com.lumira.saas.modules.system.update.app.PlatformUpdateAppService;
import com.lumira.saas.modules.system.update.vo.PlatformUpdateVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/v1/system/update")
public class PlatformUpdateController {

    private final PlatformUpdateAppService platformUpdateAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;

    public PlatformUpdateController(
            PlatformUpdateAppService platformUpdateAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this.platformUpdateAppService = platformUpdateAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping("/status")
    public ApiResponse<PlatformUpdateVO.StatusVO> status() {
        require("system:update:view");
        return ApiResponse.success(platformUpdateAppService.getStatus(), TraceContext.getRequestId());
    }

    @PostMapping("/check")
    @RepeatSubmit
    public ApiResponse<PlatformUpdateVO.StatusVO> check() {
        require("system:update:check");
        return ApiResponse.success(platformUpdateAppService.checkLatest(), TraceContext.getRequestId());
    }

    @PostMapping("/install")
    @RepeatSubmit
    public ApiResponse<PlatformUpdateVO.TaskVO> install() {
        require("system:update:install");
        return ApiResponse.success(platformUpdateAppService.install(securityContextFacade.getCurrentUser()), TraceContext.getRequestId());
    }

    @PostMapping("/rollback")
    @RepeatSubmit
    public ApiResponse<PlatformUpdateVO.TaskVO> rollback() {
        require("system:update:rollback");
        return ApiResponse.success(platformUpdateAppService.rollback(securityContextFacade.getCurrentUser()), TraceContext.getRequestId());
    }

    @GetMapping("/tasks")
    public ApiResponse<List<PlatformUpdateVO.TaskVO>> tasks() {
        require("system:update:view");
        return ApiResponse.success(platformUpdateAppService.listTasks(), TraceContext.getRequestId());
    }

    @GetMapping("/tasks/{id}")
    public ApiResponse<PlatformUpdateVO.TaskVO> task(@PathVariable Long id) {
        require("system:update:view");
        return ApiResponse.success(platformUpdateAppService.getTask(id), TraceContext.getRequestId());
    }

    private void require(String permissionKey) {
        permissionGuard.requirePermission(securityContextFacade.getCurrentUser(), permissionKey);
    }
}
