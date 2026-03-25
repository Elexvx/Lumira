package com.yourcompany.saas.modules.tenant.controller;

import com.yourcompany.saas.common.api.ApiResponse;
import com.yourcompany.saas.infrastructure.observability.TraceContext;
import com.yourcompany.saas.infrastructure.security.SecurityContextFacade;
import com.yourcompany.saas.modules.tenant.app.TenantAppService;
import com.yourcompany.saas.modules.tenant.dto.SwitchTenantRequest;
import com.yourcompany.saas.modules.tenant.vo.CurrentTenantVO;
import com.yourcompany.saas.modules.tenant.vo.MyTenantVO;
import com.yourcompany.saas.modules.tenant.vo.SwitchTenantVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tenant")
public class TenantController {

    private final TenantAppService tenantAppService;
    private final SecurityContextFacade securityContextFacade;

    public TenantController(TenantAppService tenantAppService, SecurityContextFacade securityContextFacade) {
        this.tenantAppService = tenantAppService;
        this.securityContextFacade = securityContextFacade;
    }

    @GetMapping("/current")
    public ApiResponse<CurrentTenantVO> currentTenant() {
        CurrentTenantVO response = tenantAppService.currentTenant(securityContextFacade.getCurrentUser());
        return ApiResponse.success(response, TraceContext.getRequestId());
    }

    @GetMapping("/my-tenants")
    public ApiResponse<List<MyTenantVO>> myTenants() {
        List<MyTenantVO> response = tenantAppService.myTenants(securityContextFacade.getCurrentUser());
        return ApiResponse.success(response, TraceContext.getRequestId());
    }

    @PostMapping("/switch")
    public ApiResponse<SwitchTenantVO> switchTenant(@Valid @RequestBody SwitchTenantRequest request, HttpServletRequest httpServletRequest) {
        SwitchTenantVO response = tenantAppService.switchTenant(
                securityContextFacade.getCurrentUser(),
                request.getTenantId(),
                resolveClientIp(httpServletRequest),
                httpServletRequest.getHeader("User-Agent"));
        return ApiResponse.success(response, TraceContext.getRequestId());
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            int commaIndex = xff.indexOf(",");
            return commaIndex > 0 ? xff.substring(0, commaIndex).trim() : xff.trim();
        }
        return request.getRemoteAddr();
    }
}
