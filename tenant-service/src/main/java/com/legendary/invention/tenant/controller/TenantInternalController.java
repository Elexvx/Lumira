package com.legendary.invention.tenant.controller;

import com.legendary.invention.api.tenant.MyTenantDTO;
import com.legendary.invention.api.tenant.TenantSummaryDTO;
import com.legendary.invention.api.tenant.TenantSwitchCheckDTO;
import com.legendary.invention.tenant.domain.TenantDomainService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/internal/tenant")
public class TenantInternalController {

    private final TenantDomainService tenantDomainService;

    public TenantInternalController(TenantDomainService tenantDomainService) {
        this.tenantDomainService = tenantDomainService;
    }

    @GetMapping("/users/{userId}/visible-tenants")
    public List<MyTenantDTO> listVisibleTenants(@PathVariable("userId") Long userId) {
        return tenantDomainService.listVisibleTenants(userId);
    }

    @GetMapping("/summary/{tenantId}")
    public TenantSummaryDTO findTenantSummary(@PathVariable("tenantId") Long tenantId) {
        return tenantDomainService.findTenantById(tenantId).map(tenantDomainService::toTenantSummaryDTO).orElse(null);
    }

    @GetMapping("/users/{userId}/switch-check")
    public TenantSwitchCheckDTO validateTenantSwitch(@PathVariable("userId") Long userId, @RequestParam("tenantId") Long tenantId) {
        return tenantDomainService.validateTenantSwitch(userId, tenantId);
    }
}
