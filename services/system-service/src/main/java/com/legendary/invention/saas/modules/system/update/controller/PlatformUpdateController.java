package com.legendary.invention.saas.modules.system.update.controller;

import com.legendary.invention.common.web.TraceContext;
import com.legendary.invention.saas.common.api.ApiResponse;
import com.legendary.invention.saas.infrastructure.security.SecurityContextFacade;
import com.legendary.invention.saas.modules.iam.service.PermissionGuard;
import com.legendary.invention.saas.modules.system.update.app.PlatformUpdateAppService;
import com.legendary.invention.saas.modules.system.update.vo.PlatformUpdateVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public ApiResponse<PlatformUpdateVO.StatusVO> check() {
        require("system:update:check");
        return ApiResponse.success(platformUpdateAppService.checkLatest(), TraceContext.getRequestId());
    }

    private void require(String permissionKey) {
        permissionGuard.requirePermission(securityContextFacade.getCurrentUser(), permissionKey);
    }
}
