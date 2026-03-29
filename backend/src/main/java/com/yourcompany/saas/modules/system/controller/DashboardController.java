package com.yourcompany.saas.modules.system.controller;

import com.yourcompany.saas.common.api.ApiResponse;
import com.yourcompany.saas.infrastructure.observability.TraceContext;
import com.yourcompany.saas.infrastructure.security.SecurityContextFacade;
import com.yourcompany.saas.modules.system.app.SystemManagementAppService;
import com.yourcompany.saas.modules.system.vo.SystemVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final SystemManagementAppService systemManagementAppService;
    private final SecurityContextFacade securityContextFacade;

    public DashboardController(SystemManagementAppService systemManagementAppService, SecurityContextFacade securityContextFacade) {
        this.systemManagementAppService = systemManagementAppService;
        this.securityContextFacade = securityContextFacade;
    }

    @GetMapping("/summary")
    public ApiResponse<SystemVO.DashboardSummaryVO> summary() {
        return ApiResponse.success(
                systemManagementAppService.dashboardSummary(securityContextFacade.getCurrentUser()),
                TraceContext.getRequestId()
        );
    }
}
