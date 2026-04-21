package com.legendary.invention.saas.modules.system.controller;

import com.legendary.invention.saas.common.api.ApiResponse;
import com.legendary.invention.saas.infrastructure.observability.TraceContext;
import com.legendary.invention.saas.infrastructure.security.SecurityContextFacade;
import com.legendary.invention.saas.modules.system.app.SystemManagementAppService;
import com.legendary.invention.saas.modules.system.vo.SystemVO;
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
