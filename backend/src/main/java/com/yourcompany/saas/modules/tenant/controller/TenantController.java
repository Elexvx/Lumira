package com.yourcompany.saas.modules.tenant.controller;

import com.yourcompany.saas.common.api.ApiResponse;
import com.yourcompany.saas.common.vo.PageResponse;
import com.yourcompany.saas.infrastructure.observability.TraceContext;
import com.yourcompany.saas.infrastructure.security.SecurityContextFacade;
import com.yourcompany.saas.modules.iam.service.PermissionGuard;
import com.yourcompany.saas.modules.tenant.app.TenantAppService;
import com.yourcompany.saas.modules.tenant.dto.TenantDTO;
import com.yourcompany.saas.modules.tenant.dto.SwitchTenantRequest;
import com.yourcompany.saas.modules.tenant.vo.CurrentTenantVO;
import com.yourcompany.saas.modules.tenant.vo.MyTenantVO;
import com.yourcompany.saas.modules.tenant.vo.SwitchTenantVO;
import com.yourcompany.saas.modules.tenant.vo.TenantSummaryVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tenant")
public class TenantController {

    private final TenantAppService tenantAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;

    public TenantController(TenantAppService tenantAppService, SecurityContextFacade securityContextFacade, PermissionGuard permissionGuard) {
        this.tenantAppService = tenantAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
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

    @GetMapping("/tenants")
    public ApiResponse<PageResponse<TenantSummaryVO>> tenants(
            @RequestParam(name = "tenantCode", required = false) String tenantCode,
            @RequestParam(name = "tenantName", required = false) String tenantName,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        permissionGuard.requirePermission(securityContextFacade.getCurrentUser(), "tenant:view");
        return ApiResponse.success(
                tenantAppService.listTenants(securityContextFacade.getCurrentUser(), tenantCode, tenantName, status, pageNo, pageSize),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/tenants/{id}")
    public ApiResponse<TenantSummaryVO> tenant(@PathVariable("id") Long id) {
        permissionGuard.requirePermission(securityContextFacade.getCurrentUser(), "tenant:view");
        return ApiResponse.success(tenantAppService.getTenant(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    @PostMapping("/tenants")
    public ApiResponse<TenantSummaryVO> createTenant(@Valid @RequestBody TenantDTO.TenantUpsertRequest request) {
        return ApiResponse.success(tenantAppService.createTenant(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @PutMapping("/tenants/{id}")
    public ApiResponse<TenantSummaryVO> updateTenant(@PathVariable("id") Long id, @Valid @RequestBody TenantDTO.TenantUpsertRequest request) {
        return ApiResponse.success(tenantAppService.updateTenant(securityContextFacade.getCurrentUser(), id, request), TraceContext.getRequestId());
    }

    @DeleteMapping("/tenants/{id}")
    public ApiResponse<Boolean> deleteTenant(@PathVariable("id") Long id) {
        return ApiResponse.success(tenantAppService.deleteTenant(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
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
