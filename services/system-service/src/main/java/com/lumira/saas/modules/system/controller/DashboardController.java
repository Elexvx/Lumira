package com.lumira.saas.modules.system.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.web.TraceContext;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.saas.modules.system.app.SystemManagementAppService;
import com.lumira.saas.modules.system.vo.SystemVO;
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
