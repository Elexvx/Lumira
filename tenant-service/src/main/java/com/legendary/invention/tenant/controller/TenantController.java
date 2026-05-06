package com.legendary.invention.tenant.controller;

import com.legendary.invention.api.client.AuthInternalApi;
import com.legendary.invention.api.tenant.MyTenantDTO;
import com.legendary.invention.api.tenant.TenantSummaryDTO;
import com.legendary.invention.api.tenant.TenantSwitchCheckDTO;
import com.legendary.invention.api.tenant.TenantSwitchRequest;
import com.legendary.invention.common.api.ApiResponse;
import com.legendary.invention.common.exception.BizException;
import com.legendary.invention.common.security.CurrentUser;
import com.legendary.invention.common.security.SecurityContextFacade;
import com.legendary.invention.common.web.TraceContext;
import com.legendary.invention.tenant.domain.TenantDomainService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tenant")
public class TenantController {

    private final TenantDomainService tenantDomainService;
    private final SecurityContextFacade securityContextFacade;
    private final AuthInternalApi authInternalApi;

    public TenantController(TenantDomainService tenantDomainService, SecurityContextFacade securityContextFacade, AuthInternalApi authInternalApi) {
        this.tenantDomainService = tenantDomainService;
        this.securityContextFacade = securityContextFacade;
        this.authInternalApi = authInternalApi;
    }

    @GetMapping("/visible")
    public ApiResponse<List<MyTenantDTO>> visibleTenants() {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        return ApiResponse.success(tenantDomainService.listVisibleTenants(currentUser.getUserId()), TraceContext.getRequestId());
    }

    @GetMapping("/summary/{tenantId}")
    public ApiResponse<TenantSummaryDTO> tenantSummary(@PathVariable("tenantId") Long tenantId) {
        return ApiResponse.success(tenantDomainService.findTenantById(tenantId).map(tenantDomainService::toTenantSummaryDTO).orElse(null), TraceContext.getRequestId());
    }

    @GetMapping("/switch/check")
    public ApiResponse<TenantSwitchCheckDTO> switchCheck(@RequestParam("tenantId") Long tenantId) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        return ApiResponse.success(tenantDomainService.validateTenantSwitch(currentUser.getUserId(), tenantId), TraceContext.getRequestId());
    }

    @PostMapping("/switch")
    public ApiResponse<Boolean> switchTenant(@Valid @RequestBody TenantSwitchRequest request) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        TenantSwitchCheckDTO check = tenantDomainService.validateTenantSwitch(currentUser.getUserId(), request.tenantId());
        if (!check.allowed()) {
            throw new BizException(com.legendary.invention.common.enums.ErrorCode.TENANT_NOT_BOUND, check.message());
        }
        if (request.tenantId().equals(currentUser.getCurrentTenantId())) {
            return ApiResponse.success(Boolean.TRUE, TraceContext.getRequestId());
        }
        authInternalApi.switchSessionTenant(currentUser.getSessionId(), request.tenantId());
        return ApiResponse.success(Boolean.TRUE, TraceContext.getRequestId());
    }
}
